package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class SifakaEatPlantGoal extends Goal {

    private final SifakaEntity sifaka;
    private final double speed;
    private final int searchRadius;
    private final float reach;

    @Nullable private BlockPos targetPos;
    private int eatTimer = 0;
    private int cooldown = 0;
    private int recalcTicks = 0;
    private int stuckTime = 0;

    private static final int EAT_DURATION = 60;
    private long lastScanTime = -999;
    private static final int SCAN_INTERVAL_TICKS = 20;

    public SifakaEatPlantGoal(SifakaEntity sifaka, double speed, int searchRadius) {
        this.sifaka = sifaka;
        this.speed = speed;
        this.searchRadius = searchRadius;
        this.reach = 1.0F;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (sifaka.isNewborn()) return false;
        if (sifaka.level().isClientSide()) return false;
        if (!sifaka.isHungry()) return false;
        if (sifaka.isOrderedToSit() || sifaka.isSleeping() || sifaka.isPreparingSleep()) return false;
        if (sifaka.isClimbing() || sifaka.isEatingPlant()) return false;

        long now = sifaka.level().getGameTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS) return false;
        lastScanTime = now;

        targetPos = findNearestPlantBlock();
        if (targetPos == null) return false;

        if (sifaka.getNavigation().createPath(targetPos, 0) == null) {
            targetPos = null;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (sifaka.level().isClientSide()) return false;
        if (targetPos == null) return false;
        return isValidPlant(sifaka.level().getBlockState(targetPos));
    }

    @Override
    public void start() {
        resetCalc();
        eatTimer = 0;
        if (targetPos != null) navigateToTarget();
    }

    @Override
    public void stop() {
        eatTimer = 0;
        sifaka.setEatingPlant(false);
        sifaka.setEatingItem(ItemStack.EMPTY);
        sifaka.getNavigation().stop();
        targetPos = null;
        cooldown = 200;
        resetCalc();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;
        if (!isValidPlant(sifaka.level().getBlockState(targetPos))) {
            this.stop();
            return;
        }

        Vec3 blockCenter = Vec3.atCenterOf(targetPos);
        sifaka.getLookControl().setLookAt(blockCenter.x, blockCenter.y, blockCenter.z);

        if (eatTimer > 0) {
            sifaka.getNavigation().stop();
            sifaka.setDeltaMovement(0, sifaka.getDeltaMovement().y, 0);
            eatTimer++;
            if (eatTimer >= EAT_DURATION) {
                sifaka.onAteFood();
                this.stop();
            }
            return;
        }

        double thresholdSq = Mth.square(reach + sifaka.getBbWidth() / 2.0);

        if (sifaka.distanceToSqr(blockCenter) <= thresholdSq) {
            sifaka.getNavigation().stop();
            sifaka.setDeltaMovement(0, sifaka.getDeltaMovement().y, 0);

            Vec3 toBlockHoriz = new Vec3(blockCenter.x - sifaka.getX(), 0, blockCenter.z - sifaka.getZ());
            if (toBlockHoriz.lengthSqr() > 1e-6) {
                float targetYaw = (float)(Math.atan2(toBlockHoriz.z, toBlockHoriz.x) * Mth.RAD_TO_DEG) - 90.0F;
                sifaka.setYRot(sifaka.getYRot() + Mth.wrapDegrees(targetYaw - sifaka.getYRot()) * 0.3F);
                sifaka.yHeadRot = sifaka.getYRot();
                sifaka.yBodyRot = sifaka.getYRot();
            }

            ItemStack plantStack = new ItemStack(sifaka.level().getBlockState(targetPos).getBlock());
            sifaka.setEatingItem(plantStack);
            sifaka.setEatingPlant(true);
            eatTimer = 1;
            resetCalc();
            return;
        }

        if (++recalcTicks >= 20) {
            recalcTicks = 0;
            PathNavigation nav = sifaka.getNavigation();
            if (nav.isDone()) {
                if (++stuckTime > 2) {
                    stuckTime = 0;
                    navigateToTarget();
                }
            } else {
                stuckTime = 0;
            }
        }
    }

    private void navigateToTarget() {
        if (targetPos == null) return;
        sifaka.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speed);
    }

    private void resetCalc() {
        recalcTicks = 0;
        stuckTime = 0;
    }

    private boolean isValidPlant(BlockState state) {
        return state.is(BlockTags.LEAVES);
    }

    @Nullable
    private BlockPos findNearestPlantBlock() {
        Level level = (Level) sifaka.level();
        BlockPos origin = sifaka.blockPosition();
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        int r = searchRadius;
        int vy = Math.min(r, 6);

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -vy, -r),
                origin.offset(r, vy, r))) {
            if (isValidPlant(level.getBlockState(pos))) {
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
