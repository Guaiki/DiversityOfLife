package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class SifakaMoveControl extends MoveControl {
    private final SifakaEntity sifaka;

    private static final double CLIMB_SPEED = 0.16D;
    private static final double STEP_UP_IMPULSE = 0.02D;
    private static final double STEP_UP_HORIZONTAL_DAMPING = 0.02D;
    private static final int POST_CLIMB_DAMPING_TICKS = 6;
    private static final double POST_CLIMB_HORIZONTAL_DAMPING = 0.65D;
    private static final int LOOKAHEAD_NODES = 5;

    private boolean wasClimbingLastTick = false;
    private int postClimbDampingTicks = 0;

    private int landingCooldown = 0;
    private static final int LANDING_COOLDOWN_TICKS = 15;

    public SifakaMoveControl(SifakaEntity pMob) {
        super(pMob);
        this.sifaka = pMob;
    }

    @Override
    public void tick() {
        boolean isClimbingNow = this.sifaka.isClimbing();

        if (this.wasClimbingLastTick && !isClimbingNow && this.sifaka.onGround()) {
            this.landingCooldown = LANDING_COOLDOWN_TICKS;
        }
        if (this.landingCooldown > 0) {
            this.landingCooldown--;
            Vec3 motion = this.sifaka.getDeltaMovement();
            this.sifaka.setDeltaMovement(0, motion.y, 0);
        }

        if (isClimbingNow) {
            this.wasClimbingLastTick = true;
            this.postClimbDampingTicks = 0;
            this.handleClimbingMovement();
            return;
        }

        if (this.wasClimbingLastTick) {
            this.postClimbDampingTicks = POST_CLIMB_DAMPING_TICKS;
            this.wasClimbingLastTick = false;
        }

        if (this.postClimbDampingTicks > 0) {
            Vec3 motion = this.sifaka.getDeltaMovement();
            this.sifaka.setDeltaMovement(
                    motion.x * POST_CLIMB_HORIZONTAL_DAMPING,
                    motion.y,
                    motion.z * POST_CLIMB_HORIZONTAL_DAMPING
            );
            this.postClimbDampingTicks--;
        }

        if (!this.sifaka.getNavigation().isDone()) {
            updateClimbDirectionFromPath();
        }

        if (this.operation == Operation.MOVE_TO && !this.sifaka.getNavigation().isDone()) {
            double dx = this.wantedX - this.sifaka.getX();
            double dy = this.wantedY - this.sifaka.getY();
            double dz = this.wantedZ - this.sifaka.getZ();
            double distSqr = dx * dx + dy * dy + dz * dz;

            if (distSqr < 2.500000277905201E-7D) {
                this.mob.setZza(0.0F);
                return;
            }

            float targetYaw = (float)(Mth.atan2(dz, dx) * (double)(180F / (float)Math.PI)) - 90.0F;
            this.sifaka.setYRot(this.rotlerp(this.sifaka.getYRot(), targetYaw, 30.0F));
            this.sifaka.setYBodyRot(this.sifaka.getYRot());
            this.sifaka.setSpeed((float)(this.speedModifier * this.sifaka.getAttributeValue(Attributes.MOVEMENT_SPEED)));

            if (dy > (double)this.sifaka.maxUpStep()
                    && dx * dx + dz * dz < (double)Math.max(1.0F, this.sifaka.getBbWidth())) {
                if (!this.sifaka.isNewborn()) {
                    Vec3 motion = this.sifaka.getDeltaMovement();
                    this.sifaka.setDeltaMovement(
                            motion.x * STEP_UP_HORIZONTAL_DAMPING,
                            Math.max(motion.y, STEP_UP_IMPULSE),
                            motion.z * STEP_UP_HORIZONTAL_DAMPING
                    );
                }
            }
        } else {
            this.sifaka.setZza(0.0F);
        }
    }

    private void updateClimbDirectionFromPath() {
        Path path = this.sifaka.getNavigation().getPath();
        if (path == null || path.isDone()) return;

        int nextIdx = path.getNextNodeIndex();
        if (nextIdx >= path.getNodeCount()) return;

        Node nextNode = path.getNode(nextIdx);
        int currentY = (nextIdx > 0)
                ? path.getNode(nextIdx - 1).y
                : Mth.floor(this.sifaka.getY());

        int dy = nextNode.y - currentY;
        boolean adjacentToWall = this.sifaka.hasAdjacentClimbableBlock();
        boolean onGround = this.sifaka.onGround();

        if (dy > 0 && adjacentToWall && !this.sifaka.isInPostDescentCooldown()) {
            this.sifaka.setClimbState(SifakaEntity.CLIMB_UP);
            return;
        }

        if (dy < 0 && adjacentToWall && !onGround && !this.sifaka.isInPostDescentCooldown()) {
            this.sifaka.setClimbState(SifakaEntity.CLIMB_DOWN);
            return;
        }

        if (onGround && !this.sifaka.isInPostDescentCooldown()) {
            BlockPos below = this.sifaka.blockPosition().below();
            boolean onTopOfClimbable = this.sifaka.level()
                    .getBlockState(below).is(DoCTags.Blocks.CIVET_CLIMBABLE);

            if (onTopOfClimbable) {
                boolean pathHasDescent = false;
                for (int i = nextIdx; i < path.getNodeCount(); i++) {
                    if (path.getNode(i).y < currentY) {
                        pathHasDescent = true;
                        break;
                    }
                }

                if (pathHasDescent) {
                    for (int i = nextIdx; i < Math.min(nextIdx + LOOKAHEAD_NODES, path.getNodeCount()); i++) {
                        if (path.getNode(i).y < currentY) {
                            Node target = path.getNode(i);
                            float speed = (float)(this.speedModifier
                                    * this.sifaka.getAttributeValue(Attributes.MOVEMENT_SPEED));
                            double tx = target.x + 0.5 - this.sifaka.getX();
                            double tz = target.z + 0.5 - this.sifaka.getZ();
                            float yaw = (float)(Mth.atan2(tz, tx) * (180.0 / Math.PI)) - 90.0F;
                            this.sifaka.setYRot(this.rotlerp(this.sifaka.getYRot(), yaw, 30.0F));
                            this.sifaka.setYBodyRot(this.sifaka.getYRot());
                            this.sifaka.setSpeed(speed);
                            return;
                        }
                    }
                }
            }
        }

        if (dy == 0 && adjacentToWall && !onGround && !this.sifaka.isInPostDescentCooldown()) {
            int limit = Math.min(nextIdx + LOOKAHEAD_NODES, path.getNodeCount());
            for (int i = nextIdx + 1; i < limit; i++) {
                int futureY = path.getNode(i).y;
                if (futureY < currentY) {
                    this.sifaka.setClimbState(SifakaEntity.CLIMB_DOWN);
                    return;
                }
                if (futureY > currentY) break;
            }
        }
    }

    private void handleClimbingMovement() {
        float horizontalSpeed = (float)(0.8 * this.sifaka.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 0.5F;
        this.sifaka.setSpeed(horizontalSpeed);
        Vec3 motion = this.sifaka.getDeltaMovement();

        if (this.sifaka.isHanging()) {
            this.sifaka.setDeltaMovement(motion.x, 0.0D, motion.z);
        } else if (this.sifaka.isClimbingDown()) {
            float yawRad = (float) Math.toRadians(this.sifaka.getClimbFacingYaw());
            double pushX = -Math.sin(yawRad) * 0.15;
            double pushZ = Math.cos(yawRad) * 0.15;
            this.sifaka.setDeltaMovement(pushX, -CLIMB_SPEED, pushZ);
        } else {
            this.sifaka.setDeltaMovement(motion.x, CLIMB_SPEED, motion.z);
        }
    }
}
