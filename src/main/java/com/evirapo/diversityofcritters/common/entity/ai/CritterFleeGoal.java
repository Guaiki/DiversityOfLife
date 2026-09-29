package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Corre en direccion contraria a la amenaza mientras el sensor lo diga.
 *
 * <p><b>No toca el sprint ni registra animaciones.</b> {@code customServerAiStep()} de cada especie
 * ya deriva {@code setSprinting()} de {@code getMoveControl().getSpeedModifier() >= 1.25}, y
 * {@link DiverseCritter#fleeSpeed()} vale 1.6, asi que huir enciende el clip {@code run} por si solo.
 * Escribir la bandera tambien desde aqui solo anadia un segundo dueno a un flag compartido, que es
 * como se tarda una tarde en diagnosticar unas particulas colgadas.
 *
 * <p><b>Retiene MOVE durante toda la huida, y eso es la mitad del valor del goal.</b> Sin el, el
 * estado FLEE del sensor no sujeta ningun flag y los goals de idle —el paseo en prioridad 19, el
 * gesto en 22— arrancan tan tranquilos: se veia en juego como una civeta que se congela cuatro
 * segundos y despues se pone a pasear mientras te acercas. Con esto en prioridad 2 no pueden ni
 * empezar, porque {@code canBeReplacedBy} exige prioridad estrictamente menor.
 *
 * <p><b>Huye solo por el suelo.</b> Con prioridad 2 desaloja a {@code CivetClimbGoal} (16), asi que
 * no trepa huyendo. Integrar la trepada <i>en</i> la huida es un trozo de trabajo comparable a todo
 * el sistema; en cambio, al soltar MOVE el goal de trepada toma el relevo por si solo, con lo que el
 * bicho escapa corriendo y acaba subido a un arbol. Efecto casi identico, coste cero.
 */
public class CritterFleeGoal extends Goal {

    /** Cada cuanto se recalcula el destino, para que la huida siga al que persigue. */
    private static final int REPATH_INTERVAL = 10;
    /**
     * Reintento cuando no se encontro sitio a donde huir. Esperar los 10 ticks normales con la
     * navegacion parada es medio segundo de critter plantado en mitad de la huida.
     */
    private static final int RETRY_INTERVAL = 2;
    private static final int FLEE_DIST_HORIZONTAL = 16;
    private static final int FLEE_DIST_VERTICAL = 7;

    private final DiverseCritter mob;
    private int repathCooldown;

    public CritterFleeGoal(DiverseCritter mob) {
        this.mob = mob;
        // Solo MOVE: sin LOOK, para que siga pudiendo mirar hacia donde corre.
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.mob.sensor().isFleeing();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.repath();
    }

    @Override
    public void tick() {
        if (--this.repathCooldown <= 0) {
            this.repath();
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    private void repath() {
        LivingEntity threat = this.mob.sensor().nearestThreat();
        if (threat == null) {
            this.repathCooldown = REPATH_INTERVAL;
            return;
        }

        Vec3 away = DefaultRandomPos.getPosAway(
                this.mob, FLEE_DIST_HORIZONTAL, FLEE_DIST_VERTICAL, threat.position());
        // moveTo devuelve false si no hay ruta hasta el punto, asi que no basta con que getPosAway
        // haya devuelto algo: las dos cosas tienen que salir bien para dar por buena la ruta.
        boolean routed = away != null
                && this.mob.getNavigation().moveTo(away.x, away.y, away.z, this.mob.fleeSpeed());
        this.repathCooldown = routed ? REPATH_INTERVAL : RETRY_INTERVAL;
    }
}
