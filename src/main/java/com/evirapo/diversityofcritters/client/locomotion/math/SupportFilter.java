package com.evirapo.diversityofcritters.client.locomotion.math;

/**
 * Decides how much each foot is actually holding the body up.
 *
 * <p>Feet are compared against a reference foot: the planted one asking for the smallest
 * correction, which is the contact most certainly real. A foot is trusted as long as the gap to
 * the reference is something the body could actually produce (by tilting and stretching legs);
 * beyond that it fades out over a band, so a foot hanging over a drop, or a foot the stance
 * heuristic wrongly calls planted mid-stride, stops bending the body.
 *
 * <p>The test is one-sided. A foot below its ground is in contact and must keep pushing the body
 * up, however far off it is.
 */
public final class SupportFilter {

    /** Plant weight a foot needs before it can serve as the reference. */
    public static final float MIN_PLANT = 0.05f;

    /** Width of the fade past the limit, as a fraction of the limit. */
    public static final float FADE_BAND = 0.5f;

    private SupportFilter() {
    }

    /**
     * Index of the planted foot asking for the smallest correction, or -1 if none is planted.
     *
     * @param lifts  per-foot correction the ground asks for (any sign)
     * @param plants per-foot plant weight in [0, 1]
     */
    public static int reference(float[] lifts, float[] plants, int count) {
        int best = -1;
        float bestMagnitude = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (plants[i] <= MIN_PLANT) {
                continue;
            }
            final float magnitude = Math.abs(lifts[i]);
            if (magnitude < bestMagnitude) {
                bestMagnitude = magnitude;
                best = i;
            }
        }
        return best;
    }

    /**
     * Largest height difference the rig can bridge between two hips.
     *
     * @param hipDx          hip separation along X
     * @param hipDz          hip separation along Z
     * @param sinMaxPitch    sine of the pitch limit
     * @param sinMaxRoll     sine of the roll limit
     * @param tiltEfficiency share of the ideal tilt that actually reaches a foot, in (0, 1]; the
     *                       pelvis height limit eats part of it
     * @param stretchRange   combined stretch of both legs (one lengthens, the other shortens)
     */
    public static float reachEnvelope(float hipDx, float hipDz, float sinMaxPitch, float sinMaxRoll,
                                      float tiltEfficiency, float stretchRange) {
        final float tilt = Math.abs(hipDz * sinMaxPitch) + Math.abs(hipDx * sinMaxRoll);
        return tiltEfficiency * tilt + stretchRange;
    }

    /** 1 while {@code excess <= limit}, falling linearly to 0 across the fade band. */
    public static float fade(float excess, float limit) {
        if (excess <= limit) {
            return 1.0f;
        }
        final float band = limit * FADE_BAND;
        if (band <= 0.0f) {
            return 0.0f;
        }
        return Math.max(0.0f, 1.0f - (excess - limit) / band);
    }

    /**
     * Support of one foot, from two independent tests; the stricter one wins.
     *
     * @param terrainExcess   reference ground minus this foot's ground (positive: this foot's
     *                        ground is lower)
     * @param envelope        from {@link #reachEnvelope}
     * @param strideExcess    this sole minus the reference sole as animated (positive: drawn higher)
     * @param strideTolerance how far apart two soles really in contact can be drawn
     */
    public static float support(float terrainExcess, float envelope, float strideExcess, float strideTolerance) {
        return Math.min(fade(terrainExcess, envelope), fade(strideExcess, strideTolerance));
    }
}
