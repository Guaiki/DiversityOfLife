package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SifakaClimbGoal extends Goal {
    private final SifakaEntity sifaka;

    private int hangTicksRemaining = 0;
    private int cooldown = 0;

    private static final int COOLDOWN_MIN = 600;
    private static final int COOLDOWN_MAX = 2400;
    private static final int HANG_DURATION_MIN = 60;
    private static final int HANG_DURATION_MAX = 160;

    public SifakaClimbGoal(SifakaEntity sifaka, double speed, int searchRadius) {
        this.sifaka = sifaka;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (sifaka.level().isClientSide() || sifaka.isNewborn()) return false;
        if (sifaka.isSleeping() || sifaka.isOrderedToSit()) return false;
        if (this.cooldown-- > 0) return false;
        return sifaka.isClimbingUp() && sifaka.getRandom().nextInt(60) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (sifaka.isSleeping() || sifaka.isOrderedToSit()) return false;
        return this.hangTicksRemaining > 0;
    }

    @Override
    public void start() {
        this.hangTicksRemaining = HANG_DURATION_MIN
                + sifaka.getRandom().nextInt(HANG_DURATION_MAX - HANG_DURATION_MIN);
        sifaka.setClimbState(SifakaEntity.CLIMB_HANG);
        sifaka.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (sifaka.getClimbState() != SifakaEntity.CLIMB_HANG) {
            sifaka.setClimbState(SifakaEntity.CLIMB_HANG);
        }
        sifaka.setDeltaMovement(0, 0, 0);
        this.hangTicksRemaining--;
    }

    @Override
    public void stop() {
        if (sifaka.isHanging()) {
            sifaka.setClimbState(SifakaEntity.CLIMB_NONE);
        }
        sifaka.setEnrichment(sifaka.maxEnrichment());
        this.cooldown = COOLDOWN_MIN + sifaka.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN);
    }
}
