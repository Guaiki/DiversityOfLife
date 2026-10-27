package com.evirapo.diversityofcritters.common.entity.ai;

import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.animal.Animal;

import java.util.EnumSet;

/**
 * {@link FollowParentGoal} que reclama {@code MOVE}.
 *
 * <p>El de vanilla no declara flags, así que el selector lo ejecuta a la vez que cualquier otro goal,
 * y su {@code moveTo(parent)} cada 10 ticks le pisa la ruta a quien estuviera moviendo al critter. Un
 * juvenil con hambre que salía hacia el bowl daba media vuelta en cuanto se alejaba 3 bloques de la
 * madre, y se moría de hambre a su lado. Con el flag, los goals de mejor prioridad (el bowl va en la
 * 11, este en la 18) lo desalojan en vez de pelearse con él.
 */
public class CritterFollowParentGoal extends FollowParentGoal {

    public CritterFollowParentGoal(Animal animal, double speed) {
        super(animal, speed);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }
}
