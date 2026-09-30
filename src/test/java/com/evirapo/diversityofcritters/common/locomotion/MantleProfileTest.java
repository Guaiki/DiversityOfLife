package com.evirapo.diversityofcritters.common.locomotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The manoeuvre scaled to lips lower than a full block. */
class MantleProfileTest {

    private static final MantleProfile FULL = MantleProfile.CIVET;

    @Test
    void fullBlockAndHigherKeepTheProfile() {
        assertSame(FULL, FULL.forLip(1.0));
        assertSame(FULL, FULL.forLip(FULL.maxMantleHeight));
        assertFalse(FULL.isScaled());
    }

    @Test
    void slabRearsHalfAsFarInLessTime() {
        final MantleProfile slab = FULL.forLip(0.5);
        assertTrue(slab.isScaled());
        assertEquals(FULL.reachPitch * 0.5f, slab.reachPitch, 1.0e-5f);
        assertEquals(FULL.risePitch * 0.5f, slab.risePitch, 1.0e-5f);
        assertEquals(FULL.crestPitch * 0.5f, slab.crestPitch, 1.0e-5f);
        assertEquals(FULL.archDegrees * 0.5f, slab.archDegrees, 1.0e-5f);
        assertEquals(FULL.rearArchDegrees * 0.5f, slab.rearArchDegrees, 1.0e-5f);
        // Timed phases by the square root of the height.
        assertEquals(Math.round(FULL.reachTicks * Math.sqrt(0.5)), slab.reachTicks);
        assertEquals(Math.round(FULL.riseTicks * Math.sqrt(0.5)), slab.riseTicks);
        assertEquals(Math.round(FULL.crestTicks * Math.sqrt(0.5)), slab.crestTicks);
        // What does not depend on the height stays.
        assertEquals(FULL.recoverTicks, slab.recoverTicks);
        assertEquals(FULL.recoverDistance, slab.recoverDistance);
        assertEquals(FULL.maxTicks, slab.maxTicks);
        assertEquals(FULL.reach, slab.reach);
        assertEquals(FULL.crestStart, slab.crestStart);
        assertEquals(FULL.minMantleHeight, slab.minMantleHeight);
    }

    @Test
    void scaledProfilesAreKept() {
        assertSame(FULL.forLip(0.5), FULL.forLip(0.5));
        assertSame(FULL.forLip(0.5), FULL.forLip(0.5).forLip(0.5), "a scaled profile scales from the full one");
        assertSame(FULL.forLip(0.875), FULL.forLip(0.5).forLip(0.875));
    }

    @Test
    void everyLipTheProbeAcceptsBuildsAValidProfile() {
        for (double lip = FULL.minMantleHeight; lip <= FULL.maxMantleHeight; lip += 1.0 / 64.0) {
            final MantleProfile p = FULL.forLip(lip);
            assertTrue(p.reachTicks >= 1 && p.riseTicks >= 1 && p.crestTicks >= 1, "lip " + lip);
            assertTrue(p.risePitch <= FULL.risePitch, "lip " + lip);
        }
    }
}
