package com.evirapo.diversityofcritters.client.guidebook.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditsTest {

    private static Credits parse(String json) {
        return Credits.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void parsesRolesInOrder() {
        Credits credits = parse("""
                {
                  "team": [
                    {"role": "owner", "names": ["A"]},
                    {"role": "developers", "names": ["B", "C"]}
                  ],
                  "discord": "https://discord.gg/x"
                }""");
        assertEquals(List.of(new Credits.Group("owner", List.of("A")), new Credits.Group("developers", List.of("B", "C"))), credits.team());
        assertEquals("https://discord.gg/x", credits.discord());
    }

    @Test
    void missingDiscordIsBlank() {
        assertTrue(parse("{\"team\": []}").discord().isBlank());
    }

    @Test
    void bundledCreditsHaveTheTeamAndTheInvite() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/assets/diversityofcritters/guidebook/credits.json")), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            Credits credits = Credits.fromJson(json);
            assertEquals(List.of(
                    new Credits.Group("owner", List.of("Carlos the Guaiki")),
                    new Credits.Group("main_developer", List.of("Darkblade")),
                    new Credits.Group("developers", List.of("Desmo", "PaleoGuy", "Void"))), credits.team());
            assertEquals("https://discord.gg/Pwad2ZVjQM", credits.discord());
        }
    }
}
