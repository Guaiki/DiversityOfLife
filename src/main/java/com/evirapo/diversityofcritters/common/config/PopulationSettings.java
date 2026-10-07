package com.evirapo.diversityofcritters.common.config;

import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import com.evirapo.diversityofcritters.common.spawn.PopulationMath;

import java.util.Map;

/**
 * Already-resolved values of {@code [spawn.population]}.
 *
 * @param basePopulation      starting population, and the one that spawns at exactly the base chance
 * @param floor               lowest population; 0 allows true extinction
 * @param cap                 highest population
 * @param endangeredThreshold below this the species is endangered
 * @param wildBreedingRate    multiplier on how often wild animals breed
 * @param weights             how far each event moves the population
 */
public record PopulationSettings(int basePopulation, int floor, int cap, int endangeredThreshold,
                                 double wildBreedingRate, Map<PopulationEvent, Integer> weights) {

    /** The signed change an event makes with the configured weight. */
    public int delta(PopulationEvent event) {
        return event.delta(this.weights.getOrDefault(event, 1));
    }

    public int clamp(int population) {
        return PopulationMath.clamp(population, this.floor, this.cap);
    }
}
