package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import com.evirapo.diversityofcritters.common.entity.util.DrinkSources;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
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
        // Agua detras de una valla o al pie de un talud: sin camino la aproximacion se aborta y el
        // goal se pasa el enfriamiento sin haber bebido. Mejor descartarla ya. canReach() y no
        // "!= null": createPath devuelve un camino parcial hasta el punto mas cercano cuando no llega.
        if (this.standPos == null) return false;
        Path path = mob.getNavigation().createPath(this.standPos, 0);
        return path != null && path.canReach();
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
        if (best == null) {
            // Un charco (waterhole) de una casilla casi nunca sale en el muestreo aleatorio.
            BlockPos hole = DrinkSources.nearestDrinkableBlock(mob.level(), mob.blockPosition(), range / 2, 4);
            if (hole != null && landNextTo(hole) != null) best = hole;
        }
        return best;
    }

    /**
     * Donde ponerse de pie para beber de {@code pos}, o {@code null} si no hay sitio (o no es agua).
     *
     * <p>Antes devolvia la casilla de al lado que no tuviera fluido, y en una orilla natural esa
     * casilla es <b>la propia tierra de la orilla</b>: un bloque solido a la altura del agua. La
     * aproximacion mandaba al bicho dentro de ese bloque, la navegacion no encontraba camino y la
     * actividad se abortaba en el acto, asi que de un rio o un lago solo bebia de casualidad. Ahora se
     * busca una casilla donde quepa de pie: al nivel del agua si la orilla es llana (un cubo vaciado
     * en el suelo), o un bloque mas arriba si la orilla es un talud.
     */
    private @Nullable BlockPos landNextTo(BlockPos pos) {
        if (!DrinkSources.isDrinkable(mob.level(), pos)) return null;
        for (Direction dir : HORIZONTALS) {
            BlockPos side = pos.relative(dir);
            if (canStandAt(side)) return side;
            if (canStandAt(side.above())) return side.above();
        }
        return null;
    }

    /** Pies y cabeza en aire seco, y suelo firme debajo. */
    private boolean canStandAt(BlockPos feet) {
        Level level = mob.level();
        BlockPos head = feet.above();
        BlockPos ground = feet.below();
        return level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getFluidState(head).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP);
    }
}
