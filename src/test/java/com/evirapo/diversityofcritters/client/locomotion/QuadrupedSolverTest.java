package com.evirapo.diversityofcritters.client.locomotion;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body's decisions on a civet-sized stance: hips at x = +-2.25, front at z = -6.5, rear at
 * z = +6.5, legs 11 units long. Each scene is solved once from a fresh state, which takes every
 * target whole, so the springs hold exactly what the solver decided.
 */
class QuadrupedSolverTest {

    private static final float DT = 1.0f / 60.0f;
    // Order: front-left, front-right, rear-left, rear-right.
    private static final float[] HIP_X = {-2.25f, 2.25f, -2.25f, 2.25f};
    private static final float[] HIP_Z = {-6.5f, -6.5f, 6.5f, 6.5f};

    private final QuadrupedSolver solver = new QuadrupedSolver(4,
            (float) Math.toRadians(35.0), (float) Math.toRadians(20.0), 0.15f, 0.5f, 11.0f);
    private final LocomotionState state = new LocomotionState();

    QuadrupedSolverTest() {
        this.state.ensureLegs(4);
        for (int i = 0; i < 4; i++) {
            this.solver.hipX[i] = HIP_X[i];
            this.solver.hipZ[i] = HIP_Z[i];
            this.state.legs()[i].plantWeight = 1.0f;
        }
    }

    /** Soles drawn at {@code soles}, ground at {@code grounds}, solved once. */
    private void scene(float[] soles, float[] grounds, float gate) {
        System.arraycopy(soles, 0, this.solver.soleUp, 0, 4);
        System.arraycopy(grounds, 0, this.solver.groundUp, 0, 4);
        this.solver.solve(this.state, gate, DT);
    }

    private float pitch() {
        return this.state.pelvisPitch.value();
    }

    private float roll() {
        return this.state.pelvisRoll.value();
    }

    private float drop() {
        return this.state.pelvisDrop.value();
    }

    @Test
    void flatGroundNeedsNothing() {
        scene(new float[4], new float[4], 1.0f);
        assertEquals(0.0f, pitch(), 1.0e-6f);
        assertEquals(0.0f, roll(), 1.0e-6f);
        assertEquals(0.0f, drop(), 1.0e-6f);
    }

    @Test
    void frontFeetOnHigherGroundRaiseTheFront() {
        scene(new float[4], new float[]{4.0f, 4.0f, 0.0f, 0.0f}, 1.0f);
        // Negative xRot lifts the front (-Z) in model space.
        final float expected = (float) Math.atan(-4.0 / 13.0);
        assertEquals(expected, pitch(), Math.abs(expected) * 0.02f);
        assertEquals(0.0f, roll(), 1.0e-5f);
        // Half the feet ask for 4 up, half for nothing: the body rises by the middle, 2.
        assertEquals(-2.0f, drop(), 1.0e-4f);
    }

    @Test
    void rightFeetOnHigherGroundRaiseTheRightSide() {
        scene(new float[4], new float[]{0.0f, 2.0f, 0.0f, 2.0f}, 1.0f);
        // Negative zRot lifts +X in model space.
        assertTrue(roll() < -0.1f, "roll " + roll());
        assertEquals(0.0f, pitch(), 1.0e-5f);
    }

    @Test
    void oneFootOnAStepDoesNotMoveTheBodyHeight() {
        scene(new float[4], new float[]{6.0f, 0.0f, 0.0f, 0.0f}, 1.0f);
        assertEquals(0.0f, drop(), 1.0e-4f);
    }

    @Test
    void aFootOverACliffIsNotSupportingTheBody() {
        scene(new float[4], new float[]{0.0f, 0.0f, -20.0f, 0.0f}, 1.0f);
        assertEquals(0.0f, this.state.legs()[2].support, 1.0e-6f);
        assertEquals(0.0f, pitch(), 1.0e-3f);
        assertEquals(0.0f, roll(), 1.0e-3f);
    }

    @Test
    void aFootDrawnMidStrideDoesNotTiltTheBody() {
        // Flat ground; the animation holds one foot 2 units up but it still reads as planted.
        scene(new float[]{2.0f, 0.0f, 0.0f, 0.0f}, new float[4], 1.0f);
        assertEquals(0.0f, pitch(), 1.0e-5f);
        assertEquals(0.0f, roll(), 1.0e-5f);
        assertEquals(0.0f, drop(), 1.0e-5f);
    }

    @Test
    void nothingTouchingMeansNoCorrection() {
        for (LocomotionState.LegState leg : this.state.legs()) {
            leg.plantWeight = 0.0f;
        }
        // Soles hanging well above uneven ground.
        scene(new float[]{5.0f, 5.0f, 5.0f, 5.0f}, new float[]{3.0f, 0.0f, -2.0f, 1.0f}, 1.0f);
        assertEquals(0.0f, pitch(), 1.0e-6f);
        assertEquals(0.0f, roll(), 1.0e-6f);
        assertEquals(0.0f, drop(), 1.0e-6f);
    }

    @Test
    void closedGateMeansNoCorrection() {
        scene(new float[4], new float[]{4.0f, 4.0f, 0.0f, 0.0f}, 0.0f);
        assertEquals(0.0f, pitch(), 1.0e-6f);
        assertEquals(0.0f, drop(), 1.0e-6f);
    }

    @Test
    void inTheAirTheBodyKeepsItsTiltButNotItsHeight() {
        this.state.airborneSeconds = 1.0f;
        scene(new float[4], new float[]{4.0f, 4.0f, 0.0f, 0.0f}, 1.0f);
        assertTrue(pitch() < -0.2f, "pitch " + pitch());
        assertEquals(0.0f, drop(), 1.0e-6f);
    }

    @Test
    void limitsHold() {
        scene(new float[4], new float[]{40.0f, 40.0f, 0.0f, 0.0f}, 1.0f);
        assertTrue(pitch() >= -(float) Math.toRadians(35.0) - 1.0e-6f, "pitch " + pitch());
        assertTrue(Math.abs(drop()) <= 5.5f + 1.0e-5f, "drop " + drop());
    }

    @Test
    void residualComesOutOfTheGroundButOnlyReachesDownWhenPlanted() {
        assertEquals(3.0f, QuadrupedSolver.appliedResidual(3.0f, 0.0f, 1.0f), 1.0e-6f);
        assertEquals(1.5f, QuadrupedSolver.appliedResidual(3.0f, 1.0f, 0.5f), 1.0e-6f);
        assertEquals(-1.5f, QuadrupedSolver.appliedResidual(-3.0f, 0.5f, 1.0f), 1.0e-6f);
        assertEquals(0.0f, QuadrupedSolver.appliedResidual(-3.0f, 0.0f, 1.0f), 1.0e-6f);
    }

    @Test
    void stretchShortensToRiseAndStopsAtTheLimit() {
        assertEquals(-0.1f, this.solver.stretchFor(1.1f), 1.0e-6f);
        assertEquals(0.1f, this.solver.stretchFor(-1.1f), 1.0e-6f);
        assertEquals(-0.15f, this.solver.stretchFor(50.0f), 1.0e-6f);
    }

    @Test
    void hipShiftFollowsThePelvisRotation() {
        final Vector3f out = new Vector3f();
        final Matrix3f scratch = new Matrix3f();

        QuadrupedSolver.hipShift(scratch, 0.0f, 0.0f, 0.0f, 0.3f, 0.0f, 0.0f, -11.0f, -6.5f, out);
        final double c = Math.cos(0.3);
        final double s = Math.sin(0.3);
        assertEquals(0.0f, out.x, 1.0e-5f);
        assertEquals((float) (-11.0 * c + 6.5 * s - (-11.0)), out.y, 1.0e-4f);
        assertEquals((float) (-11.0 * s - 6.5 * c - (-6.5)), out.z, 1.0e-4f);

        QuadrupedSolver.hipShift(scratch, 0.2f, -0.4f, 0.1f, 0.0f, 0.0f, 3.0f, -11.0f, 6.5f, out);
        assertEquals(0.0f, out.length(), 1.0e-5f);
    }
}
