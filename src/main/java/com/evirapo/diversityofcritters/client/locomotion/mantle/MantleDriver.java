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
 *   <tr><th>Phase</th><th>Front paws</th><th>Hind legs</th><th>Body</th></tr>
 *   <tr><td>REACH</td><td>leave the ground for the lip</td><td>anchored where they stood</td><td>0 to reach pitch</td></tr>
 *   <tr><td>RISE</td><td>on the lip</td><td>anchored</td><td>to rise pitch</td></tr>
 *   <tr><td>CREST</td><td>from the lip onto the top</td><td>let go, off the ground</td><td>to crest pitch</td></tr>
 *   <tr><td>RECOVER</td><td>hand back to the ground path</td><td>ground path</td><td>level</td></tr>
 * </table>
 *
 * <p>The server raises the entity through RISE and CREST. Until the crest the body stands on its
 * hind legs at the foot of the wall, so the pose holds the body down by as much as the entity has
 * risen ({@link LocomotionState#mantleBodyDrop}), letting go over the crest as the hind legs leave.
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

    /**
     * @param rear per leg, in the rig's order: whether it is a hind leg
     */
    public MantleDriver(MantleProfile profile, boolean[] rear) {
        this.profile = profile;
        this.rear = rear.clone();
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
            for (int i = 0; i < legs.length; i++) {
                if (this.rear[i]) {
                    hind(legs[i], progress);
                } else {
                    this.front(legs[i], progress, mantle);
                }
            }
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

    /** A new mantle: the hind legs stay where they stand. */
    private void begin(LocomotionState state, MantleState mantle, LocomotionState.LegState[] legs) {
        state.mantleActive = true;
        state.mantleStartTick = mantle.startTick;
        state.mantleStartY = mantle.startY;
        for (int i = 0; i < legs.length; i++) {
            final LocomotionState.LegState leg = legs[i];
            if (this.rear[i]) {
                leg.anchorX = leg.soleX;
                leg.anchorY = leg.groundY;
                leg.anchorZ = leg.soleZ;
            }
        }
    }

    /** Front paw: up to the lip, then onto the top, then back to the ground path. */
    private void front(LocomotionState.LegState leg, MantleProgress progress, MantleState mantle) {
        final float s = MantleProgress.smoothstep(progress.t);
        final double depth;
        switch (progress.phase) {
            case REACH -> {
                leg.mantleWeight = s;
                leg.contactOverride = 0.0f;
                depth = this.profile.lipInset;
            }
            case RISE -> {
                leg.mantleWeight = 1.0f;
                leg.contactOverride = 1.0f;
                depth = this.profile.lipInset;
            }
            case CREST -> {
                leg.mantleWeight = 1.0f;
                leg.contactOverride = 1.0f;
                depth = this.profile.lipInset + (this.profile.plantDepth - this.profile.lipInset) * s;
            }
            case RECOVER -> {
                leg.mantleWeight = 1.0f - s;
                leg.contactOverride = LocomotionState.LegState.NO_OVERRIDE;
                depth = this.profile.plantDepth;
            }
            default -> {
                leg.mantleWeight = 0.0f;
                return;
            }
        }
        // Onto the lip straight ahead of where the paw is, across the face.
        final Direction facing = mantle.facing;
        final double along = mantle.wallCoord() + (facing.getStepX() + facing.getStepZ()) * depth;
        if (facing.getStepX() != 0) {
            leg.anchorX = along;
            leg.anchorZ = leg.soleZ;
        } else {
            leg.anchorX = leg.soleX;
            leg.anchorZ = along;
        }
        leg.anchorY = mantle.lipY;
    }

    /** Hind leg: planted until the crest, then off the ground. */
    private static void hind(LocomotionState.LegState leg, MantleProgress progress) {
        final float s = MantleProgress.smoothstep(progress.t);
        switch (progress.phase) {
            case REACH, RISE -> {
                leg.mantleWeight = 1.0f;
                leg.contactOverride = 1.0f;
            }
            case CREST -> {
                leg.mantleWeight = 1.0f - s;
                leg.contactOverride = 0.0f;
            }
            default -> {
                leg.mantleWeight = 0.0f;
                leg.contactOverride = LocomotionState.LegState.NO_OVERRIDE;
            }
        }
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
