package com.evirapo.diversityofcritters.client.locomotion.math;

/**
 * Weighted mean of the central share of a set of readings.
 *
 * <p>Used for "how far should the body rise or sink": with one foot on a step and three on the
 * floor, a plain mean lifts the body toward the odd foot and hands the other three a correction
 * they never needed. Dropping the outer quarters of the weight on each side lets the majority
 * decide, while staying continuous in the inputs (unlike a median), which matters for a value that
 * feeds a spring every frame.
 */
public final class TrimmedMean {

    /** Share of the total weight, taken from the middle, that decides the result. */
    public static final float DEFAULT_KEEP = 0.5f;

    private TrimmedMean() {
    }

    /**
     * @param values  the readings
     * @param weights weight per reading; zero or less ignores it
     * @param order   scratch of at least {@code count} ints, overwritten
     * @param count   number of readings
     * @param keep    share of the weight to keep, in (0, 1]
     * @return the trimmed mean, or zero when nothing carries weight
     */
    public static float of(float[] values, float[] weights, int[] order, int count, float keep) {
        float total = 0.0f;
        for (int i = 0; i < count; i++) {
            if (weights[i] > 0.0f) {
                total += weights[i];
            }
        }
        if (total <= 0.0f) {
            return 0.0f;
        }

        sortByValue(values, order, count);

        final float bandStart = total * (1.0f - keep) * 0.5f;
        final float bandEnd = bandStart + total * keep;

        float cursor = 0.0f;
        float kept = 0.0f;
        float sum = 0.0f;
        for (int k = 0; k < count && cursor < bandEnd; k++) {
            final int i = order[k];
            final float w = weights[i];
            if (w <= 0.0f) {
                continue;
            }
            final float overlap = Math.min(cursor + w, bandEnd) - Math.max(cursor, bandStart);
            cursor += w;
            if (overlap > 0.0f) {
                sum += overlap * values[i];
                kept += overlap;
            }
        }
        return kept > 0.0f ? sum / kept : 0.0f;
    }

    public static float of(float[] values, float[] weights, int[] order, int count) {
        return of(values, weights, order, count, DEFAULT_KEEP);
    }

    /** Insertion sort of indices: a handful of legs, no allocation. */
    private static void sortByValue(float[] values, int[] order, int count) {
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        for (int i = 1; i < count; i++) {
            final int current = order[i];
            final float v = values[current];
            int j = i - 1;
            while (j >= 0 && values[order[j]] > v) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = current;
        }
    }
}
