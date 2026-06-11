package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class CritterMotherCleanBabyGoal<T extends DiverseCritter> extends Goal {

    private final T mother;
    private final Class<T> entityClass;
    private T targetBaby;
    private final double speed;
    private final double searchRadius;

    private int cleanTimer = 0;
    private boolean isFinishing = false;
    private int finishingTimer = 0;

    private static final double MAX_CLEAN_DIST_SQ = 0.8D;

    public CritterMotherCleanBabyGoal(T mother, Class<T> entityClass, double speed, double searchRadius) {
        this.mother = mother;
        this.entityClass = entityClass;
        this.speed = speed;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mother.isNewborn() || mother.isBaby() || mother.isJuvenile() || mother.level().isClientSide()) return false;
        if (mother.isSleeping() || mother.isPreparingSleep() || mother.isAttacking()) return false;
        if (mother.isDoingSpecialActivity()) return false;

        targetBaby = findDirtyBaby();
        return targetBaby != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (mother.level().isClientSide()) return false;
        if (targetBaby == null || !targetBaby.isAlive()) return false;
        if (mother.isDoingSpecialActivity()) return false;

        if (isFinishing) return finishingTimer < 5;
        return mother.distanceToSqr(targetBaby) <= 256.0D;
    }

    @Override
    public void start() {
        cleanTimer = 0;
        isFinishing = false;
        finishingTimer = 0;
        mother.setIsDrinking(false);
        if (targetBaby != null) targetBaby.isBeingCleaned = true;
    }

    @Override
    public void stop() {
        cleanTimer = 0;
        isFinishing = false;
        finishingTimer = 0;
        mother.setIsDrinking(false);
        mother.getNavigation().stop();
        if (targetBaby != null) targetBaby.isBeingCleaned = false;
        targetBaby = null;
    }

    @Override
    public void tick() {
        if (isFinishing) {
            finishingTimer++;
            if (finishingTimer >= 5) this.stop();
            return;
        }

        if (targetBaby == null) return;

        targetBaby.getNavigation().stop();
        Vec3 babyDm = targetBaby.getDeltaMovement();
        targetBaby.setDeltaMovement(0, babyDm.y, 0);
        targetBaby.getLookControl().setLookAt(mother, 10.0F, (float) targetBaby.getMaxHeadXRot());

        if (targetBaby.getHygiene() >= targetBaby.maxHygiene()) {
            mother.setIsDrinking(false);
            isFinishing = true;
            return;
        }

        double distSq = mother.distanceToSqr(targetBaby);

        if (distSq > MAX_CLEAN_DIST_SQ) {
            mother.setIsDrinking(false);
            if (!mother.getNavigation().isInProgress()) {
                mother.getNavigation().moveTo(targetBaby, speed);
            }
            return;
        }

        mother.getNavigation().stop();
        mother.getLookControl().setLookAt(targetBaby, 10.0F, (float) mother.getMaxHeadXRot());
        mother.setIsDrinking(true);

        cleanTimer++;
        if (cleanTimer % 20 == 15) {
            targetBaby.setHygiene(targetBaby.maxHygiene());
        }
    }

    private T findDirtyBaby() {
        AABB searchBox = mother.getBoundingBox().inflate(searchRadius, 4.0D, searchRadius);
        List<T> babies = mother.level().getEntitiesOfClass(entityClass, searchBox,
                baby -> baby.isNewborn() && baby.hasLowHygiene() && !baby.isBeingCleaned);

        if (babies.isEmpty()) return null;

        T closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (T baby : babies) {
            double d = mother.distanceToSqr(baby);
            if (d < closestDistSq) { closestDistSq = d; closest = baby; }
        }
        return closest;
    }
}
