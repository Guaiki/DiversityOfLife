package com.evirapo.diversityofcritters.common.spawn;

import net.minecraft.core.BlockPos;

/**
 * The verdict of a spawn attempt, carrying everything needed to explain it afterwards.
 *
 * @param species       the species that was evaluated
 * @param reason        the filter that decided the outcome
 * @param chanceBase    base chance from the config, before the bonus
 * @param bonus         bonus from the wild population (0.5 = +50%, -0.75 = -75%)
 * @param chanceFinal   effective chance after applying the bonus
 * @param population    wild population of this species in this save
 * @param pos           the position that was evaluated
 * @param detail        short text with the concrete value that failed, or an empty string
 */
public record SpawnCheckResult(
        DOCSpecies species,
        SpawnRejectReason reason,
        double chanceBase,
        double bonus,
        double chanceFinal,
        int population,
        BlockPos pos,
        String detail
) {

    public boolean allowed() {
        return this.reason.allowed();
    }

    public static SpawnCheckResult of(DOCSpecies species, SpawnRejectReason reason, BlockPos pos, String detail) {
        return new SpawnCheckResult(species, reason, 0.0D, 0.0D, 0.0D, 0, pos.immutable(), detail);
    }
}
