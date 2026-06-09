package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Idle variant behavior — identical to CivetIdleGoal, typed to SquirrelEntity. */
public class SquirrelIdleGoal extends Goal {
    private final SquirrelEntity squirrel;
    private int durationTimer;
    private SquirrelEntity.IdleVariant currentVariant;

    public SquirrelIdleGoal(SquirrelEntity squirrel) {
        this.squirrel = squirrel;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        boolean sleepingLike = this.squirrel.isPreparingSleep() || this.squirrel.isSleeping() || this.squirrel.isAwakeing();
        boolean swimming     = this.squirrel.isInWaterOrBubble();
        boolean climbing     = this.squirrel.isClimbing();
        boolean gliding      = this.squirrel.isGliding();
        boolean onGround     = this.squirrel.onGround();

        boolean doingAttack  = this.squirrel.isAttacking();
        boolean drinking     = this.squirrel.IsDrinking();
        boolean eatingPlant  = this.squirrel.isEatingPlant();
        boolean hasTarget    = this.squirrel.getTarget() != null;
        boolean hasWantedNav = !this.squirrel.getNavigation().isDone();

        boolean canIdle = this.squirrel.isAlive()
                && !sleepingLike && !swimming && !climbing && !gliding && !hasWantedNav
                && !doingAttack && !drinking && !eatingPlant && !hasTarget && onGround
                && !this.squirrel.isCleaning() && !this.squirrel.isNewborn();

        return canIdle && this.squirrel.idleVariantCooldown <= 0 && this.squirrel.getRandom().nextFloat() < 0.01f;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.squirrel.getTarget() != null || this.squirrel.getLastHurtByMob() != null || !this.squirrel.onGround() || this.squirrel.isInWater()) {
            return false;
        }
        return this.durationTimer > 0;
    }

    @Override
    public void start() {
        this.currentVariant = pickVariant();
        this.squirrel.setIdleVariant(this.currentVariant);
        this.squirrel.getNavigation().stop();

        switch (this.currentVariant) {
            case STAND_UP                -> this.durationTimer = 40;
            case SNIFF_LEFT, SNIFF_RIGHT -> this.durationTimer = 40;
            case SIT  -> this.durationTimer = 110;
            case LAY  -> this.durationTimer = 120;
            default   -> this.durationTimer = 0;
        }
    }

    @Override
    public void tick() {
        this.durationTimer--;
    }

    @Override
    public void stop() {
        int cd = switch (this.currentVariant) {
            case STAND_UP -> 40;
            case SNIFF_LEFT, SNIFF_RIGHT -> 40;
            case SIT -> 110;
            case LAY -> 120;
            default -> 40;
        };
        this.squirrel.idleVariantCooldown = cd;
        this.squirrel.setIdleVariant(SquirrelEntity.IdleVariant.NONE);
        this.currentVariant = SquirrelEntity.IdleVariant.NONE;
    }

    private SquirrelEntity.IdleVariant pickVariant(){
        int roll = this.squirrel.getRandom().nextInt(100);
        if      (roll < 20) return SquirrelEntity.IdleVariant.STAND_UP;
        else if (roll < 45) return SquirrelEntity.IdleVariant.SNIFF_LEFT;
        else if (roll < 70) return SquirrelEntity.IdleVariant.SNIFF_RIGHT;
        else if (roll < 85) return SquirrelEntity.IdleVariant.SIT;
        else                return SquirrelEntity.IdleVariant.LAY;
    }
}
