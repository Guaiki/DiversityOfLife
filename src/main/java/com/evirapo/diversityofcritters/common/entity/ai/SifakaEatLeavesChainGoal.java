package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Eating leaves. Replaces {@link SifakaEatPlantGoal}, the same way {@link SquirrelEatLeavesChainGoal}
 * replaced the squirrel's: the leaf search stays, the approach and the timing move to the base class,
 * and the animator plays the eat_enter / eat_loop / eat_exit chain.
 */
public class SifakaEatLeavesChainGoal extends PhasedActivityGoal<DiverseCritter> implements FeedingGoal {

    private final SifakaEntity sifaka;
    private final int searchRadius;
    private @Nullable BlockPos leavesPos;

    private long lastScanTime = -999L;
    private static final int SCAN_INTERVAL_TICKS = 20;

    public SifakaEatLeavesChainGoal(SifakaEntity sifaka, double speed, int searchRadius) {
        super(sifaka, "eat_enter", "eat_loop", "eat_exit");
        this.sifaka = sifaka;
        this.searchRadius = searchRadius;
        // EATING_IDLE lasts 0.5 s (10 ticks); 5-6 passes is about the old goal's 60 ticks.
        repeat(5, 6);
        cooldown(200, 200);
        approach(1.0F + sifaka.getBbWidth() / 2.0F, speed);
    }

    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isHungry()) return false;
        if (mob.isOrderedToSit() || mob.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (sifaka.isClimbing() || sifaka.isEatingPlant()) return false;

        long now = mob.level().getGameTime();
        if (now - this.lastScanTime < SCAN_INTERVAL_TICKS) return false;
        this.lastScanTime = now;

        this.leavesPos = findNearestLeaves();
        return this.leavesPos != null
                && mob.getNavigation().createPath(this.leavesPos, 0) != null;
    }

    @Override
    protected boolean shouldContinue() {
        return this.leavesPos != null
                && mob.level().getBlockState(this.leavesPos).is(BlockTags.LEAVES);
    }

    @Override
    protected @Nullable Vec3 approachTarget() {
        return this.leavesPos == null ? null : Vec3.atCenterOf(this.leavesPos);
    }

    @Override
    protected boolean faceTarget() {
        return true;
    }

    @Override
    protected void onActivityStart() {
        if (this.leavesPos != null) {
            sifaka.setEatingItem(new ItemStack(mob.level().getBlockState(this.leavesPos).getBlock()));
        }
        sifaka.setEatingPlant(true);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        sifaka.setEatingPlant(false);
        sifaka.setEatingItem(ItemStack.EMPTY);
        if (!interrupted) {
            mob.onAteFood();
        }
        this.leavesPos = null;
    }

    private @Nullable BlockPos findNearestLeaves() {
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        int r = this.searchRadius;
        int vy = Math.min(r, 6);

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -vy, -r), origin.offset(r, vy, r))) {
            if (mob.level().getBlockState(pos).is(BlockTags.LEAVES)) {
                double d = origin.distSqr(pos);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
