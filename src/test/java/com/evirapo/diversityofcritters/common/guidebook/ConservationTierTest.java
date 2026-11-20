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
}
