package com.evirapo.diversityofcritters.common.locomotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase and pose from the body's height: the part client and server have to agree on. */
class MantleProgressTest {

    private static final MantleProfile PROFILE = MantleProfile.CIVET;
    private static final double HALF_WIDTH = 0.375;
    /** Centre standing against the face. */
    private static final double AT_FACE = -HALF_WIDTH;

    private static MantleProgress at(double y, double lipY, double ticks, double forward) {
        return MantleProgress.of(y, 64.0, lipY, ticks, forward, HALF_WIDTH, PROFILE, new MantleProgress());
    }

    private static MantleProgress at(double y, double ticks) {
        return at(y, 65.0, ticks, AT_FACE);
    }

    @Test
    void reachCountsTicksWhileStanding() {
        final MantleProgress p = at(64.0, PROFILE.reachTicks * 0.5);
        assertEquals(MantlePhase.REACH, p.phase);
        assertEquals(0.5f, p.t, 1.0e-6f);
        assertEquals(0.0f, p.h, 1.0e-6f);
    }

    @Test
    void reachEndsIntoRiseAtZero() {
        final MantleProgress p = at(64.0, PROFILE.reachTicks);
        assertEquals(MantlePhase.RISE, p.phase);
        assertEquals(0.0f, p.t, 1.0e-6f);
    }

    @Test
    void risingEarlyLeavesReach() {
        // The server started lifting before the client's tick count ran out: height wins.
        final MantleProgress p = at(64.1, 1.0);
        assertEquals(MantlePhase.RISE, p.phase);
    }

    @Test
    void riseThenCrestSplitAtCrestStart() {
        final MantleProgress rise = at(64.0 + PROFILE.crestStart * 0.5, 20.0);
        assertEquals(MantlePhase.RISE, rise.phase);
        assertEquals(0.5f, rise.t, 1.0e-5f);

        final MantleProgress crest = at(64.0 + PROFILE.crestStart + 1.0e-9, 20.0);
        assertEquals(MantlePhase.CREST, crest.phase);
        assertEquals(0.0f, crest.t, 1.0e-5f);

        final MantleProgress late = at(64.0 + (1.0 + PROFILE.crestStart) * 0.5, 20.0);
        assertEquals(0.5f, late.t, 1.0e-5f);
    }

    @Test
    void recoverMeasuresTheWalkOntoTheTop() {
        final MantleProgress start = at(65.0, 65.0, 30.0, AT_FACE);
        assertEquals(MantlePhase.RECOVER, start.phase);
        assertEquals(0.0f, start.t, 1.0e-6f);

        final MantleProgress done = at(65.0, 65.0, 30.0, AT_FACE + PROFILE.recoverDistance);
        assertEquals(1.0f, done.t, 1.0e-6f);
    }

    @Test
    void heightOutOfRangeIsClamped() {
        final MantleProgress below = at(63.5, 20.0);
        assertEquals(0.0f, below.h, 1.0e-6f);
        assertEquals(MantlePhase.RISE, below.phase);

        final MantleProgress above = at(65.4, 20.0);
        assertEquals(1.0f, above.h, 1.0e-6f);
        assertEquals(MantlePhase.RECOVER, above.phase);
    }

    @Test
    void lowLipScalesTheSameWay() {
        // A 0.6 lip: halfway up is h = 0.5 whatever the height.
        final MantleProgress p = at(64.3, 64.6, 20.0, AT_FACE);
        assertEquals(0.5f, p.h, 1.0e-5f);
        assertEquals(MantlePhase.RISE, p.phase);
    }

    @Test
    void zeroHeightLipIsAlreadyOnTop() {
        assertEquals(MantlePhase.RECOVER, at(64.0, 64.0, 0.0, AT_FACE).phase);
    }

    @Test
    void pitchIsContinuousAcrossPhases() {
        final MantlePhase[] order = {MantlePhase.REACH, MantlePhase.RISE, MantlePhase.CREST, MantlePhase.RECOVER};
        assertEquals(0.0f, MantleProgress.pitchDegrees(MantlePhase.REACH, 0.0f, PROFILE), 1.0e-6f);
        for (int i = 0; i + 1 < order.length; i++) {
            assertEquals(MantleProgress.pitchDegrees(order[i], 1.0f, PROFILE),
                    MantleProgress.pitchDegrees(order[i + 1], 0.0f, PROFILE), 1.0e-5f,
                    order[i] + " into " + order[i + 1]);
        }
        assertEquals(0.0f, MantleProgress.pitchDegrees(MantlePhase.RECOVER, 1.0f, PROFILE), 1.0e-6f);
        assertEquals(PROFILE.risePitch, MantleProgress.pitchDegrees(MantlePhase.RISE, 1.0f, PROFILE), 1.0e-5f);
    }

    @Test
    void archExtendsWhileRearingFlexesOverTheCrestAndIsContinuous() {
        final MantleProfile profile = MantleProfile.builder().arch(25.0f, 30.0f).build();
        final MantlePhase[] order = {MantlePhase.REACH, MantlePhase.RISE, MantlePhase.CREST, MantlePhase.RECOVER};
        assertEquals(0.0f, MantleProgress.archDegrees(MantlePhase.REACH, 0.0f, profile));
        for (int i = 0; i + 1 < order.length; i++) {
            assertEquals(MantleProgress.archDegrees(order[i], 1.0f, profile),
                    MantleProgress.archDegrees(order[i + 1], 0.0f, profile), 1.0e-5f, order[i] + " into " + order[i + 1]);
        }
        assertEquals(-30.0f, MantleProgress.archDegrees(MantlePhase.RISE, 0.5f, profile), 1.0e-5f, "head up while rearing");
        assertEquals(25.0f, MantleProgress.archDegrees(MantlePhase.CREST, 1.0f, profile), 1.0e-5f, "head down on top");
        assertEquals(0.0f, MantleProgress.archDegrees(MantlePhase.RECOVER, 1.0f, profile), 1.0e-6f);
    }

    @Test
    void phasesFollowEachOtherAsTheBodyRises() {
        MantlePhase last = MantlePhase.REACH;
        for (int step = 0; step <= 100; step++) {
            final double y = 64.0 + step / 100.0;
            final MantlePhase phase = at(y, PROFILE.reachTicks + step).phase;
            assertTrue(phase.ordinal() >= last.ordinal(), "went back from " + last + " to " + phase + " at " + y);
            last = phase;
        }
        assertEquals(MantlePhase.RECOVER, last);
    }

    @Test
    void fromAClimbThereIsNoReach() {
        // Up the face already, standing still at the start: rising, not reaching.
        final MantleProgress p = MantleProgress.of(64.0, 64.0, 65.0, 0.0, AT_FACE, HALF_WIDTH, PROFILE, true,
                new MantleProgress());
        assertEquals(MantlePhase.RISE, p.phase);
        assertEquals(0.0f, p.t, 1.0e-6f);
    }

    @Test
    void fromAClimbTheRiseStartsUpright() {
        assertEquals(MantleProgress.CLIMB_PITCH, MantleProgress.pitchDegrees(MantlePhase.RISE, 0.0f, PROFILE, true), 1.0e-6f);
        assertEquals(PROFILE.risePitch, MantleProgress.pitchDegrees(MantlePhase.RISE, 1.0f, PROFILE, true), 1.0e-6f);
        // A climbing body's spine is straight; it extends through the rise as a reach would have.
        assertEquals(0.0f, MantleProgress.archDegrees(MantlePhase.RISE, 0.0f, PROFILE, true), 1.0e-6f);
        assertEquals(-PROFILE.rearArchDegrees, MantleProgress.archDegrees(MantlePhase.RISE, 1.0f, PROFILE, true), 1.0e-6f);
        // Continuous into the crest, as from the ground.
        assertEquals(MantleProgress.pitchDegrees(MantlePhase.CREST, 0.0f, PROFILE),
                MantleProgress.pitchDegrees(MantlePhase.RISE, 1.0f, PROFILE, true), 1.0e-6f);
    }
}
