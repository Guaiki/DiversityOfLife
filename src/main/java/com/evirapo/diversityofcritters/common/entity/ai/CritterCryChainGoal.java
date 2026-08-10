package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;

/**
 * Llanto del recién nacido. Sustituye a {@link BabyCryGoal} en las especies cuyo modelo de bebé ya
 * lee la capa de animación.
 *
 * <p>Lo que cambia respecto al viejo:
 *
 * <ul>
 *   <li>El goal anterior sólo paraba la navegación; las tres fases las llevaba
 *       {@code handleCryingAnimationClient()} con contadores que <b>sólo existían en el cliente</b>,
 *       así que un jugador que entraba en rango a mitad del llanto veía la fase equivocada.</li>
 *   <li>No retenía durante los 10 ticks del clip de parada, el mismo fallo que tenía el acicalado:
 *       el bebé podía echar a gatear con la pose de dejar de llorar a medias. Ahora la salida lleva
 *       {@code locksMovement()} y la sostiene {@code AnimationLockGoal}.</li>
 * </ul>
 *
 * <p>El bucle es indefinido: llora hasta que deja de tener hambre, o hasta que su madre lo atiende
 * ({@code FindCryingBabyGoal} apaga el flag). Que el flag tenga varios dueños es justo el motivo de
 * que el bucle lleve {@code playCondition} además del goal — así se para lo apague quien lo apague.
 */
public class CritterCryChainGoal extends PhasedActivityGoal<DiverseCritter> {

    public CritterCryChainGoal(DiverseCritter critter) {
        super(critter, "cry_enter", "cry_loop", "cry_exit");
        repeatIndefinitely();
    }

    @Override
    protected boolean shouldStart() {
        return mob.isNewborn() && mob.isCrying();
    }

    @Override
    protected boolean shouldContinue() {
        return mob.isNewborn() && mob.isCrying();
    }
}
