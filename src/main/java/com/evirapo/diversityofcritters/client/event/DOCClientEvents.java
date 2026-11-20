package com.evirapo.diversityofcritters.client.event;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.client.models.*;
import com.evirapo.diversityofcritters.client.renderer.CivetRenderer;
import com.evirapo.diversityofcritters.client.renderer.LionRenderer;
import com.evirapo.diversityofcritters.client.renderer.SifakaRenderer;
import com.evirapo.diversityofcritters.client.renderer.SquirrelRenderer;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.client.renderer.BiomeColors;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DOCClientEvents {

    @SubscribeEvent
    public static void registerReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(com.evirapo.diversityofcritters.client.guidebook.data.GuidebookData.INSTANCE);
    }

    @SubscribeEvent
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModelLayers.LION_LAYER, LionModel::createBodyLayer);
        event.registerLayerDefinition(ModelLayers.LION_CUB_LAYER, LionCubModel::createBodyLayer);

        event.registerLayerDefinition(ModelLayers.CIVET_LAYER, CivetModel::createBodyLayer);
        event.registerLayerDefinition(ModelLayers.CIVET_BABY_LAYER, CivetBabyModel::createBodyLayer);

        event.registerLayerDefinition(ModelLayers.SQUIRREL_LAYER, SquirrelModel::createBodyLayer);
        event.registerLayerDefinition(ModelLayers.SQUIRREL_BABY_LAYER, SquirrelBabyModel::createBodyLayer);

        event.registerLayerDefinition(ModelLayers.SIFAKA_LAYER, SifakaModel::createBodyLayer);
        event.registerLayerDefinition(ModelLayers.SIFAKA_BABY_LAYER, SifakaBabyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DOCEntities.LION.get(), LionRenderer::new);
        event.registerEntityRenderer(DOCEntities.CIVET.get(), CivetRenderer::new);
        event.registerEntityRenderer(DOCEntities.SQUIRREL.get(), SquirrelRenderer::new);
        event.registerEntityRenderer(DOCEntities.SIFAKA.get(), SifakaRenderer::new);
        event.registerEntityRenderer(DOCEntities.TRANQUILIZER_DART.get(), ThrownItemRenderer::new);
    }

    /** The vanilla default water colour, for the item and wherever no biome is known. */
    private static final int DEFAULT_WATER_COLOR = 0x3F76E4;

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> level != null && pos != null
                        ? BiomeColors.getAverageWaterColor(level, pos) : DEFAULT_WATER_COLOR,
                DOCBlocks.WATERHOLE.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> DEFAULT_WATER_COLOR, DOCItems.WATERHOLE.get());
    }
}
