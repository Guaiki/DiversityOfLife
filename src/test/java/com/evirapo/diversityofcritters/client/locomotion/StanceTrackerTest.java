package com.evirapo.diversityofcritters.client.locomotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StanceTrackerTest {

    private static final float DT = 1.0f / 60.0f;

    /** A walk cycle: the sole lifts 4 units during the first half of each second, rests for the second half. */
    private static float walkHeight(float t) {
        return (float) Math.max(0.0, Math.sin(2.0 * Math.PI * t)) * 4.0f;
    }

    @Test
    void zeroDeltaChangesNothing() {
        final LocomotionState.LegState leg = new LocomotionState.LegState();
        StanceTracker.update(leg, 0.0f, DT);
        leg.plantWeight = 0.42f;
        assertEquals(0.42f, StanceTracker.update(leg, 5.0f, 0.0f));
    }

    @Test
    void aFootSeenForTheFirstTimeIsPlanted() {
        final LocomotionState.LegState leg = new LocomotionState.LegState();
        assertEquals(1.0f, StanceTracker.update(leg, -3.0f, DT));
    }

    @Test
    void aStillFootStaysPlanted() {
        final LocomotionState.LegState leg = new LocomotionState.LegState();
        for (int i = 0; i < 300; i++) {
            StanceTracker.update(leg, -11.0f, DT);
        }
        assertTrue(leg.plantWeight > 0.95f, "weight " + leg.plantWeight);
    }

    @Test
    void walkingFootIsPlantedInStanceAndFreeInSwing() {
        final LocomotionState.LegState leg = new LocomotionState.LegState();
        float midStance = -1.0f;
        float lateSwing = -1.0f;
        for (int i = 0; i < 60 * 6; i++) {
            final float t = i * DT;
            StanceTracker.update(leg, walkHeight(t), DT);
            final float phase = t - (float) Math.floor(t);
            if (t > 4.0f && Math.abs(phase - 0.85f) < DT / 2) {
                midStance = leg.plantWeight;
            }
            if (t > 4.0f && Math.abs(phase - 0.35f) < DT / 2) {
                lateSwing = leg.plantWeight;
            }
        }
        assertTrue(midStance > 0.8f, "mid stance " + midStance);
        assertTrue(lateSwing >= 0.0f && lateSwing < 0.2f, "late swing " + lateSwing);
    }

    @Test
    void overrideWinsOverTheHeuristic() {
        final LocomotionState.LegState leg = new LocomotionState.LegState();
        leg.contactOverride = 0.0f;
        for (int i = 0; i < 120; i++) {
            StanceTracker.update(leg, -11.0f, DT);
        }
        assertTrue(leg.plantWeight < 0.01f, "weight " + leg.plantWeight);
    }
}
