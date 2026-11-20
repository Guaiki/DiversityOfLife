package com.evirapo.diversityofcritters.network;

import com.evirapo.diversityofcritters.client.guidebook.ClientGuidebook;
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.config.PopulationSettings;
import com.evirapo.diversityofcritters.common.guidebook.ConservationTier;
import com.evirapo.diversityofcritters.common.guidebook.GuidebookDiscoveries;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Server to client: opens the guidebook with the player's discoveries and how each species is doing
 * in this world. Sent on every right-click of the book so both are always current.
 */
public record OpenGuidebookMsg(Set<String> discovered, Map<String, ConservationTier> worldTiers) {

    public static OpenGuidebookMsg forPlayer(ServerPlayer player) {
        PopulationSettings settings = DOCServerConfig.population();
        WildPopulation population = WildPopulation.get(player.server);
        Map<String, ConservationTier> tiers = new HashMap<>();
        for (DOCSpecies species : DOCSpecies.values()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(species.entityType());
            if (id == null) continue;
            tiers.put(id.toString(), ConservationTier.forWildPopulation(population.get(species),
                    settings.basePopulation(), settings.endangeredThreshold()));
        }
        return new OpenGuidebookMsg(GuidebookDiscoveries.discovered(player), tiers);
    }

    public static void encode(OpenGuidebookMsg msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.discovered(), FriendlyByteBuf::writeUtf);
        buf.writeMap(msg.worldTiers(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeEnum);
    }

    public static OpenGuidebookMsg decode(FriendlyByteBuf buf) {
        Set<String> discovered = buf.readCollection(HashSet::new, FriendlyByteBuf::readUtf);
        Map<String, ConservationTier> tiers = buf.readMap(FriendlyByteBuf::readUtf, b -> b.readEnum(ConservationTier.class));
        return new OpenGuidebookMsg(discovered, tiers);
    }

    public static void handle(OpenGuidebookMsg msg, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientGuidebook.open(msg.discovered(), msg.worldTiers())));
        context.get().setPacketHandled(true);
    }
}
