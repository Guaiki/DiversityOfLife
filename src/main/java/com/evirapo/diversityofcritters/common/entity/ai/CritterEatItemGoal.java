package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

public class CritterEatItemGoal extends Goal {

    private final DiverseCritter entity;
    private final TagKey<Item> foodTag;

    @Nullable private ItemEntity targetItem;
    private boolean isEating = false;
    private int eatTimer = 0;
    private int cooldown = 0;
    private int navigationRetryTimer = 0;

    private static final int EAT_DURATION = 60;
    private static final int NAV_RETRY_INTERVAL = 40;

    public CritterEatItemGoal(DiverseCritter entity, TagKey<Item> foodTag) {
        this.entity = entity;
        this.foodTag = foodTag;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (entity.isNewborn()) return false;
        if (entity.level().isClientSide()) return false;
        if (!entity.isHungry()) return false;
        if (entity.isOrderedToSit() || entity.isSleeping() || entity.isPreparingSleep()) return false;
        if (entity.isClimbing() || entity.isDoingSpecialActivity()) return false;

        targetItem = findFoodItem();
        return targetItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.level().isClientSide()) return false;
        if (isEating) return eatTimer > 0;
        return targetItem != null && targetItem.isAlive() && !targetItem.getItem().isEmpty();
    }

    @Override
    public void start() {
        isEating = false;
        eatTimer = 0;
        navigationRetryTimer = 0;
        navigateToItem();
    }

    @Override
    public void stop() {
        isEating = false;
        eatTimer = 0;
        entity.onStopEatingItem();
        entity.getNavigation().stop();
        targetItem = null;
        cooldown = 100;
    }

    @Override
    public void tick() {
        if (isEating) {
            Vec3 dm = entity.getDeltaMovement();
            entity.setDeltaMovement(0, dm.y, 0);
            eatTimer--;
            if (eatTimer <= 0) {
                entity.onAteFood();
                this.stop();
            }
            return;
        }

        if (targetItem == null || !targetItem.isAlive() || targetItem.getItem().isEmpty()) {
            this.stop();
            return;
        }

        boolean isClose = entity.distanceToSqr(targetItem) < 1.2 * 1.2;

        if (!isClose) {
            navigationRetryTimer++;
            if (!entity.getNavigation().isInProgress() || navigationRetryTimer >= NAV_RETRY_INTERVAL) {
                navigationRetryTimer = 0;
                navigateToItem();
            }
            return;
        }

        navigationRetryTimer = 0;
        entity.getNavigation().stop();

        ItemStack stack = targetItem.getItem().copy();
        stack.setCount(1);
        targetItem.getItem().shrink(1);
        if (targetItem.getItem().isEmpty()) targetItem.discard();
        targetItem = null;

        entity.onStartEatingItem(stack);
        isEating = true;
        eatTimer = EAT_DURATION;
    }

    public void trigger() {
        if (entity.isNewborn() || entity.isDoingSpecialActivity() || isEating) return;
        targetItem = findFoodItem();
        if (targetItem != null) {
            entity.getNavigation().stop();
            navigateToItem();
        }
    }

    private void navigateToItem() {
        if (targetItem == null) return;
        entity.getNavigation().moveTo(targetItem, 1.1);
    }

    @Nullable
    private ItemEntity findFoodItem() {
        Predicate<ItemEntity> predicate = e -> e.getItem().is(foodTag);
        List<? extends ItemEntity> list = entity.level().getEntitiesOfClass(
                ItemEntity.class,
                entity.getBoundingBox().inflate(32, 8, 32),
                predicate
        );
        return list.isEmpty() ? null : list.get(0);
    }
}
