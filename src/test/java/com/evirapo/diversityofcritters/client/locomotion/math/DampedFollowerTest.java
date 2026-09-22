package com.evirapo.diversityofcritters.client.locomotion.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DampedFollowerTest {

    @Test
    void zeroDeltaChangesNothing() {
        final DampedFollower follower = new DampedFollower(0.2f, 0.4f);
        follower.snap(1.5f);
        follower.update(9.0f, 0.0f);
        assertEquals(1.5f, follower.value());
    }

    @Test
    void lightDampingOvershoots() {
        final DampedFollower follower = new DampedFollower(0.3f, 0.3f);
        float peak = 0.0f;
        for (int i = 0; i < 120; i++) {
            peak = Math.max(peak, follower.update(1.0f, 1.0f / 60.0f));
        }
        assertTrue(peak > 1.05f, "expected overshoot, peak was " + peak);
    }

    @Test
    void settlesOnTarget() {
        final DampedFollower follower = new DampedFollower(0.1f, 0.5f);
        for (int i = 0; i < 240; i++) {
            follower.update(-3.0f, 1.0f / 60.0f);
        }
        assertEquals(-3.0f, follower.value(), 1.0e-3f);
    }

    @Test
    void resultDoesNotDependOnFrameRate() {
        final DampedFollower coarse = new DampedFollower(0.2f, 0.45f);
        final DampedFollower fine = new DampedFollower(0.2f, 0.45f);
        for (int i = 0; i < 5; i++) {
            coarse.update(2.0f, 0.06f);
        }
        for (int i = 0; i < 300; i++) {
            fine.update(2.0f, 0.001f);
        }
        assertEquals(coarse.value(), fine.value(), 1.0e-3f);
    }

    @Test
    void reportedVelocityMatchesMotion() {
        final DampedFollower follower = new DampedFollower(0.25f, 0.35f);
        follower.update(1.0f, 0.05f);
        final float before = follower.value();
        final float velocity = follower.velocity();
        final float epsilon = 1.0e-4f;
        final float after = follower.update(1.0f, epsilon);
        assertEquals(velocity, (after - before) / epsilon, Math.abs(velocity) * 0.01f + 1.0e-3f);
    }

    @Test
    void dampingIsClampedToStayUnderdamped() {
        final DampedFollower follower = new DampedFollower(0.2f, 1.5f);
        for (int i = 0; i < 60; i++) {
            assertTrue(Float.isFinite(follower.update(1.0f, 1.0f / 60.0f)));
        }
        follower.setDamping(0.0f);
        assertTrue(Float.isFinite(follower.update(0.0f, 1.0f / 60.0f)));
    }
}
