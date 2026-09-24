package com.evirapo.diversityofcritters.common.locomotion;

/**
 * Per-species numbers for climbing a ledge. Heights and distances are in blocks.
 *
 * <p>Only the ledge probe's numbers exist so far; the manoeuvre's own (reach, phase thresholds,
 * speeds) join them when the manoeuvre is built.
 */
public final class MantleProfile {

    /** Civet: one full block, but not fences or walls. */
    public static final MantleProfile CIVET = new MantleProfile(0.6, 1.0625, 0.5);

    /** A rise lower than this is an ordinary step, not a ledge. */
    public final double minMantleHeight;
    /** A rise higher than this cannot be mantled (fences, walls, two-block walls). */
    public final double maxMantleHeight;
    /** How far past the front of the body the probe looks for a wall. */
    public final double probeDistance;

    public MantleProfile(double minMantleHeight, double maxMantleHeight, double probeDistance) {
        if (!(minMantleHeight > 0.0 && maxMantleHeight >= minMantleHeight && probeDistance > 0.0)) {
            throw new IllegalArgumentException("Bad mantle profile: min " + minMantleHeight
                    + ", max " + maxMantleHeight + ", probe " + probeDistance);
        }
        this.minMantleHeight = minMantleHeight;
        this.maxMantleHeight = maxMantleHeight;
        this.probeDistance = probeDistance;
    }
}
