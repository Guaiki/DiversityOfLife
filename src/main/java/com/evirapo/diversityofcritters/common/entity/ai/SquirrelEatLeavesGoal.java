package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class SquirrelEatLeavesGoal extends Goal implements FeedingGoal {

    private final SquirrelEntity squirrel;
    private final double speed;
    private final int searchRadius;
    private final float reach;

    @Nullable private BlockPos leavesPos;
    private int eatTimer = 0;
    private int cooldown = 0;
    private int recalcTicks = 0;
    private int stuckTime = 0;

    private static final int EAT_DURATION = 60;
    private long lastScanTime = -999;
    private static final int SCAN_INTERVAL_TICKS = 20;

    public SquirrelEatLeavesGoal(SquirrelEntity squirrel, double speed, int searchRadius) {
        this.squirrel = squirrel;
        this.speed = speed;
        this.searchRadius = searchRadius;
        this.reach = 1.0F;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (squirrel.isNewborn()) return false;
        if (squirrel.level().isClientSide()) return false;
        if (!squirrel.isHungry()) return false;
        if (squirrel.isOrderedToSit() || squirrel.isSleeping() || squirrel.isPreparingSleep()) return false;
        if (squirrel.isClimbing() || squirrel.isGliding() || squirrel.isEatingPlant()) return false;

        long now = squirrel.level().getGameTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS) return false;
        lastScanTime = now;

        leavesPos = findNearestLeavesBlock();
        if (leavesPos == null) return false;

        // Pre-check that a path exists before committing
        if (squirrel.getNavigation().createPath(leavesPos, 0) == null) {
            leavesPos = null;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (squirrel.level().isClientSide()) return false;
        if (leavesPos == null) return false;
        return squirrel.level().getBlockState(leavesPos).is(BlockTags.LEAVES);
    }

    @Override
    public void start() {
        resetCalc();
        eatTimer = 0;
        if (leavesPos != null) navigateToLeaves();
    }

    @Override
    public void stop() {
        eatTimer = 0;
        squirrel.setEatingPlant(false);
        squirrel.setEatingItem(ItemStack.EMPTY);
        squirrel.getNavigation().stop();
        leavesPos = null;
        cooldown = 200;
        resetCalc();
    }

    @Override
    public void tick() {
        if (leavesPos == null) return;
        if (!squirrel.level().getBlockState(leavesPos).is(BlockTags.LEAVES)) {
            this.stop();
            return;
        }

        Vec3 blockCenter = Vec3.atCenterOf(leavesPos);
        squirrel.getLookControl().setLookAt(blockCenter.x, blockCenter.y, blockCenter.z);

        if (eatTimer > 0) {
            squirrel.getNavigation().stop();
            squirrel.setDeltaMovement(0, squirrel.getDeltaMovement().y, 0);
            eatTimer++;
            if (eatTimer >= EAT_DURATION) {
                squirrel.onAteFood();
                this.stop();
            }
            return;
        }

        double thresholdSq = Mth.square(reach + squirrel.getBbWidth() / 2.0);

        if (squirrel.distanceToSqr(blockCenter) <= thresholdSq) {
            squirrel.getNavigation().stop();
            squirrel.setDeltaMovement(0, squirrel.getDeltaMovement().y, 0);

            Vec3 toBlockHoriz = new Vec3(blockCenter.x - squirrel.getX(), 0, blockCenter.z - squirrel.getZ());
            if (toBlockHoriz.lengthSqr() > 1e-6) {
                float targetYaw = (float)(Math.atan2(toBlockHoriz.z, toBlockHoriz.x) * Mth.RAD_TO_DEG) - 90.0F;
                squirrel.setYRot(squirrel.getYRot() + Mth.wrapDegrees(targetYaw - squirrel.getYRot()) * 0.3F);
                squirrel.yHeadRot = squirrel.getYRot();
                squirrel.yBodyRot = squirrel.getYRot();
            }

            ItemStack leafStack = new ItemStack(squirrel.level().getBlockState(leavesPos).getBlock());
            squirrel.setEatingItem(leafStack);
            squirrel.setEatingPlant(true);
            eatTimer = 1;
            resetCalc();
            return;
        }

        if (++recalcTicks >= 20) {
            recalcTicks = 0;
            PathNavigation nav = squirrel.getNavigation();
            if (nav.isDone()) {
                if (++stuckTime > 2) {
                    stuckTime = 0;
                    navigateToLeaves();
                }
            } else {
                stuckTime = 0;
            }
        }
    }

    private void navigateToLeaves() {
        if (leavesPos == null) return;
        squirrel.getNavigation().moveTo(leavesPos.getX() + 0.5, leavesPos.getY(), leavesPos.getZ() + 0.5, speed);
    }

    private void resetCalc() {
        recalcTicks = 0;
        stuckTime = 0;
    }

    @Nullable
    private BlockPos findNearestLeavesBlock() {
        Level level = (Level) squirrel.level();
        BlockPos origin = squirrel.blockPosition();
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        int r = searchRadius;
        int vy = Math.min(r, 6);

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -vy, -r),
                origin.offset(r, vy, r))) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
                double distSq = origin.distSqr(pos);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPos = pos.immutable();
                }
            }
        }
        return bestPos;
    }
}
