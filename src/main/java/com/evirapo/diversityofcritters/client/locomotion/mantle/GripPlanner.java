package com.evirapo.diversityofcritters.client.locomotion.mantle;

import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleProgress;

/**
 * Where the two front paws are through a mantle, relative to the wall, free of Minecraft types.
 *
 * <ul>
 *   <li>REACH: both paws come up to their first grips on the face, one higher than the other.</li>
 *   <li>RISE: the paws climb in alternate grips, first then second, up to the lip. Only one paw is
 *       ever in swing; the other holds. A swing is an arc: it rises, leaves the face, comes back.</li>
 *   <li>CREST: both paws on the lip, sliding onto the top.</li>
 *   <li>RECOVER: planted on the top, handing back to the ground path.</li>
 * </ul>
 *
 * Heights are above the body's starting Y; depths are past the face along the facing (negative in
 * front of it). A grip on the face sits {@link #FACE_GAP} in front of it, so the sole touches the
 * face instead of sinking into it.
 */
public final class GripPlanner {

    /** How far in front of the face a paw gripping it sits. */
    public static final double FACE_GAP = 0.02;

    private GripPlanner() {
    }

    /** One paw's plan. */
    public static final class Hand {
        /** Above the body's starting Y, blocks. */
        public double height;
        /** Past the face along the facing, blocks; negative in front of it. */
        public double depth;
        /** How much the paw aims at its target rather than following the ground. */
        public float anchorWeight;
        /** How much the paw is in the air between grips, in [0, 1]. */
        public float swingWeight;

        void set(double height, double depth, float anchorWeight, float swingWeight) {
            this.height = height;
            this.depth = depth;
            this.anchorWeight = anchorWeight;
            this.swingWeight = swingWeight;
        }
    }

    /**
     * @param lipHeight the lip above the body's starting Y
     * @param first     the paw that moves first (leads)
     * @param second    the other paw
     */
    public static void plan(MantlePhase phase, float t, double lipHeight, MantleProfile profile,
                            Hand first, Hand second) {
        final float s = MantleProgress.smoothstep(t);
        switch (phase) {
            case REACH -> {
                first.set(lipHeight * profile.firstGripHigh, -FACE_GAP, s, 0.0f);
                second.set(lipHeight * profile.firstGripLow, -FACE_GAP, s, 0.0f);
            }
            case RISE -> climb(t, lipHeight, profile, first, second);
            case CREST -> {
                final double depth = profile.lipInset + (profile.plantDepth - profile.lipInset) * s;
                first.set(lipHeight, depth, 1.0f, 0.0f);
                second.set(lipHeight, depth, 1.0f, 0.0f);
            }
            case RECOVER -> {
                first.set(lipHeight, profile.plantDepth, 1.0f - s, 0.0f);
                second.set(lipHeight, profile.plantDepth, 1.0f - s, 0.0f);
            }
            default -> {
                first.set(0.0, 0.0, 0.0f, 0.0f);
                second.set(0.0, 0.0, 0.0f, 0.0f);
            }
        }
    }

    /**
     * The rise split into {@code 2 × gripsPerHand} equal swings, alternating first, second, first...
     * Each paw's last grip is the lip.
     */
    private static void climb(float t, double lipHeight, MantleProfile profile, Hand first, Hand second) {
        final int perHand = profile.gripsPerHand;
        final int swings = 2 * perHand;
        final float scaled = Math.max(0.0f, Math.min(t, 1.0f)) * swings;
        final int swing = Math.min((int) scaled, swings - 1);
        final float u = scaled - swing;
        final boolean firstMoves = swing % 2 == 0;
        // Grips each paw has finished before this swing.
        final int firstDone = (swing + 1) / 2;
        final int secondDone = swing / 2;

        final double firstStart = lipHeight * profile.firstGripHigh;
        final double secondStart = lipHeight * profile.firstGripLow;
        if (firstMoves) {
            swingBetween(first, firstStart, firstDone, perHand, u, lipHeight, profile);
            hold(second, secondStart, secondDone, perHand, lipHeight, profile);
        } else {
            hold(first, firstStart, firstDone, perHand, lipHeight, profile);
            swingBetween(second, secondStart, secondDone, perHand, u, lipHeight, profile);
        }
    }

    private static void hold(Hand hand, double start, int grip, int perHand, double lipHeight, MantleProfile profile) {
        hand.set(gripHeight(start, grip, perHand, lipHeight), gripDepth(grip, perHand, profile), 1.0f, 0.0f);
    }

    /**
     * From grip {@code from} to the next, along an arc that leaves the face in the middle. The swing
     * onto the lip first rises to the lip's height and only then moves over it, so the paw clears
     * the corner instead of cutting through it.
     */
    private static void swingBetween(Hand hand, double start, int from, int perHand, float u,
                                     double lipHeight, MantleProfile profile) {
        final boolean ontoLip = from + 1 >= perHand;
        final float rise = MantleProgress.smoothstep(ontoLip ? u / OVER_LIP_AT : u);
        final float across = ontoLip ? MantleProgress.smoothstep((u - OVER_LIP_AT) / (1.0f - OVER_LIP_AT)) : rise;
        final float bulge = (float) Math.sin(Math.PI * u);
        final double height = lerp(rise, gripHeight(start, from, perHand, lipHeight),
                gripHeight(start, from + 1, perHand, lipHeight));
        final double depth = lerp(across, gripDepth(from, perHand, profile), gripDepth(from + 1, perHand, profile))
                - profile.swingOut * bulge;
        hand.set(height, depth, 1.0f, bulge);
    }

    /** Share of the swing onto the lip spent rising to its height, before moving over it. */
    static final float OVER_LIP_AT = 0.7f;

    /** Grip {@code i} of a paw starting at {@code start}: evenly up to the lip at {@code perHand}. */
    static double gripHeight(double start, int i, int perHand, double lipHeight) {
        return lerp((double) i / perHand, start, lipHeight);
    }

    /** On the face until the last grip, which is over the lip. */
    static double gripDepth(int i, int perHand, MantleProfile profile) {
        return i >= perHand ? profile.lipInset : -FACE_GAP;
    }

    private static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }
}
