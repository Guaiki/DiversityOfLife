package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Identical to CivetNavigation but typed to SquirrelEntity.
 * Reuses CivetNodeEvaluator as-is (it only depends on the climbable tag,
 * not on the entity class), so climb pathing behaves exactly like the civet.
 */
public class SquirrelNavigation extends WallClimberNavigation {

    private final SquirrelEntity squirrel;

    public SquirrelNavigation(Mob mob, Level level) {
        super(mob, level);
        this.squirrel = (SquirrelEntity) mob;
    }

    @Override
    protected PathFinder createPathFinder(int pMaxVisitedNodes) {
        this.nodeEvaluator = new CivetNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
    }

    @Override
    protected Path createPath(Set<BlockPos> pTargets, int pRegionOffset,
                              boolean pOffsetUpward, int pAccuracy, float pFollowRange) {
        for (BlockPos target : pTargets) {
            if (!this.level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) return null;
        }
        BlockPos self = this.mob.blockPosition();
        if (!this.level.hasChunk(self.getX() >> 4, self.getZ() >> 4)) return null;

        return super.createPath(pTargets, pRegionOffset, pOffsetUpward, pAccuracy, pFollowRange);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.squirrel.isClimbing()
                || this.squirrel.isGliding()
                || this.mob.onGround()
                || this.isInLiquid()
                || this.mob.isPassenger();
    }

    @Override
    protected double getGroundY(Vec3 pVec) {
        if (this.squirrel.isClimbing() || this.squirrel.isGliding()) return pVec.y;
        return super.getGroundY(pVec);
    }

    @Override
    protected Vec3 getTempMobPos() {
        if (this.squirrel.isClimbing() || this.squirrel.isGliding()) return this.mob.position();
        return super.getTempMobPos();
    }
}
