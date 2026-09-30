package com.evirapo.diversityofcritters.common.locomotion;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;

/**
 * Whether a critter at the edge of a drop still stands on its feet. Vanilla holds a body up while
 * any corner of its collision box rests on a block, but a critter's feet stand in a small patch in
 * the middle of its box: at an edge it would hang in the air on nothing. With {@link #FALLS_WITH}
 * or more feet over the drop, the body slides off toward it.
 *
 * <p>A foot is held by ground at most {@link #REACH_DOWN} below the body, but the body only pitches:
 * a front pair half a block lower than the hind one is a stair, and the legs reach it. Across a
 * pair it cannot lean that far, so the foot more than {@link #ROLL_DROP} lower than its partner
 * hangs (the side of a slab). And a body whose feet all reach down to ground below its own level,
 * with none at it, is only held by its box: every foot hangs.
 *
 * <p>Neither applies on stairs: standing across steps, sideways or on a corner too, is the terrain
 * rig's to pose. There only a foot with no ground within reach hangs.
 *
 * <p>Server side, from the feet's rest positions (the legs as modelled, turned with the body and
 * scaled with its growth), not from the pose: the pose only exists on the client. One per critter;
 * allocation free.
 */
public final class FootSupport {

    /** Ground this far under a foot still holds it: a slab or stair step below is within a leg's bend. */
    public static final double REACH_DOWN = 0.5 + 1.0 / 64.0;
    /**
     * Most a foot's ground may lie below its partner's across the body, or the body's own level:
     * a snow layer or a carpet is fine, the side of a slab is not.
     */
    public static final double ROLL_DROP = 0.25;
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
     * body's left and model -Z ahead of it. Feet come in left/right pairs: 0 and 1, 2 and 3.
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
            if (left.length != ahead.length || left.length == 0 || left.length % 2 != 0) {
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

        /** The block is a stair. */
        boolean isStairs(int x, int y, int z);
    }

    /** Outline points of the collision box, as multiples of its half width: corners and mid sides. */
    private static final double[][] RIM = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private final Layout layout;
    /** Span scratch; slot 2 says whether the last ground found was a stair. */
    private final double[] span = new double[3];
    /** Per foot, this check's ground under it, or NaN. */
    private final double[] ground;
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
        this.ground = new double[layout.feet()];
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

        boolean level = false;
        boolean stairs = false;
        for (int i = 0; i < this.ground.length; i++) {
            final double g = groundBelow(world, this.span, x + this.footX(i, leftX, aheadX, growth), y,
                    z + this.footZ(i, leftZ, aheadZ, growth), REACH_DOWN);
            this.ground[i] = g;
            level |= g >= y - ROLL_DROP;
            stairs |= !Double.isNaN(g) && this.span[2] > 0.0;
        }
        if (!stairs) {
            stairs = !Double.isNaN(groundBelow(world, this.span, x, y, z, REACH_DOWN)) && this.span[2] > 0.0;
        }

        // Where the ground holding the body is, relative to its centre: the supported feet and outline.
        double heldX = 0.0;
        double heldZ = 0.0;
        int held = 0;
        this.unsupported = 0;
        for (int i = 0; i < this.supported.length; i++) {
            final double partner = this.ground[i ^ 1];
            final boolean on = !Double.isNaN(this.ground[i])
                    && (stairs || level && !(partner - this.ground[i] > ROLL_DROP));
            this.supported[i] = on;
            if (on) {
                heldX += this.footX(i, leftX, aheadX, growth);
                heldZ += this.footZ(i, leftZ, aheadZ, growth);
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
            // What holds the box is ground at the body's own level.
            if (!Double.isNaN(groundBelow(world, this.span, x + offX, y, z + offZ, ROLL_DROP))) {
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

    private double footX(int i, double leftX, double aheadX, float growth) {
        return (leftX * this.layout.left[i] + aheadX * this.layout.ahead[i]) * growth;
    }

    private double footZ(int i, double leftZ, double aheadZ, float growth) {
        return (leftZ * this.layout.left[i] + aheadZ * this.layout.ahead[i]) * growth;
    }

    /**
     * The top of the ground at (x, z) no higher than the feet and at most {@code depth} below
     * them, or NaN if there is none. When there is, {@code span[2]} is 1 if it is a stair, else 0.
     */
    static double groundBelow(World world, double[] span, double x, double feetY, double z, double depth) {
        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);
        final double inX = x - blockX;
        final double inZ = z - blockZ;
        final double lowest = feetY - depth;
        for (int y = Mth.floor(feetY + ON_TOP); y >= Mth.floor(lowest); y--) {
            if (!world.span(blockX, y, blockZ, inX, inZ, span)) {
                continue;
            }
            final double top = y + span[1];
            if (top <= feetY + ON_TOP) {
                if (top < lowest) {
                    return Double.NaN;
                }
                span[2] = world.isStairs(blockX, y, blockZ) ? 1.0 : 0.0;
                return top;
            }
        }
        return Double.NaN;
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

        @Override
        public boolean isStairs(int x, int y, int z) {
            return this.level.getBlockState(this.cursor.set(x, y, z)).is(BlockTags.STAIRS);
        }
    }
}
