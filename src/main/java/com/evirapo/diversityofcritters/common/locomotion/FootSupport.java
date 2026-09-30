package com.evirapo.diversityofcritters.common.locomotion;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;

/**
 * Whether a critter at the edge of a drop still stands on its feet. Vanilla holds a body up while
 * any corner of its collision box rests on a block, but a critter's feet stand in a small patch in
 * the middle of its box: at an edge it would hang in the air on nothing. With {@link #FALLS_WITH}
 * or more feet over the drop, the body slides off toward it.
 *
 * <p>Server side, from the feet's rest positions (the legs as modelled, turned with the body and
 * scaled with its growth), not from the pose: the pose only exists on the client. One per critter;
 * allocation free.
 */
public final class FootSupport {

    /** Ground this far under a foot still holds it: a slab or stair step below is within a leg's bend. */
    public static final double REACH_DOWN = 0.5 + 1.0 / 64.0;
    /** Feet with nothing under them at which the body slides off. One hanging over is fine. */
    public static final int FALLS_WITH = 2;
    /** The body slides off at least this fast, blocks per tick. */
    public static final double SLIDE_SPEED = 0.08;
    /** How far inside the collision box's edge its outline is read. */
    static final double RIM_INSET = 1.0e-3;
    /** Ground reaching this far over the feet's Y still counts as under them (rounding). */
    static final double ON_TOP = 1.0e-3;

    /**
     * Where a species' feet stand at rest, adult size, blocks from the body's centre: to its left
     * and ahead of it. Read from the model's legs: 32 model units to a block, model +X is the
     * body's left and model -Z ahead of it.
     */
    public static final class Layout {
        /** Civet: legs at x ±2.25 (front left at -2), z ±6.5 of the root, at the root's centre. */
        public static final Layout CIVET = new Layout(
                new double[]{-2.0 / 32.0, 2.25 / 32.0, -2.25 / 32.0, 2.25 / 32.0},
                new double[]{6.5 / 32.0, 6.5 / 32.0, -6.5 / 32.0, -6.5 / 32.0});
        /** Squirrel: front legs at x ±3.5, hind at ±4; the root sits 0.35 back, front 5 ahead, hind 2 behind. */
        public static final Layout SQUIRREL = new Layout(
                new double[]{3.5 / 32.0, -3.5 / 32.0, 4.0 / 32.0, -4.0 / 32.0},
                new double[]{5.0 / 32.0, 5.0 / 32.0, -2.0 / 32.0, -2.0 / 32.0});

        final double[] left;
        final double[] ahead;

        public Layout(double[] left, double[] ahead) {
            if (left.length != ahead.length || left.length == 0) {
                throw new IllegalArgumentException("Bad foot layout: " + left.length + " by " + ahead.length);
            }
            this.left = left.clone();
            this.ahead = ahead.clone();
        }

        public int feet() {
            return this.left.length;
        }
    }

    /** The world as the check sees it. Tests supply a synthetic one. */
    interface World {
        /** Same contract as {@link BlockSpans#read}. */
        boolean span(int x, int y, int z, double inX, double inZ, double[] span);
    }

    /** Outline points of the collision box, as multiples of its half width: corners and mid sides. */
    private static final double[][] RIM = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private final Layout layout;
    private final double[] span = new double[2];
    private final BlockWorld blocks = new BlockWorld();

    /** Per foot, from the last check: whether it has ground under it. */
    public final boolean[] supported;
    /** Feet with nothing under them, from the last check. */
    public int unsupported;
    /** Which way the body slides off, a horizontal unit vector; zero when it holds. */
    public double slideX;
    public double slideZ;

    public FootSupport(Layout layout) {
        this.layout = layout;
        this.supported = new boolean[layout.feet()];
    }

    /**
     * @param x          the body's position (centre of its feet)
     * @param yawDeg     the body's yaw
     * @param growth     how big it is next to an adult
     * @param halfWidth  half its collision box's width
     * @return whether the body has to slide off; {@link #slideX}/{@link #slideZ} say which way
     */
    public boolean check(BlockGetter level, double x, double y, double z, float yawDeg, float growth,
                         float halfWidth) {
        final BlockWorld world = this.blocks.with(level);
        try {
            return this.check(world, x, y, z, yawDeg, growth, halfWidth);
        } finally {
            world.with(null);
        }
    }

    boolean check(World world, double x, double y, double z, float yawDeg, float growth, float halfWidth) {
        // Yaw 0 faces +Z: ahead is (-sin, cos), left is (cos, sin).
        final double yaw = Math.toRadians(yawDeg);
        final double aheadX = -Math.sin(yaw);
        final double aheadZ = Math.cos(yaw);
        final double leftX = aheadZ;
        final double leftZ = -aheadX;

        // Where the ground holding the body is, relative to its centre: the supported feet and outline.
        double heldX = 0.0;
        double heldZ = 0.0;
        int held = 0;
        this.unsupported = 0;
        for (int i = 0; i < this.supported.length; i++) {
            final double offX = (leftX * this.layout.left[i] + aheadX * this.layout.ahead[i]) * growth;
            final double offZ = (leftZ * this.layout.left[i] + aheadZ * this.layout.ahead[i]) * growth;
            final boolean on = hasGround(world, this.span, x + offX, y, z + offZ);
            this.supported[i] = on;
            if (on) {
                heldX += offX;
                heldZ += offZ;
                held++;
            } else {
                this.unsupported++;
            }
        }

        this.slideX = 0.0;
        this.slideZ = 0.0;
        if (this.unsupported < FALLS_WITH) {
            return false;
        }
        final double rim = halfWidth - RIM_INSET;
        for (double[] p : RIM) {
            final double offX = p[0] * rim;
            final double offZ = p[1] * rim;
            if (hasGround(world, this.span, x + offX, y, z + offZ)) {
                heldX += offX;
                heldZ += offZ;
                held++;
            }
        }
        final double length = Math.sqrt(heldX * heldX + heldZ * heldZ);
        if (held == 0 || length < 1.0e-6) {
            // Held evenly all round (a post under the middle), or by nothing the check can see.
            return false;
        }
        // Away from what holds it.
        this.slideX = -heldX / length;
        this.slideZ = -heldZ / length;
        return true;
    }

    /** Whether there is ground at (x, z) no higher than the feet and at most {@link #REACH_DOWN} below them. */
    static boolean hasGround(World world, double[] span, double x, double feetY, double z) {
        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);
        final double inX = x - blockX;
        final double inZ = z - blockZ;
        final double lowest = feetY - REACH_DOWN;
        for (int y = Mth.floor(feetY + ON_TOP); y >= Mth.floor(lowest); y--) {
            if (!world.span(blockX, y, blockZ, inX, inZ, span)) {
                continue;
            }
            final double top = y + span[1];
            if (top <= feetY + ON_TOP) {
                return top >= lowest;
            }
        }
        return false;
    }

    /** {@link World} over real blocks. */
    static final class BlockWorld implements World {
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private BlockGetter level;

        BlockWorld with(BlockGetter level) {
            this.level = level;
            return this;
        }

        @Override
        public boolean span(int x, int y, int z, double inX, double inZ, double[] span) {
            return BlockSpans.read(this.level, this.cursor, x, y, z, inX, inZ, span);
        }
    }
}
