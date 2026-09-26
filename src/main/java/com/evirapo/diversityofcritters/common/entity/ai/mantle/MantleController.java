package com.evirapo.diversityofcritters.common.entity.ai.mantle;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.locomotion.LedgeSample;
import com.evirapo.diversityofcritters.common.locomotion.LedgeSampler;
import com.evirapo.diversityofcritters.common.locomotion.MantleMotion;
import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleProgress;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * The server side of climbing a ledge: decides whether to, moves the body through it, and puts
 * everything back when it ends. Owned by the critter; driven from its {@code MoveControl}, which
 * hands movement over while {@link #tick} returns true.
 *
 * <p>The phase is never stored: both sides derive it from the body's height
 * ({@link MantleProgress}), and the clients see the start through the critter's synced data.
 *
 * <p>Every way out goes through {@link #end}: gravity, step height and synced data are restored in
 * one place.
 */
public final class MantleController {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** How often the lip is checked for having been broken or changed, in ticks. */
    private static final int LIP_CHECK_INTERVAL = 4;
    /** How far the lip's top may move before it counts as a different lip. */
    private static final double LIP_TOLERANCE = 0.0625;
    /** Where an interrupted, nearly finished mantle drops the body: this far onto the top. */
    private static final double COMPLETE_ONTO_TOP = 0.05;

    private final DiverseCritter critter;
    private final MantleProfile profile;
    private final LedgeSample sample = new LedgeSample();
    private final MantleProgress progress = new MantleProgress();
    private final MantleMotion motion = new MantleMotion();
    private final BlockPos.MutableBlockPos lip = new BlockPos.MutableBlockPos();

    private boolean active;
    private boolean fromPath;
    private boolean holdsGravity;
    private boolean hop;
    private long startTick;
    private double startY;
    private double lipY;
    private double wallCoord;
    private Direction facing = Direction.SOUTH;

    public MantleController(DiverseCritter critter, MantleProfile profile) {
        this.critter = critter;
        this.profile = profile;
    }

    public MantleProfile profile() {
        return this.profile;
    }

    public boolean isActive() {
        return this.active;
    }

    /** Whether gravity is off because of this mantle (and has to come back when it ends). */
    public boolean holdsGravity() {
        return this.holdsGravity;
    }

    /** The probe from the last {@link #tryStart}, for debug output. */
    public LedgeSample lastSample() {
        return this.sample;
    }

    /**
     * Probes ahead along {@code headingYaw} and starts a mantle if there is a ledge to climb:
     * found, faced squarely enough, and not a climbable wall that carries on up (that is a climb).
     *
     * @param fromPath the path asked for it; giving up the path before rising gives up the mantle
     * @return whether a mantle started
     */
    public boolean tryStart(float headingYaw, boolean fromPath) {
        if (this.active || !this.canMantle()) {
            return false;
        }
        LedgeSampler.sample(this.critter.level(), this.critter.position(), headingYaw,
                this.critter.getBbWidth() * 0.5f, this.critter.getBbHeight(), this.profile, this.sample);
        final LedgeSample s = this.sample;
        if (!s.found || s.continuesUp || s.confidence < this.profile.minConfidence) {
            return false;
        }

        this.active = true;
        this.fromPath = fromPath;
        this.startTick = this.critter.level().getGameTime();
        this.startY = this.critter.getY();
        this.lipY = s.lipY;
        this.wallCoord = s.wallCoord;
        this.facing = s.facing;
        this.lip.set(s.lipX, s.lipBlockY, s.lipZ);
        this.hop = MantleMotion.needsHop(s.lipHeight, this.critter.growthScale(), this.profile);

        // Vanilla would otherwise step the body straight up the block and leave the pose behind.
        this.critter.setMaxUpStep(0.0f);
        this.critter.setMantleData(this.lip.immutable(), this.facing, (int) this.startTick,
                (float) this.startY, (float) this.lipY);
        this.lockRotation();
        LOGGER.debug("[Mantle] {} starts at {} facing {}, lip +{}{}", this.critter, this.lip, this.facing,
                String.format("%.2f", s.lipHeight), this.hop ? ", with a hop" : "");
        return true;
    }

    /**
     * Moves the body one tick through the mantle. Call first thing in the move control's tick.
     *
     * @return true while the mantle owns movement this tick
     */
    public boolean tick() {
        if (!this.active) {
            this.releaseStrayGravity();
            return false;
        }
        final long ticks = this.critter.level().getGameTime() - this.startTick;
        final double pastFace = this.pastFace();
        MantleProgress.of(this.critter.getY(), this.startY, this.lipY, ticks, pastFace,
                this.critter.getBbWidth() * 0.5, this.profile, this.progress);

        final String exit = this.exitReason(ticks);
        if (exit != null) {
            this.interrupt(exit);
            return false;
        }

        MantleMotion.plan(this.progress, this.critter.getY(), this.startY, this.lipY, pastFace,
                this.critter.getBbWidth() * 0.5, this.hop, this.profile, this.motion);
        if (this.motion.done) {
            this.end("done");
            return false;
        }

        this.lockRotation();
        this.critter.setSpeed(0.0f);
        this.critter.setXxa(0.0f);
        this.critter.setZza(0.0f);

        this.holdGravity(this.motion.holdsVertical);
        final Vec3 current = this.critter.getDeltaMovement();
        final double vy = this.motion.holdsVertical ? this.motion.up : current.y;
        this.critter.setDeltaMovement(this.facing.getStepX() * this.motion.along, vy,
                this.facing.getStepZ() * this.motion.along);
        return true;
    }

    /** Stops the mantle from outside (the critter was picked up, put to sleep, ...). */
    public void cancel(String reason) {
        if (this.active) {
            this.interrupt(reason);
        }
    }

    /** Nearly done: finish on top. Otherwise let go where it is. */
    private void interrupt(String reason) {
        if (MantleMotion.completesWhenInterrupted(this.progress.h)) {
            final double along = this.wallCoord + (this.facing.getStepX() + this.facing.getStepZ())
                    * (this.critter.getBbWidth() * 0.5 + COMPLETE_ONTO_TOP);
            if (this.facing.getStepX() != 0) {
                this.critter.setPos(along, this.lipY, this.critter.getZ());
            } else {
                this.critter.setPos(this.critter.getX(), this.lipY, along);
            }
            this.end(reason + ", completed");
        } else {
            this.end(reason + ", let go");
        }
    }

    /** The single place a mantle ends. */
    private void end(String reason) {
        this.active = false;
        this.holdGravity(false);
        this.critter.clearMantleData();
        // The critter's own tick puts its usual step height back.
        LOGGER.debug("[Mantle] {} ends: {}", this.critter, reason);
    }

    private String exitReason(long ticks) {
        if (this.critter.isRemoved()) {
            return "removed";
        }
        if (ticks > this.profile.maxTicks) {
            return "timed out";
        }
        if (this.critter.hurtTime > 0) {
            return "hurt";
        }
        if (this.critter.isInWaterOrBubble()) {
            return "in water";
        }
        if (this.critter.isClimbing()) {
            // Never both: the climb pose turns the whole model and would fight the mantle's.
            return "climbing";
        }
        if (this.critter.isLocomotionSuspended()) {
            return "busy";
        }
        if (this.fromPath && this.progress.phase == MantlePhase.REACH && this.critter.getNavigation().isDone()) {
            return "path given up";
        }
        if (ticks % LIP_CHECK_INTERVAL == 0 && !this.lipStillThere()) {
            return "lip changed";
        }
        return null;
    }

    private boolean canMantle() {
        return this.critter.onGround()
                && !this.critter.isClimbing()
                && this.critter.hurtTime == 0
                && !this.critter.isInWaterOrBubble()
                && !this.critter.isLocomotionSuspended();
    }

    private boolean lipStillThere() {
        final BlockState state = this.critter.level().getBlockState(this.lip);
        final VoxelShape shape = state.getCollisionShape(this.critter.level(), this.lip);
        return !shape.isEmpty()
                && Math.abs(this.lip.getY() + shape.max(Direction.Axis.Y) - this.lipY) <= LIP_TOLERANCE;
    }

    private double pastFace() {
        return this.facing.getStepX() != 0
                ? this.facing.getStepX() * (this.critter.getX() - this.wallCoord)
                : this.facing.getStepZ() * (this.critter.getZ() - this.wallCoord);
    }

    private void lockRotation() {
        final float yaw = this.facing.toYRot();
        this.critter.setYRot(yaw);
        this.critter.yRotO = yaw;
        this.critter.setYBodyRot(yaw);
        this.critter.yBodyRotO = yaw;
        this.critter.setYHeadRot(yaw);
    }

    private void holdGravity(boolean hold) {
        if (hold && !this.holdsGravity) {
            if (this.critter.isNoGravity()) {
                // Someone else turned gravity off; leave it to them.
                return;
            }
            this.critter.setNoGravity(true);
            this.holdsGravity = true;
        } else if (!hold && this.holdsGravity) {
            this.critter.setNoGravity(false);
            this.holdsGravity = false;
        }
    }

    /** Safety net: gravity held with no mantle running is a bug, and must not leave a critter floating. */
    private void releaseStrayGravity() {
        if (this.holdsGravity) {
            LOGGER.warn("[Mantle] {} had gravity held with no mantle running; restoring it", this.critter);
            this.holdGravity(false);
        }
        if (this.critter.isMantling()) {
            this.critter.clearMantleData();
        }
    }
}
