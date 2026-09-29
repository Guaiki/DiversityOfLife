package com.evirapo.diversityofcritters.common.locomotion;

/**
 * How the server moves a body through a mantle this tick, free of Minecraft types.
 *
 * <ul>
 *   <li>REACH: stand against the face, gravity on.</li>
 *   <li>RISE and CREST: gravity off, rise at the profile's pace, never past the lip, the body
 *       pinned a hair in front of the face so it does not rub against it.</li>
 *   <li>RECOVER: gravity back on, walk forward onto the top; done once the body is on it.</li>
 * </ul>
 *
 * Velocities are blocks per tick: the displacement the entity makes this tick. A reusable holder.
 */
public final class MantleMotion {

    /** How far in front of the face the body is held while it rises. */
    public static final double PIN_GAP = 0.01;
    /** Fastest the body moves to its pinned distance, blocks per tick. */
    public static final double MAX_PIN_SPEED = 0.1;

    /** Along the facing, blocks per tick. */
    public double along;
    /** Up, blocks per tick. Only meaningful while {@link #holdsVertical}. */
    public double up;
    /** The mantle sets the vertical speed and turns gravity off; otherwise the entity falls as usual. */
    public boolean holdsVertical;
    /** The body is on top: the mantle is over. */
    public boolean done;

    /**
     * @param progress  this tick's phase
     * @param y         the body's Y
     * @param startY    its Y at the start
     * @param lipY      the lip's Y
     * @param pastFace  how far the body's centre is past the face, along the facing
     * @param halfWidth half the body's width
     * @param hop       the lip is past the body's reach: speed up the end of the rise
     */
    public static MantleMotion plan(MantleProgress progress, double y, double startY, double lipY,
                                    double pastFace, double halfWidth, boolean hop, MantleProfile profile,
                                    MantleMotion out) {
        final double rise = Math.max(lipY - startY, 0.0);
        final double pinned = -(halfWidth + PIN_GAP);
        final double toPin = clamp(pinned - pastFace, MAX_PIN_SPEED);
        final double toLip = Math.max(lipY - y, 0.0);
        out.done = false;

        switch (progress.phase) {
            case REACH -> {
                out.along = toPin;
                out.up = 0.0;
                out.holdsVertical = false;
            }
            case RISE -> {
                double speed = rise * profile.crestStart / profile.riseTicks;
                if (hop && progress.t >= profile.hopFrom) {
                    speed *= profile.hopBoost;
                }
                out.along = toPin;
                out.up = Math.min(speed, toLip);
                out.holdsVertical = true;
            }
            case CREST -> {
                out.along = toPin;
                out.up = Math.min(rise * (1.0 - profile.crestStart) / profile.crestTicks, toLip);
                out.holdsVertical = true;
            }
            case RECOVER -> {
                out.along = profile.recoverDistance / profile.recoverTicks;
                out.up = 0.0;
                out.holdsVertical = false;
                out.done = progress.t >= 1.0f;
            }
            default -> {
                out.along = 0.0;
                out.up = 0.0;
                out.holdsVertical = false;
                out.done = true;
            }
        }
        return out;
    }

    /**
     * This tick's rise at a slower pace ({@code timeScale} below 1, debug slow motion). The plan's
     * last step up is whatever is left to the lip; scaling that too would leave the body forever a
     * hair under the top, and walking on it would run into the lip's edge. So the last step is
     * taken whole.
     */
    public double upAt(float timeScale, double y, double lipY) {
        final double toLip = Math.max(lipY - y, 0.0);
        return this.up >= toLip - 1.0e-9 ? toLip : this.up * timeScale;
    }

    /** Whether a body drawn at {@code growthScale} needs a hop to reach a lip {@code lipHeight} up. */
    public static boolean needsHop(double lipHeight, float growthScale, MantleProfile profile) {
        return lipHeight > profile.reach * growthScale;
    }

    /** Past this share of the rise, an interrupted mantle finishes instead of dropping back. */
    public static final float COMPLETE_ABOVE = 0.8f;

    /** Whether a mantle interrupted at {@code h} should be completed rather than let go. */
    public static boolean completesWhenInterrupted(float h) {
        return h > COMPLETE_ABOVE;
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
