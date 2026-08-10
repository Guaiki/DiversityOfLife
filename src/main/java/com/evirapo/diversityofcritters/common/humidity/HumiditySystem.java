package com.evirapo.diversityofcritters.common.humidity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class HumiditySystem {

    /** Tier efectivo: prioriza el área de un Humidifier, si no, bioma + lluvia. */
    public static HumidityTier getEffectiveTier(Level level, BlockPos pos) {
        HumidityTier override = HumidifierManager.getOverride(level, pos);
        if (override != null) return override;
        return getBiomeTier(level, pos);
    }

    public static HumidityTier getBiomeTier(Level level, BlockPos pos) {
        float downfall = level.getBiome(pos).value().getModifiedClimateSettings().downfall();
        HumidityTier tier = HumidityTier.fromDownfall(downfall);

        // La lluvia sube un tier temporalmente en biomas que pueden llover.
        if (level.isRainingAt(pos) && tier.ordinal() < HumidityTier.SATURATED.ordinal()) {
            tier = HumidityTier.byId(tier.ordinal() + 1);
        }
        return tier;
    }
}
