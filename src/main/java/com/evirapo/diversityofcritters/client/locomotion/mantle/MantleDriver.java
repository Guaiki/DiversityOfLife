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
    /** Per leg, hip to sole, model units. */
    private final float[] length;

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
        this.length = length.clone();
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
            final double lipHeight = mantle.lipY - mantle.startY;
            // A lower lip than a full block (a slab) gets a smaller, quicker manoeuvre. Not the crest
            // of a climb: the body is up the face already.
            final MantleProfile profile = mantle.fromClimb ? this.profile : this.profile.forLip(lipHeight);
            final MantleProgress progress = MantleProgress.of(y, mantle.startY, mantle.lipY,
                    mantle.elapsed(gameTime), mantle.pastFace(x, z), bodyHalfWidth, profile, mantle.fromClimb,
                    state.mantleProgress);
            // Nose up is a negative xRot.
            state.mantlePitch = (float) -Math.toRadians(
                    MantleProgress.pitchDegrees(progress.phase, progress.t, profile, mantle.fromClimb));
            state.mantleLock = lock(progress);
            state.mantleCycleHold = progress.phase == MantlePhase.RECOVER
                    ? 1.0f - MantleProgress.smoothstep(progress.t) : 1.0f;
            state.mantleArch = (float) Math.toRadians(
                    MantleProgress.archDegrees(progress.phase, progress.t, profile, mantle.fromClimb));

            GripPlanner.plan(progress.phase, progress.t, lipHeight, profile, mantle.fromClimb, this.leading, this.trailing);
            GripPlanner.planHind(progress.phase, progress.t, lipHeight,
                    baseDepth(legs, this.leadHind, mantle), baseDepth(legs, this.otherHind, mantle),
                    state.mantleApproach,
                    this.hindFaceHeight(legs, this.leadHind, mantle, lipHeight, unitsPerBlock),
                    this.hindFaceHeight(legs, this.otherHind, mantle, lipHeight, unitsPerBlock),
                    profile, this.leadingHind, this.trailingHind);
            place(legs, this.leadFront, progress.phase, this.leading, mantle, false);
            place(legs, this.otherFront, progress.phase, this.trailing, mantle, false);
            place(legs, this.leadHind, progress.phase, this.leadingHind, mantle, true);
            place(legs, this.otherHind, progress.phase, this.trailingHind, mantle, true);
            for (LocomotionState.LegState leg : legs) {
                smooth(leg, mantle, dt);
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
            // Walking onto the top, and fading out after it: the body walks on, the planted feet do not.
            final boolean walkingOn = mantle == null || state.mantleProgress.phase == MantlePhase.RECOVER;
            for (int i = 0; i < legs.length; i++) {
                final LocomotionState.LegState leg = legs[i];
                final float held = walkingOn ? this.heldAfterWalkingOn(leg, i, state.bodyYaw, unitsPerBlock) : 1.0f;
                leg.anchorWeight = leg.mantleWeight * authority * held;
            }
            state.mantleBodyDrop = (float) ((y - state.mantleStartY) * unitsPerBlock) * state.mantleLock * authority;
            state.mantleTuck = mantle != null
                    ? (float) (this.tuck(state.mantleProgress, mantle.pastFace(x, z), bodyHalfWidth, mantle.fromClimb)
                            * unitsPerBlock) * authority
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
     * the face; the feet step on from there. From a climb they stand on the face itself, at the
     * body's height, and the body is already against it.
     */
    private void begin(LocomotionState state, MantleState mantle, LocomotionState.LegState[] legs,
                       double x, double z, float bodyHalfWidth) {
        state.mantleActive = true;
        state.mantleStartTick = mantle.startTick;
        state.mantleStartY = mantle.startY;
        final double pinned = -(bodyHalfWidth + MantleMotion.PIN_GAP);
        state.mantleApproach = mantle.fromClimb ? 0.0f : (float) Math.max(0.0, pinned - mantle.pastFace(x, z));
        final Direction facing = mantle.facing;
        final double onFace = mantle.wallCoord() - (facing.getStepX() + facing.getStepZ()) * GripPlanner.FACE_GAP;
        for (int i = 0; i < legs.length; i++) {
            final LocomotionState.LegState leg = legs[i];
            if (!this.rear[i]) {
                continue;
            }
            if (mantle.fromClimb) {
                leg.mantleBaseX = facing.getStepX() != 0 ? onFace : leg.soleX;
                leg.mantleBaseY = mantle.startY;
                leg.mantleBaseZ = facing.getStepX() != 0 ? leg.soleZ : onFace;
                // Last frame's hips were drawn by the climb, not by this mantle.
                leg.mantleHipValid = false;
            } else {
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

    /**
     * How much of its anchor a planted foot keeps as the body walks on past it: all of it until its
     * hip is {@link #LET_GO_FROM} of a leg past the foot along the heading, none by
     * {@link #LET_GO_BY}. Past that the leg would trail behind the body, and with the anchor still
     * held the rig pulls the body back and down to reach it: the body pitches up and the hind feet
     * sink into the top. Let go, the leg goes back under its hip on the ground route and the walk,
     * resuming, carries it on. By distance, not time: the client ends the mantle a few ticks behind
     * the server, still part way through walking on.
     */
    private float heldAfterWalkingOn(LocomotionState.LegState leg, int index, float bodyYawDeg, float unitsPerBlock) {
        if (!leg.mantleHipValid) {
            return 1.0f;
        }
        final double yaw = Math.toRadians(bodyYawDeg);
        final double past = (leg.mantleHipX - leg.anchorX) * -Math.sin(yaw) + (leg.mantleHipZ - leg.anchorZ) * Math.cos(yaw);
        final double reach = this.length[index] / unitsPerBlock;
        final double from = LET_GO_FROM * reach;
        return 1.0f - MantleProgress.smoothstep((float) ((past - from) / (LET_GO_BY * reach - from)));
    }

    /** Hip past its planted foot, as shares of the leg's length, where the foot starts and ends letting go. */
    static final double LET_GO_FROM = 0.3;
    static final double LET_GO_BY = 0.8;

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
     * body walks onto the top, never more than the body has yet to walk. From a climb it starts as
     * close as the climb pose had it.
     */
    private double tuck(MantleProgress progress, double pastFace, float bodyHalfWidth, boolean fromClimb) {
        final double from = fromClimb ? this.profile.climbTuck : 0.0;
        return switch (progress.phase) {
            case RISE -> from;
            case CREST -> from + (this.profile.crestTuck - from) * MantleProgress.smoothstep(progress.t);
            case RECOVER -> {
                final double walked = Math.max(pastFace + bodyHalfWidth + MantleMotion.PIN_GAP, 0.0);
                yield Math.max(this.profile.crestTuck - walked, 0.0)
                        * (1.0 - MantleProgress.smoothstep(progress.t));
            }
            default -> 0.0;
        };
    }

    /**
     * Follows the leg's planned anchor through its spring. A leg the mantle was not holding yet
     * starts right on its target: while its weight is nil where the anchor sits does not show.
     */
    private static void smooth(LocomotionState.LegState leg, MantleState mantle, float dt) {
        final float x = (float) (leg.anchorX - mantle.lipX);
        final float y = (float) (leg.anchorY - mantle.lipBlockY);
        final float z = (float) (leg.anchorZ - mantle.lipZ);
        if (leg.anchorWeight < SMOOTH_FROM_WEIGHT) {
            leg.mantleAnchorX.snap(x);
            leg.mantleAnchorY.snap(y);
            leg.mantleAnchorZ.snap(z);
        } else {
            leg.mantleAnchorX.update(x, dt);
            leg.mantleAnchorY.update(y, dt);
            leg.mantleAnchorZ.update(z, dt);
        }
        leg.anchorX = mantle.lipX + leg.mantleAnchorX.value();
        leg.anchorY = mantle.lipBlockY + leg.mantleAnchorY.value();
        leg.anchorZ = mantle.lipZ + leg.mantleAnchorZ.value();
    }

    /** Below this anchor weight a leg's anchor jumps to its target instead of following it. */
    static final float SMOOTH_FROM_WEIGHT = 0.01f;

    /** Share of the rise the pose holds down: all of it until the crest, none on top. */
    static float lock(MantleProgress progress) {
        return switch (progress.phase) {
            case REACH, RISE -> 1.0f;
            case CREST -> 1.0f - MantleProgress.smoothstep(progress.t);
            default -> 0.0f;
        };
    }
}
