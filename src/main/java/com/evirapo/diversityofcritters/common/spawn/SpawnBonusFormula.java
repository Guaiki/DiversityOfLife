package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import net.minecraft.util.Mth;

/**
 * Turns the number of released animals into an increase of the spawn chance.
 *
 * Additive multiplier with a cap:
 * <pre>
 *   bonus       = min(released * bonusPerRelease, maxBonus)
 *   chanceFinal = clamp(chanceBase * (1 + bonus), 0, 1)
 * </pre>
 *
 * With the default values (0.05 per animal, cap 2.0) the maximum bonus (+200%) is reached at
 * 40 releases, before the counter's own cap (50).
 */
public final class SpawnBonusFormula {

    /** Accumulated bonus, already capped. 0.5 means "+50% over the base chance". */
    public static double bonus(int released) {
        DOCServerConfig.Globals globals = DOCServerConfig.globals();
        return bonus(released, globals.bonusPerRelease(), globals.maxBonus());
    }

    public static double bonus(int released, double bonusPerRelease, double maxBonus) {
        if (released <= 0) return 0.0D;
        return Math.min(released * bonusPerRelease, maxBonus);
    }

    /** Effective spawn chance after applying the bonus. */
    public static double finalChance(double chanceBase, int released) {
        return Mth.clamp(chanceBase * (1.0D + bonus(released)), 0.0D, 1.0D);
    }

    private SpawnBonusFormula() {}
}
