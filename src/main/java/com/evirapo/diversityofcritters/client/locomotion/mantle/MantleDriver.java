package com.evirapo.diversityofcritters.client.locomotion.mantle;

import com.evirapo.diversityofcritters.client.locomotion.LocomotionState;
import com.evirapo.diversityofcritters.common.locomotion.MantleMotion;
import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleProgress;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Poses a quadruped climbing a ledge, from the mantle the entity reports. A module of the rig, not
 * a rig of its own: it writes what the rig then solves.
 *
 * <table>
 *   <tr><th>Phase</th><th>Front paws</th><th>Hind feet</th><th>Body</th></tr>
 *   <tr><td>REACH</td><td>up to their first grips, one after the other</td><td>a step each, keeping up with the body</td><td>0 to reach pitch</td></tr>
 *   <tr><td>RISE</td><td>climb the face in alternate grips to the lip</td><td>alternate steps toward the wall</td><td>to rise pitch</td></tr>
 *   <tr><td>CREST</td><td>from the lip onto the top, one after the other</td><td>scramble onto the face, one then the other</td><td>to crest pitch</td></tr>
 *   <tr><td>RECOVER</td><td>hand back to the walk</td><td>hand back to the walk</td><td>level</td></tr>
 * </table>
 *
 * <p>Where the paws go is {@link GripPlanner}'s call. The first front leg in the rig leads, and the
 * hind foot diagonal to it moves with it, as in a walk: some leg is always moving, so the critter
 * never seems to stop for the climb. The walk cycle is held back only while the climb places the
 * legs ({@link LocomotionState#mantleCycleHold}).
 *
 * <p>The server raises the entity through RISE and CREST. Until the crest the body stands on its
 * hind legs at the foot of the wall, so the pose holds the body down by as much as the entity has
 * risen ({@link LocomotionState#mantleBodyDrop}), letting go over the crest as the hind feet leave
 * the ground.
 *
 * <p>Everything scales with {@link LocomotionState#mantleAuthority}, a spring: starting, finishing
 * or aborting a mantle blends, never snaps. Allocation free.
 */
@OnlyIn(Dist.CLIENT)
public final class MantleDriver {

    /** Below this the mantle has let go of the pose. */
    static final float AUTHORITY_EPSILON = 1.0e-3f;

    private final MantleProfile profile;
    private final boolean[] rear;
    /** Leg indices: the leading front paw, the other front paw, the hind foot diagonal to the lead, the other. */
    private final int leadFront;
    private final int otherFront;
    private final int leadHind;
    private final int otherHind;
    private final GripPlanner.Hand leading = new GripPlanner.Hand();
    private final GripPlanner.Hand trailing = new GripPlanner.Hand();
    private final GripPlanner.Hand leadingHind = new GripPlanner.Hand();
    private final GripPlanner.Hand trailingHind = new GripPlanner.Hand();
    /** Hind leg length, hip to sole, model units: how far below the hips the feet can push. */
    private final float hindLength;

    /**
     * @param rear per leg, in the rig's order: whether it is a hind leg
     * @param side per leg: its hip's sideways position (model X), to pair the diagonals
     * @param length per leg: hip to sole, model units
     */
    public MantleDriver(MantleProfile profile, boolean[] rear, float[] side, float[] length) {
        this.profile = profile;
        this.rear = rear.clone();
        int leadFront = -1;
        int otherFront = -1;
        int leadHind = -1;
        int otherHind = -1;
        for (int i = 0; i < rear.length; i++) {
            if (!rear[i]) {
                if (leadFront < 0) {
                    leadFront = i;
                } else if (otherFront < 0) {
                    otherFront = i;
                }
            }
        }
        // The hind foot on the other side from the leading paw leads the hind feet.
        for (int i = 0; i < rear.length; i++) {
            if (rear[i] && leadFront >= 0 && Math.signum(side[i]) != Math.signum(side[leadFront]) && leadHind < 0) {
                leadHind = i;
            }
        }
        for (int i = 0; i < rear.length; i++) {
            if (rear[i] && i != leadHind) {
                if (leadHind < 0) {
                    leadHind = i;
                } else if (otherHind < 0) {
                    otherHind = i;
                }
            }
        }
        float hindLength = 0.0f;
        for (int i = 0; i < rear.length; i++) {
            if (rear[i]) {
                hindLength = Math.max(hindLength, length[i]);
            }
        }
        this.hindLength = hindLength;
        this.leadFront = leadFront;
        this.otherFront = otherFront;
        this.leadHind = leadHind;
        this.otherHind = otherHind;
    }

    public MantleProfile profile() {
        return this.profile;
    }

    /**
     * Call after the legs' soles and ground have been measured this frame, before the solve.
     *
     * @param mantle        the mantle in progress, or null
     * @param x             the body's position this frame
     * @param gameTime      game time now, with the partial tick
     * @param bodyHalfWidth half the body's collision width, in blocks
     * @param unitsPerBlock model units per block at the model root
     */
    public void update(LocomotionState state, @Nullable MantleState mantle, double x, double y, double z,
                       double gameTime, float bodyHalfWidth, float unitsPerBlock, float dt) {
        final LocomotionState.LegState[] legs = state.legs();

        if (mantle != null) {
            if (!state.mantleActive || state.mantleStartTick != mantle.startTick) {
                this.begin(state, mantle, legs, x, z, bodyHalfWidth);
            }
            final MantleProgress progress = MantleProgress.of(y, mantle.startY, mantle.lipY,
                    gameTime - mantle.startTick, mantle.pastFace(x, z), bodyHalfWidth, this.profile,
                    state.mantleProgress);
            // Nose up is a negative xRot.
            state.mantlePitch = (float) -Math.toRadians(
                    MantleProgress.pitchDegrees(progress.phase, progress.t, this.profile));
            state.mantleLock = lock(progress);
            state.mantleCycleHold = progress.phase == MantlePhase.RECOVER
                    ? 1.0f - MantleProgress.smoothstep(progress.t) : 1.0f;
            state.mantleArch = (float) Math.toRadians(
                    MantleProgress.archDegrees(progress.phase, progress.t, this.profile));

            final double lipHeight = mantle.lipY - mantle.startY;
            GripPlanner.plan(progress.phase, progress.t, lipHeight, this.profile, this.leading, this.trailing);
            GripPlanner.planHind(progress.phase, progress.t, lipHeight,
                    baseDepth(legs, this.leadHind, mantle), baseDepth(legs, this.otherHind, mantle),
                    state.mantleApproach,
                    this.hindFaceHeight(legs, this.leadHind, mantle, lipHeight, unitsPerBlock),
                    this.hindFaceHeight(legs, this.otherHind, mantle, lipHeight, unitsPerBlock),
                    this.profile, this.leadingHind, this.trailingHind);
            place(legs, this.leadFront, progress.phase, this.leading, mantle, false);
            place(legs, this.otherFront, progress.phase, this.trailing, mantle, false);
            place(legs, this.leadHind, progress.phase, this.leadingHind, mantle, true);
            place(legs, this.otherHind, progress.phase, this.trailingHind, mantle, true);
        } else if (state.mantleActive) {
            state.mantleActive = false;
            state.mantleProgress.phase = MantlePhase.NONE;
            for (LocomotionState.LegState leg : legs) {
                leg.contactOverride = LocomotionState.LegState.NO_OVERRIDE;
            }
        }

        final float target = mantle != null ? 1.0f : 0.0f;
        if (state.initialised) {
            state.mantleAuthority.update(target, dt);
        } else {
            state.mantleAuthority.snap(target);
        }
        final float authority = Math.max(0.0f, Math.min(state.mantleAuthority.value(), 1.0f));

        if (mantle != null || authority > AUTHORITY_EPSILON) {
            state.mantleOwnsAnchors = true;
            for (LocomotionState.LegState leg : legs) {
                leg.anchorWeight = leg.mantleWeight * authority;
            }
            state.mantleBodyDrop = (float) ((y - state.mantleStartY) * unitsPerBlock) * state.mantleLock * authority;
            state.mantleTuck = mantle != null
                    ? (float) (this.tuck(state.mantleProgress, mantle.pastFace(x, z), bodyHalfWidth) * unitsPerBlock) * authority
                    : state.mantleTuck * (authority / Math.max(state.mantleAuthority.value(), authority));
        } else {
            state.mantleBodyDrop = 0.0f;
            state.mantleTuck = 0.0f;
            if (state.mantleOwnsAnchors) {
                state.mantleOwnsAnchors = false;
                for (LocomotionState.LegState leg : legs) {
                    leg.anchorWeight = 0.0f;
                    leg.mantleWeight = 0.0f;
                }
            }
        }
    }

    /**
     * A new mantle: remember where the hind feet stand, and how far the body still has to go to
     * the face; the feet step on from there.
     */
    private void begin(LocomotionState state, MantleState mantle, LocomotionState.LegState[] legs,
                       double x, double z, float bodyHalfWidth) {
        state.mantleActive = true;
        state.mantleStartTick = mantle.startTick;
        state.mantleStartY = mantle.startY;
        final double pinned = -(bodyHalfWidth + MantleMotion.PIN_GAP);
        state.mantleApproach = (float) Math.max(0.0, pinned - mantle.pastFace(x, z));
        for (int i = 0; i < legs.length; i++) {
            final LocomotionState.LegState leg = legs[i];
            if (this.rear[i]) {
                leg.mantleBaseX = leg.soleX;
                leg.mantleBaseY = leg.groundY;
                leg.mantleBaseZ = leg.soleZ;
            }
        }
    }

    /**
     * Where on the face a hind foot pushes, above the start: as far down the face as its leg reaches
     * from where its hip was last frame, so the foot stays against the wall as the body rises
     * instead of hanging below it. A hip standing off the wall by {@code d} reaches
     * {@code √(reach² − d²)} down the face. Never below the profile's lowest face grip, never over
     * the lip.
     */
    private double hindFaceHeight(LocomotionState.LegState[] legs, int index, MantleState mantle, double lipHeight,
                                  float unitsPerBlock) {
        final double lowest = lipHeight * this.profile.hindFaceGrip;
        final double top = Math.max(lipHeight - HIND_BELOW_LIP, lowest);
        if (index < 0 || !legs[index].mantleHipValid) {
            return lowest;
        }
        final LocomotionState.LegState leg = legs[index];
        final double reach = this.hindLength / unitsPerBlock * HIND_REACH;
        final double off = Math.max(-mantle.pastFace(leg.mantleHipX, leg.mantleHipZ) - GripPlanner.FACE_GAP, 0.0);
        final double down = Math.sqrt(Math.max(reach * reach - off * off, 0.0));
        return Math.min(Math.max(leg.mantleHipY - mantle.startY - down, lowest), top);
    }

    /** Share of a hind leg's length its foot reaches from the hip: a little past straight, within stretch. */
    static final double HIND_REACH = 1.05;
    /** How far under the lip the hind feet push at the highest. */
    static final double HIND_BELOW_LIP = 0.03;

    /** How far in front of the face a hind foot stood when the mantle began (negative). */
    private static double baseDepth(LocomotionState.LegState[] legs, int leg, MantleState mantle) {
        return leg < 0 ? 0.0 : mantle.pastFace(legs[leg].mantleBaseX, legs[leg].mantleBaseZ);
    }

    /**
     * Puts one leg where its plan says, straight ahead of it across the face. Front paws keep the
     * sideways position of the paw as animated; hind feet keep the one they started from.
     */
    private static void place(LocomotionState.LegState[] legs, int index, MantlePhase phase, GripPlanner.Hand hand,
                              MantleState mantle, boolean hind) {
        if (index < 0) {
            return;
        }
        final LocomotionState.LegState leg = legs[index];
        leg.mantleWeight = hand.anchorWeight;
        switch (phase) {
            case REACH -> leg.contactOverride = 1.0f - hand.swingWeight;
            case RISE -> leg.contactOverride = 1.0f - hand.swingWeight;
            case CREST -> leg.contactOverride = 1.0f - hand.swingWeight;
            case RECOVER -> leg.contactOverride = LocomotionState.LegState.NO_OVERRIDE;
            default -> {
                leg.mantleWeight = 0.0f;
                return;
            }
        }
        final Direction facing = mantle.facing;
        final double along = mantle.wallCoord() + (facing.getStepX() + facing.getStepZ()) * hand.depth;
        final double sideX = hind ? leg.mantleBaseX : leg.soleX;
        final double sideZ = hind ? leg.mantleBaseZ : leg.soleZ;
        if (facing.getStepX() != 0) {
            leg.anchorX = along;
            leg.anchorZ = sideZ;
        } else {
            leg.anchorX = sideX;
            leg.anchorZ = along;
        }
        leg.anchorY = mantle.startY + hand.height;
    }

    /**
     * How far the drawn body closes on the wall, blocks: in over the crest, then given back as the
     * body walks onto the top, never more than the body has yet to walk.
     */
    private double tuck(MantleProgress progress, double pastFace, float bodyHalfWidth) {
        return switch (progress.phase) {
            case CREST -> this.profile.crestTuck * MantleProgress.smoothstep(progress.t);
            case RECOVER -> {
                final double walked = Math.max(pastFace + bodyHalfWidth + MantleMotion.PIN_GAP, 0.0);
                yield Math.max(this.profile.crestTuck - walked, 0.0)
                        * (1.0 - MantleProgress.smoothstep(progress.t));
            }
            default -> 0.0;
        };
    }

    /** Share of the rise the pose holds down: all of it until the crest, none on top. */
    static float lock(MantleProgress progress) {
        return switch (progress.phase) {
            case REACH, RISE -> 1.0f;
            case CREST -> 1.0f - MantleProgress.smoothstep(progress.t);
            default -> 0.0f;
        };
    }
}
