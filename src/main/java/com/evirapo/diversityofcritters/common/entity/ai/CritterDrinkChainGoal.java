package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Beber de una masa de agua. Sustituye a {@link CritterDrinkGoal} en las especies que ya tienen la
 * cadena {@code drink_*} registrada.
 *
 * <p>La búsqueda de agua se conserva tal cual del goal anterior. Lo que desaparece es la máquina de
 * fases hecha a mano: el {@code isFinishing}/{@code finishingTimer < 5} que replicaba a ojo la
 * longitud del clip de salida, y el {@code (drinkTimer - 5) % 20 == 10} para los sorbos — ahora cada
 * sorbo es una repetición del bucle, así que el número de sorbos y la animación no pueden
 * desincronizarse.
 */
public class CritterDrinkChainGoal extends PhasedActivityGoal<DiverseCritter> {

    private static final double MAX_DRINK_DIST_SQ = 1.2D;
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private @Nullable BlockPos waterPos;
    private @Nullable BlockPos standPos;

    public CritterDrinkChainGoal(DiverseCritter critter) {
        super(critter, "drink_enter", "drink_loop", "drink_exit");
        repeat(2, 3);                       // dos o tres sorbos
        chance(0.3F);
        cooldown(100, 200);
        approach((float) Math.sqrt(MAX_DRINK_DIST_SQ), 1.2D);
    }

    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isThirsty()) return false;
        this.waterPos = findWater();
        this.standPos = this.waterPos == null ? null : landNextTo(this.waterPos);
        return this.standPos != null;
    }

    @Override
    protected boolean shouldContinue() {
        return this.waterPos != null && !mob.isInWater();
    }

    /** Se camina hasta la orilla, no hasta el agua: meterse dentro rompería la pose de beber. */
    @Override
    protected @Nullable Vec3 approachTarget() {
        return this.standPos == null ? null : Vec3.atBottomCenterOf(this.standPos);
    }

    /** Mirar y encarar el agua, no la casilla de tierra a la que hemos caminado. */
    @Override
    protected void tickActivity() {
        if (this.waterPos == null || phase() == Phase.APPROACH) return;
        double dx = this.waterPos.getX() + 0.5D - mob.getX();
        double dz = this.waterPos.getZ() + 0.5D - mob.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        mob.setYRot(yaw);
        mob.yHeadRot = yaw;
        mob.yBodyRot = yaw;
        mob.getLookControl().setLookAt(this.waterPos.getX() + 0.5D, mob.getEyeY(), this.waterPos.getZ() + 0.5D);
    }

    @Override
    protected void onActivityStart() {
        mob.setIsDrinking(true);
        mob.setDrinkPos(this.waterPos);
    }

    /** Un sorbo por pasada del bucle. */
    @Override
    protected void onLoopCycle() {
        mob.setThirst(mob.maxThirst());
        mob.gameEvent(GameEvent.BLOCK_ACTIVATE);
        mob.playSound(SoundEvents.GENERIC_SWIM, 0.7F, 0.5F + mob.getRandom().nextFloat());
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        mob.setIsDrinking(false);
        mob.setDrinkPos(null);
        this.waterPos = null;
        this.standPos = null;
    }

    // --- Búsqueda de agua (idéntica a CritterDrinkGoal) ---

    private @Nullable BlockPos findWater() {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        RandomSource random = mob.getRandom();
        int range = 24;

        for (int i = 0; i < 30; i++) {
            BlockPos p = mob.blockPosition().offset(
                    random.nextInt(range) - range / 2,
                    random.nextInt(8) - 4,
                    random.nextInt(range) - range / 2);
            while (mob.level().isEmptyBlock(p) && p.getY() > mob.level().getMinBuildHeight()) {
                p = p.below();
            }
            if (landNextTo(p) != null) {
                double d = mob.blockPosition().distSqr(p);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    /** Casilla seca y despejada junto a {@code pos}, o {@code null} si no la hay (o no es agua). */
    private @Nullable BlockPos landNextTo(BlockPos pos) {
        if (!mob.level().getFluidState(pos).is(FluidTags.WATER)) return null;
        for (Direction dir : HORIZONTALS) {
            BlockPos side = pos.relative(dir);
            if (mob.level().getFluidState(side).isEmpty()
                    && mob.level().getFluidState(side.above()).isEmpty()) {
                return side;
            }
        }
        return null;
    }
}
