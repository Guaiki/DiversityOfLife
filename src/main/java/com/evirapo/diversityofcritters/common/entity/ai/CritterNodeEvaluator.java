package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class CritterNodeEvaluator extends WalkNodeEvaluator {

    private static final float CLIMB_COST_MALUS = 0.5F;

    /** Sin escalada solo quedan los vecinos de andar de {@link WalkNodeEvaluator}. */
    private final boolean climbs;

    public CritterNodeEvaluator(boolean climbs) {
        this.climbs = climbs;
    }

    @Override
    public int getNeighbors(Node[] pOutputArray, Node pNode) {
        int count = super.getNeighbors(pOutputArray, pNode);
        if (!this.climbs) return count;

        BlockPos here = new BlockPos(pNode.x, pNode.y, pNode.z);

        // --- A) CLIMB UP: beside wall, space above ---
        if (hasAdjacentClimbable(here) && isOpenSpace(here.above())) {
            Node upNode = getNode(pNode.x, pNode.y + 1, pNode.z);
            if (!upNode.closed) {
                upNode.type = BlockPathTypes.WALKABLE;
                upNode.costMalus = CLIMB_COST_MALUS;
                if (count < pOutputArray.length) {
                    pOutputArray[count++] = upNode;
                }
            }
        }

        // --- B) CLIMB DOWN: beside wall, climbable also below (one step) ---
        if (hasAdjacentClimbable(here) && isOpenSpace(here.below())
                && hasAdjacentClimbable(here.below())) {
            Node downNode = getNode(pNode.x, pNode.y - 1, pNode.z);
            if (!downNode.closed) {
                downNode.type = BlockPathTypes.WALKABLE;
                downNode.costMalus = CLIMB_COST_MALUS;
                if (count < pOutputArray.length) {
                    pOutputArray[count++] = downNode;
                }
            }
        }

        // --- C) DESCEND FROM TOP: standing ON TOP of a climbable column ---
        if (isClimbable(here.below()) && !hasAdjacentClimbable(here)) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int faceX = pNode.x + dir.getStepX();
                int faceZ = pNode.z + dir.getStepZ();
                BlockPos facePos      = new BlockPos(faceX, pNode.y - 1, faceZ);
                BlockPos aboveFacePos = new BlockPos(faceX, pNode.y,     faceZ);
                if (!isOpenSpace(facePos) || !isOpenSpace(aboveFacePos)) continue;
                Node stepNode = getNode(faceX, pNode.y - 1, faceZ);
                if (!stepNode.closed) {
                    stepNode.type = BlockPathTypes.WALKABLE;
                    stepNode.costMalus = CLIMB_COST_MALUS;
                    if (count < pOutputArray.length) {
                        pOutputArray[count++] = stepNode;
                    }
                }
            }
        }

        return count;
    }

    private boolean hasAdjacentClimbable(BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (isClimbable(pos.relative(dir))) return true;
        }
        return false;
    }

    private boolean isClimbable(BlockPos pos) {
        return this.level.getBlockState(pos).is(DoCTags.Blocks.CIVET_CLIMBABLE);
    }

    private boolean isOpenSpace(BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = this.level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        return state.isAir() || !state.isSolid();
    }
}
