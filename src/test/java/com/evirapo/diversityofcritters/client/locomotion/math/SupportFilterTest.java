package com.evirapo.diversityofcritters.client.locomotion.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportFilterTest {

    @Test
    void referenceIsThePlantedFootAskingForLeast() {
        final float[] lifts = {5.0f, -0.5f, 0.1f, 3.0f};
        final float[] plants = {1.0f, 1.0f, 0.0f, 1.0f};
        // Foot 2 asks for less but is in swing.
        assertEquals(1, SupportFilter.reference(lifts, plants, 4));
    }

    @Test
    void noReferenceWithoutAPlantedFoot() {
        assertEquals(-1, SupportFilter.reference(new float[]{1.0f, 2.0f}, new float[]{0.0f, SupportFilter.MIN_PLANT}, 2));
    }

    @Test
    void fadeIsFullInsideTheLimitAndLinearAcrossTheBand() {
        assertEquals(1.0f, SupportFilter.fade(-50.0f, 10.0f));
        assertEquals(1.0f, SupportFilter.fade(10.0f, 10.0f));
        assertEquals(0.5f, SupportFilter.fade(12.5f, 10.0f), 1.0e-6f);
        assertEquals(0.0f, SupportFilter.fade(15.0f, 10.0f), 1.0e-6f);
        assertEquals(0.0f, SupportFilter.fade(40.0f, 10.0f));
    }

    @Test
    void zeroLimitRejectsAnyExcess() {
        assertEquals(1.0f, SupportFilter.fade(0.0f, 0.0f));
        assertEquals(0.0f, SupportFilter.fade(0.01f, 0.0f));
    }

    @Test
    void supportTakesTheStricterTest() {
        assertEquals(0.5f, SupportFilter.support(0.0f, 10.0f, 2.5f, 2.0f), 1.0e-6f);
        assertEquals(0.0f, SupportFilter.support(20.0f, 10.0f, 0.0f, 2.0f), 1.0e-6f);
        assertEquals(1.0f, SupportFilter.support(-30.0f, 10.0f, -5.0f, 2.0f));
    }

    @Test
    void envelopeAddsDeratedTiltToStretch() {
        final float envelope = SupportFilter.reachEnvelope(4.0f, -13.0f, 0.5f, 0.25f, 0.8f, 3.0f);
        assertEquals(0.8f * (13.0f * 0.5f + 4.0f * 0.25f) + 3.0f, envelope, 1.0e-5f);
    }
}
