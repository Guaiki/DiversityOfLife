package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.spawn.LivestockRules;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Lets a vanilla livestock animal breed without a player: every {@code livestockBreedIntervalSeconds}
 * an idle adult has a coin-flip chance to fall in love together with an idle adult of its kind
 * nearby, as long as the area is below the livestock cap. The vanilla BreedGoal does the rest.
 * Added to the four animals when they join the level (see {@code LivestockEvents}); it takes no
 * goal flags.
 */
public class LivestockSelfBreedGoal extends Goal {

    private static final double PARTNER_RANGE = 8.0D;

    private final Animal animal;
    private long nextAttemptTick = -1;
    private @Nullable Animal partner;

    public LivestockSelfBreedGoal(Animal animal) {
        this.animal = animal;
    }

    @Override
    public boolean canUse() {
        if (animal.level().isClientSide()) return false;
        var eco = DOCServerConfig.ecosystem();
        if (!eco.livestockEnabled()) return false;

        long now = animal.level().getGameTime();
        long interval = eco.livestockBreedIntervalSeconds() * 20L;
        if (nextAttemptTick < 0 || nextAttemptTick - now > interval) {
            // First tick, or the interval was shortened: stagger the herd over one interval.
            nextAttemptTick = now + 1 + animal.getRandom().nextInt((int) Math.max(1, interval));
            return false;
        }
        if (now < nextAttemptTick) return false;
        nextAttemptTick = now + interval;

        if (!isIdleAdult(animal) || animal.getRandom().nextBoolean()) return false;
        if (LivestockRules.isAtCap(animal.level(), animal.getType(), animal.blockPosition())) return false;

        List<? extends Animal> others = animal.level().getEntitiesOfClass(animal.getClass(),
                animal.getBoundingBox().inflate(PARTNER_RANGE),
                other -> other != animal && other.getType() == animal.getType() && isIdleAdult(other));
        if (others.isEmpty()) return false;
        partner = others.get(0);
        return true;
    }

    private static boolean isIdleAdult(Animal a) {
        return a.isAlive() && !a.isBaby() && a.getAge() == 0 && !a.isInLove();
    }

    @Override
    public void start() {
        animal.setInLove(null);
        if (partner != null) partner.setInLove(null);
        partner = null;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
