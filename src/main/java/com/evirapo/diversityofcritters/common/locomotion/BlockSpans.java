package com.evirapo.diversityofcritters.common.locomotion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reads the solid extent of one block's collision shape along a vertical line through it.
 *
 * <p>Shared by the terrain probe (client) and the ledge probe (both sides), so both see slabs,
 * stairs, fences and snow layers at their true collision height.
 */
public final class BlockSpans {

    private BlockSpans() {
    }

    /**
     * Writes the collision's {bottom, top} at (inX, inZ) inside block (x, y, z), relative to the
     * block's own Y, into {@code span}.
     *
     * @param cursor scratch position, overwritten
     * @return false if nothing solid is at that point
     */
    public static boolean read(BlockGetter level, BlockPos.MutableBlockPos cursor,
                               int x, int y, int z, double inX, double inZ, double[] span) {
        cursor.set(x, y, z);
        final BlockState state = level.getBlockState(cursor);
        if (state.isAir()) {
            return false;
        }
        final VoxelShape shape = state.getCollisionShape(level, cursor);
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
