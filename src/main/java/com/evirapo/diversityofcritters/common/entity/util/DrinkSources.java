package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;

import javax.annotation.Nullable;

/** What a critter can drink from out in the world (bowls are handled apart). */
public final class DrinkSources {

    private DrinkSources() {
    }

    /** Water, or a block tagged {@code diversityofcritters:drinkable} such as the waterhole. */
    public static boolean isDrinkable(LevelReader level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER) || level.getBlockState(pos).is(DoCTags.Blocks.DRINKABLE);
    }

    /**
     * The nearest {@code drinkable}-tagged block in a box around {@code origin}, or {@code null}.
     *
     * <p>Random sampling finds a lake or a river, but a one-block waterhole is a needle in a
     * haystack: 30 samples over a 24 × 24 area hit one column about one time in twenty. This
     * scan only reads block states (no fluids, no paths) and only runs for a thirsty animal whose
     * sampling came back empty.
     */
    @Nullable
    public static BlockPos nearestDrinkableBlock(LevelReader level, BlockPos origin, int radius, int vertical) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -vertical, -radius),
                origin.offset(radius, vertical, radius))) {
            if (!level.getBlockState(pos).is(DoCTags.Blocks.DRINKABLE)) continue;
            double d = origin.distSqr(pos);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = pos.immutable();
            }
        }
        return best;
    }
}
