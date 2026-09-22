package com.evirapo.diversityofcritters.client.locomotion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@link SurfaceQuery} over real block collision shapes, so slabs, stairs, paths, farmland and
 * snow layers read at their true height rather than as whole blocks.
 *
 * <p>Scans each column downward from just above the foot. A shape whose bottom sits clearly above
 * the foot is an overhang (a top slab, a low ceiling) and is skipped: the foot cannot be standing
 * on something it would have to pass through.
 *
 * <p>One instance is reused for every query; not thread safe.
 */
public final class BlockSurfaceQuery implements SurfaceQuery {

    /** How far above the foot the scan starts: enough for a full-block step. */
    public static final double SCAN_ABOVE = 1.0;
    /** How many blocks below the start the scan gives up. */
    public static final int SCAN_DEPTH = 16;
    /** A shape starting more than this above the foot is overhead, not underfoot. */
    public static final double OVERHANG_CLEARANCE = 0.25;

    /** Solid extent of one block at one point of its column. */
    @FunctionalInterface
    interface ShapeReader {
        /**
         * Writes the collision's {bottom, top} at (inX, inZ) inside block (x, y, z), relative to the
         * block's own Y, into {@code span}.
         *
         * @return false if nothing solid is at that point
         */
        boolean read(int x, int y, int z, double inX, double inZ, double[] span);
    }

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final double[] span = new double[2];
    private final ShapeReader blocks = this::readBlock;
    private BlockGetter level;

    public BlockSurfaceQuery with(BlockGetter level) {
        this.level = level;
        return this;
    }

    @Override
    public double surfaceY(double x, double z, double footY) {
        return scan(this.blocks, this.span, x, z, footY);
    }

    static double scan(ShapeReader reader, double[] span, double x, double z, double footY) {
        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);
        final double inX = x - blockX;
        final double inZ = z - blockZ;

        final int startY = Mth.floor(footY + SCAN_ABOVE);
        for (int y = startY; y >= startY - SCAN_DEPTH; y--) {
            if (reader.read(blockX, y, blockZ, inX, inZ, span) && y + span[0] <= footY + OVERHANG_CLEARANCE) {
                return y + span[1];
            }
        }
        return startY - SCAN_DEPTH;
    }

    private boolean readBlock(int x, int y, int z, double inX, double inZ, double[] span) {
        this.cursor.set(x, y, z);
        final BlockState state = this.level.getBlockState(this.cursor);
        if (state.isAir()) {
            return false;
        }
        final VoxelShape shape = state.getCollisionShape(this.level, this.cursor);
        if (shape.isEmpty()) {
            return false;
        }
        // For the Y axis, VoxelShape takes the position within the block as (z, x).
        final double bottom = shape.min(Direction.Axis.Y, inZ, inX);
        final double top = shape.max(Direction.Axis.Y, inZ, inX);
        if (!Double.isFinite(bottom) || !Double.isFinite(top)) {
            return false;
        }
        span[0] = bottom;
        span[1] = top;
        return true;
    }
}
