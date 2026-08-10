package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sustituye a {@code SitWhenOrderedToGoal} en las especies que ya tienen la cadena
 * {@code ordered_sit_*} registrada en el animator.
 *
 * <p>Hace lo mismo que el goal vanilla —parar la navegación y levantar la pose de sentado— pero
 * además es dueño de las tres fases de la animación. El vanilla soltaba sus flags en el instante en
 * que el jugador la levantaba, así que durante los 20 ticks del clip de salida un goal de paseo ya
 * podía reclamar MOVE y la ardilla se deslizaba por el suelo todavía sentada. Aquí la fase de salida
 * la sostiene {@code AnimationLockGoal} a través de {@code locksMovement()}.
 *
 * <p>El bucle es indefinido: dura lo que el jugador quiera, no un número de repeticiones.
 */
public class CritterOrderedSitGoal extends PhasedActivityGoal<DiverseCritter> {

    /** Distancia (al cuadrado) dentro de la cual el critter se levanta si atacan a su dueño. */
    private static final double OWNER_HELP_RANGE_SQR = 144.0D;

    public CritterOrderedSitGoal(DiverseCritter critter) {
        super(critter, "ordered_sit_enter", "ordered_sit_loop", "ordered_sit_exit");
        repeatIndefinitely();
    }

    @Override
    protected boolean shouldStart() {
        if (!mob.isTame() || mob.isInWaterOrBubble() || !mob.onGround() || !mob.isOrderedToSit()) {
            return false;
        }
        // Igual que vanilla: si están atacando al dueño y lo tenemos cerca, no nos sentamos.
        // (Vanilla devuelve true sin mirar isOrderedToSit cuando no hay dueño; aquí lo exigimos
        // siempre, que es lo que se espera y evita arrancar el goal para pararlo al tick siguiente.)
        LivingEntity owner = mob.getOwner();
        return owner == null
                || mob.distanceToSqr(owner) >= OWNER_HELP_RANGE_SQR
                || owner.getLastHurtByMob() == null;
    }

    @Override
    protected boolean shouldContinue() {
        return mob.isOrderedToSit();
    }

    @Override
    protected void onActivityStart() {
        mob.setInSittingPose(true);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        mob.setInSittingPose(false);
    }
}
