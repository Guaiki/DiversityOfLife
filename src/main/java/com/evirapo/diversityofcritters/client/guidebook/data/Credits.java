package com.evirapo.diversityofcritters.client.guidebook.data;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Names on the credits page and the Discord invite, from {@code guidebook/credits.json}. */
public record Credits(List<String> names, String discord) {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FILE = new ResourceLocation(DiversityOfCritters.MODID, "guidebook/credits.json");
    private static final Credits EMPTY = new Credits(List.of(), "");

    public static Credits get() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FILE);
        if (resource.isEmpty()) return EMPTY;
        try (Reader reader = resource.get().openAsReader()) {
            return fromJson(GsonHelper.parse(reader));
        } catch (Exception e) {
            LOGGER.warn("Could not read {}: {}", FILE, e.getMessage());
            return EMPTY;
        }
    }

    public static Credits fromJson(JsonObject json) {
        List<String> names = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "names").forEach(e -> names.add(e.getAsString()));
        return new Credits(List.copyOf(names), GsonHelper.getAsString(json, "discord", ""));
    }
}
