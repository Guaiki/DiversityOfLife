package com.evirapo.diversityofcritters.client.locomotion;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

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

    /** The entity's state if a rig has ever run on it, without creating one. */
    public static @Nullable LocomotionState peek(Entity entity) {
        return STATES.get(entity);
    }

    /** Every live entity a rig has run on. For debug tools. */
    public static void forEach(BiConsumer<Entity, LocomotionState> action) {
        STATES.forEach(action);
    }
}
