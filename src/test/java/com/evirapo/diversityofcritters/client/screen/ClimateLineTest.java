package com.evirapo.diversityofcritters.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClimateLineTest {

    @Test
    void eachZoneHasItsNameAndColour() {
        assertEquals(new ClimateLine("Temp: Warm (Comfort)", 0x55FF55), ClimateLine.of("Temp", "Warm", 0, true, 0xFFFFFF));
        assertEquals(new ClimateLine("Temp: Cool (Stress)", 0xFFFF55), ClimateLine.of("Temp", "Cool", 1, true, 0xFFFFFF));
        assertEquals(new ClimateLine("Humidity: Arid (Danger)", 0xFF5555), ClimateLine.of("Humidity", "Arid", 3, true, 0xFFFFFF));
    }

    @Test
    void disabledClimateReadsOffInTheNormalTextColour() {
        assertEquals(new ClimateLine("Temp: Off", 0xFFFFFF), ClimateLine.of("Temp", "Warm", 0, false, 0xFFFFFF));
    }
}
