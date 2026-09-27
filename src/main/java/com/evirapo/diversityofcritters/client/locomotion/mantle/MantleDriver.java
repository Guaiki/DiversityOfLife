package com.evirapo.diversityofcritters.client.locomotion.mantle;

import com.evirapo.diversityofcritters.client.locomotion.LocomotionState;
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
 *   <tr><td>REACH</td><td>leave the ground for their first grips</td><td>planted where they stood</td><td>0 to reach pitch</td></tr>
 *   <tr><td>RISE</td><td>climb the face in alternate grips to the lip</td><td>alternate steps toward the wall</td><td>to rise pitch</td></tr>
 *   <tr><td>CREST</td><td>from the lip onto the top</td><td>scramble onto the face, one then the other</td><td>to crest pitch</td></tr>
 *   <tr><td>RECOVER</td><td>hand back to the ground path</td><td>hand back to the ground path</td><td>level</td></tr>
 * </table>
 *
 * <p>Where the paws go is {@link GripPlanner}'s call. The first front leg in the rig leads, and the
 * hind foot diagonal to it moves with it, as in a walk.
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

    /**
     * @param rear per leg, in the rig's order: whether it is a hind leg
     * @param side per leg: its hip's sideways position (model X), to pair the diagonals
     */
    public MantleDriver(MantleProfile profile, boolean[] rear, float[] side) {
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
                this.begin(state, mantle, legs);
            }
            final MantleProgress progress = MantleProgress.of(y, mantle.startY, mantle.lipY,
                    gameTime - mantle.startTick, mantle.pastFace(x, z), bodyHalfWidth, this.profile,
                    state.mantleProgress);
            // Nose up is a negative xRot.
            state.mantlePitch = (float) -Math.toRadians(
                    MantleProgress.pitchDegrees(progress.phase, progress.t, this.profile));
            state.mantleLock = lock(progress);
            state.mantleArch = (float) Math.toRadians(this.profile.archDegrees)
                    * MantleProgress.archShare(progress.phase, progress.t);

            final double lipHeight = mantle.lipY - mantle.startY;
            GripPlanner.plan(progress.phase, progress.t, lipHeight, this.profile, this.leading, this.trailing);
            GripPlanner.planHind(progress.phase, progress.t, lipHeight,
                    baseDepth(legs, this.leadHind, mantle), baseDepth(legs, this.otherHind, mantle),
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
        } else {
            state.mantleBodyDrop = 0.0f;
            if (state.mantleOwnsAnchors) {
                state.mantleOwnsAnchors = false;
                for (LocomotionState.LegState leg : legs) {
                    leg.anchorWeight = 0.0f;
                    leg.mantleWeight = 0.0f;
                }
            }
        }
    }

    /** A new mantle: remember where the hind feet stand; they step on from there. */
    private void begin(LocomotionState state, MantleState mantle, LocomotionState.LegState[] legs) {
        state.mantleActive = true;
        state.mantleStartTick = mantle.startTick;
        state.mantleStartY = mantle.startY;
        for (int i = 0; i < legs.length; i++) {
            final LocomotionState.LegState leg = legs[i];
            if (this.rear[i]) {
                leg.mantleBaseX = leg.soleX;
                leg.mantleBaseY = leg.groundY;
                leg.mantleBaseZ = leg.soleZ;
            }
        }
    }

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
            // The front paws leave the ground at once; the hind feet are still standing.
            case REACH -> leg.contactOverride = hind ? 1.0f : 0.0f;
            case RISE -> leg.contactOverride = 1.0f - hand.swingWeight;
            case CREST -> leg.contactOverride = hind ? 1.0f - hand.swingWeight : 1.0f;
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

    /** Share of the rise the pose holds down: all of it until the crest, none on top. */
    static float lock(MantleProgress progress) {
        return switch (progress.phase) {
            case REACH, RISE -> 1.0f;
            case CREST -> 1.0f - MantleProgress.smoothstep(progress.t);
            default -> 0.0f;
        };
    }
}
