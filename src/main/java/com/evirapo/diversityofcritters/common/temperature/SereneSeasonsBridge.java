package com.evirapo.diversityofcritters.common.temperature;

import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

/**
 * Wrapper opcional de Serene Seasons. Si el mod no está instalado,
 * el offset estacional es 0 y se usa solo la temperatura vanilla del bioma.
 */
public class SereneSeasonsBridge {

    private static final boolean LOADED = ModList.get().isLoaded("sereneseasons");

    public static float getSeasonOffset(Level level) {
        if (!LOADED || !level.dimensionType().natural()) return 0.0F;
        try {
            return Impl.getOffset(level);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** Clase interna: solo se carga si Serene Seasons está presente. */
    private static final class Impl {
        static float getOffset(Level level) {
            Season.SubSeason subSeason = SeasonHelper.getSeasonState(level).getSubSeason();
            return switch (subSeason) {
                case EARLY_WINTER, MID_WINTER, LATE_WINTER -> -0.8F;
                case EARLY_SPRING -> -0.25F;
                case LATE_AUTUMN -> -0.25F;
                default -> 0.0F;
            };
        }
    }
}
