package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * MÓDULO 2 — Sistema de planeo (el más complejo del quote).
 *
 * Se activa en dos escenarios:
 *   1. HANG release: SquirrelClimbGoal.stop() llama a tryStartGlide(),
 *      que arma IS_GLIDING. Este goal detecta el estado armado y toma el control.
 *   2. Altura sin pared trepable: el squirrel está cayendo, sin pared adyacente
 *      y con >= GLIDE_MIN_AIR bloques de aire debajo → inicia el glide él mismo.
 *      (Incluye el caso del Civet que perdía la pared al coronar: en el squirrel
 *      updateClimbState() ya no aplica el impulso de salto, solo suelta, y este
 *      goal atrapa la caída.)
 *
 * Diseño "infalible":
 *   - Física DETERMINISTA: cada tick se SOBRESCRIBE el deltaMovement completo
 *     (no se ajusta la gravedad — se reemplaza). Ningún otro sistema puede
 *     inyectar velocidad durante el planeo.
 *   - Dirigible: si la navegación tiene un objetivo, gira hacia él con tasa
 *     limitada (MAX_TURN_PER_TICK); si no, planea recto con el yaw actual.
 *   - CUATRO condiciones de salida redundantes: suelo, agua, colisión
 *     horizontal y timeout de seguridad (MAX_GLIDE_TICKS).
 *   - Al terminar, setGliding(false) arma AMBOS cooldowns (postGlide +
 *     postDescent) y se cera la velocidad horizontal — la misma clase de
 *     protección que el fix de postDescentCooldown en el Civet, para que el
 *     aterrizaje jamás re-dispare el sistema de climb.
 */
public class SquirrelGlideGoal extends Goal {

    private final SquirrelEntity squirrel;

    private int glideTicks = 0;

    /** Timeout de seguridad contra estados colgados. Las salidas normales (suelo, agua,
     *  colisión) manejan el aterrizaje; este límite solo protege el caso patológico.
     *  A 0.08 bloques/tick, 3000 ticks cubre ~240 bloques de descenso. */
    private static final int MAX_GLIDE_TICKS = 3000;
    /** Velocidad horizontal del planeo (bloques/tick). */
    private static final double GLIDE_FORWARD_SPEED = 0.18D;
    /** Velocidad de descenso constante del planeo (reemplaza a la gravedad). */
    private static final double GLIDE_SINK_SPEED = -0.08D;
    /** Tasa máxima de giro hacia el objetivo (grados/tick). No puede "girar en seco". */
    private static final float MAX_TURN_PER_TICK = 4.0F;

    public SquirrelGlideGoal(SquirrelEntity squirrel) {
        this.squirrel = squirrel;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    /** La física necesita correr CADA tick, no cada 2 (default de Goal). */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (squirrel.level().isClientSide()) return false;

        // ESCENARIO 1: el glide ya fue armado externamente (hang release vía
        // tryStartGlide, o handoff de descenso desde SquirrelMoveControl).
        if (squirrel.isGliding()) return true;

        // ESCENARIO 2: en altura, cayendo, sin pared trepable.
        if (squirrel.isNewborn() || squirrel.isSleeping() || squirrel.isOrderedToSit()) return false;
        if (squirrel.onGround() || squirrel.isClimbing() || squirrel.isMantling()) return false;
        if (squirrel.isInWater() || squirrel.isInWaterOrBubble()) return false;
        if (squirrel.isInPostGlideCooldown()) return false;
        // Block glide during the brief arc after cresting a log column: the squirrel
        // just reached the top and is still rising — it should land, not fly away.
        if (squirrel.isInPostCrestCooldown()) return false;
        // Con pared adyacente el sistema de climb es quien resuelve, no el glide.
        if (squirrel.hasAdjacentClimbableBlock()) return false;
        // Solo si realmente está cayendo (evita activarse en el ápice de un salto).
        if (squirrel.getDeltaMovement().y > -0.05D) return false;

        return squirrel.distanceToGround(SquirrelEntity.GLIDE_MIN_AIR + 1) >= SquirrelEntity.GLIDE_MIN_AIR;
    }

    @Override
    public boolean canContinueToUse() {
        // Cuatro salidas redundantes + el failsafe del tick() de la entidad
        // (que apaga IS_GLIDING en suelo/agua aunque este goal se retrase).
        return squirrel.isGliding()
                && !squirrel.onGround()
                && !squirrel.isInWaterOrBubble()
                && !squirrel.horizontalCollision
                && this.glideTicks < MAX_GLIDE_TICKS;
    }

    @Override
    public void start() {
        this.glideTicks = 0;
        if (!squirrel.isGliding()) {
            // Escenario 2: armamos el glide nosotros. tryStartGlide() re-valida
            // todo internamente; si falla, canContinueToUse() corta de inmediato.
            squirrel.tryStartGlide();
        }
    }

    @Override
    public void tick() {
        this.glideTicks++;

        // --- 1. Dirección: dirigible hacia el objetivo del path, si existe ---
        float targetYaw = squirrel.getYRot();

        Path path = squirrel.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            BlockPos target = path.getTarget();
            double dx = target.getX() + 0.5D - squirrel.getX();
            double dz = target.getZ() + 0.5D - squirrel.getZ();
            // Solo dirigir si el objetivo no está prácticamente debajo.
            if (dx * dx + dz * dz > 1.0D) {
                targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            }
        }

        // Giro con tasa limitada: imposible "dar vueltas locas".
        float newYaw = Mth.approachDegrees(squirrel.getYRot(), targetYaw, MAX_TURN_PER_TICK);
        squirrel.setYRot(newYaw);
        squirrel.setYBodyRot(newYaw);
        squirrel.setYHeadRot(newYaw);

        // --- 2. Física determinista: sobrescritura TOTAL del deltaMovement ---
        // No se "ajusta" la gravedad de Minecraft: se reemplaza por completo.
        float yawRad = (float) Math.toRadians(newYaw);
        double fx = -Math.sin(yawRad) * GLIDE_FORWARD_SPEED;
        double fz =  Math.cos(yawRad) * GLIDE_FORWARD_SPEED;
        squirrel.setDeltaMovement(fx, GLIDE_SINK_SPEED, fz);

        // Redundante con causeFallDamage()=false, pero gratis y a prueba de todo.
        squirrel.fallDistance = 0.0F;
    }

    @Override
    public void stop() {
        // Arma postGlideCooldown + postDescentCooldown (ver setGliding en la entidad):
        // el aterrizaje no puede re-disparar CLIMB_UP vía horizontalCollision.
        squirrel.setGliding(false);

        // Cero velocidad horizontal al soltar el control: evita que el impulso
        // residual empuje al squirrel contra una pared (mismo patrón que el
        // landingCooldown del Civet, que además refuerza esto en el MoveControl).
        Vec3 motion = squirrel.getDeltaMovement();
        squirrel.setDeltaMovement(0.0D, motion.y, 0.0D);

        // El path que traía probablemente quedó obsoleto tras volar varios bloques.
        squirrel.getNavigation().recomputePath();

        this.glideTicks = 0;
    }
}
