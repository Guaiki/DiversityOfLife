package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.NoSuchElementException;

/**
 * Congela al critter en pose de alerta mientras el sensor lo diga.
 *
 * <p>El goal no decide nada: {@code ThreatSensor} es dueno del temporizador, del disparo por sprint
 * y del pausado por agachado. Esto solo clava al bicho, lo hace mirar y lanza el clip.
 *
 * <p>Prioridad 2, la misma que {@code CritterFleeGoal}. Pueden compartirla porque son mutuamente
 * excluyentes por construccion: el sensor esta en un solo estado a la vez.
 */
public class CritterAlertGoal extends Goal {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final DiverseCritter mob;

    /** Se apaga si la especie no registro los clips; ver {@link #playEnterClip()}. */
    private boolean clipMissing;

    public CritterAlertGoal(DiverseCritter mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.sensor().isAlerted();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    /** El congelado tiene que sostenerse todos los ticks, no uno de cada dos. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.plant();
        this.playEnterClip();
    }

    /**
     * Lanza la entrada con {@code play()} y no dejandola auto-arrancar por playCondition. El orden de
     * ticks lo exige: Forge dispara {@code LivingTickEvent} —donde corre {@code animator().tick()}—
     * ANTES de {@code aiStep()}, que es donde el sensor enciende {@code IS_ALERT}. En el tick en que
     * se alerta, el animator todavia no ve la condicion; este {@code start()} corre despues. Al tick
     * siguiente {@code alert_loop} ya no puede pisar a {@code alert_enter}, porque comparten
     * prioridad y el guarda del auto-arranque es {@code priority <= priority}.
     *
     * <p><b>Degrada en vez de crashear.</b> {@code MobAnimator.getByName()} <i>lanza</i>
     * {@code NoSuchElementException} si el nombre no existe: no devuelve null. Una especie que ponga
     * {@code isWary() = true} sin registrar sus clips reventaria la partida la primera vez que se
     * alertara, que es un fallo horrible para algo que se vende como "anadir una especie es una
     * linea". Asi se queda congelada sin pose, con un error en el log que dice exactamente que falta.
     */
    private void playEnterClip() {
        if (this.clipMissing) return;
        try {
            this.mob.animator().play(this.mob.animator().getByName("alert_enter"));
        } catch (NoSuchElementException e) {
            this.clipMissing = true;
            LOGGER.error("{} tiene isWary() = true pero no ha registrado 'alert_enter' / 'alert_loop' "
                            + "en registerAnimations(). Se alerta sin pose.",
                    this.mob.getType().getDescriptionId());
        }
    }

    @Override
    public void tick() {
        this.plant();
        LivingEntity threat = this.mob.sensor().nearestThreat();
        if (threat != null) {
            this.mob.getLookControl().setLookAt(threat.getX(), threat.getEyeY(), threat.getZ());
        }
    }

    /** Igual que AnimationLockGoal: parar la ruta no basta, la inercia arrastra varios bloques. */
    private void plant() {
        this.mob.getNavigation().stop();
        Vec3 velocity = this.mob.getDeltaMovement();
        this.mob.setDeltaMovement(0.0D, velocity.y, 0.0D);
    }
}
