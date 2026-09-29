package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.IDiggingCritter;
import com.evirapo.diversityofcritters.common.entity.util.CritterClaims;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Cavar en tierra buscando gusanos. Sustituye a {@link DigDirtGoal}.
 *
 * <p>Sirve a cualquier {@link DiverseCritter} que implemente {@link IDiggingCritter}; el goal en sí
 * no sabe de especies. La cota del constructor es lo que impide registrarlo en un critter que no
 * sepa cavar — ver la nota de diseño en {@link IDiggingCritter}.
 *
 * <p>La búsqueda de tierra y la recompensa (gusanos + enriquecimiento) se conservan del goal
 * anterior. Lo que desaparece es la máquina de fases a mano: el {@code DIG_DURATION = 80} escrito en
 * el goal —que no incluía la salida, así que ya estaba desincronizado del clip— y el par
 * {@code isFinishing}/{@code finishingTimer < 10} que replicaba a ojo la longitud del
 * {@code dig_exit}. Ahora la duración sale del clip (repeticiones × longitud) y la salida la
 * sostiene {@code AnimationLockGoal} vía {@code locksMovement()}, así que el critter no se va
 * caminando con la pose de sacar las patas del hoyo a medias.
 *
 * <p>La aproximación también la lleva la fase APPROACH, en vez del {@code distSq > 1.8D} + moveTo
 * dentro del propio {@code tick()}.
 */
public class CritterDigDirtChainGoal extends PhasedActivityGoal<DiverseCritter> {

    /** ≈ el √1.8 del goal anterior. */
    private static final float MAX_DIG_DIST = 1.35F;

    /** Medio lado del cubo de busqueda, el mismo ±4 del goal anterior. */
    private static final int SEARCH_RADIUS = 4;

    private final IDiggingCritter digger;
    private @Nullable BlockPos targetBlock;

    public <E extends DiverseCritter & IDiggingCritter> CritterDigDirtChainGoal(E critter) {
        super(critter, "dig_enter", "dig_loop", "dig_exit");
        this.digger = critter;
        // El bucle dura 0.5 s (10 ticks); 8 pasadas = los 80 ticks de DIG_DURATION.
        repeat(8, 8);
        cooldown(200, 400);
        approach(MAX_DIG_DIST, 1.2D);
    }

    /**
     * Aqui si vale preguntar por {@code isDoingSpecialActivity()}: el goal todavia no ha arrancado,
     * asi que {@code isDigging()} es false y no nos estamos contando a nosotros mismos.
     */
    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isEnrichmentNeeded()) return false;
        if (mob.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (mob.isAttacking() || mob.isOrderedToSit() || mob.isIdleLocked()) return false;
        if (mob.isDoingSpecialActivity()) return false;
        // Descartar los hoyos que ya tienen dueno. El radio es el doble del de busqueda porque dos
        // civetas pueden pelearse por un bloque estando cada una a 4 de el, o sea a 8 la una de la otra.
        this.targetBlock = findDirtBlock(CritterClaims.digClaims(mob, SEARCH_RADIUS * 2));
        if (this.targetBlock == null) return false;

        // Ver la nota gemela en CritterScratchLogChainGoal: si la aproximacion falla,
        // `PhasedActivityGoal.finish(true)` reproduce el clip de salida aunque la entrada no haya
        // sonado, y se ve la pose de cavar apareciendo y corrigiendose sola.
        if (mob.getNavigation().createPath(this.targetBlock.above(), 0) == null) return false;

        // Reservar ya, no al llegar: durante la aproximacion es justo cuando otra civeta haria su
        // busqueda y elegiria el mismo hoyo.
        this.digger.setDigTarget(this.targetBlock);
        return true;
    }

    /**
     * Solo la validez del objetivo, como en {@code SquirrelEatLeavesChainGoal}.
     *
     * <p><b>No preguntar aqui por {@code isDoingSpecialActivity()}.</b> {@code PhasedActivityGoal}
     * evalua esto al principio de <i>cada</i> tick y en <i>todas</i> las fases, no solo en el bucle;
     * y {@code CivetEntity.isDoingSpecialActivity()} incluye {@code isDigging()}, que es justo el
     * flag que enciende nuestro propio {@code onActivityStart()}. Ponerlo aqui hacia que el goal se
     * matara a si mismo un tick despues de empezar: se veia el clip de entrada y nada mas.
     *
     * <p>No hace falta ademas: lo que deberia cortar esto —dormir (prio 1), sentarse por orden (3),
     * atacar (7)— tiene mejor prioridad y desaloja el goal, y al desalojarlo su {@code stop()} ya
     * reproduce la salida.
     */
    @Override
    protected boolean shouldContinue() {
        return this.targetBlock != null && isDiggable(this.targetBlock);
    }

    @Override
    protected @Nullable Vec3 approachTarget() {
        return this.targetBlock == null ? null : Vec3.atBottomCenterOf(this.targetBlock.above());
    }

    /** Mirar al hoyo mientras se cava; la fase APPROACH ya orienta sola al caminar. */
    @Override
    protected void tickActivity() {
        if (this.targetBlock == null || phase() == Phase.APPROACH) return;
        mob.getLookControl().setLookAt(
                this.targetBlock.getX() + 0.5D, this.targetBlock.getY(), this.targetBlock.getZ() + 0.5D);
    }

    @Override
    protected void onActivityStart() {
        this.digger.setDigging(true);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        this.digger.setDigging(false);
        // Igual que antes: la recompensa caía en el tick final del contador, así que cavar a medias
        // no premiaba.
        if (!interrupted) {
            spawnWorms();
            mob.setEnrichment(mob.maxEnrichment());
        }
        this.digger.setDigTarget(null);
        this.targetBlock = null;
    }

    /** Siempre saca 1, y tiene 50% de probabilidad de sacar un segundo (1 o 2). */
    private void spawnWorms() {
        int count = mob.level().random.nextInt(2) + 1;
        for (int i = 0; i < count; i++) {
            ItemStack worm = new ItemStack(DOCItems.WORM.get());
            ItemEntity item = new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.3, mob.getZ(), worm);
            item.setDeltaMovement(mob.level().random.nextGaussian() * 0.05, 0.2,
                    mob.level().random.nextGaussian() * 0.05);
            mob.level().addFreshEntity(item);
        }
    }

    private @Nullable BlockPos findDirtBlock(Set<BlockPos> claimed) {
        BlockPos origin = mob.blockPosition();
        BlockPos min = origin.offset(-SEARCH_RADIUS, -1, -SEARCH_RADIUS);
        BlockPos max = origin.offset(SEARCH_RADIUS, 1, SEARCH_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isDiggable(pos) && !claimed.contains(pos)) return pos.immutable();
        }
        return null;
    }

    private boolean isDiggable(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        return (state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK))
                && mob.level().isEmptyBlock(pos.above());
    }
}
