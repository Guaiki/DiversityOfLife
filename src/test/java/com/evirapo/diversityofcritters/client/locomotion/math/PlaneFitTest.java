package com.evirapo.diversityofcritters.client.locomotion.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneFitTest {

    // Hip layout of a small quadruped, centred on the pelvis.
    private static final float[] XS = {-2.0f, 2.0f, -2.0f, 2.0f};
    private static final float[] ZS = {-6.5f, -6.5f, 6.5f, 6.5f};
    private static final float[] FULL = {1.0f, 1.0f, 1.0f, 1.0f};

    private static float[] heights(float slopeX, float slopeZ, float height) {
        final float[] ys = new float[XS.length];
        for (int i = 0; i < ys.length; i++) {
            ys[i] = slopeX * XS[i] + slopeZ * ZS[i] + height;
        }
        return ys;
    }

    @Test
    void recoversAPlaneThroughFourPlantedFeet() {
        final PlaneFit.Plane plane = new PlaneFit.Plane();
        PlaneFit.fit(XS, ZS, heights(0.1f, -0.2f, 3.0f), FULL, 4, plane);

        // With full contact the level bias costs little: most on the narrow axis (roll).
        assertEquals(0.1f, plane.slopeX, 0.1f * 0.06f);
        assertEquals(-0.2f, plane.slopeZ, 0.2f * 0.01f);
        assertEquals(3.0f, plane.height, 1.0e-4f);
    }

    @Test
    void ignoresFeetWithoutWeight() {
        final float[] xs = {-2.0f, 2.0f, -2.0f, 2.0f, 0.0f};
        final float[] zs = {-6.5f, -6.5f, 6.5f, 6.5f, 0.0f};
        final float[] ys = {0.0f, 0.0f, 0.0f, 0.0f, 50.0f};
        final float[] ws = {1.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        final PlaneFit.Plane plane = new PlaneFit.Plane();
        PlaneFit.fit(xs, zs, ys, ws, 5, plane);
        assertEquals(0.0f, plane.slopeX, 1.0e-6f);
        assertEquals(0.0f, plane.slopeZ, 1.0e-6f);
        assertEquals(0.0f, plane.height, 1.0e-6f);
    }

    @Test
    void twoDiagonalContactsTiltOnlyAlongTheirLine() {
        final float[] ys = {-1.0f, 0.0f, 0.0f, 1.0f};
        final float[] ws = {1.0f, 0.0f, 0.0f, 1.0f};

        final PlaneFit.Plane plane = new PlaneFit.Plane();
        PlaneFit.fit(XS, ZS, ys, ws, 4, plane);

        // Along the diagonal the plane nearly passes through both feet...
        final float atFirst = plane.slopeX * XS[0] + plane.slopeZ * ZS[0] + plane.height;
        final float atLast = plane.slopeX * XS[3] + plane.slopeZ * ZS[3] + plane.height;
        assertEquals(-1.0f, atFirst, 0.05f);
        assertEquals(1.0f, atLast, 0.05f);

        // ...and across it there is no tilt at all.
        final float acrossX = 13.0f;
        final float acrossZ = -4.0f;
        assertEquals(0.0f, plane.slopeX * acrossX + plane.slopeZ * acrossZ, 1.0e-4f);
    }

    @Test
    void singleContactIsFlatAtItsHeight() {
        final float[] ys = {0.0f, 4.0f, 0.0f, 0.0f};
        final float[] ws = {0.0f, 1.0f, 0.0f, 0.0f};

        final PlaneFit.Plane plane = new PlaneFit.Plane();
        PlaneFit.fit(XS, ZS, ys, ws, 4, plane);
        assertEquals(0.0f, plane.slopeX, 1.0e-6f);
        assertEquals(0.0f, plane.slopeZ, 1.0e-6f);
        assertEquals(4.0f, plane.height, 1.0e-5f);
    }

    @Test
    void traceWeightsCannotTiltTheBody() {
        final float[] ys = {0.0f, 10.0f, 10.0f, 10.0f};
        final float[] ws = {1.0f, 1.0e-4f, 1.0e-4f, 1.0e-4f};

        final PlaneFit.Plane plane = new PlaneFit.Plane();
        PlaneFit.fit(XS, ZS, ys, ws, 4, plane);
        // Taken at face value these three feet would ask for a slope of about 0.77.
        assertTrue(Math.abs(plane.slopeX) < 0.05f, "slopeX " + plane.slopeX);
        assertTrue(Math.abs(plane.slopeZ) < 0.05f, "slopeZ " + plane.slopeZ);
    }

    @Test
    void nothingPlantedGivesAFlatPlaneAtZero() {
        final PlaneFit.Plane plane = new PlaneFit.Plane();
        plane.height = 99.0f;
        PlaneFit.fit(XS, ZS, heights(1.0f, 1.0f, 5.0f), new float[4], 4, plane);
        assertEquals(0.0f, plane.slopeX);
        assertEquals(0.0f, plane.slopeZ);
        assertEquals(0.0f, plane.height);
    }

    @Test
    void feetOnOneSpotGiveTheirWeightedMean() {
        final float[] xs = {1.0f, 1.0f, 1.0f};
        final float[] zs = {2.0f, 2.0f, 2.0f};
        final float[] ys = {0.0f, 3.0f, 6.0f};
        final float[] ws = {1.0f, 1.0f, 2.0f};

        final PlaneFit.Plane plane = new PlaneFit.Plane();
        PlaneFit.fit(xs, zs, ys, ws, 3, plane);
        assertEquals(0.0f, plane.slopeX);
        assertEquals(0.0f, plane.slopeZ);
        assertEquals(3.75f, plane.height, 1.0e-5f);
    }
}
