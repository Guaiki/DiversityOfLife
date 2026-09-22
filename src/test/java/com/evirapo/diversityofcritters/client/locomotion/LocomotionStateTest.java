package com.evirapo.diversityofcritters.client.locomotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocomotionStateTest {

    private static final long MS = 1_000_000L;

    @Test
    void onlyTheFirstPassOfAFrameAdvancesTime() {
        final LocomotionState state = new LocomotionState();
        assertTrue(state.beginFrame(1, 0));
        assertEquals(0.0f, state.frameSeconds, "no previous frame to measure against");

        assertTrue(state.beginFrame(2, 16 * MS));
        assertEquals(0.016f, state.frameSeconds, 1.0e-6f);

        assertFalse(state.beginFrame(2, 17 * MS));
        assertEquals(0.0f, state.frameSeconds);
    }

    @Test
    void longHitchesAreClamped() {
        final LocomotionState state = new LocomotionState();
        state.beginFrame(1, 0);
        state.beginFrame(2, 2_000 * MS);
        assertEquals(LocomotionState.MAX_FRAME_SECONDS, state.frameSeconds);
    }

    @Test
    void legsAreReusedUntilTheCountChanges() {
        final LocomotionState state = new LocomotionState();
        final LocomotionState.LegState[] four = state.ensureLegs(4);
        state.initialised = true;
        assertSame(four, state.ensureLegs(4));
        assertTrue(state.initialised);

        assertEquals(2, state.ensureLegs(2).length);
        assertFalse(state.initialised, "a different leg layout starts over");
    }

    @Test
    void fallingNeedsSustainedAirTime() {
        final LocomotionState state = new LocomotionState();
        state.airborneSeconds = LocomotionState.FALLING_AFTER_SECONDS * 0.5f;
        assertFalse(state.falling());
        state.airborneSeconds = LocomotionState.FALLING_AFTER_SECONDS * 2.0f;
        assertTrue(state.falling());
    }
}
