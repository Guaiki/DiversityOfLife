package com.evirapo.diversityofcritters.client.locomotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundProbeTest {

    private static final float HALF = 0.05f;

    /** Floor at 64, one block higher from x = 10 on. */
    private static final SurfaceQuery STEP_AT_X10 = (x, z, footY) -> x >= 10.0 ? 65.0 : 64.0;

    private final GroundProbe.Reading reading = new GroundProbe.Reading();

    private double probe(SurfaceQuery surface, double x, double z, float yaw,
                         float minX, float maxX, float minZ, float maxZ) {
        GroundProbe.sample(surface, x, 64.0, z, yaw, minX, maxX, minZ, maxZ, this.reading);
        return this.reading.height;
    }

    private double probe(SurfaceQuery surface, double x, double z) {
        return probe(surface, x, z, 0.0f, -HALF, HALF, -HALF, HALF);
    }

    @Test
    void flatGroundReadsItsHeight() {
        assertEquals(64.0, probe((x, z, y) -> 64.0, 3.3, -7.9), 1.0e-9);
    }

    @Test
    void footWhollyOnAStepReadsTheStep() {
        assertEquals(65.0, probe(STEP_AT_X10, 10.5, 0.4), 1.0e-9);
        assertEquals(64.0, probe(STEP_AT_X10, 9.5, 0.4), 1.0e-9);
    }

    @Test
    void footPartlyOverAStepBlendsBySupportedArea() {
        // 15% of the footprint over the step: half of the 30% support slice.
        assertEquals(64.5, probe(STEP_AT_X10, 10.0 - HALF + 0.015, 0.4), 1.0e-6);
        // 30% or more over it and the foot rests on the step.
        assertEquals(65.0, probe(STEP_AT_X10, 10.0 - HALF + 0.04, 0.4), 1.0e-6);
    }

    @Test
    void slidingOntoAStepNeverJumps() {
        double previous = probe(STEP_AT_X10, 9.9, 0.4);
        assertEquals(64.0, previous, 1.0e-9);
        for (double x = 9.9; x <= 10.1; x += 0.001) {
            final double height = probe(STEP_AT_X10, x, 0.4);
            assertTrue(height >= previous - 1.0e-9, "went down at x=" + x);
            assertTrue(height - previous < 0.05, "jumped at x=" + x + " by " + (height - previous));
            previous = height;
        }
        assertEquals(65.0, previous, 1.0e-9);
    }

    @Test
    void footprintTurnsWithTheBody() {
        // Long along the model's Z, thin along X. The step starts 0.3 blocks east of the foot.
        final SurfaceQuery stepEast = (x, z, footY) -> x >= 10.3 ? 65.0 : 64.0;

        // Yaw 0: model Z runs along world Z, so the foot never reaches the step.
        assertEquals(64.0, probe(stepEast, 10.0, 0.5, 0.0f, -0.02f, 0.02f, -0.4f, 0.4f), 1.0e-9);
        // Yaw 90: model Z runs along world X, and the toe is over the step.
        assertTrue(probe(stepEast, 10.0, 0.5, 90.0f, -0.02f, 0.02f, -0.4f, 0.4f) > 64.0);
    }

    @Test
    void zeroSizeFootprintReadsThePoint() {
        assertEquals(65.0, probe(STEP_AT_X10, 10.2, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f), 1.0e-9);
    }
}
