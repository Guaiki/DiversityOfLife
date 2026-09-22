package com.evirapo.diversityofcritters.client.locomotion.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrimmedMeanTest {

    private static final float[] EQUAL = {1.0f, 1.0f, 1.0f, 1.0f};
    private final int[] order = new int[8];

    @Test
    void agreeingReadingsGiveThatValue() {
        assertEquals(2.5f, TrimmedMean.of(new float[]{2.5f, 2.5f, 2.5f, 2.5f}, EQUAL, this.order, 4), 1.0e-6f);
    }

    @Test
    void oneOutlierDoesNotMoveTheResult() {
        // One foot on a step, three on the floor.
        assertEquals(0.0f, TrimmedMean.of(new float[]{0.0f, 12.0f, 0.0f, 0.0f}, EQUAL, this.order, 4), 1.0e-6f);
    }

    @Test
    void spreadReadingsAverageTheMiddle() {
        assertEquals(2.5f, TrimmedMean.of(new float[]{4.0f, 1.0f, 3.0f, 2.0f}, EQUAL, this.order, 4), 1.0e-6f);
    }

    @Test
    void keepingEverythingIsTheWeightedMean() {
        final float[] values = {0.0f, 10.0f, 4.0f};
        final float[] weights = {1.0f, 1.0f, 2.0f};
        assertEquals(4.5f, TrimmedMean.of(values, weights, this.order, 3, 1.0f), 1.0e-5f);
    }

    @Test
    void weightlessReadingsAreIgnored() {
        final float[] values = {100.0f, 3.0f, -100.0f};
        final float[] weights = {0.0f, 1.0f, 0.0f};
        assertEquals(3.0f, TrimmedMean.of(values, weights, this.order, 3), 1.0e-6f);
        assertEquals(0.0f, TrimmedMean.of(values, new float[3], this.order, 3));
    }

    @Test
    void isContinuousInItsInputs() {
        final float[] values = {0.0f, 1.0f, 2.0f, 9.0f};
        final float before = TrimmedMean.of(values, EQUAL, this.order, 4);
        values[2] += 0.001f;
        final float after = TrimmedMean.of(values, EQUAL, this.order, 4);
        assertTrue(Math.abs(after - before) <= 0.001f, "jumped by " + (after - before));
    }

    @Test
    void doesNotDependOnInputOrder() {
        final float[] weights = {1.0f, 0.5f, 2.0f, 1.0f};
        final float a = TrimmedMean.of(new float[]{3.0f, -1.0f, 7.0f, 0.5f}, weights, this.order, 4);
        final float b = TrimmedMean.of(new float[]{7.0f, 0.5f, 3.0f, -1.0f}, new float[]{2.0f, 1.0f, 1.0f, 0.5f}, this.order, 4);
        assertEquals(a, b, 1.0e-6f);
    }
}
