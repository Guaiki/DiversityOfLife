package com.evirapo.diversityofcritters.common.locomotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A whole mantle, tick by tick, as the server would move an ideal body: no collisions, gravity
 * keeping it on whatever it stands on. Checks that the plan ends on top, in time, without ever
 * pushing the body through the wall or past the lip.
 */
class MantleMotionTest {

    private static final MantleProfile PROFILE = MantleProfile.CIVET;
    private static final double HALF_WIDTH = 0.375;
    private static final double START_Y = 64.0;

    /** Result of a simulated run. */
    private record Run(int ticks, double y, double pastFace, double highestY, boolean enteredWall,
                       MantlePhase lastPhase) {
    }

    private static Run simulate(double lipY, double startPastFace) {
        final MantleProgress progress = new MantleProgress();
        final MantleMotion motion = new MantleMotion();
        double y = START_Y;
        double pastFace = startPastFace;
        double highest = y;
        boolean enteredWall = false;
        int tick = 0;
        for (; tick <= PROFILE.maxTicks; tick++) {
            MantleProgress.of(y, START_Y, lipY, tick, pastFace, HALF_WIDTH, PROFILE, progress);
            MantleMotion.plan(progress, y, START_Y, lipY, pastFace, HALF_WIDTH, PROFILE, motion);
            if (motion.done) {
                break;
            }
            pastFace += motion.along;
            if (motion.holdsVertical) {
                y += motion.up;
            }
            highest = Math.max(highest, y);
            // Below the lip, the body's front must stay out of the wall.
            if (y < lipY - 1.0e-9 && pastFace + HALF_WIDTH > 1.0e-9) {
                enteredWall = true;
            }
        }
        return new Run(tick, y, pastFace, highest, enteredWall, progress.phase);
    }

    @Test
    void fullBlockEndsOnTopInTime() {
        final Run run = simulate(65.0, -0.6);
        assertEquals(MantlePhase.RECOVER, run.lastPhase());
        assertTrue(run.ticks() < PROFILE.maxTicks, "took " + run.ticks() + " ticks");
        assertEquals(65.0, run.y(), 1.0e-9);
        assertTrue(run.pastFace() >= -HALF_WIDTH + PROFILE.recoverDistance - 1.0e-9, "on top: " + run.pastFace());
    }

    @Test
    void neverPassesTheLipNorEntersTheWall() {
        for (double lip : new double[]{64.6, 64.8, 65.0, 65.0625}) {
            final Run run = simulate(lip, -0.5);
            assertTrue(run.highestY() <= lip + 1.0e-9, "overshot lip " + lip + ": " + run.highestY());
            assertFalse(run.enteredWall(), "entered the wall under lip " + lip);
        }
    }

    @Test
    void takesTheProfilesTiming() {
        // Standing already against the face: reach + rise + crest + walk, give or take a tick per phase.
        final Run run = simulate(65.0, -HALF_WIDTH - MantleMotion.PIN_GAP);
        final int planned = PROFILE.reachTicks + PROFILE.riseTicks + PROFILE.crestTicks + PROFILE.recoverTicks;
        assertTrue(Math.abs(run.ticks() - planned) <= 4, run.ticks() + " vs " + planned);
    }

    @Test
    void reachPullsTheBodyToTheFace() {
        final MantleProgress progress = MantleProgress.of(START_Y, START_Y, 65.0, 0.0, -0.6, HALF_WIDTH,
                PROFILE, new MantleProgress());
        final MantleMotion motion = MantleMotion.plan(progress, START_Y, START_Y, 65.0, -0.6, HALF_WIDTH,
                PROFILE, new MantleMotion());
        assertEquals(MantlePhase.REACH, progress.phase);
        assertFalse(motion.holdsVertical, "gravity stays on while standing");
        assertEquals(MantleMotion.MAX_PIN_SPEED, motion.along, 1.0e-12, "toward the face, capped");
    }

    @Test
    void interruptedHighUpCompletes() {
        assertTrue(MantleMotion.completesWhenInterrupted(0.9f));
        assertFalse(MantleMotion.completesWhenInterrupted(0.5f));
    }
}
