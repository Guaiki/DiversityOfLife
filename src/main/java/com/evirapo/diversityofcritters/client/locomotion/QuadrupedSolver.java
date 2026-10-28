package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.locomotion.math.PlaneFit;
import com.evirapo.diversityofcritters.client.locomotion.math.Spring;
import com.evirapo.diversityofcritters.client.locomotion.math.SupportFilter;
import com.evirapo.diversityofcritters.client.locomotion.math.TrimmedMean;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * The decisions of a rigid-legged body standing on uneven ground, free of Minecraft types.
 *
 * <p>Corrections are ranked by authority. The body tilts (pitch and roll from a plane through the
 * supporting feet), then rises or sinks to the height most feet agree on, and each leg stretches or
 * shrinks within a small limit for whatever is left. A one-bone leg cannot bend, so tilting the
 * body is the only correction with enough range for a whole block.
 *
 * <p>All lengths are model units; "up" values are positive upward, while pelvis {@code drop} follows
 * model space (+Y down). The caller fills the per-leg inputs, calls {@link #solve}, then reads the
 * pelvis springs in {@link LocomotionState} and each leg's {@code footLift}.
 */
public final class QuadrupedSolver {

    /** Depth inside its ground at which a sole counts as certainly in contact. */
    static final float CONTACT_DEPTH = 1.0f;
    /** Total foot weight at which the body counts as fully standing. */
    static final float STANDING_WEIGHT = 0.4f;
    /** Airborne time over which the height correction lets go: falling is the entity's job. */
    static final float DROP_RELEASE_SECONDS = 0.08f;
    /** Foot springs: quick to take up a correction, slower to let it go. */
    static final float ACQUIRE_HALF_LIFE = 0.04f;
    static final float RELEASE_HALF_LIFE = 0.15f;
    /** Rate at which contact and support weights follow their targets, per second. */
    static final float WEIGHT_RATE = 15.0f;
    /** Pitch smoothing on terrain, and while a mantle poses the body, seconds. */
    static final float PITCH_HALF_LIFE = 0.18f;
    static final float MANTLE_PITCH_HALF_LIFE = 0.08f;
    /** Share of the ideal tilt that actually reaches a foot (the drop limit eats the rest). */
    static final float TILT_EFFICIENCY = 0.85f;

    final int legCount;
    final float maxPitch;
    final float maxRoll;
    final float stretchLimit;
    final float maxDrop;
    final float legLength;

    // Per-leg inputs.
    final float[] hipX;
    final float[] hipZ;
    /** Animated sole height, up positive, from any origin shared by all legs. */
    final float[] soleUp;
    /** Ground height under each sole, same origin. */
    final float[] groundUp;

    // Per-leg working values and outputs.
    /** How much each foot is heard by the body: max(plant, contact) times support. */
    final float[] weight;
    final float[] bodySample;
    final float[] bodyLift;
    private final float[] footLift;
    private final int[] order;
    private final PlaneFit.Plane plane = new PlaneFit.Plane();

    /** Mantle input, set before {@link #solve}: its authority in [0, 1] and the pitch it asks for. */
    float mantleAuthority;
    float mantlePitch;
    /** Take this frame's targets at once instead of following them: the pose is starting over. */
    boolean snap;

    /** Unsmoothed targets this frame, for debug. */
    float pitchTarget;
    float rollTarget;
    float dropTarget;
    float authority;

    /**
     * @param maxPitchRad  pitch limit
     * @param maxRollRad   roll limit
     * @param stretchLimit fraction a leg may lengthen or shorten
     * @param maxDropFraction pelvis rise/sink limit, as a fraction of leg length
     * @param legLength    hip to sole, model units
     */
    public QuadrupedSolver(int legCount, float maxPitchRad, float maxRollRad, float stretchLimit,
                           float maxDropFraction, float legLength) {
        this.legCount = legCount;
        this.maxPitch = maxPitchRad;
        this.maxRoll = maxRollRad;
        this.stretchLimit = stretchLimit;
        this.maxDrop = maxDropFraction * legLength;
        this.legLength = legLength;
        this.hipX = new float[legCount];
        this.hipZ = new float[legCount];
        this.soleUp = new float[legCount];
        this.groundUp = new float[legCount];
        this.weight = new float[legCount];
        this.bodySample = new float[legCount];
        this.bodyLift = new float[legCount];
        this.footLift = new float[legCount];
        this.order = new int[legCount];
    }

    /**
     * Updates contact and support per leg, then the pelvis springs.
     *
     * @param gate how much the rig is allowed to act, in [0, 1]
     */
    public void solve(LocomotionState state, float gate, float dt) {
        final LocomotionState.LegState[] legs = state.legs();
        final boolean smooth = state.initialised && !this.snap;
        final float follow = Spring.blendFactor(WEIGHT_RATE, dt);
        // A mantle poses the body itself, and leaves the terrain behind while it does: over the crest
        // the animated hind feet are still over the ground below the wall. Kept lagging, that would
        // tip the body up on top when the terrain takes back over. So under a mantle the feet
        // follow the ground as it is, and the terrain starts from where the legs really are.
        final float footLag = 1.0f - clamp01(this.mantleAuthority);

        // 1. What each foot asks for, and whether it is touching.
        for (int i = 0; i < this.legCount; i++) {
            final LocomotionState.LegState leg = legs[i];
            final float lift = this.groundUp[i] - this.soleUp[i];
            leg.debugLift = smoothFoot(leg.footLift, lift, dt, smooth, footLag);

            final float contact = state.falling() ? 0.0f : clamp01(leg.footLift.value() / CONTACT_DEPTH);
            leg.contactWeight = smooth ? leg.contactWeight + (contact - leg.contactWeight) * follow : contact;
            this.weight[i] = Math.max(leg.plantWeight, leg.contactWeight);
        }

        // 2. Which feet really carry the body, and what the body should answer for each.
        final int reference = SupportFilter.reference(this.footLifts(legs), this.weight, this.legCount);
        final float stretchRange = 2.0f * this.stretchLimit * this.legLength;
        for (int i = 0; i < this.legCount; i++) {
            final LocomotionState.LegState leg = legs[i];
            if (reference < 0) {
                this.bodySample[i] = this.groundUp[i] - this.soleUp[i];
                continue;
            }
            final float envelope = SupportFilter.reachEnvelope(
                    this.hipX[i] - this.hipX[reference], this.hipZ[i] - this.hipZ[reference],
                    (float) Math.sin(this.maxPitch), (float) Math.sin(this.maxRoll), TILT_EFFICIENCY, stretchRange);
            final float support = SupportFilter.support(
                    this.groundUp[reference] - this.groundUp[i], envelope,
                    this.soleUp[i] - this.soleUp[reference], stretchRange);
            leg.support = smooth ? leg.support + (support - leg.support) * follow : support;
            this.weight[i] *= leg.support;

            // The ground under this foot measured from the reference sole: terrain only, without
            // the height the animation happened to draw this foot at.
            this.bodySample[i] = this.groundUp[i] - this.soleUp[reference];
        }
        for (int i = 0; i < this.legCount; i++) {
            this.bodyLift[i] = smoothFoot(legs[i].bodyLift, this.bodySample[i], dt, smooth, footLag);
        }

        // 3. Tilt from the plane, height from the consensus.
        PlaneFit.fit(this.hipX, this.hipZ, this.bodyLift, this.weight, this.legCount, this.plane);
        // Model +Y is down: a positive xRot lowers the front (-Z), a positive zRot lowers +X.
        this.pitchTarget = clamp((float) Math.atan(this.plane.slopeZ), this.maxPitch);
        this.rollTarget = clamp((float) -Math.atan(this.plane.slopeX), this.maxRoll);
        final float consensus = TrimmedMean.of(this.bodyLift, this.weight, this.order, this.legCount);
        this.dropTarget = clamp(-consensus, this.maxDrop);

        // 4. How much of it to apply: nothing without the gate, nothing in the air.
        float standing = 0.0f;
        for (int i = 0; i < this.legCount; i++) {
            standing += this.weight[i];
        }
        this.authority = gate * smoothstep(standing / STANDING_WEIGHT);
        final float grounded = 1.0f - smoothstep(state.airborneSeconds / DROP_RELEASE_SECONDS);

        float pitch = this.pitchTarget * this.authority;
        float roll = this.rollTarget * this.authority;
        float drop = this.dropTarget * this.authority * grounded;
        // A mantle takes the pitch over, past the terrain limit, and lets roll and height go:
        // the body is rearing against a wall, not standing on the ground under its feet.
        final float mantle = clamp01(this.mantleAuthority);
        if (mantle > 0.0f) {
            pitch += (this.mantlePitch * gate - pitch) * mantle;
            roll *= 1.0f - mantle;
            drop *= 1.0f - mantle;
        }
        // The terrain pitch is noisy and wants heavy smoothing; a mantle's is already a smooth curve
        // that has to be followed closely, or the body falls behind the legs placed around it.
        state.pelvisPitch.setHalfLife(PITCH_HALF_LIFE + (MANTLE_PITCH_HALF_LIFE - PITCH_HALF_LIFE) * mantle);
        updateOrSnap(state.pelvisPitch, pitch, dt, smooth);
        updateOrSnap(state.pelvisRoll, roll, dt, smooth);
        updateOrSnap(state.pelvisDrop, drop, dt, smooth);
    }

    private float[] footLifts(LocomotionState.LegState[] legs) {
        for (int i = 0; i < this.legCount; i++) {
            this.footLift[i] = legs[i].footLift.value();
        }
        return this.footLift;
    }

    /**
     * How much of a leg's remaining correction to apply. A sole below its ground always comes
     * out; a sole above it is only reached down for as much as the foot is planted, so a foot
     * lifted in swing keeps its stride.
     *
     * @param residual up positive, model units
     */
    public static float appliedResidual(float residual, float footWeight, float gate) {
        return residual > 0.0f ? residual * gate : residual * footWeight * gate;
    }

    /** Leg Y-scale change that moves the sole up by {@code residual}, within the stretch limit. */
    public float stretchFor(float residual) {
        return clamp(-residual / this.legLength, this.stretchLimit);
    }

    /**
     * Where a hip moves when its parent pelvis tilts by (dPitch, dRoll) on top of its animated
     * rotation, in the pelvis's parent frame. Rotation order matches {@code ModelPart}: Z, Y, X.
     */
    public static void hipShift(Matrix3f scratch, float xRot, float yRot, float zRot, float dPitch, float dRoll,
                                float hx, float hy, float hz, Vector3f out) {
        scratch.rotationZYX(zRot + dRoll, yRot, xRot + dPitch);
        scratch.transform(hx, hy, hz, out);
        final float afterX = out.x;
        final float afterY = out.y;
        final float afterZ = out.z;
        scratch.rotationZYX(zRot, yRot, xRot);
        scratch.transform(hx, hy, hz, out);
        out.set(afterX - out.x, afterY - out.y, afterZ - out.z);
    }

    /** @param lag share of the usual smoothing, 1 on terrain, 0 (none) under a mantle */
    private static float smoothFoot(Spring spring, float target, float dt, boolean smooth, float lag) {
        if (!smooth) {
            spring.snap(target);
            return target;
        }
        final float held = spring.value();
        final boolean acquiring = target * held < 0.0f || Math.abs(target) > Math.abs(held);
        spring.setHalfLife((acquiring ? ACQUIRE_HALF_LIFE : RELEASE_HALF_LIFE) * lag);
        return spring.update(target, dt);
    }

    private static void updateOrSnap(Spring spring, float target, float dt, boolean smooth) {
        if (smooth) {
            spring.update(target, dt);
        } else {
            spring.snap(target);
        }
    }

    static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static float clamp01(float x) {
        return x < 0.0f ? 0.0f : Math.min(x, 1.0f);
    }

    private static float smoothstep(float x) {
        final float t = clamp01(x);
        return t * t * (3.0f - 2.0f * t);
    }
}
