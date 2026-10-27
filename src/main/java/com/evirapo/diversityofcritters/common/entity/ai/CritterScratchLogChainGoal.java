package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.IScratchingCritter;
import com.evirapo.diversityofcritters.common.entity.util.CritterClaims;
import com.evirapo.diversityofcritters.common.entity.util.Reachability;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Afilarse las uñas contra un tronco. Sustituye a {@link ScratchLogGoal}.
 *
 * <p>Sirve a cualquier {@link DiverseCritter} que implemente {@link IScratchingCritter} — ver la
 * nota de diseño en {@link com.evirapo.diversityofcritters.common.entity.custom.base.IDiggingCritter}
 * sobre por qué la capacidad viaja por interfaz.
 *
 * <p>Lo que desaparece del goal anterior es el {@code TOTAL_DURATION = 100} escrito a mano —sin
 * salida, así que el clip {@code scratch_exit} se cortaba en seco al caer el flag— y el empujón
 * manual con {@code setDeltaMovement} para pegarse al tronco.
 *
 * <p><b>Cómo se pega al tronco.</b> No se navega al tronco: se navega a la casilla <i>de al lado</i>,
 * igual que {@link CritterDrinkChainGoal} camina a la orilla y no al agua. Es lo que obliga la forma
 * de {@code PhasedActivityGoal}: su fase APPROACH termina en cuanto la distancia al destino baja de
 * {@code approach(reach)}, medida al <b>centro</b> del destino. Apuntando al centro del tronco hacía
 * falta un reach de al menos 1.12 sólo para que un critter ya pegado contase como llegado (0.75 de
 * ancho, medio bloque de tronco, medio de altura), y con ese radio se daba por bueno pararse a un
 * bloque de distancia: el bicho arañaba el aire. Apuntando a la casilla contigua, "haber llegado" es
 * "estar encima de ella", y ahí el reach puede ser corto de verdad.
 */
public class CritterScratchLogChainGoal extends PhasedActivityGoal<DiverseCritter> {

    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** Corto a propósito: el destino es la casilla contigua, y la siguiente está a 1.0. */
    private static final float STAND_REACH = 0.8F;

    private final IScratchingCritter scratcher;
    private final int searchRadius;

    private @Nullable BlockPos logPos;
    private @Nullable BlockPos standPos;

    public <E extends DiverseCritter & IScratchingCritter> CritterScratchLogChainGoal(
            E critter, double speed, int searchRadius) {
        super(critter, "scratch_enter", "scratch_loop", "scratch_exit");
        this.scratcher = critter;
        this.searchRadius = searchRadius;
        // El bucle dura 0.5 s (10 ticks); 10 pasadas = los 100 ticks de TOTAL_DURATION.
        repeat(10, 10);
        cooldown(200, 400);
        approach(STAND_REACH, speed);
    }

    /**
     * Aquí sí vale preguntar por {@code isDoingSpecialActivity()}: el goal todavía no ha arrancado,
     * así que {@code isScratching()} es false y no nos estamos contando a nosotros mismos.
     */
    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isEnrichmentNeeded()) return false;
        if (mob.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (mob.isAttacking() || mob.isOrderedToSit() || mob.isIdleLocked()) return false;
        if (mob.isDoingSpecialActivity()) return false;

        // Descartar los troncos que ya tienen dueno, y quedarse con el mas cercano al que haya ruta
        // COMPLETA hasta su casilla contigua. Sin ruta se entraba en la fase APPROACH condenada a
        // fallar, y al fallar `PhasedActivityGoal.finish(true)` reproduce el clip de SALIDA aunque la
        // entrada no haya sonado nunca: la civeta se erguia en la pose de aranar y "se corregia" sin
        // haber aranado. Y con "createPath != null" no bastaba: con una pared delante devuelve un
        // camino parcial hasta la pared, asi que el tronco de detras se elegia una y otra vez.
        this.logPos = Reachability.nearestReachable(mob,
                findLogs(CritterClaims.scratchClaims(mob, searchRadius * 2.0D)),
                this::standNextTo, 0, searchRadius * 2.0F);
        this.standPos = this.logPos == null ? null : standNextTo(this.logPos);
        if (this.standPos == null) return false;

        // Reservar ya, no al llegar: durante la aproximacion es justo cuando otra civeta haria su
        // busqueda y elegiria el mismo tronco.
        this.scratcher.setScratchTarget(this.logPos);
        return true;
    }

    /**
     * Sólo la validez del objetivo y la necesidad que nos trajo aquí, como en
     * {@code SquirrelEatLeavesChainGoal}.
     *
     * <p><b>No preguntar aquí por {@code isDoingSpecialActivity()}.</b> {@code PhasedActivityGoal}
     * evalúa esto al principio de <i>cada</i> tick y en <i>todas</i> las fases, no sólo en el bucle;
     * y {@code CivetEntity.isDoingSpecialActivity()} incluye {@code isScratching()}, que es justo el
     * flag que enciende nuestro propio {@code onActivityStart()}. Ponerlo aquí hacía que el goal se
     * matara a sí mismo un tick después de empezar: se veía el clip de entrada y nada más.
     *
     * <p>No hace falta además: lo que debería cortar esto —dormir (prio 1), sentarse por orden (3),
     * atacar (7)— tiene mejor prioridad y desaloja el goal, y al desalojarlo su {@code stop()} ya
     * reproduce la salida.
     */
    @Override
    protected boolean shouldContinue() {
        return this.logPos != null && isValidLog(this.logPos) && mob.isEnrichmentNeeded();
    }

    /** La casilla de al lado del tronco, no el tronco: ver la nota de la clase. */
    @Override
    protected @Nullable Vec3 approachTarget() {
        return this.standPos == null ? null : Vec3.atBottomCenterOf(this.standPos);
    }

    /**
     * Encarar el tronco con el cuerpo, no sólo con la cabeza. La pose de arañar levanta las patas
     * delanteras contra la corteza, así que si el cuerpo mira a otro lado se araña el aire de al
     * lado. Mismo tratamiento que {@link CritterDrinkChainGoal} le da a beber.
     */
    @Override
    protected void tickActivity() {
        if (this.logPos == null || phase() == Phase.APPROACH) return;

        double dx = this.logPos.getX() + 0.5D - mob.getX();
        double dz = this.logPos.getZ() + 0.5D - mob.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        mob.setYRot(yaw);
        mob.yHeadRot = yaw;
        mob.yBodyRot = yaw;
        mob.getLookControl().setLookAt(
                this.logPos.getX() + 0.5D, this.logPos.getY() + 0.5D, this.logPos.getZ() + 0.5D);
    }

    @Override
    protected void onActivityStart() {
        // Centrar en la casilla contigua, conservando la altura. La fase APPROACH sólo garantiza
        // "a menos de STAND_REACH del centro", asi que sin esto el hueco contra la corteza dependia
        // de donde parase la navegacion y cambiaba en cada intento. Es lo mismo que hace
        // FindDigBoxGoal al subirse a la caja, y el salto no se ve porque cae en el mismo tick en
        // que arranca el clip de entrada.
        if (this.standPos != null) {
            mob.setPos(this.standPos.getX() + 0.5D, mob.getY(), this.standPos.getZ() + 0.5D);
            mob.setDeltaMovement(Vec3.ZERO);
        }
        this.scratcher.setScratching(true);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        this.scratcher.setScratching(false);
        // Igual que antes: la recompensa sólo cae si se completa.
        if (!interrupted) {
            mob.setEnrichment(mob.maxEnrichment());
        }
        this.scratcher.setScratchTarget(null);
        this.logPos = null;
        this.standPos = null;
    }

    /**
     * Los troncos sin dueño a los que además se pueda uno arrimar. Exigir la casilla contigua durante
     * la búsqueda —y no después— evita elegir un tronco encajonado y quedarse dando vueltas: es la
     * misma forma que {@code CritterDrinkChainGoal.findWater()}, que descarta el agua sin orilla. Cuál
     * se alcanza andando lo decide {@link Reachability}.
     */
    private List<BlockPos> findLogs(Set<BlockPos> claimed) {
        BlockPos origin = mob.blockPosition();
        List<BlockPos> logs = new ArrayList<>();

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (claimed.contains(pos)) continue;
                    if (!isValidLog(pos) || standNextTo(pos) == null) continue;
                    logs.add(pos.immutable());
                }
            }
        }
        return logs;
    }

    /**
     * Casilla pisable pegada al tronco y a su misma altura, la más cercana de las cuatro. A su altura
     * y no a la del suelo: la pose de arañar es contra la corteza que tiene delante, y un tronco dos
     * bloques por encima no se araña desde abajo (la civeta mide 0.4 de alto).
     */
    private @Nullable BlockPos standNextTo(BlockPos log) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction dir : HORIZONTALS) {
            BlockPos side = log.relative(dir);
            BlockState at = mob.level().getBlockState(side);
            BlockState below = mob.level().getBlockState(side.below());

            boolean free = at.getCollisionShape(mob.level(), side).isEmpty();
            boolean floor = below.isFaceSturdy(mob.level(), side.below(), Direction.UP);
            if (!free || !floor) continue;

            double distSq = mob.distanceToSqr(side.getX() + 0.5D, side.getY(), side.getZ() + 0.5D);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = side.immutable();
            }
        }
        return best;
    }

    private boolean isValidLog(BlockPos pos) {
        return mob.level().getBlockState(pos).is(BlockTags.LOGS);
    }
}
