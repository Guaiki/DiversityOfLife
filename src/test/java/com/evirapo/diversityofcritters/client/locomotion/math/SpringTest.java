package com.evirapo.diversityofcritters.client.locomotion.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTest {

    private static float run(Spring spring, float target, float totalSeconds, int steps) {
        final float dt = totalSeconds / steps;
        for (int i = 0; i < steps; i++) {
            spring.update(target, dt);
        }
        return spring.value();
    }

    @Test
    void zeroDeltaChangesNothing() {
        final Spring spring = new Spring(0.2f);
        spring.snap(3.0f);
        spring.update(10.0f, 0.0f);
        spring.update(10.0f, -1.0f);
        assertEquals(3.0f, spring.value());
        assertEquals(0.0f, spring.velocity());
    }

    @Test
    void coversHalfTheDistanceInOneHalfLife() {
        final Spring spring = new Spring(0.25f);
        assertEquals(0.5f, run(spring, 1.0f, 0.25f, 1), 1.0e-3f);
    }

    @Test
    void resultDoesNotDependOnFrameRate() {
        final Spring coarse = new Spring(0.15f);
        final Spring fine = new Spring(0.15f);
        run(coarse, 5.0f, 0.4f, 4);
        run(fine, 5.0f, 0.4f, 400);
        assertEquals(coarse.value(), fine.value(), 1.0e-3f);
        assertEquals(coarse.velocity(), fine.velocity(), 1.0e-2f);
    }

    @Test
    void neverOvershootsFromRest() {
        final Spring spring = new Spring(0.1f);
        float previous = 0.0f;
        for (int i = 0; i < 200; i++) {
            final float value = spring.update(1.0f, 1.0f / 60.0f);
            assertTrue(value <= 1.0f + 1.0e-6f, "overshot at step " + i);
            assertTrue(value >= previous - 1.0e-6f, "moved backwards at step " + i);
            previous = value;
        }
    }

    @Test
    void settlesOnTarget() {
        final Spring spring = new Spring(0.1f);
        spring.snap(-4.0f);
        assertEquals(2.0f, run(spring, 2.0f, 2.0f, 120), 1.0e-4f);
    }

    @Test
    void zeroHalfLifeFollowsInstantly() {
        final Spring spring = new Spring(0.0f);
        assertEquals(7.0f, spring.update(7.0f, 0.016f));
        assertEquals(0.0f, spring.velocity());
    }

    @Test
    void blendFactorIsBoundedAndRateIndependent() {
        assertEquals(0.0f, Spring.blendFactor(10.0f, 0.0f));
        assertTrue(Spring.blendFactor(10.0f, 100.0f) > 0.999f);

        // Two half steps compose to one full step.
        final float half = Spring.blendFactor(8.0f, 0.05f);
        final float full = Spring.blendFactor(8.0f, 0.1f);
        assertEquals(full, 1.0f - (1.0f - half) * (1.0f - half), 1.0e-6f);
    }
}
