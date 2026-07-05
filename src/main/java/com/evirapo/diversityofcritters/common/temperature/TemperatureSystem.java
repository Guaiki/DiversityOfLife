package com.evirapo.diversityofcritters.common.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class TemperatureSystem {

    /** Los biomas muy calientes (desierto, badlands, nether) no se ven afectados por las estaciones. */
    private static final float SEASON_IMMUNE_TEMPERATURE = 1.9F;

    /** Tier efectivo: prioriza el área de un Air Conditioner, si no, bioma + estación. */
    public static TemperatureTier getEffectiveTier(Level level, BlockPos pos) {
        TemperatureTier override = AirConditionerManager.getOverride(level, pos);
        if (override != null) return override;
        return getBiomeTier(level, pos);
    }

    public static TemperatureTier getBiomeTier(Level level, BlockPos pos) {
        float base = level.getBiome(pos).value().getBaseTemperature();
        if (base < SEASON_IMMUNE_TEMPERATURE) {
            base += SereneSeasonsBridge.getSeasonOffset(level);
        }
        return TemperatureTier.fromTemperature(base);
    }
}
