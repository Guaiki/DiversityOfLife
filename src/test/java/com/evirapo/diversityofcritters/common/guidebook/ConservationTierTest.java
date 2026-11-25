package com.evirapo.diversityofcritters.common.guidebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConservationTierTest {

    @Test
    void dotsAreFourteenApart() {
        assertEquals(164, ConservationTier.EX.dotX());
        assertEquals(178, ConservationTier.EW.dotX());
        assertEquals(262, ConservationTier.DD.dotX());
    }

    @Test
    void wildPopulationMapsToTiersWithDefaultConfig() {
        int base = 20, threshold = 10;
        assertEquals(ConservationTier.EW, ConservationTier.forWildPopulation(0, base, threshold));
        assertEquals(ConservationTier.CR, ConservationTier.forWildPopulation(4, base, threshold));
        assertEquals(ConservationTier.CR, ConservationTier.forWildPopulation(5, base, threshold));
        assertEquals(ConservationTier.EN, ConservationTier.forWildPopulation(6, base, threshold));
        assertEquals(ConservationTier.EN, ConservationTier.forWildPopulation(9, base, threshold));
        assertEquals(ConservationTier.VU, ConservationTier.forWildPopulation(10, base, threshold));
        assertEquals(ConservationTier.VU, ConservationTier.forWildPopulation(14, base, threshold));
        assertEquals(ConservationTier.NT, ConservationTier.forWildPopulation(15, base, threshold));
        assertEquals(ConservationTier.NT, ConservationTier.forWildPopulation(19, base, threshold));
        assertEquals(ConservationTier.LC, ConservationTier.forWildPopulation(20, base, threshold));
        assertEquals(ConservationTier.LC, ConservationTier.forWildPopulation(60, base, threshold));
    }

    @Test
    void noThresholdMeansNoEndangeredTiers() {
        assertEquals(ConservationTier.VU, ConservationTier.forWildPopulation(1, 20, 0));
    }
}
