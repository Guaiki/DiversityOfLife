package com.evirapo.diversityofcritters.client.guidebook.data;

import com.evirapo.diversityofcritters.common.guidebook.ConservationTier;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeciesEntryTest {

    private static final String CIVET = """
            {
              "entity": "diversityofcritters:civet",
              "background": "diversityofcritters:textures/gui/guidebook/species_civet.png",
              "scientific_name": "Hemigalus derbyanus",
              "genus": "Hemigalus",
              "family": "Viverridae",
              "tags": ["civet", "carnivore", "carnivora", "mammal", "nocturnal"],
              "status": "NT",
              "diet": "diversityofcritters:meats",
              "enrichment": [
                {"item": "diversityofcritters:dig_box", "tooltip": "guidebook.diversityofcritters.enrichment.dig_box"},
                {"item": "minecraft:oak_log", "tooltip": "guidebook.diversityofcritters.enrichment.scratch_logs"},
                {"item": "minecraft:dirt", "tooltip": "guidebook.diversityofcritters.enrichment.dig_dirt"},
                {"item": "minecraft:rabbit", "tooltip": "guidebook.diversityofcritters.enrichment.hunting"}
              ]
            }""";

    private static JsonObject civet() {
        return JsonParser.parseString(CIVET).getAsJsonObject();
    }

    @Test
    void parsesCivet() {
        SpeciesEntry entry = SpeciesEntry.fromJson("civet", civet());
        assertEquals("Hemigalus", entry.genus());
        assertTrue(entry.tags().contains("carnivora"));
        assertEquals(ConservationTier.NT, entry.realStatus());
        assertEquals(4, entry.enrichment().size());
        assertEquals("guidebook.diversityofcritters.fact.civet", entry.factKey());
        assertEquals("entity.diversityofcritters.civet", entry.nameKey());
    }

    @Test
    void missingFieldNamesIt() {
        JsonObject json = civet();
        json.remove("genus");
        JsonParseException e = assertThrows(JsonParseException.class, () -> SpeciesEntry.fromJson("civet", json));
        assertTrue(e.getMessage().contains("genus"), e.getMessage());
    }

    @Test
    void unknownStatusFails() {
        JsonObject json = civet();
        json.addProperty("status", "XX");
        assertThrows(JsonParseException.class, () -> SpeciesEntry.fromJson("civet", json));
    }
}
