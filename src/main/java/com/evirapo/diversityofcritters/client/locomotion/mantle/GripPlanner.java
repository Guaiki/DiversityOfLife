package com.evirapo.diversityofcritters.client.locomotion.mantle;

import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleProgress;

/**
 * Where the paws are through a mantle, relative to the wall, free of Minecraft types. Front paws
 * climb the face ({@link #plan}); hind feet step in and scramble up after them ({@link #planHind}).
 *
 * <ul>
 *   <li>REACH: the paws come up to their first grips on the face one after the other: the walk
 *       carries on into the climb instead of stopping for it. The paw moving first, with the body
 *       still low, takes the lower grip; the other, once the body has reared further, the higher.</li>
 *   <li>RISE: the paws climb in alternate grips, first then second, up to the lip. Only one paw is
 *       ever in swing; the other holds. A swing is an arc: it rises, leaves the face, comes back.</li>
 *   <li>CREST: from the lip onto the top, one paw after the other.</li>
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
                // Off the ground and up to the face, a paw at a time; until its turn a paw walks on.
                final float u0 = half(t, 0);
                final float u1 = half(t, 1);
                first.set(lipHeight * profile.firstGripLow, -FACE_GAP, MantleProgress.smoothstep(u0), arc(u0));
                second.set(lipHeight * profile.firstGripHigh, -FACE_GAP, MantleProgress.smoothstep(u1), arc(u1));
            }
            case RISE -> climb(t, lipHeight, profile, first, second);
            case CREST -> {
                ontoTop(first, half(t, 0), lipHeight, profile);
                ontoTop(second, half(t, 1), lipHeight, profile);
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

    /** Progress through its half of a phase for the paw moving first (0) or second (1). */
    static float half(float t, int which) {
        return Math.max(0.0f, Math.min(t * 2.0f - which, 1.0f));
    }

    /** How much of a paw is in the air along a move from 0 to 1. */
    private static float arc(float u) {
        return u <= 0.0f || u >= 1.0f ? 0.0f : (float) Math.sin(Math.PI * u);
    }

    /** From the lip onto the top, lifting a little on the way. */
    private static void ontoTop(Hand hand, float u, double lipHeight, MantleProfile profile) {
        final float s = MantleProgress.smoothstep(u);
        final float bulge = arc(u);
        hand.set(lipHeight + profile.swingOut * 0.5 * bulge, lerp(s, profile.lipInset, profile.plantDepth), 1.0f, bulge);
    }

    /**
     * The hind feet, walking with the front paws: the first hind foot to move is the one diagonal to
     * the leading front paw, and it swings when that paw does.
     *
     * <ul>
     *   <li>REACH: one step each, one after the other, covering what the body still travels to the
     *       face ({@code approach}), so they keep up instead of being left behind.</li>
     *   <li>RISE: {@code 2 × hindSteps} alternate steps toward the wall, each a small arc.</li>
     *   <li>CREST: one after the other, they scramble onto the face below the lip.</li>
     *   <li>RECOVER: on the face, handing back to the ground path.</li>
     * </ul>
     *
     * @param firstDepth  where the first foot stood, past the face (negative)
     * @param secondDepth likewise for the other
     * @param approach    how far the body still moves toward the face while reaching, blocks
     */
    public static void planHind(MantlePhase phase, float t, double lipHeight, double firstDepth,
                                double secondDepth, double approach, MantleProfile profile, Hand first,
                                Hand second) {
        final double faceHeight = lipHeight * profile.hindFaceGrip;
        switch (phase) {
            case REACH -> {
                approachStep(first, firstDepth, approach, half(t, 0), profile);
                approachStep(second, secondDepth, approach, half(t, 1), profile);
                return;
            }
            default -> {
            }
        }
        // From here on the feet stand where the approach left them.
        firstDepth = Math.min(firstDepth + approach, -FACE_GAP);
        secondDepth = Math.min(secondDepth + approach, -FACE_GAP);
        switch (phase) {
            case RISE -> {
                final int swings = 2 * profile.hindSteps;
                if (swings == 0) {
                    first.set(0.0, stepDepth(firstDepth, 0, profile), 1.0f, 0.0f);
                    second.set(0.0, stepDepth(secondDepth, 0, profile), 1.0f, 0.0f);
                    return;
                }
                final float scaled = Math.max(0.0f, Math.min(t, 1.0f)) * swings;
                final int swing = Math.min((int) scaled, swings - 1);
                final float u = scaled - swing;
                final int firstDone = (swing + 1) / 2;
                final int secondDone = swing / 2;
                if (swing % 2 == 0) {
                    step(first, firstDepth, firstDone, u, profile);
                    second.set(0.0, stepDepth(secondDepth, secondDone, profile), 1.0f, 0.0f);
                } else {
                    first.set(0.0, stepDepth(firstDepth, firstDone, profile), 1.0f, 0.0f);
                    step(second, secondDepth, secondDone, u, profile);
                }
            }
            case CREST -> {
                final double firstGround = stepDepth(firstDepth, profile.hindSteps, profile);
                final double secondGround = stepDepth(secondDepth, profile.hindSteps, profile);
                if (t < 0.5f) {
                    scramble(first, firstGround, faceHeight, t * 2.0f, profile);
                    second.set(0.0, secondGround, 1.0f, 0.0f);
                } else {
                    first.set(faceHeight, -FACE_GAP, 1.0f, 0.0f);
                    scramble(second, secondGround, faceHeight, Math.min((t - 0.5f) * 2.0f, 1.0f), profile);
                }
            }
            case RECOVER -> {
                final float s = MantleProgress.smoothstep(t);
                first.set(faceHeight, -FACE_GAP, 1.0f - s, 0.0f);
                second.set(faceHeight, -FACE_GAP, 1.0f - s, 0.0f);
            }
            default -> {
                first.set(0.0, 0.0, 0.0f, 0.0f);
                second.set(0.0, 0.0, 0.0f, 0.0f);
            }
        }
    }

    /** The step a hind foot takes to keep up with the body closing on the face. */
    private static void approachStep(Hand hand, double start, double approach, float u, MantleProfile profile) {
        final float s = MantleProgress.smoothstep(u);
        final float bulge = approach > 1.0e-6 ? arc(u) : 0.0f;
        final double depth = Math.min(lerp(s, start, start + approach), -FACE_GAP);
        hand.set(profile.hindStepLift * bulge, depth, 1.0f, bulge);
    }

    /** A foot's depth after {@code steps} steps from {@code start}, never closer than the face allows. */
    static double stepDepth(double start, int steps, MantleProfile profile) {
        return Math.min(start + steps * profile.hindStepLength, -FACE_GAP);
    }

    /** One step toward the wall, lifting the foot in the middle. */
    private static void step(Hand hand, double start, int done, float u, MantleProfile profile) {
        final float s = MantleProgress.smoothstep(u);
        final float bulge = arc(u);
        final double depth = lerp(s, stepDepth(start, done, profile), stepDepth(start, done + 1, profile));
        hand.set(profile.hindStepLift * bulge, depth, 1.0f, bulge);
    }

    /** From the ground onto the face, pulling back off it in the middle. */
    private static void scramble(Hand hand, double groundDepth, double faceHeight, float u, MantleProfile profile) {
        final float s = MantleProgress.smoothstep(u);
        final float bulge = arc(u);
        final double depth = lerp(s, groundDepth, -FACE_GAP) - profile.hindStepLift * bulge;
        hand.set(lerp(s, 0.0, faceHeight), depth, 1.0f, bulge);
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

        final double firstStart = lipHeight * profile.firstGripLow;
        final double secondStart = lipHeight * profile.firstGripHigh;
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
        final float bulge = arc(u);
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
