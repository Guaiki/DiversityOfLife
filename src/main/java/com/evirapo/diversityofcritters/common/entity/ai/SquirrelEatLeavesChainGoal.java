package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Comer hojas. Sustituye a {@link SquirrelEatLeavesGoal}.
 *
 * <p>La búsqueda de hojas se conserva; lo que se va es el {@code EAT_DURATION = 60} y los reintentos
 * de navegación a mano, que ahora los lleva la fase APPROACH de la clase base.
 */
public class SquirrelEatLeavesChainGoal extends PhasedActivityGoal<DiverseCritter> {

    /** El parámetro de tipo de la clase base es {@code DiverseCritter}, que es quien implementa
     *  {@code Animatable}; esto guarda la vista tipada para lo específico de la ardilla. */
    private final SquirrelEntity squirrel;
    private final int searchRadius;
    private @Nullable BlockPos leavesPos;

    private long lastScanTime = -999L;
    private static final int SCAN_INTERVAL_TICKS = 20;

    public SquirrelEatLeavesChainGoal(SquirrelEntity squirrel, double speed, int searchRadius) {
        super(squirrel, "eat_enter", "eat_loop", "eat_exit");
        this.squirrel = squirrel;
        this.searchRadius = searchRadius;
        // EATING_IDLE dura 0.5 s (10 ticks); 5-6 pasadas ≈ los 60 ticks del goal anterior.
        repeat(5, 6);
        cooldown(200, 200);
        approach(1.0F + squirrel.getBbWidth() / 2.0F, speed);
    }

    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isHungry()) return false;
        if (mob.isOrderedToSit() || mob.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (squirrel.isClimbing() || squirrel.isGliding() || squirrel.isEatingPlant()) return false;

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
            squirrel.setEatingItem(new ItemStack(mob.level().getBlockState(this.leavesPos).getBlock()));
        }
        squirrel.setEatingPlant(true);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        squirrel.setEatingPlant(false);
        squirrel.setEatingItem(ItemStack.EMPTY);
        if (!interrupted) {
            mob.setHunger(mob.maxHunger());
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
