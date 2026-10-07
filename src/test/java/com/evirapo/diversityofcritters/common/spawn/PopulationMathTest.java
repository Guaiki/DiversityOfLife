package com.evirapo.diversityofcritters.common.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopulationMathTest {

    @Test
    void clampKeepsTheValueBetweenFloorAndCap() {
        assertEquals(5, PopulationMath.clamp(-3, 5, 60));
        assertEquals(60, PopulationMath.clamp(99, 5, 60));
        assertEquals(23, PopulationMath.clamp(23, 5, 60));
    }

    @Test
    void capWinsWhenTheFloorIsAboveIt() {
        assertEquals(10, PopulationMath.clamp(0, 30, 10));
        assertEquals(10, PopulationMath.clamp(50, 30, 10));
    }

    @Test
    void multiplierIsPopulationOverBase() {
        assertEquals(0.25D, PopulationMath.multiplier(5, 20, 2.0D), 1.0e-9);
        assertEquals(1.0D, PopulationMath.multiplier(20, 20, 2.0D), 1.0e-9);
        assertEquals(2.0D, PopulationMath.multiplier(40, 20, 2.0D), 1.0e-9);
    }

    @Test
    void multiplierIsCappedByMaxBonusAndNeverNegative() {
        assertEquals(3.0D, PopulationMath.multiplier(100, 20, 2.0D), 1.0e-9);
        assertEquals(0.0D, PopulationMath.multiplier(0, 20, 2.0D), 1.0e-9);
        assertEquals(0.0D, PopulationMath.multiplier(-4, 20, 2.0D), 1.0e-9);
    }

    @Test
    void finalChanceIsClampedToAProbability() {
        assertEquals(0.1D, PopulationMath.finalChance(0.4D, 0.25D), 1.0e-9);
        assertEquals(1.0D, PopulationMath.finalChance(0.65D, 3.0D), 1.0e-9);
        assertEquals(0.0D, PopulationMath.finalChance(0.65D, 0.0D), 1.0e-9);
    }

    @Test
    void version1CountersBecomeBasePlusReleased() {
        assertEquals(32, PopulationMath.migrateV1(12, 20, 5, 60));
        assertEquals(20, PopulationMath.migrateV1(0, 20, 5, 60));
        assertEquals(60, PopulationMath.migrateV1(50, 20, 5, 60));
    }

    @Test
    void historyKeepsTheLastSevenSamplesOldestFirst() {
        int[] history = new int[0];
        for (int day = 1; day <= 9; day++) {
            history = PopulationMath.pushSample(history, day);
        }
        assertArrayEquals(new int[] {3, 4, 5, 6, 7, 8, 9}, history);
    }

    @Test
    void trendComparesAgainstTheOldestSample() {
        assertEquals(0, PopulationMath.trend(20, new int[0]));
        assertEquals(3, PopulationMath.trend(23, new int[] {20, 21}));
        assertEquals(-6, PopulationMath.trend(14, new int[] {20, 19, 18, 17, 16, 15, 14}));
    }

    @Test
    void crossingOnlyFiresWhenTheThresholdIsCrossed() {
        assertEquals(PopulationMath.Crossing.BECAME_ENDANGERED, PopulationMath.crossing(10, 9, 10));
        assertEquals(PopulationMath.Crossing.RECOVERED, PopulationMath.crossing(9, 10, 10));
        assertEquals(PopulationMath.Crossing.NONE, PopulationMath.crossing(9, 8, 10));
        assertEquals(PopulationMath.Crossing.NONE, PopulationMath.crossing(20, 19, 10));
    }

    @Test
    void mateRollScalesWithTheRateAndZeroMeansNever() {
        assertEquals(400, PopulationMath.mateRollBound(400, 1.0D));
        assertEquals(200, PopulationMath.mateRollBound(400, 2.0D));
        assertEquals(800, PopulationMath.mateRollBound(400, 0.5D));
        assertEquals(0, PopulationMath.mateRollBound(400, 0.0D));
    }

    @Test
    void cooldownShrinksWithAFasterRate() {
        assertEquals(96000, PopulationMath.scaledCooldown(96000, 1.0D));
        assertEquals(48000, PopulationMath.scaledCooldown(96000, 2.0D));
        assertEquals(96000, PopulationMath.scaledCooldown(96000, 0.0D));
    }

    @Test
    void eventsCarryTheirDirection() {
        assertEquals(-1, PopulationEvent.TAME.delta(1));
        assertEquals(3, PopulationEvent.RELEASE.delta(3));
        assertEquals(0, PopulationEvent.PLAYER_KILL.delta(0));
    }
}
