package com.evirapo.diversityofcritters.common.config;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Entry point of the configuration system.
 *
 * Called from the mod constructor, before anything else starts up.
 */
public final class DOCConfig {

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DOCServerConfig.SPEC);

        modEventBus.addListener(DOCConfig::onLoad);
        modEventBus.addListener(DOCConfig::onReload);
        modEventBus.addListener(DOCConfig::onUnload);
    }

    private static void onLoad(final ModConfigEvent.Loading event) {
        if (isOurs(event.getConfig())) DOCServerConfig.bake();
    }

    private static void onReload(final ModConfigEvent.Reloading event) {
        if (isOurs(event.getConfig())) DOCServerConfig.bake();
    }

    private static void onUnload(final ModConfigEvent.Unloading event) {
        if (isOurs(event.getConfig())) DOCServerConfig.unbake();
    }

    private static boolean isOurs(ModConfig config) {
        return config.getSpec() == DOCServerConfig.SPEC;
    }

    private DOCConfig() {}
}
