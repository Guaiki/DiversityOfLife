package com.evirapo.diversityofcritters.common.locomotion;

import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Looks ahead of a body for a ledge it could mantle: a wall between {@link MantleProfile#minMantleHeight}
 * and {@link MantleProfile#maxMantleHeight} high (a slab up to a full block), with room on top and
 * level where the body walks onto it (stairs are not ledges).
 *
 * <p>Runs on both sides: the server decides whether to start a mantle, the client shapes the pose
 * around the lip. The same code on both is half of keeping them in agreement.
 *
 * <p>The probe walks forward along the horizontal axis the body faces most, in steps of
 * {@link #STEP}, reading real collision shapes. The first column that rises at least the minimum
 * height above the feet is the wall; its face is then refined by bisection. Allocation free.
 */
public final class LedgeSampler {

    /** Forward step of the probe, in blocks. */
    public static final double STEP = 0.125;
    /** Rises up to this (carpet, one snow layer) count as flat ground. */
    public static final double FLAT = 0.0625;
    /** Headroom is not measured past this. */
    public static final double MAX_HEADROOM = 2.0;
    /** Side lanes run at this fraction of the half width: roughly where the front paws land. */
    public static final double LANE_SPREAD = 0.6;
    /** A side lane agrees with the centre when its top is within this of the lip. */
    public static final double LIP_TOLERANCE = 0.0625;
    /** How far past the face the lip is read, so the reading lands inside the wall. */
    static final double INSIDE = 1.0 / 32.0;
    static final int FACE_ITERATIONS = 8;

    private static final double COS_45 = Math.sqrt(0.5);

    private LedgeSampler() {
    }

    /** The world as the probe sees it. Tests supply a synthetic one. */
    interface World {
        /** Same contract as {@link BlockSpans#read}. */
        boolean span(int x, int y, int z, double inX, double inZ, double[] span);

        /** The block is something the species climbs as a wall. */
        boolean isClimbable(int x, int y, int z);

        /** Nothing should step into this block: liquid or fire. */
        boolean isHazard(int x, int y, int z);
    }

    /**
     * @param feet          the body's position (centre of its feet)
     * @param headingYawDeg the body's yaw
     * @param bodyHalfWidth half the body's collision width
     * @param bodyHeight    the body's collision height
     */
    public static void sample(BlockGetter level, Vec3 feet, float headingYawDeg,
                              float bodyHalfWidth, float bodyHeight, MantleProfile profile,
                              LedgeSample out) {
        final BlockWorld blocks = out.blocks.with(level);
        try {
            sample(blocks, feet.x, feet.y, feet.z, headingYawDeg, bodyHalfWidth, bodyHeight, profile, out);
        } finally {
            blocks.with(null);
        }
    }

    static void sample(World world, double feetX, double feetY, double feetZ, float headingYawDeg,
                       float bodyHalfWidth, float bodyHeight, MantleProfile profile, LedgeSample out) {
        out.clear();
        final double[] span = out.span;

        final Direction facing = Direction.fromYRot(headingYawDeg);
        final int ax = facing.getStepX();
        final int az = facing.getStepZ();
        out.facing = facing;

        // Yaw 0 faces +Z; the heading is (-sin, cos). Squareness is 1 head on, 0 at 45 degrees.
        final double yaw = Math.toRadians(headingYawDeg);
        final double alignment = Math.abs(-Math.sin(yaw) * ax + Math.cos(yaw) * az);
        final double squareness = Mth.clamp((alignment - COS_45) / (1.0 - COS_45), 0.0, 1.0);

        // March forward until something rises high enough to be a wall.
        final int steps = (int) Math.floor((bodyHalfWidth + profile.probeDistance) / STEP + 1.0e-9);
        double clear = 0.0;
        double hit = -1.0;
        boolean stepped = false;
        for (int i = 1; i <= steps; i++) {
            final double d = i * STEP;
            final double rise = obstacleTop(world, span, feetX + ax * d, feetZ + az * d,
                    feetY, bodyHeight, profile) - feetY;
            if (rise >= profile.minMantleHeight) {
                hit = d;
                break;
            }
            if (rise > FLAT) {
                stepped = true;
            }
            clear = d;
        }
        if (hit < 0.0) {
            out.reason = stepped ? LedgeSample.Reason.STEPPED : LedgeSample.Reason.NO_WALL;
            return;
        }

        // Pin the face between the last clear step and the hit.
        for (int i = 0; i < FACE_ITERATIONS; i++) {
            final double mid = (clear + hit) * 0.5;
            final double rise = obstacleTop(world, span, feetX + ax * mid, feetZ + az * mid,
                    feetY, bodyHeight, profile) - feetY;
            if (rise >= profile.minMantleHeight) {
                hit = mid;
            } else {
                clear = mid;
            }
        }
        out.wallCoord = ax != 0 ? feetX + ax * hit : feetZ + az * hit;

        // The lip, read just inside the face.
        final double lipDepth = hit + INSIDE;
        final double lipPointX = feetX + ax * lipDepth;
        final double lipPointZ = feetZ + az * lipDepth;
        out.lipY = obstacleTop(world, span, lipPointX, lipPointZ, feetY, bodyHeight, profile);
        out.lipBlockY = (int) span[2];
        out.lipX = Mth.floor(lipPointX);
        out.lipZ = Mth.floor(lipPointZ);
        out.lipHeight = out.lipY - feetY;

        final double landDepth = hit + bodyHalfWidth;
        out.topY = surfaceBelow(world, span, feetX + ax * landDepth, feetZ + az * landDepth, out.lipY + 0.5);

        // Side lanes: does the lip run under the whole front of the body?
        final double laneOffset = bodyHalfWidth * LANE_SPREAD;
        int agreeing = 1;
        for (int side = -1; side <= 1; side += 2) {
            final double laneX = lipPointX - az * side * laneOffset;
            final double laneZ = lipPointZ + ax * side * laneOffset;
            final double laneTop = obstacleTop(world, span, laneX, laneZ, feetY, bodyHeight, profile);
            if (Math.abs(laneTop - out.lipY) <= LIP_TOLERANCE) {
                agreeing++;
            }
        }
        out.confidence = (float) (squareness * agreeing / 3.0);

        // Headroom over the lip and where the body lands, across its width.
        double headroom = MAX_HEADROOM;
        for (int depth = 0; depth < 2; depth++) {
            final double d = depth == 0 ? lipDepth : landDepth;
            for (int side = -1; side <= 1; side++) {
                final double x = feetX + ax * d - az * side * laneOffset;
                final double z = feetZ + az * d + ax * side * laneOffset;
                headroom = Math.min(headroom, clearance(world, span, x, z, out.lipY));
            }
        }
        out.headroom = headroom;

        // Stairs: the top rises again where the body would walk onto it. A slab is a ledge; the
        // low half of a stair block is a step with the next one right behind it.
        final boolean risesAgain = risesAgain(world, span, feetX, feetZ, ax, az, hit, out.lipY,
                bodyHeight, profile);

        // Climbable wall still ahead once the body has risen as far as it can mantle.
        final int aboveReach = Mth.floor(Math.min(out.lipY, feetY + profile.maxMantleHeight) + 1.0e-3);
        out.continuesUp = world.isClimbable(out.lipX, aboveReach, out.lipZ);

        if (stepped) {
            out.reason = LedgeSample.Reason.STEPPED;
        } else if (out.lipHeight > profile.maxMantleHeight) {
            out.reason = LedgeSample.Reason.TOO_HIGH;
        } else if (headroom < bodyHeight) {
            out.reason = LedgeSample.Reason.NO_HEADROOM;
        } else if (risesAgain) {
            // After headroom: a low ceiling over the top also reads as rising again.
            out.reason = LedgeSample.Reason.STEPPED;
        } else if (world.isHazard(out.lipX, out.lipBlockY + 1, out.lipZ)) {
            out.reason = LedgeSample.Reason.HAZARD;
        } else {
            out.reason = LedgeSample.Reason.NONE;
            out.found = true;
        }
    }

    /**
     * Top of whatever the body would walk into at (x, z): the first solid overlapping its height
     * band above the feet, plus everything stacked on it without a gap. {@code feetY} when the way
     * is clear. Leaves the block Y the top ends in at {@code span[2]}.
     */
    static double obstacleTop(World world, double[] span, double x, double z,
                              double feetY, float bodyHeight, MantleProfile profile) {
        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);
        final double inX = x - blockX;
        final double inZ = z - blockZ;

        final int first = Mth.floor(feetY);
        final int last = Mth.floor(feetY + profile.maxMantleHeight + bodyHeight) + 1;
        double top = feetY;
        int topBlock = first - 1;
        boolean inWall = false;
        for (int y = first; y <= last; y++) {
            if (!world.span(blockX, y, blockZ, inX, inZ, span)) {
                if (inWall) {
                    break;
                }
                continue;
            }
            final double bottom = y + span[0];
            final double spanTop = y + span[1];
            if (!inWall) {
                // Ground under the feet, or something the body passes beneath.
                if (spanTop <= feetY + 1.0e-6 || bottom >= feetY + bodyHeight) {
                    continue;
                }
                inWall = true;
            } else if (bottom > top + 1.0e-6) {
                break;
            }
            if (spanTop > top) {
                top = spanTop;
                topBlock = y;
            }
        }
        span[2] = topBlock;
        return top;
    }

    /**
     * Whether something rises more than {@link #FLAT} over the lip within
     * {@link MantleProfile#recoverDistance} past the face: where the front of the body goes as it
     * walks onto the top, so it would run into it before the mantle is over.
     */
    static boolean risesAgain(World world, double[] span, double feetX, double feetZ, int ax, int az,
                              double face, double lipY, float bodyHeight, MantleProfile profile) {
        final double end = profile.recoverDistance - INSIDE;
        for (int i = 1; ; i++) {
            final double past = Math.min(i * STEP, end);
            final double d = face + past;
            if (obstacleTop(world, span, feetX + ax * d, feetZ + az * d, lipY, bodyHeight, profile) - lipY > FLAT) {
                return true;
            }
            if (past >= end) {
                return false;
            }
        }
    }

    /** Highest surface at (x, z) that starts at or below {@code fromY}. */
    static double surfaceBelow(World world, double[] span, double x, double z, double fromY) {
        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);
        final double inX = x - blockX;
        final double inZ = z - blockZ;

        final int start = Mth.floor(fromY);
        for (int y = start; y >= start - 3; y--) {
            if (world.span(blockX, y, blockZ, inX, inZ, span) && y + span[0] <= fromY) {
                return y + span[1];
            }
        }
        return start - 3;
    }

    /**
     * Free height at (x, z) above the floor there, the floor being {@code floorY} raised by anything
     * sitting on it. Capped at {@link #MAX_HEADROOM}.
     */
    static double clearance(World world, double[] span, double x, double z, double floorY) {
        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);
        final double inX = x - blockX;
        final double inZ = z - blockZ;

        double floor = floorY;
        final int last = Mth.floor(floorY + MAX_HEADROOM) + 1;
        for (int y = Mth.floor(floorY); y <= last; y++) {
            if (!world.span(blockX, y, blockZ, inX, inZ, span)) {
                continue;
            }
            final double bottom = y + span[0];
            final double top = y + span[1];
            if (top <= floor + 1.0e-6) {
                continue;
            }
            if (bottom <= floor + FLAT) {
                floor = top;
                continue;
            }
            return Math.min(bottom - floor, MAX_HEADROOM);
        }
        return MAX_HEADROOM;
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
        public boolean isClimbable(int x, int y, int z) {
            // Every critter that climbs uses the civet's tag.
            return this.state(x, y, z).is(DoCTags.Blocks.CIVET_CLIMBABLE);
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            final BlockState state = this.state(x, y, z);
            return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE);
        }

        private BlockState state(int x, int y, int z) {
            return this.level.getBlockState(this.cursor.set(x, y, z));
        }
    }
}
