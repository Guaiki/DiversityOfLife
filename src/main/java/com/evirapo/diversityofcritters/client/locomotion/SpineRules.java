package com.evirapo.diversityofcritters.client.locomotion;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Secondary responses of head, neck and tail to what the rig does to the body.
 *
 * <ul>
 *   <li><b>STABILISE</b>: the bone gives back part of the terrain tilt it inherits from the pelvis,
 *       so a head stays nearer level while the body climbs a step. The fraction is of the tilt in
 *       the world: a stabilised ancestor's share is not given back twice.</li>
 *   <li><b>LAG</b>: the bone follows the body's tilt and turning late and settles with a little
 *       overshoot, so a tail trails a turn instead of being welded to the rump.</li>
 *   <li><b>ARCH</b>: a manoeuvre bends the spine by one scalar curvature, shared out by weight:
 *       each arched bone's {@code xRot} gains curvature × weight, with one weight for flexing
 *       (positive curvature) and another for extending (negative). With a rigid torso the bend
 *       lives in the head and tail.</li>
 * </ul>
 *
 * <p>Declared on a rig builder, bound once to the model's bones; per-entity memory lives in
 * {@link LocomotionState}. Followers are tracked every frame, even while the rig is closed, so
 * reopening it never whips a bone toward a stale value.
 */
@OnlyIn(Dist.CLIENT)
public final class SpineRules {

    enum Kind { STABILISE, LAG, ARCH }

    /** What a builder asked for, before any bone is resolved. */
    record Declaration(Kind kind, String boneName, float fraction, float halfLife, float damping, float maxDegrees,
                       float extendFraction) {
    }

    /** Declarations collected by a rig builder. */
    public static final class Declarations {
        private final List<Declaration> declared = new ArrayList<>();

        public void stabilise(String bone, float fraction) {
            this.declared.add(new Declaration(Kind.STABILISE, bone, fraction, 0.0f, 0.0f, 0.0f, 0.0f));
        }

        public void lag(String bone, float halfLifeSeconds, float damping, float maxDegrees) {
            this.declared.add(new Declaration(Kind.LAG, bone, 0.0f, halfLifeSeconds, damping, maxDegrees, 0.0f));
        }

        public void arch(String bone, float weight) {
            this.arch(bone, weight, weight);
        }

        public void arch(String bone, float flexWeight, float extendWeight) {
            this.declared.add(new Declaration(Kind.ARCH, bone, flexWeight, 0.0f, 0.0f, 0.0f, extendWeight));
        }

        /**
         * Resolves every declared bone.
         *
         * @throws IllegalArgumentException naming the first bone that does not exist
         */
        public SpineRules bind(Function<String, ModelPart> lookup) {
            final Rule[] rules = new Rule[this.declared.size()];
            for (int i = 0; i < rules.length; i++) {
                final Declaration declaration = this.declared.get(i);
                final ModelPart bone = lookup.apply(declaration.boneName());
                if (bone == null) {
                    throw new IllegalArgumentException("spine bone '" + declaration.boneName() + "' not found");
                }
                rules[i] = new Rule(declaration, bone);
            }
            // Whatever a stabilised ancestor gives back, its descendants receive already.
            for (Rule rule : rules) {
                if (rule.kind != Kind.STABILISE) {
                    continue;
                }
                for (Rule other : rules) {
                    if (other != rule && other.kind == Kind.STABILISE
                            && other.bone.getAllParts().anyMatch(part -> part == rule.bone)) {
                        rule.stabiliseShare -= other.fraction;
                    }
                }
            }
            return new SpineRules(rules);
        }
    }

    static final class Rule {
        final Kind kind;
        final ModelPart bone;
        final float fraction;
        final float halfLife;
        final float damping;
        final float maxRadians;
        /** ARCH: the weight while the spine extends; {@link #fraction} is the one while it flexes. */
        final float extendFraction;
        /** STABILISE: the fraction this bone itself gives back, after its ancestors. */
        float stabiliseShare;

        Rule(Declaration declaration, ModelPart bone) {
            this.kind = declaration.kind();
            this.bone = bone;
            this.fraction = declaration.fraction();
            this.halfLife = declaration.halfLife();
            this.damping = declaration.damping();
            this.maxRadians = (float) Math.toRadians(declaration.maxDegrees());
            this.extendFraction = declaration.extendFraction();
            this.stabiliseShare = declaration.fraction();
        }
    }

    private final Rule[] rules;

    private SpineRules(Rule[] rules) {
        this.rules = rules;
    }

    public int size() {
        return this.rules.length;
    }

    /** Makes the followers start over from the body's tilt next frame, as on the first one. */
    public void reseed(LocomotionState state) {
        for (LocomotionState.SpineState s : state.ensureSpine(this.rules.length)) {
            s.seeded = false;
        }
    }

    /**
     * Advances the followers toward the body's current tilt and heading. Call every frame, whether
     * or not the pose is written; a zero {@code dt} changes nothing.
     */
    public void track(LocomotionState state, float pitch, float roll, float bodyYawDeg, float dt) {
        final LocomotionState.SpineState[] spine = state.ensureSpine(this.rules.length);
        for (int i = 0; i < this.rules.length; i++) {
            final Rule rule = this.rules[i];
            if (rule.kind != Kind.LAG) {
                continue;
            }
            final LocomotionState.SpineState s = spine[i];
            if (!s.seeded) {
                s.pitch.snap(pitch);
                s.roll.snap(roll);
                s.yaw.snap(bodyYawDeg);
                s.seeded = true;
                continue;
            }
            s.pitch.setHalfLife(rule.halfLife);
            s.pitch.setDamping(rule.damping);
            s.roll.setHalfLife(rule.halfLife);
            s.roll.setDamping(rule.damping);
            s.yaw.setHalfLife(rule.halfLife);
            s.yaw.setDamping(rule.damping);
            s.pitch.update(pitch, dt);
            s.roll.update(roll, dt);
            // Heading kept continuous: follow the shortest way round, never across the 180 seam.
            s.yaw.update(s.yaw.value() + Mth.wrapDegrees(bodyYawDeg - s.yaw.value()), dt);
        }
    }

    /** {@link #write(LocomotionState, float, float, float, float, float)} with a straight spine. */
    public void write(LocomotionState state, float pitch, float roll, float bodyYawDeg, float gate) {
        this.write(state, pitch, roll, bodyYawDeg, gate, 0.0f);
    }

    /**
     * Writes the responses onto the bones, scaled by the gate.
     *
     * @param pitch      tilt the rig added to the pelvis this frame
     * @param roll       likewise
     * @param bodyYawDeg body yaw this frame
     * @param arch       spine curvature a manoeuvre asks for, radians, already scaled by its
     *                   authority and the gate
     */
    public void write(LocomotionState state, float pitch, float roll, float bodyYawDeg, float gate, float arch) {
        final LocomotionState.SpineState[] spine = state.ensureSpine(this.rules.length);
        for (int i = 0; i < this.rules.length; i++) {
            final Rule rule = this.rules[i];
            final ModelPart bone = rule.bone;
            if (rule.kind == Kind.ARCH) {
                if (arch != 0.0f) {
                    bone.xRot += arch * (arch > 0.0f ? rule.fraction : rule.extendFraction);
                }
                continue;
            }
            if (rule.kind == Kind.STABILISE) {
                // pitch and roll are already scaled by the rig's authority.
                bone.xRot -= pitch * rule.stabiliseShare;
                bone.zRot -= roll * rule.stabiliseShare;
                continue;
            }
            final LocomotionState.SpineState s = spine[i];
            // The follower is where the bone would like to be; the body is already at the current
            // value, so the difference is how far the bone trails.
            final float trailPitch = clamp(s.pitch.value() - pitch, rule.maxRadians) * gate;
            final float trailRoll = clamp(s.roll.value() - roll, rule.maxRadians) * gate;
            // Model yRot turns opposite to body yaw: the renderer mirrors the model's X axis.
            final float trailYaw = clamp((float) Math.toRadians(Mth.wrapDegrees(s.yaw.value() - bodyYawDeg)),
                    rule.maxRadians) * gate;
            bone.xRot += trailPitch;
            bone.zRot += trailRoll;
            bone.yRot += trailYaw;
            s.debugTrailYaw = trailYaw;
        }
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
