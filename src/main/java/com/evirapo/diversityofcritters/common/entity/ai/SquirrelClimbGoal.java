package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * HANG enrichment behavior (same as CivetClimbGoal, typed to SquirrelEntity).
 *
 * KEY DIFFERENCE vs the civet: when the squirrel finishes hanging and releases,
 * it attempts to GLIDE (scenario 1 of the gliding system) instead of dropping.
 * tryStartGlide() internally checks air clearance, so short hangs near the
 * ground still resolve as a plain release.
 */
public class SquirrelClimbGoal extends Goal {
    private final SquirrelEntity squirrel;

    private int hangTicksRemaining = 0;
    private int cooldown = 0;

    private static final int COOLDOWN_MIN = 600;
    private static final int COOLDOWN_MAX = 2400;
    private static final int HANG_DURATION_MIN = 60;
    private static final int HANG_DURATION_MAX = 160;

    public SquirrelClimbGoal(SquirrelEntity squirrel) {
        this.squirrel = squirrel;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (squirrel.level().isClientSide() || squirrel.isNewborn()) return false;
        if (squirrel.isSleeping() || squirrel.isOrderedToSit()) return false;
        if (this.cooldown-- > 0) return false;

        return squirrel.isClimbingUp() && squirrel.getRandom().nextInt(60) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (squirrel.isSleeping() || squirrel.isOrderedToSit()) return false;
        return this.hangTicksRemaining > 0;
    }

    @Override
    public void start() {
        this.hangTicksRemaining = HANG_DURATION_MIN
                + squirrel.getRandom().nextInt(HANG_DURATION_MAX - HANG_DURATION_MIN);
        squirrel.setClimbState(SquirrelEntity.CLIMB_HANG);
        squirrel.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (squirrel.getClimbState() != SquirrelEntity.CLIMB_HANG) {
            squirrel.setClimbState(SquirrelEntity.CLIMB_HANG);
        }
        squirrel.setDeltaMovement(0, 0, 0);
        this.hangTicksRemaining--;
    }

    @Override
    public void stop() {
        if (squirrel.isHanging()) {
            squirrel.setClimbState(SquirrelEntity.CLIMB_NONE);
            // SCENARIO 1 of the glide: hang finished → release into a glide.
            // If there isn't enough air below, this is a no-op and the
            // squirrel simply drops (no fall damage anyway).
            squirrel.tryStartGlide();
        }
        squirrel.setEnrichment(squirrel.maxEnrichment());
        this.cooldown = COOLDOWN_MIN + squirrel.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN);
    }
}
