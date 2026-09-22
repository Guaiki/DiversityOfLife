package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.locomotion.math.Spring;

/**
 * Everything the rig remembers about one entity between frames.
 *
 * <p>Models are shared by every entity of a type, so no per-entity value may live on the rig;
 * it lives here, one instance per entity (see {@link LocomotionStateCache}).
 */
public final class LocomotionState {

    /** A vertical jump larger than this between frames is a teleport: smoothing across it is wrong. */
    public static final double TELEPORT_BLOCKS = 3.0;
    /** Airborne longer than this and the feet stop having any say. */
    public static final float FALLING_AFTER_SECONDS = 0.25f;
    /** Frame deltas are clamped to this, so a hitch does not fling the springs. */
    public static final float MAX_FRAME_SECONDS = 0.1f;

    /** Fades the whole rig in and out; 1 = full correction. */
    public final Spring gate = new Spring(0.2f);
    public final Spring pelvisPitch = new Spring(0.18f);
    public final Spring pelvisRoll = new Spring(0.18f);
    /** Pelvis rise (negative) or sink (positive), model units, model +Y down. */
    public final Spring pelvisDrop = new Spring(0.12f);

    public boolean initialised;
    public double lastEntityY;
    public float airborneSeconds;
    /** Body yaw this frame, degrees. */
    public float bodyYaw;

    private long frame = -1;
    private long frameNanos;
    /** Seconds since the previous frame this entity was drawn in; 0 on repeat passes. */
    public float frameSeconds;

    private LegState[] legs = new LegState[0];

    public LegState[] ensureLegs(int count) {
        if (this.legs.length != count) {
            this.legs = new LegState[count];
            for (int i = 0; i < count; i++) {
                this.legs[i] = new LegState();
            }
            this.initialised = false;
        }
        return this.legs;
    }

    public LegState[] legs() {
        return this.legs;
    }

    /**
     * Marks the start of a render pass.
     *
     * @return true on the first pass of a frame, which is the only one that advances time
     */
    public boolean beginFrame(long frame, long nanos) {
        if (frame == this.frame) {
            this.frameSeconds = 0.0f;
            return false;
        }
        final boolean seen = this.frame >= 0;
        this.frame = frame;
        this.frameSeconds = seen ? Math.min((nanos - this.frameNanos) * 1.0e-9f, MAX_FRAME_SECONDS) : 0.0f;
        this.frameSeconds = Math.max(this.frameSeconds, 0.0f);
        this.frameNanos = nanos;
        return true;
    }

    public boolean falling() {
        return this.airborneSeconds > FALLING_AFTER_SECONDS;
    }

    /** Forgets all smoothing; the next frame starts from what it measures. */
    public void reset() {
        this.initialised = false;
        this.airborneSeconds = 0.0f;
        for (LegState leg : this.legs) {
            leg.reset();
        }
    }

    /** Per-leg memory and this frame's per-leg readings. */
    public static final class LegState {

        /** Sentinel for {@link #contactOverride}: let the stance heuristic decide. */
        public static final float NO_OVERRIDE = -1.0f;

        /** How planted the foot is according to the animation, in [0, 1]. */
        public float plantWeight;
        /** How much the foot is inside its ground (so certainly in contact), in [0, 1]. */
        public float contactWeight;
        /** How much of the foot's weight the body should answer, in [0, 1]. */
        public float support = 1.0f;
        /** Forces the plant weight this frame, or {@link #NO_OVERRIDE}. */
        public float contactOverride = NO_OVERRIDE;

        // Stance tracker memory.
        public boolean stanceSeeded;
        public float envelopeLow;
        public float envelopeHigh;
        public float lastSoleHeight;
        public float stillSeconds;

        // This frame's readings.
        public double soleX;
        public double soleY;
        public double soleZ;
        public double groundY;
        public final GroundProbe.Reading ground = new GroundProbe.Reading();

        /** Smoothed correction the leg itself answers, model units. */
        public final Spring footLift = new Spring(0.08f);
        /** Smoothed correction the body answers, model units. */
        public final Spring bodyLift = new Spring(0.08f);

        // Debug.
        public String name = "?";
        public float debugLift;
        public float debugResidual;
        public float debugStretch;
        public double debugCorrectedSoleY;

        public void reset() {
            this.plantWeight = 0.0f;
            this.contactWeight = 0.0f;
            this.support = 1.0f;
            this.contactOverride = NO_OVERRIDE;
            this.stanceSeeded = false;
            this.stillSeconds = 0.0f;
            this.footLift.snap(0.0f);
            this.bodyLift.snap(0.0f);
        }
    }
}
