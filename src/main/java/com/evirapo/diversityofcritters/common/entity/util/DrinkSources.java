package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

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
     * sampling came back empty, at most once every few seconds (the caller throttles it). Of the blocks
     * found, the nearest one {@code usable} accepts (one with somewhere to stand, say).
     */
    @Nullable
    public static BlockPos nearestDrinkableBlock(LevelReader level, BlockPos origin, int radius, int vertical,
                                                 Predicate<BlockPos> usable) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -vertical, -radius),
                origin.offset(radius, vertical, radius))) {
            if (level.getBlockState(pos).is(DoCTags.Blocks.DRINKABLE)) found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(origin::distSqr));
        for (BlockPos pos : found) {
            if (usable.test(pos)) return pos;
        }
        return null;
    }
}
