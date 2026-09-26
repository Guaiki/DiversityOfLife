package com.evirapo.diversityofcritters.client.locomotion.mantle;

import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The front paws' plan through a mantle, with one grip per paw and with several. */
class GripPlannerTest {

    private static final double LIP = 1.0;
    private static final MantleProfile ONE = MantleProfile.CIVET;
    private static final MantleProfile THREE = MantleProfile.builder().grips(3, 0.6, 0.4, 0.1).build();
    private static final MantlePhase[] ORDER = {MantlePhase.REACH, MantlePhase.RISE, MantlePhase.CREST, MantlePhase.RECOVER};
    private static final int STEPS = 400;

    private final GripPlanner.Hand first = new GripPlanner.Hand();
    private final GripPlanner.Hand second = new GripPlanner.Hand();

    private void plan(MantlePhase phase, float t, MantleProfile profile) {
        GripPlanner.plan(phase, t, LIP, profile, this.first, this.second);
    }

    @Test
    void neverBothPawsInSwing() {
        for (MantleProfile profile : new MantleProfile[]{ONE, THREE}) {
            for (MantlePhase phase : ORDER) {
                for (int i = 0; i <= STEPS; i++) {
                    plan(phase, i / (float) STEPS, profile);
                    assertTrue(this.first.swingWeight == 0.0f || this.second.swingWeight == 0.0f,
                            phase + " at " + i + ": both paws off");
                }
            }
        }
    }

    @Test
    void pawsStayOnTheFaceUntilTheCrest() {
        for (MantleProfile profile : new MantleProfile[]{ONE, THREE}) {
            for (MantlePhase phase : new MantlePhase[]{MantlePhase.REACH, MantlePhase.RISE}) {
                for (int i = 0; i <= STEPS; i++) {
                    plan(phase, i / (float) STEPS, profile);
                    assertTrue(this.first.height <= LIP + 1.0e-12 && this.second.height <= LIP + 1.0e-12,
                            phase + " at " + i + ": above the lip");
                    // In front of the face, or over the lip once there.
                    assertTrue(this.first.depth <= 0.0 || this.first.height >= LIP - 1.0e-12, "first inside the wall");
                    assertTrue(this.second.depth <= 0.0 || this.second.height >= LIP - 1.0e-12, "second inside the wall");
                }
            }
        }
    }

    @Test
    void continuousAcrossPhases() {
        for (MantleProfile profile : new MantleProfile[]{ONE, THREE}) {
            for (int p = 0; p + 1 < ORDER.length; p++) {
                plan(ORDER[p], 1.0f, profile);
                final double[] end = snapshot();
                plan(ORDER[p + 1], 0.0f, profile);
                final double[] start = snapshot();
                for (int k = 0; k < end.length; k++) {
                    assertEquals(end[k], start[k], 1.0e-6, ORDER[p] + " into " + ORDER[p + 1] + ", value " + k);
                }
            }
        }
    }

    @Test
    void smoothWithinTheRise() {
        // No jump between neighbouring samples bigger than a swing could make in one step.
        for (MantleProfile profile : new MantleProfile[]{ONE, THREE}) {
            plan(MantlePhase.RISE, 0.0f, profile);
            double[] last = snapshot();
            for (int i = 1; i <= STEPS; i++) {
                plan(MantlePhase.RISE, i / (float) STEPS, profile);
                final double[] now = snapshot();
                for (int k = 0; k < now.length; k++) {
                    assertTrue(Math.abs(now[k] - last[k]) < 0.05, "jump at " + i + " in value " + k);
                }
                last = now;
            }
        }
    }

    @Test
    void theRiseEndsWithBothPawsOnTheLip() {
        plan(MantlePhase.RISE, 1.0f, THREE);
        assertEquals(LIP, this.first.height, 1.0e-9);
        assertEquals(LIP, this.second.height, 1.0e-9);
        assertEquals(THREE.lipInset, this.first.depth, 1.0e-9);
        assertEquals(THREE.lipInset, this.second.depth, 1.0e-9);
    }

    @Test
    void pawsTakeTurns() {
        // One grip each: the leading paw moves in the first half, the other in the second.
        plan(MantlePhase.RISE, 0.25f, ONE);
        assertTrue(this.first.swingWeight > 0.5f && this.second.swingWeight == 0.0f);
        plan(MantlePhase.RISE, 0.75f, ONE);
        assertTrue(this.second.swingWeight > 0.5f && this.first.swingWeight == 0.0f);
    }

    @Test
    void aSwingLeavesTheFace() {
        plan(MantlePhase.RISE, 0.25f, ONE);
        assertTrue(this.first.depth < -GripPlanner.FACE_GAP - 0.05, "mid-swing depth " + this.first.depth);
    }

    private double[] snapshot() {
        return new double[]{this.first.height, this.first.depth, this.first.anchorWeight, this.first.swingWeight,
                this.second.height, this.second.depth, this.second.anchorWeight, this.second.swingWeight};
    }
}
