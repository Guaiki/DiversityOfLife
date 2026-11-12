package com.evirapo.diversityofcritters.common.event;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.entity.ai.LivestockSelfBreedGoal;
import com.evirapo.diversityofcritters.common.spawn.LivestockRules;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla livestock: self-breeding and the per-area cap on natural spawns. Which biomes they spawn
 * in is the {@code livestock_spawns} biome modifier.
 */
@Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivestockEvents {

    private LivestockEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Animal animal && LivestockRules.isLivestock(animal.getType())) {
            // After the vanilla BreedGoal (2) and tempt (3); it takes no flags anyway.
            animal.goalSelector.addGoal(9, new LivestockSelfBreedGoal(animal));
        }
    }

    /**
     * Natural spawns stop at the cap. During chunk generation the level is a WorldGenRegion that
     * cannot list entities, so those first groups (2-4 animals) are not capped.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!DOCServerConfig.ecosystem().livestockEnabled()) return;
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) return;
        if (!LivestockRules.isLivestock(event.getEntity().getType())) return;
        if (!(event.getLevel() instanceof Level level)) return;
        if (LivestockRules.isAtCap(level, event.getEntity().getType(), event.getEntity().blockPosition())) {
            event.setSpawnCancelled(true);
        }
    }
}
