package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter.IdleVariant;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import org.jetbrains.annotations.NotNull;

/**
 * Poses de reposo: sentarse y tumbarse. Sustituye a la parte correspondiente de
 * {@link CritterIdleGoal}.
 *
 * <p>Los gestos cosméticos —erguirse, olfatear— <b>no</b> están aquí: viven en
 * {@link CritterIdleGestureGoal}, que no reserva ningún flag porque no comprometen al animal.
 * La división es la que importa: un olfateo no debe impedir que el critter siga paseando, pero
 * sentarse sí, y por eso esto sigue sobre {@code PhasedActivityGoal} con MOVE/LOOK/JUMP.
 *
 * <p>Las dos son cadenas de tres fases, así que la duración sale del clip: {@code chooseChain()}
 * elige la pose y ajusta las repeticiones para que el bucle dure lo mismo que en el goal anterior
 * (80 ticks en las dos). El clip de salida lleva {@code locksMovement()}, así que
 * {@code AnimationLockGoal} sostiene al critter mientras se levanta aunque este goal ya haya
 * terminado.
 */
public class CritterIdleVariantGoal extends PhasedActivityGoal<DiverseCritter> {

    private IdleVariant picked = IdleVariant.NONE;

    public CritterIdleVariantGoal(DiverseCritter critter) {
        super(critter, "idle_sit_enter", "idle_sit_loop", "idle_sit_exit");   // sustituido en chooseChain()
        chance(0.02F);
        cooldown(200, 400);
    }

    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isAlive() || !mob.onGround()) return false;
        if (mob.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (mob.isInWaterOrBubble() || mob.isAttacking()) return false;
        if (mob.isPerformingActivity()) return false;
        if (mob.getTarget() != null || !mob.getNavigation().isDone()) return false;

        this.picked = mob.getRandom().nextBoolean() ? IdleVariant.SIT : IdleVariant.STAND_UP;
        return true;
    }

    @Override
    protected boolean shouldContinue() {
        return mob.getTarget() == null
                && mob.getLastHurtByMob() == null
                && mob.onGround()
                && !mob.isInWater()
                && mob.getSleepState() == DiverseCritter.SleepState.AWAKE;
    }

    /**
     * Elige los clips de la pose sorteada y, de paso, ajusta las repeticiones del bucle. Se puede
     * porque {@code PhasedActivityGoal.start()} llama a este método antes de entrar en la fase LOOP,
     * que es donde se leen.
     */
    @Override
    protected String @NotNull [] chooseChain() {
        if (this.picked == IdleVariant.STAND_UP) {
            repeat(4, 4);            // el bucle dura 1 s; 4 pasadas = 80 ticks
            return new String[]{"idle_stand_up_enter", "idle_stand_up_loop", "idle_stand_up_exit"};
        }
        repeat(1, 1);                // SIT_IDLE dura 4 s = 80 ticks
        return new String[]{"idle_sit_enter", "idle_sit_loop", "idle_sit_exit"};
    }

    @Override
    protected void onActivityStart() {
        mob.setIdleVariant(this.picked);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        // Sólo limpiar si sigue siendo la nuestra: un gesto cosmético pudo haber tomado el relevo.
        if (mob.getIdleVariant() == this.picked) {
            mob.setIdleVariant(IdleVariant.NONE);
        }
        this.picked = IdleVariant.NONE;
    }
}
