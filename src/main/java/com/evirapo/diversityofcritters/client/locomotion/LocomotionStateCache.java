package com.evirapo.diversityofcritters.client.locomotion;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * One {@link LocomotionState} per entity, released when the entity is garbage collected.
 * Render thread only.
 */
@OnlyIn(Dist.CLIENT)
public final class LocomotionStateCache {

    private static final Map<Entity, LocomotionState> STATES = new WeakHashMap<>();

    private LocomotionStateCache() {
    }

    public static LocomotionState of(Entity entity) {
        return STATES.computeIfAbsent(entity, e -> new LocomotionState());
    }
}
