package com.evirapo.diversityofcritters.common.event;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.guidebook.GuidebookDiscoveries;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Guidebook discoveries survive death: the respawned player is a new entity. */
@Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GuidebookEvents {

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        GuidebookDiscoveries.copy(event.getOriginal(), event.getEntity());
    }
}
