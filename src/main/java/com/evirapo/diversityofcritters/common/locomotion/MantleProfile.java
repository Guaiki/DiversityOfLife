package com.evirapo.diversityofcritters.common.locomotion;

/**
 * Per-species numbers for climbing a ledge. Heights and distances are in blocks, angles in
 * degrees (nose up positive).
 */
public final class MantleProfile {

    /** Civet: one full block, but not fences or walls. */
    public static final MantleProfile CIVET = builder().build();

    /**
     * Squirrel: shorter legs and hips closer together than the civet's reach about 0.97 blocks
     * rearing up, so even an adult hops the last of a full block.
     */
    public static final MantleProfile SQUIRREL = builder().hop(0.95, 0.5, 3.0).build();

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
    /**
     * Highest lip an adult reaches rearing up on its hind legs, blocks; scaled by growth. Higher
     * lips get a hop: the second part of the rise goes faster, as if pushed off the hind legs.
     */
    public final double reach;
    /** Share of the rise after which the hop kicks in. */
    public final double hopFrom;
    /** How much faster the rise goes during the hop. */
    public final double hopBoost;

    // --- The pose.
    /** Body pitch at the end of each phase; it starts from the previous phase's. */
    public final float reachPitch;
    public final float risePitch;
    public final float crestPitch;
    /** Spine flexion at its most, on top of the crest (see MantleProgress.archDegrees). */
    public final float archDegrees;
    /** Spine extension while rearing up: the head looks up the face, clear of the paws. */
    public final float rearArchDegrees;
    /** How far past the face the front paws grip the lip. */
    public final double lipInset;
    /** How far past the face the front paws plant on the top by the end of the crest. */
    public final double plantDepth;
    /** Grips each front paw takes on the way up, the last one being the lip. */
    public final int gripsPerHand;
    /**
     * First grips on the face, as shares of the lip's height: the higher for the paw that reaches
     * second (the body has reared further by then), the lower for the one that leads.
     */
    public final double firstGripHigh;
    public final double firstGripLow;
    /** How far a paw leaves the face in the middle of a swing between grips. */
    public final double swingOut;
    /** Steps each hind leg takes toward the wall while the body rears up. */
    public final int hindSteps;
    /** How far each of those steps goes, and how high the foot lifts on it, blocks. */
    public final double hindStepLength;
    public final double hindStepLift;
    /** Where the hind feet scramble onto the face over the crest, as a share of the lip's height. */
    public final double hindFaceGrip;

    private MantleProfile(Builder b) {
        if (!(b.minMantleHeight > 0.0 && b.maxMantleHeight >= b.minMantleHeight && b.probeDistance > 0.0)) {
            throw new IllegalArgumentException("Bad mantle heights: min " + b.minMantleHeight
                    + ", max " + b.maxMantleHeight + ", probe " + b.probeDistance);
        }
        if (!(b.reach > 0.0 && b.hopFrom >= 0.0 && b.hopFrom < 1.0 && b.hopBoost >= 1.0)) {
            throw new IllegalArgumentException("Bad mantle hop: reach " + b.reach + ", from " + b.hopFrom
                    + ", boost " + b.hopBoost);
        }
        if (!(b.hindSteps >= 0 && b.hindStepLength >= 0.0 && b.hindStepLift >= 0.0
                && b.hindFaceGrip >= 0.0 && b.hindFaceGrip <= 1.0)) {
            throw new IllegalArgumentException("Bad mantle hind steps: " + b.hindSteps + " of " + b.hindStepLength
                    + " lifting " + b.hindStepLift + ", face grip " + b.hindFaceGrip);
        }
        if (!(b.gripsPerHand > 0 && b.firstGripLow >= 0.0 && b.firstGripHigh <= 1.0 && b.swingOut >= 0.0)) {
            throw new IllegalArgumentException("Bad mantle grips: " + b.gripsPerHand + " per paw, first at "
                    + b.firstGripHigh + "/" + b.firstGripLow + ", swing out " + b.swingOut);
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
        this.reach = b.reach;
        this.hopFrom = b.hopFrom;
        this.hopBoost = b.hopBoost;
        this.reachPitch = b.reachPitch;
        this.risePitch = b.risePitch;
        this.crestPitch = b.crestPitch;
        this.archDegrees = b.archDegrees;
        this.rearArchDegrees = b.rearArchDegrees;
        this.lipInset = b.lipInset;
        this.plantDepth = b.plantDepth;
        this.gripsPerHand = b.gripsPerHand;
        this.firstGripHigh = b.firstGripHigh;
        this.firstGripLow = b.firstGripLow;
        this.swingOut = b.swingOut;
        this.hindSteps = b.hindSteps;
        this.hindStepLength = b.hindStepLength;
        this.hindStepLift = b.hindStepLift;
        this.hindFaceGrip = b.hindFaceGrip;
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
        private double reach = 1.09;
        private double hopFrom = 0.5;
        private double hopBoost = 3.0;
        private float reachPitch = 50.0f;
        private float risePitch = 80.0f;
        private float crestPitch = 20.0f;
        private float archDegrees = 25.0f;
        // Rearing to 50 degrees before the paws climb and extending the spine lifts the head out
        // of the way of a front leg reaching past it (see MantleClearanceTest).
        private float rearArchDegrees = 70.0f;
        private double lipInset = 0.06;
        private double plantDepth = 0.25;
        private int gripsPerHand = 1;
        private double firstGripHigh = 0.75;
        private double firstGripLow = 0.5;
        private double swingOut = 0.12;
        private int hindSteps = 1;
        private double hindStepLength = 0.06;
        private double hindStepLift = 0.06;
        private double hindFaceGrip = 0.45;

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

        /**
         * @param reach    highest lip an adult reaches without a hop, blocks
         * @param hopFrom  share of the rise after which a hop speeds it up
         * @param hopBoost how much faster
         */
        public Builder hop(double reach, double hopFrom, double hopBoost) {
            this.reach = reach;
            this.hopFrom = hopFrom;
            this.hopBoost = hopBoost;
            return this;
        }

        public Builder pitches(float reach, float rise, float crest) {
            this.reachPitch = reach;
            this.risePitch = rise;
            this.crestPitch = crest;
            return this;
        }

        /**
         * @param crest flexion on top of the crest, degrees
         * @param rear  extension while rearing up, degrees
         */
        public Builder arch(float crest, float rear) {
            this.archDegrees = crest;
            this.rearArchDegrees = rear;
            return this;
        }

        public Builder paws(double lipInset, double plantDepth) {
            this.lipInset = lipInset;
            this.plantDepth = plantDepth;
            return this;
        }

        /**
         * @param perHand   grips each front paw takes, the lip being the last
         * @param firstHigh first grip of the paw that reaches second, as a share of the lip's height
         * @param firstLow  first grip of the leading paw
         * @param swingOut  how far a paw leaves the face mid-swing, blocks
         */
        public Builder grips(int perHand, double firstHigh, double firstLow, double swingOut) {
            this.gripsPerHand = perHand;
            this.firstGripHigh = firstHigh;
            this.firstGripLow = firstLow;
            this.swingOut = swingOut;
            return this;
        }

        /**
         * @param steps    steps each hind leg takes toward the wall while rearing up
         * @param length   how far each goes, blocks
         * @param lift     how high the foot lifts on it, blocks
         * @param faceGrip where the hind feet scramble onto the face, as a share of the lip's height
         */
        public Builder hindSteps(int steps, double length, double lift, double faceGrip) {
            this.hindSteps = steps;
            this.hindStepLength = length;
            this.hindStepLift = lift;
            this.hindFaceGrip = faceGrip;
            return this;
        }

        public MantleProfile build() {
            return new MantleProfile(this);
        }
    }
}
