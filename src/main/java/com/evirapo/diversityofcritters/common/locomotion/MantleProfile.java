package com.evirapo.diversityofcritters.common.locomotion;

/**
 * Per-species numbers for climbing a ledge. Heights and distances are in blocks, angles in
 * degrees (nose up positive).
 */
public final class MantleProfile {

    /** Civet: one full block, but not fences or walls. */
    public static final MantleProfile CIVET = builder().build();

    // --- The ledge probe.
    /** A rise lower than this is an ordinary step, not a ledge. */
    public final double minMantleHeight;
    /** A rise higher than this cannot be mantled (fences, walls, two-block walls). */
    public final double maxMantleHeight;
    /** How far past the front of the body the probe looks for a wall. */
    public final double probeDistance;

    // --- Phases (see MantleProgress).
    /** How long the front paws take to reach for the face, in ticks, before the body rises. */
    public final int reachTicks;
    /** Share of the rise, from 0 to 1, at which the front paws pass the lip. */
    public final double crestStart;
    /** How far the body walks onto the top before the manoeuvre is over. */
    public final double recoverDistance;

    // --- Server movement (see MantleMotion).
    /** Ticks the rise takes, up to the crest. */
    public final int riseTicks;
    /** Ticks the crest takes, up to level with the top. */
    public final int crestTicks;
    /** Ticks the walk onto the top takes. */
    public final int recoverTicks;
    /** A mantle still going after this many ticks is given up. */
    public final int maxTicks;
    /** Ledges probed with less confidence than this are not mantled. */
    public final float minConfidence;

    // --- The pose.
    /** Body pitch at the end of each phase; it starts from the previous phase's. */
    public final float reachPitch;
    public final float risePitch;
    public final float crestPitch;
    /** How far past the face the front paws grip the lip. */
    public final double lipInset;
    /** How far past the face the front paws plant on the top by the end of the crest. */
    public final double plantDepth;

    private MantleProfile(Builder b) {
        if (!(b.minMantleHeight > 0.0 && b.maxMantleHeight >= b.minMantleHeight && b.probeDistance > 0.0)) {
            throw new IllegalArgumentException("Bad mantle heights: min " + b.minMantleHeight
                    + ", max " + b.maxMantleHeight + ", probe " + b.probeDistance);
        }
        if (!(b.reachTicks > 0 && b.crestStart > 0.0 && b.crestStart < 1.0 && b.recoverDistance > 0.0
                && b.riseTicks > 0 && b.crestTicks > 0 && b.recoverTicks > 0
                && b.maxTicks > b.reachTicks + b.riseTicks + b.crestTicks + b.recoverTicks)) {
            throw new IllegalArgumentException("Bad mantle phases: reach " + b.reachTicks
                    + " ticks, crest at " + b.crestStart + ", recover " + b.recoverDistance
                    + ", rise/crest/recover " + b.riseTicks + "/" + b.crestTicks + "/" + b.recoverTicks
                    + " of max " + b.maxTicks);
        }
        this.minMantleHeight = b.minMantleHeight;
        this.maxMantleHeight = b.maxMantleHeight;
        this.probeDistance = b.probeDistance;
        this.reachTicks = b.reachTicks;
        this.crestStart = b.crestStart;
        this.recoverDistance = b.recoverDistance;
        this.riseTicks = b.riseTicks;
        this.crestTicks = b.crestTicks;
        this.recoverTicks = b.recoverTicks;
        this.maxTicks = b.maxTicks;
        this.minConfidence = b.minConfidence;
        this.reachPitch = b.reachPitch;
        this.risePitch = b.risePitch;
        this.crestPitch = b.crestPitch;
        this.lipInset = b.lipInset;
        this.plantDepth = b.plantDepth;
    }

    /** Starts from the civet's numbers. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double minMantleHeight = 0.6;
        private double maxMantleHeight = 1.0625;
        private double probeDistance = 0.5;
        private int reachTicks = 6;
        private double crestStart = 0.55;
        private double recoverDistance = 0.75;
        private int riseTicks = 10;
        private int crestTicks = 6;
        private int recoverTicks = 8;
        private int maxTicks = 40;
        private float minConfidence = 0.5f;
        private float reachPitch = 35.0f;
        private float risePitch = 80.0f;
        private float crestPitch = 20.0f;
        private double lipInset = 0.06;
        private double plantDepth = 0.25;

        private Builder() {
        }

        public Builder heights(double min, double max) {
            this.minMantleHeight = min;
            this.maxMantleHeight = max;
            return this;
        }

        public Builder probeDistance(double blocks) {
            this.probeDistance = blocks;
            return this;
        }

        public Builder reachTicks(int ticks) {
            this.reachTicks = ticks;
            return this;
        }

        public Builder crestStart(double share) {
            this.crestStart = share;
            return this;
        }

        public Builder recoverDistance(double blocks) {
            this.recoverDistance = blocks;
            return this;
        }

        /** Ticks for the rise, the crest and the walk onto the top. */
        public Builder timing(int rise, int crest, int recover) {
            this.riseTicks = rise;
            this.crestTicks = crest;
            this.recoverTicks = recover;
            return this;
        }

        public Builder maxTicks(int ticks) {
            this.maxTicks = ticks;
            return this;
        }

        public Builder minConfidence(float confidence) {
            this.minConfidence = confidence;
            return this;
        }

        public Builder pitches(float reach, float rise, float crest) {
            this.reachPitch = reach;
            this.risePitch = rise;
            this.crestPitch = crest;
            return this;
        }

        public Builder paws(double lipInset, double plantDepth) {
            this.lipInset = lipInset;
            this.plantDepth = plantDepth;
            return this;
        }

        public MantleProfile build() {
            return new MantleProfile(this);
        }
    }
}
