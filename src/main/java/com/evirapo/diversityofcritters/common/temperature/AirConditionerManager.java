package com.evirapo.diversityofcritters.common.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro server-side de las áreas activas de Air Conditioners.
 * Cada block entity se registra al cargar y se elimina al removerse/descargarse.
 */
public class AirConditionerManager {

    public record Area(TemperatureTier tier, int radius) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Area>> AREAS = new ConcurrentHashMap<>();

    public static void add(Level level, BlockPos pos, TemperatureTier tier, int radius) {
        AREAS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                .put(pos.immutable(), new Area(tier, radius));
    }

    public static void remove(Level level, BlockPos pos) {
        Map<BlockPos, Area> map = AREAS.get(level.dimension());
        if (map != null) map.remove(pos);
    }

    /**
     * true si la posición está dentro del área de un Air Conditioner en modo frío
     * (FREEZING/COLD): ahí la nieve y el hielo no deben derretirse (vanilla o Serene Seasons).
     */
    public static boolean isProtectedFromMelt(Level level, BlockPos pos) {
        TemperatureTier tier = getOverride(level, pos);
        return tier != null && tier.ordinal() <= TemperatureTier.COLD.ordinal();
    }

    /** Devuelve el tier del Air Conditioner más cercano que cubra la posición, o null si ninguno. */
    @Nullable
    public static TemperatureTier getOverride(Level level, BlockPos pos) {
        Map<BlockPos, Area> map = AREAS.get(level.dimension());
        if (map == null || map.isEmpty()) return null;

        TemperatureTier best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, Area> entry : map.entrySet()) {
            Area area = entry.getValue();
            double distSq = entry.getKey().distSqr(pos);
            double radius = area.radius();
            if (distSq <= radius * radius && distSq < bestDistSq) {
                bestDistSq = distSq;
                best = area.tier();
            }
        }
        return best;
    }

    /** Positions of every Air Conditioner whose block is within {@code range} blocks of {@code pos}. */
    public static List<BlockPos> centersWithin(Level level, BlockPos pos, int range) {
        Map<BlockPos, Area> map = AREAS.get(level.dimension());
        if (map == null || map.isEmpty()) return List.of();
        List<BlockPos> centers = new ArrayList<>();
        double rangeSq = (double) range * range;
        for (BlockPos center : map.keySet()) {
            if (center.distSqr(pos) <= rangeSq) centers.add(center);
        }
        return centers;
    }
}
