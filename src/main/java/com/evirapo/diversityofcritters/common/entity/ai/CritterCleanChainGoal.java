package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;

/**
 * Acicalado. Sustituye a {@link CritterCleanGoal} en las especies que ya tienen la cadena
 * {@code clean_*} registrada; las que no, siguen con el goal viejo hasta la fase 4.
 *
 * <p>Lo que cambia respecto al viejo: la duración sale del clip (repeticiones × longitud) en vez de
 * un {@code CLEAN_DURATION = 80} escrito a mano que además no incluía la salida, el clip de salida
 * se reproduce entero sin que el critter se deslice, y se añade el gate que faltaba contra trepar
 * (vía {@code isDoingSpecialActivity()}, que ahora incluye {@code isClimbing()}).
 */
public class CritterCleanChainGoal extends PhasedActivityGoal<DiverseCritter> {

    public CritterCleanChainGoal(DiverseCritter critter) {
        super(critter, "clean_enter", "clean_loop", "clean_exit");
        // El bucle dura 0.5 s (10 ticks); 6-8 pasadas ≈ los 60-80 ticks del goal anterior.
        repeat(6, 8);
        chance(0.005F);
        cooldown(200, 400);
    }

    @Override
    protected boolean shouldStart() {
        return mob.hasLowHygiene() && this.notBusy() && !mob.isNewborn();
    }

    @Override
    protected boolean shouldContinue() {
        return this.notBusy();
    }

    private boolean notBusy() {
        return mob.getSleepState() == DiverseCritter.SleepState.AWAKE
                && !mob.isAttacking()
                && !mob.isOrderedToSit()
                && !mob.isIdleLocked()
                && !mob.isDoingSpecialActivity();
    }

    @Override
    protected void onActivityStart() {
        mob.setCleaning(true);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        mob.setCleaning(false);
        // Igual que antes: cortarlo a medias no premia. El goal viejo daba la higiene en el tick
        // final del contador, así que un acicalado interrumpido tampoco contaba.
        if (!interrupted) {
            mob.setHygiene(mob.maxHygiene());
        }
    }
}
