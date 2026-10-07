package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;

/**
 * Turns a species' wild population into its spawn chance.
 *
 * <pre>
 *   multiplier  = clamp(population / basePopulation, 0, 1 + maxBonus)
 *   chanceFinal = clamp(chanceBase * multiplier, 0, 1)
 * </pre>
 *
 * With the default values (base 20, maxBonus 2.0): 5 animals give 25% of the base chance, 20 give
 * exactly the base chance, 60 give triple. Each animal is worth 5% of the base chance, the same a
 * release was worth in module 2.
 */
public final class SpawnBonusFormula {

    public static double multiplier(int population) {
        return PopulationMath.multiplier(population,
                DOCServerConfig.population().basePopulation(),
                DOCServerConfig.globals().maxBonus());
    }

    /** Bonus over the base chance. Negative when the species is below its base population. */
    public static double bonus(int population) {
        return multiplier(population) - 1.0D;
    }

    /** Effective spawn chance for this population. */
    public static double finalChance(double chanceBase, int population) {
        return PopulationMath.finalChance(chanceBase, multiplier(population));
    }

    /** The bonus as players read it: {@code "+15%"}, {@code "-75%"}. */
    public static String bonusLabel(int population) {
        return String.format("%+d%%", Math.round(bonus(population) * 100.0D));
    }

    private SpawnBonusFormula() {}
}
