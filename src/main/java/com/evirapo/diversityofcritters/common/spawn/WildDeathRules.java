package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.damage.DOCDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Which deaths of a wild animal count against its population.
 *
 * The caller has already checked that the animal is wild and counted; this only looks at the
 * cause. Rules are checked in order and the first that applies decides.
 */
public final class WildDeathRules {

    /**
     * @param lastHurtByPlayer the player vanilla would give the kill to (the same rule that hands
     *                         out XP: valid for 5 seconds after the hit, and it already covers
     *                         arrows, pushing into lava, setting on fire and the player's pets)
     * @return the event to count, or null if this death does not count
     */
    @Nullable
    public static PopulationEvent classify(DamageSource source, @Nullable Player lastHurtByPlayer) {
        // Admin tooling and the void: otherwise testing and moderation would corrupt the counters.
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return null;

        // The habitat killed it. Our own types, so a player's fire or powder snow never lands here.
        if (source.is(DOCDamageTypes.STARVATION)) return PopulationEvent.STARVATION;
        if (source.is(DOCDamageTypes.HYPOTHERMIA) || source.is(DOCDamageTypes.HEATSTROKE)) {
            return PopulationEvent.TEMPERATURE_DEATH;
        }
        if (source.is(DOCDamageTypes.HUMIDITY)) return PopulationEvent.HUMIDITY_DEATH;
        // Thirst is excluded at the client's request.
        if (source.is(DOCDamageTypes.DEHYDRATION)) return null;

        if (lastHurtByPlayer != null && !lastHurtByPlayer.isCreative() && !lastHurtByPlayer.isSpectator()) {
            return PopulationEvent.PLAYER_KILL;
        }

        // Predators, falls, drowning, cactus...: the ecosystem working as intended.
        return null;
    }

    private WildDeathRules() {}
}
