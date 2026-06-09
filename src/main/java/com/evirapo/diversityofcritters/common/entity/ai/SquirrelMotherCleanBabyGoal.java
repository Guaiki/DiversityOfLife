package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/** Mother grooming behavior — identical to MotherCleanBabyGoal, typed to SquirrelEntity. */
public class SquirrelMotherCleanBabyGoal extends Goal {

    private final SquirrelEntity mother;
    private SquirrelEntity targetBaby;
    private final double speed;
    private final double searchRadius;

    private int cleanTimer = 0;

    private boolean isFinishing = false;
    private int finishingTimer = 0;

    private static final double MAX_CLEAN_DIST_SQ = 0.8D;

    public SquirrelMotherCleanBabyGoal(SquirrelEntity mother, double speed, double searchRadius) {
        this.mother = mother;
        this.speed = speed;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mother.isNewborn() || mother.isBaby() || mother.isJuvenile() || mother.level().isClientSide()) return false;
        if (mother.isSleeping() || mother.isPreparingSleep() || mother.isAttacking()) return false;
        if (mother.isGliding() || mother.isEatingPlant()) return false;

        targetBaby = findDirtyBaby();
        return targetBaby != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (mother.level().isClientSide()) return false;
        if (targetBaby == null || !targetBaby.isAlive()) return false;
        if (mother.isGliding()) return false;

        if (isFinishing) return finishingTimer < 5;
        if (mother.distanceToSqr(targetBaby) > 256.0D) return false;

        return true;
    }

    @Override
    public void start() {
        cleanTimer = 0;
        isFinishing = false;
        finishingTimer = 0;
        mother.setIsDrinking(false);

        if (targetBaby != null) {
            targetBaby.isBeingCleaned = true;
        }
    }

    @Override
    public void stop() {
        cleanTimer = 0;
        isFinishing = false;
        finishingTimer = 0;
        mother.setIsDrinking(false);
        mother.getNavigation().stop();

        if (targetBaby != null) {
            targetBaby.isBeingCleaned = false;
        }
        targetBaby = null;
    }

    @Override
    public void tick() {
        if (isFinishing) {
            finishingTimer++;
            if (finishingTimer >= 5) {
                this.stop();
            }
            return;
        }

        if (targetBaby == null) return;

        targetBaby.getNavigation().stop();
        Vec3 babyDm = targetBaby.getDeltaMovement();
        targetBaby.setDeltaMovement(0, babyDm.y, 0); // Anclamos X/Z pero mantenemos gravedad Y
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

    private SquirrelEntity findDirtyBaby() {
        AABB searchBox = mother.getBoundingBox().inflate(searchRadius, 4.0D, searchRadius);

        List<SquirrelEntity> nearbySquirrels = mother.level().getEntitiesOfClass(SquirrelEntity.class, searchBox,
                (squirrel) -> squirrel.isNewborn()
                        && squirrel.hasLowHygiene()
                        && !squirrel.isBeingCleaned);

        if (nearbySquirrels.isEmpty()) {
            return null;
        }

        SquirrelEntity closestBaby = null;
        double closestDistSq = Double.MAX_VALUE;

        for (SquirrelEntity baby : nearbySquirrels) {
            double distSq = mother.distanceToSqr(baby);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closestBaby = baby;
            }
        }

        return closestBaby;
    }
}
