package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class CritterExploreHeightGoal extends Goal {

    private final DiverseCritter entity;

    private static final int SEARCH_RADIUS = 12;
    private static final int SEARCH_Y_UP   = 6;
    private static final int SEARCH_Y_DOWN = 2;
    private static final int MIN_COLUMN_HEIGHT = 2;
    private static final int MAX_COLUMN_SCAN   = 10;
    private static final int SCAN_ATTEMPTS     = 20;

    private int cooldown = 0;
    private static final int COOLDOWN_MIN = 400;
    private static final int COOLDOWN_MAX = 1000;

    @Nullable private BlockPos climbTarget = null;

    public CritterExploreHeightGoal(DiverseCritter entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (entity.level().isClientSide()) return false;
        if (this.cooldown-- > 0) return false;
        if (entity.getRandom().nextInt(40) != 0) return false;
        if (entity.isNewborn() || entity.isSleeping() || entity.isOrderedToSit()) return false;
        if (entity.isInWaterOrBubble() || entity.isClimbing() || entity.isDoingSpecialActivity()) return false;
        if (!entity.onGround()) return false;

        this.climbTarget = findClimbTarget();
        return this.climbTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.isNewborn() || entity.isSleeping() || entity.isOrderedToSit()) return false;
        if (entity.isInWaterOrBubble()) return false;
        return !entity.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (this.climbTarget != null) {
            entity.getNavigation().moveTo(
                    this.climbTarget.getX() + 0.5D,
                    this.climbTarget.getY(),
                    this.climbTarget.getZ() + 0.5D,
                    1.0D
            );
        }
    }

    @Override
    public void stop() {
        this.climbTarget = null;
        this.cooldown = COOLDOWN_MIN + entity.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN);
    }

    @Nullable
    private BlockPos findClimbTarget() {
        BlockPos origin = entity.blockPosition();
        Level level = entity.level();

        for (int attempt = 0; attempt < SCAN_ATTEMPTS; attempt++) {
            int dx = entity.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int dz = entity.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;

            for (int dy = -SEARCH_Y_DOWN; dy <= SEARCH_Y_UP; dy++) {
                BlockPos check = origin.offset(dx, dy, dz);

                if (!level.getBlockState(check).is(DoCTags.Blocks.CIVET_CLIMBABLE)) continue;
                if (level.getBlockState(check.above()).is(DoCTags.Blocks.CIVET_CLIMBABLE)) continue;

                int height = 0;
                BlockPos.MutableBlockPos scan = check.mutable();
                while (height < MAX_COLUMN_SCAN
                        && level.getBlockState(scan).is(DoCTags.Blocks.CIVET_CLIMBABLE)) {
                    height++;
                    scan.move(0, -1, 0);
                }
                if (height < MIN_COLUMN_HEIGHT) continue;

                BlockPos target = check.above();
                if (!level.getBlockState(target).isAir()) continue;
                if (!level.getBlockState(target.above()).isAir()) continue;

                return target;
            }
        }
        return null;
    }
}
