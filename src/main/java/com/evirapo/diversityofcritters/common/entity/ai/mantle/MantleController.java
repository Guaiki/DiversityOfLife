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
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
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
    /** A path node this close (horizontally, squared) is near enough to start mantling toward. */
    private static final double PATH_NODE_RANGE_SQR = 1.5D * 1.5D;
    /** How often the lip is checked for having been broken or changed, in ticks. */
    private static final int LIP_CHECK_INTERVAL = 4;
    /** How far the lip's top may move before it counts as a different lip. */
    private static final double LIP_TOLERANCE = 0.0625;
    /** Where an interrupted, nearly finished mantle drops the body: this far onto the top. */
    private static final double COMPLETE_ONTO_TOP = 0.05;

    /** Slowest and fastest pace {@link #setDebugTimeScale} accepts. */
    public static final float MIN_TIME_SCALE = 0.05f;
    public static final float MAX_TIME_SCALE = 1.0f;
    /**
     * Pace for mantles that start from now on, 1 normally: a debug slow motion for the manoeuvre
     * alone, the rest of the critter keeps the game's pace. Each mantle keeps the pace it started
     * with, and its clients get it with the rest of the mantle.
     */
    private static volatile float debugTimeScale = 1.0f;

    public static float debugTimeScale() {
        return debugTimeScale;
    }

    /** @return the pace actually set, within {@link #MIN_TIME_SCALE} and {@link #MAX_TIME_SCALE} */
    public static float setDebugTimeScale(float scale) {
        debugTimeScale = Mth.clamp(scale, MIN_TIME_SCALE, MAX_TIME_SCALE);
        return debugTimeScale;
    }

    private final DiverseCritter critter;
    private final MantleProfile profile;
    /** The profile scaled to the lip of the mantle in progress ({@link MantleProfile#forLip}). */
    private MantleProfile lipProfile;
    private final LedgeSample sample = new LedgeSample();
    private final MantleProgress progress = new MantleProgress();
    private final MantleMotion motion = new MantleMotion();
    private final BlockPos.MutableBlockPos lip = new BlockPos.MutableBlockPos();

    private boolean active;
    /** Game time of the last probe, so {@link #shouldJumpInstead} only answers for this tick's. */
    private long probedTick = Long.MIN_VALUE;
    private boolean fromPath;
    private boolean holdsGravity;
    private boolean hop;
    private float timeScale = 1.0f;
    private long startTick;
    private double startY;
    private double lipY;
    private double wallCoord;
    private Direction facing = Direction.SOUTH;

    public MantleController(DiverseCritter critter, MantleProfile profile) {
        this.critter = critter;
        this.profile = profile;
        this.lipProfile = profile;
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
     * The body turns to face the wall square on as it starts.
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
        this.probedTick = this.critter.level().getGameTime();
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
        this.lipProfile = this.profile.forLip(s.lipHeight);
        this.hop = MantleMotion.needsHop(s.lipHeight, this.critter.growthScale(), this.profile);
        this.timeScale = debugTimeScale;

        // Vanilla would otherwise step the body straight up the block and leave the pose behind.
        this.critter.setMaxUpStep(0.0f);
        this.critter.setMantleData(this.lip.immutable(), this.facing, (int) this.startTick,
                (float) this.startY, (float) this.lipY, this.timeScale);
        this.lockRotation();
        LOGGER.debug("[Mantle] {} starts at {} facing {}, lip +{}{}", this.critter, this.lip, this.facing,
                String.format("%.2f", s.lipHeight), this.hop ? ", with a hop" : "");
        return true;
    }

    /**
     * Starts a mantle when the path's next node is one block up and near (a slab too: vanilla puts
     * the node for standing on a slab in the block above it): the probe then decides whether there
     * is a ledge to climb, or a step, stairs or a wall to climb instead. Call before
     * the species' own climb logic, while vanilla would still step the body straight up the block.
     * Newborns never mantle.
     */
    public boolean tryStartFromPath() {
        if (this.active || this.critter.isNewborn()) {
            return false;
        }
        final Path path = this.critter.getNavigation().getPath();
        if (path == null || path.isDone() || path.getNextNodeIndex() >= path.getNodeCount()) {
            return false;
        }
        final Node next = path.getNode(path.getNextNodeIndex());
        if (next.y != Mth.floor(this.critter.getY() + 0.01D) + 1) {
            return false;
        }
        final double dx = next.x + 0.5D - this.critter.getX();
        final double dz = next.z + 0.5D - this.critter.getZ();
        if (dx * dx + dz * dz > PATH_NODE_RANGE_SQR) {
            return false;
        }
        final float headingYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        if (this.tryStart(headingYaw, true)) {
            return true;
        }
        // The lip is there but was met at a slant: the next node is diagonal, as when the path cuts
        // a corner out of a hole. Face the wall square on and climb it that way, rather than jump.
        final LedgeSample s = this.sample;
        if (this.probedTick == this.critter.level().getGameTime()
                && s.found && !s.continuesUp && s.confidence < this.profile.minConfidence) {
            return this.tryStart(s.facing.toYRot(), true);
        }
        return false;
    }

    /**
     * Whether the body has to jump the rise ahead instead: this tick's probe met a wall no mantle
     * could take even square on (the lip under too little of the body, no room on top, ...), and
     * it is higher than the body walks up but no higher than it could mantle. Stairs and slabs are
     * walked; a climbable wall that carries on up is climbed.
     */
    public boolean shouldJumpInstead() {
        final LedgeSample s = this.sample;
        final boolean jump = !this.active
                && this.probedTick == this.critter.level().getGameTime()
                && s.hasWall()
                && !s.continuesUp
                && s.lipHeight > this.critter.maxUpStep()
                && s.lipHeight <= this.profile.maxMantleHeight;
        if (jump) {
            LOGGER.debug("[Mantle] {} jumps instead: {}, confidence {}, lip +{}", this.critter, s.reason,
                    String.format("%.2f", s.confidence), String.format("%.2f", s.lipHeight));
        }
        return jump;
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
        // Mantle time runs at the mantle's own pace: slower in debug slow motion.
        final double mantleTicks = ticks * (double) this.timeScale;
        final double pastFace = this.pastFace();
        MantleProgress.of(this.critter.getY(), this.startY, this.lipY, mantleTicks, pastFace,
                this.critter.getBbWidth() * 0.5, this.lipProfile, this.progress);

        final String exit = this.exitReason(ticks, mantleTicks);
        if (exit != null) {
            this.interrupt(exit);
            return false;
        }

        MantleMotion.plan(this.progress, this.critter.getY(), this.startY, this.lipY, pastFace,
                this.critter.getBbWidth() * 0.5, this.hop, this.lipProfile, this.motion);
        if (this.motion.done) {
            this.end("done");
            return false;
        }

        this.lockRotation();
        this.critter.setSpeed(0.0f);
        this.critter.setXxa(0.0f);
        this.critter.setZza(0.0f);

        this.holdGravity(this.motion.holdsVertical);
        // The plan is per tick at full pace; slow motion covers less of it each tick.
        final Vec3 current = this.critter.getDeltaMovement();
        final double along = this.motion.along * this.timeScale;
        final double vy = this.motion.holdsVertical
                ? this.motion.upAt(this.timeScale, this.critter.getY(), this.lipY) : current.y;
        this.critter.setDeltaMovement(this.facing.getStepX() * along, vy, this.facing.getStepZ() * along);
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

    /**
     * @param ticks       game ticks since the start
     * @param mantleTicks the same at the mantle's own pace, which is what the timeout counts
     */
    private String exitReason(long ticks, double mantleTicks) {
        if (this.critter.isRemoved()) {
            return "removed";
        }
        if (mantleTicks > this.lipProfile.maxTicks) {
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
