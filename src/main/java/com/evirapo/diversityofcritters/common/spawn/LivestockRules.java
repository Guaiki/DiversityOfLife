package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The per-area cap on vanilla livestock (cows, pigs, sheep and chickens, the
 * {@code diversityofcritters:livestock} entity tag). Natural spawns and self-breeding stop at the cap;
 * players can still breed past it.
 */
public final class LivestockRules {

    private LivestockRules() {
    }

    public static boolean isLivestock(EntityType<?> type) {
        return type.is(DoCTags.EntityTypes.LIVESTOCK);
    }

    /** Living animals of {@code type}, babies included, within the cap radius of {@code center}. */
    public static int countNearby(Level level, EntityType<?> type, BlockPos center) {
        int radius = DOCServerConfig.ecosystem().livestockCapRadius();
        AABB box = new AABB(center).inflate(radius);
        return level.getEntities((Entity) null, box, e -> e.getType() == type && e.isAlive()).size();
    }

    public static boolean isAtCap(Level level, EntityType<?> type, BlockPos center) {
        return countNearby(level, type, center) >= DOCServerConfig.ecosystem().livestockCap();
    }
}
