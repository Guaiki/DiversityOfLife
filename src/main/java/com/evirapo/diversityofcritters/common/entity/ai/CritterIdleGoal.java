package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class CritterIdleGoal extends Goal {

    private final DiverseCritter entity;
    private int durationTimer;
    private DiverseCritter.IdleVariant currentVariant;

    public CritterIdleGoal(DiverseCritter entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        boolean sleepingLike = entity.isPreparingSleep() || entity.isSleeping() || entity.isAwakeing();
        boolean swimming     = entity.isInWaterOrBubble();
        boolean climbing     = entity.isClimbing();
        boolean onGround     = entity.onGround();
        boolean doingAttack  = entity.isAttacking();
        boolean drinking     = entity.IsDrinking();
        boolean hasTarget    = entity.getTarget() != null;
        boolean hasWantedNav = !entity.getNavigation().isDone();

        boolean canIdle = entity.isAlive()
                && !sleepingLike && !swimming && !climbing && !hasWantedNav
                && !doingAttack && !drinking && !hasTarget && onGround
                && !entity.isCleaning() && !entity.isNewborn()
                && !entity.isDoingSpecialActivity();

        return canIdle && entity.idleVariantCooldown <= 0 && entity.getRandom().nextFloat() < 0.01f;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.getTarget() != null || entity.getLastHurtByMob() != null
                || !entity.onGround() || entity.isInWater()) {
            return false;
        }
        return durationTimer > 0;
    }

    @Override
    public void start() {
        currentVariant = pickVariant();
        entity.setIdleVariant(currentVariant);
        entity.getNavigation().stop();

        switch (currentVariant) {
            case STRETCH                 -> durationTimer = 40;
            case SNIFF_LEFT, SNIFF_RIGHT -> durationTimer = 40;
            case SIT      -> durationTimer = 110;
            case STAND_UP -> durationTimer = 120;
            default   -> durationTimer = 0;
        }
    }

    @Override
    public void tick() {
        durationTimer--;
    }

    @Override
    public void stop() {
        int cd = switch (currentVariant) {
            case STRETCH -> 40;
            case SNIFF_LEFT, SNIFF_RIGHT -> 40;
            case SIT -> 110;
            case STAND_UP -> 120;
            default -> 40;
        };
        entity.idleVariantCooldown = cd;
        entity.setIdleVariant(DiverseCritter.IdleVariant.NONE);
        currentVariant = DiverseCritter.IdleVariant.NONE;
    }

    private DiverseCritter.IdleVariant pickVariant() {
        int roll = entity.getRandom().nextInt(100);
        if      (roll < 20) return DiverseCritter.IdleVariant.STRETCH;
        else if (roll < 45) return DiverseCritter.IdleVariant.SNIFF_LEFT;
        else if (roll < 70) return DiverseCritter.IdleVariant.SNIFF_RIGHT;
        else if (roll < 85) return DiverseCritter.IdleVariant.SIT;
        else                return DiverseCritter.IdleVariant.STAND_UP;
    }
}
