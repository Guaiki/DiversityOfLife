package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class CritterCleanGoal extends Goal {

    private final DiverseCritter entity;
    private int cleanTimer;
    private static final int CLEAN_DURATION = 80;

    public CritterCleanGoal(DiverseCritter entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (entity.isNewborn()) return false;
        if (entity.isSleeping() || entity.isPreparingSleep() || entity.isAwakeing() ||
                entity.isAttacking() || entity.isOrderedToSit() || entity.isIdleLocked() ||
                entity.isDoingSpecialActivity()) return false;
        if (!entity.hasLowHygiene()) return false;
        return entity.getRandom().nextFloat() < 0.005f;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.isNewborn() || entity.isSleeping() || entity.isPreparingSleep() || entity.isAwakeing() ||
                entity.isAttacking() || entity.isOrderedToSit() || entity.isDoingSpecialActivity()) return false;
        return cleanTimer > 0;
    }

    @Override
    public void start() {
        this.cleanTimer = CLEAN_DURATION;
        this.entity.getNavigation().stop();
        this.entity.setCleaning(true);
    }

    @Override
    public void stop() {
        this.cleanTimer = 0;
        this.entity.setCleaning(false);
    }

    @Override
    public void tick() {
        this.cleanTimer--;
        if (this.cleanTimer == 1) {
            this.entity.setHygiene(this.entity.maxHygiene());
        }
    }
}
