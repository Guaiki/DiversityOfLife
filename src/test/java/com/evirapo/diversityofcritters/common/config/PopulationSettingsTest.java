package com.evirapo.diversityofcritters.common.config;

import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopulationSettingsTest {

    private static PopulationSettings withWeight(PopulationEvent event, int weight) {
        Map<PopulationEvent, Integer> weights = new EnumMap<>(PopulationEvent.class);
        for (PopulationEvent e : PopulationEvent.values()) weights.put(e, 1);
        weights.put(event, weight);
        return new PopulationSettings(20, 5, 60, 10, 1.0D, weights);
    }

    @Test
    void deltaAppliesTheEventsWeightAndSign() {
        assertEquals(-3, withWeight(PopulationEvent.PLAYER_KILL, 3).delta(PopulationEvent.PLAYER_KILL));
        assertEquals(1, withWeight(PopulationEvent.PLAYER_KILL, 3).delta(PopulationEvent.RELEASE));
    }

    @Test
    void weightZeroMeansTheEventDoesNotCount() {
        assertEquals(0, withWeight(PopulationEvent.TAME, 0).delta(PopulationEvent.TAME));
    }

    @Test
    void clampUsesTheConfiguredFloorAndCap() {
        PopulationSettings settings = withWeight(PopulationEvent.TAME, 1);
        assertEquals(5, settings.clamp(0));
        assertEquals(60, settings.clamp(70));
    }
}
