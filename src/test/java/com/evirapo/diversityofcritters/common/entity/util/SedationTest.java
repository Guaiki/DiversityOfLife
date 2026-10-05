package com.evirapo.diversityofcritters.common.entity.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SedationTest {

    private static void tick(Sedation s, int ticks) {
        for (int i = 0; i < ticks; i++) s.tick();
    }

    @Test
    void freshIsIdle() {
        Sedation s = new Sedation();
        assertFalse(s.isDrowsy());
        assertFalse(s.isSedated());
        assertEquals(0, s.cooldownTicks());
    }

    @Test
    void doseMakesDrowsyThenSedatedThenIdle() {
        Sedation s = new Sedation();
        assertEquals(Sedation.DoseResult.ACCEPTED, s.dose());
        assertTrue(s.isDrowsy());

        tick(s, Sedation.DROWSY_TICKS - 1);
        assertTrue(s.isDrowsy());
        assertFalse(s.isSedated());

        tick(s, 1);
        assertFalse(s.isDrowsy());
        assertTrue(s.isSedated());

        tick(s, Sedation.SEDATED_TICKS - 1);
        assertTrue(s.isSedated());

        tick(s, 1);
        assertFalse(s.isSedated());
        assertFalse(s.isDrowsy());
    }

    @Test
    void cooldownRejectsUntilItRunsOut() {
        Sedation s = new Sedation();
        s.dose();
        assertEquals(Sedation.COOLDOWN_TICKS, s.cooldownTicks());

        tick(s, Sedation.COOLDOWN_TICKS - 1);
        assertEquals(Sedation.DoseResult.ON_COOLDOWN, s.dose());
        assertEquals(1, s.cooldownTicks());

        tick(s, 1);
        assertEquals(Sedation.DoseResult.ACCEPTED, s.dose());
    }

    @Test
    void cooldownOutlastsTheWholeEffect() {
        assertTrue(Sedation.COOLDOWN_TICKS > Sedation.DROWSY_TICKS + Sedation.SEDATED_TICKS);
    }

    @Test
    void rejectedDoseLeavesStateAlone() {
        Sedation s = new Sedation();
        s.dose();
        tick(s, 10);
        s.dose();
        assertEquals(Sedation.DROWSY_TICKS - 10, s.drowsyTicks());
    }

    @Test
    void restoreResumesWhereItWasSaved() {
        Sedation s = new Sedation();
        s.restore(0, 5, 100);
        assertTrue(s.isSedated());
        tick(s, 5);
        assertFalse(s.isSedated());
        assertEquals(95, s.cooldownTicks());
    }

    @Test
    void restoreClampsNegatives() {
        Sedation s = new Sedation();
        s.restore(-3, -1, -50);
        assertFalse(s.isDrowsy());
        assertFalse(s.isSedated());
        assertEquals(0, s.cooldownTicks());
    }
}
