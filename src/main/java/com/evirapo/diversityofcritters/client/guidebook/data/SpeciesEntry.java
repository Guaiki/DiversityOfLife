package com.evirapo.diversityofcritters.client.guidebook.data;

import com.evirapo.diversityofcritters.common.guidebook.ConservationTier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One animal's guidebook entry, read from {@code assets/<ns>/guidebook/animals/<id>.json}. The name
 * comes from the entity's own translation and the fact from {@code guidebook.<ns>.fact.<id>}.
 */
public record SpeciesEntry(String id, ResourceLocation entity, ResourceLocation background,
                           String scientificName, String genus, String family, List<String> tags,
                           ConservationTier realStatus, ResourceLocation dietTag, List<EnrichmentEntry> enrichment) {

    /** An enrichment icon: the item drawn and what its tooltip says the animal does with it. */
    public record EnrichmentEntry(ResourceLocation item, String tooltipKey) {
    }

    public static SpeciesEntry fromJson(String id, JsonObject json) {
        List<String> tags = new ArrayList<>();
        for (JsonElement tag : GsonHelper.getAsJsonArray(json, "tags")) {
            tags.add(tag.getAsString().toLowerCase(Locale.ROOT));
        }
        List<EnrichmentEntry> enrichment = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "enrichment")) {
            JsonObject o = GsonHelper.convertToJsonObject(e, "enrichment entry");
            enrichment.add(new EnrichmentEntry(location(o, "item"), GsonHelper.getAsString(o, "tooltip")));
        }
        String status = GsonHelper.getAsString(json, "status");
        ConservationTier tier;
        try {
            tier = ConservationTier.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unknown status '" + status + "', expected one of EX EW CR EN VU NT LC DD");
        }
        return new SpeciesEntry(id, location(json, "entity"), location(json, "background"),
                GsonHelper.getAsString(json, "scientific_name"), GsonHelper.getAsString(json, "genus"),
                GsonHelper.getAsString(json, "family"), List.copyOf(tags), tier,
                location(json, "diet"), List.copyOf(enrichment));
    }

    private static ResourceLocation location(JsonObject json, String field) {
        String value = GsonHelper.getAsString(json, field);
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) throw new JsonParseException("Invalid id '" + value + "' in " + field);
        return location;
    }

    /** Entity id as a plain string, the key used by discoveries and conservation status. */
    public String entityId() {
        return this.entity.toString();
    }

    public String nameKey() {
        return "entity." + this.entity.getNamespace() + "." + this.entity.getPath();
    }

    public String factKey() {
        return "guidebook." + this.entity.getNamespace() + ".fact." + this.id;
    }
}
