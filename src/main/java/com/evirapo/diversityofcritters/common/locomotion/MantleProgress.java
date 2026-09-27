package com.evirapo.diversityofcritters.common.locomotion;

/**
 * How far along a mantle is, derived from where the body is rather than from a clock: the server
 * moves the body, the client reads the synchronised position, and both agree on the phase.
 *
 * <pre>
 *   h = (y - startY) / (lipY - startY)          0 standing, 1 level with the top
 *
 *   REACH    first reachTicks ticks, while h is still 0
 *   RISE     0 <= h < crestStart
 *   CREST    crestStart <= h < 1
 *   RECOVER  on top, until the centre is recoverDistance past where it stood at the face
 * </pre>
 *
 * REACH is the one phase the height cannot tell apart (the body has not moved yet), so it counts
 * ticks from the synchronised start tick. A reusable holder; pure functions.
 */
public final class MantleProgress {

    /** h counted as still standing, for REACH. */
    static final double STANDING = 1.0e-3;
    /** h counted as level with the top. */
    static final double ON_TOP = 1.0 - 1.0e-3;

    public MantlePhase phase = MantlePhase.NONE;
    /** Progress through {@link #phase}, in [0, 1]. */
    public float t;
    /** Share of the rise done, in [0, 1]. */
    public float h;

    /**
     * @param y              the body's Y now
     * @param startY         its Y when the mantle started
     * @param lipY           the lip's Y
     * @param ticks          ticks since the start, fractional
     * @param forward        how far the body's centre is past the wall's face, along the facing
     *                       (negative while in front of it)
     * @param bodyHalfWidth  half the body's width: where the centre stands against the face
     */
    public static MantleProgress of(double y, double startY, double lipY, double ticks, double forward,
                                    double bodyHalfWidth, MantleProfile profile, MantleProgress out) {
        final double rise = lipY - startY;
        final double h = rise > 1.0e-6 ? clamp01((y - startY) / rise) : 1.0;
        out.h = (float) h;

        if (h >= ON_TOP) {
            out.phase = MantlePhase.RECOVER;
            out.t = (float) clamp01((forward + bodyHalfWidth) / profile.recoverDistance);
        } else if (h >= profile.crestStart) {
            out.phase = MantlePhase.CREST;
            out.t = (float) ((h - profile.crestStart) / (1.0 - profile.crestStart));
        } else if (h <= STANDING && ticks < profile.reachTicks) {
            out.phase = MantlePhase.REACH;
            out.t = (float) clamp01(ticks / profile.reachTicks);
        } else {
            out.phase = MantlePhase.RISE;
            out.t = (float) (h / profile.crestStart);
        }
        return out;
    }

    /** Body pitch in degrees, nose up positive; continuous across phase borders. */
    public static float pitchDegrees(MantlePhase phase, float t, MantleProfile profile) {
        final float s = smoothstep(t);
        return switch (phase) {
            case NONE -> 0.0f;
            // Rears quickly at first, so the body is up before the first paw reaches the face.
            case REACH -> lerp(easeOut(t), 0.0f, profile.reachPitch);
            case RISE -> lerp(s, profile.reachPitch, profile.risePitch);
            case CREST -> lerp(s, profile.risePitch, profile.crestPitch);
            case RECOVER -> lerp(s, profile.crestPitch, 0.0f);
        };
    }

    /**
     * Spine curvature in degrees; positive bends the head down toward the tail's side of the body.
     * While the body rears up the spine extends (negative: head up, looking up the face, its
     * underside clear of the paws reaching past it); over the crest it flexes (positive: head down
     * toward the top); walking on, it straightens out. Continuous across phase borders.
     */
    public static float archDegrees(MantlePhase phase, float t, MantleProfile profile) {
        final float s = smoothstep(t);
        return switch (phase) {
            case NONE -> 0.0f;
            case REACH -> lerp(easeOut(t), 0.0f, -profile.rearArchDegrees);
            case RISE -> -profile.rearArchDegrees;
            case CREST -> lerp(s, -profile.rearArchDegrees, profile.archDegrees);
            case RECOVER -> lerp(s, profile.archDegrees, 0.0f);
        };
    }

    /** Fast at first, settling at 1. */
    public static float easeOut(float x) {
        final float t = x < 0.0f ? 0.0f : Math.min(x, 1.0f);
        final float u = 1.0f - t;
        return 1.0f - u * u;
    }

    public static float smoothstep(float x) {
        final float t = x < 0.0f ? 0.0f : Math.min(x, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }

    private static double clamp01(double x) {
        return x < 0.0 ? 0.0 : Math.min(x, 1.0);
    }
}
