package com.evirapo.diversityofcritters.common.config;

/**
 * Already-resolved values of {@code [ecosystem]}: the November mechanics (thirst from food, comfort
 * seeking, vanilla livestock and corpses).
 *
 * @param smallThirstFromFood          % of max thirst a small animal recovers per meal
 * @param largeThirstFromFood          % of max thirst a large animal recovers per meal
 * @param comfortSearchRadius          how far, in blocks, an uncomfortable animal looks for a better spot
 * @param comfortScanCooldownTicks     ticks between two searches for a better spot
 * @param livestockEnabled             vanilla livestock self-breeds and is capped per area
 * @param livestockCap                 most animals of one livestock type in one area
 * @param livestockCapRadius           radius of that area, in blocks
 * @param livestockBreedIntervalSeconds how often an adult may try to breed on its own
 * @param corpsePortions               meals a fresh corpse holds
 * @param corpseRotSeconds             how long an untouched corpse lasts
 */
public record EcosystemSettings(int smallThirstFromFood, int largeThirstFromFood,
                                int comfortSearchRadius, int comfortScanCooldownTicks,
                                boolean livestockEnabled, int livestockCap, int livestockCapRadius,
                                int livestockBreedIntervalSeconds,
                                int corpsePortions, int corpseRotSeconds) {

    public static final EcosystemSettings DEFAULTS =
            new EcosystemSettings(50, 15, 24, 100, true, 6, 32, 600, 3, 600);
}
