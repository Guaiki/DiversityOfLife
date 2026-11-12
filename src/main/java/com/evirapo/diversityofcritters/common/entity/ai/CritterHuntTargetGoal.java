package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Picks the nearest prey in sight, by prey category ({@link DiverseCritter#preyCategories()}), up to
 * {@link DiverseCritter#huntRange()} blocks away, while {@link DiverseCritter#canHunt()}. The chase
 * and the bite are the species' own melee goal; the chase gives up at the {@code FOLLOW_RANGE}
 * attribute, which should be at least the hunt range.
 */
public class CritterHuntTargetGoal extends TargetGoal {

    private static final int SCAN_INTERVAL_TICKS = 20;

    private final DiverseCritter hunter;
    private long nextScanTick;
    private @Nullable LivingEntity candidate;

    public CritterHuntTargetGoal(DiverseCritter hunter) {
        super(hunter, true, false);
        this.hunter = hunter;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (hunter.preyCategories().isEmpty() || !hunter.canHunt()) return false;
        long now = hunter.level().getGameTime();
        if (now < nextScanTick) return false;
        nextScanTick = now + SCAN_INTERVAL_TICKS;

        double range = hunter.huntRange();
        TargetingConditions conditions = TargetingConditions.forCombat()
                .range(range)
                .selector(target -> DiverseCritter.isPreyFor(hunter, target));
        candidate = hunter.level().getNearestEntity(LivingEntity.class, conditions, hunter,
                hunter.getX(), hunter.getEyeY(), hunter.getZ(),
                hunter.getBoundingBox().inflate(range, 8.0D, range));
        return candidate != null;
    }

    @Override
    public void start() {
        hunter.setTarget(candidate);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = hunter.getTarget();
        if (target == null || !hunter.canHunt() || !DiverseCritter.isPreyFor(hunter, target)) return false;
        return super.canContinueToUse();
    }

    @Override
    public void stop() {
        super.stop();
        candidate = null;
    }
}
