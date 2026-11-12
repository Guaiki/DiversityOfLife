package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.util.Reachability;
import com.evirapo.diversityofcritters.common.humidity.HumidifierManager;
import com.evirapo.diversityofcritters.common.temperature.AirConditionerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Walks an animal out of a temperature or humidity it does not like, toward a nearby spot where
 * both are better: another biome, or the area of an air conditioner or humidifier.
 *
 * <ul>
 *   <li>Wild animals always; tamed ones only while set to wander. Sitting or following the owner,
 *       the order wins.</li>
 *   <li>In mere stress, hunger and thirst come first. In danger, a hungry or thirsty animal first gets
 *       30 seconds to eat or drink (about one heart of danger damage), then the climate wins. Without
 *       that grace one whose only food lies in the danger would walk out on arrival, forever; without
 *       the limit one with no food in reach would never leave the danger.</li>
 *   <li>A destination must be strictly better than where the animal stands, so it never walks into
 *       something worse. Stepping into danger during a stroll triggers this goal and walks it out.</li>
 *   <li>With no better spot in reach, the search backs off (three cooldowns) so it never blocks
 *       eating or drinking and never oscillates.</li>
 * </ul>
 */
public class SeekComfortGoal extends Goal {

    private static final int RANDOM_SAMPLES = 16;
    private static final int GROUND_SEARCH_DEPTH = 12;
    private static final double ARRIVED_DIST_SQ = 1.5D * 1.5D;
    private static final int FEEDING_GRACE_TICKS = 600;

    private final DiverseCritter critter;
    private final double speed;
    private final Reachability.Unreachable unreachable = new Reachability.Unreachable();
    private long nextScanTick;
    private long needyInDangerSince = -1;
    private @Nullable BlockPos target;

    public SeekComfortGoal(DiverseCritter critter, double speed) {
        this.critter = critter;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (critter.level().isClientSide()) return false;
        long now = critter.level().getGameTime();
        if (now < nextScanTick) return false;
        if (!isEligible()) return false;

        int here = critter.climateScoreAt(critter.blockPosition());
        if (here == 0) return false;
        boolean danger = here >= DiverseCritter.CLIMATE_DANGER_SCORE;
        boolean needy = critter.isHungry() || critter.isThirsty();
        if (needy && !danger) {
            needyInDangerSince = -1;
            return false;
        }
        if (needy) {
            // Hungry or thirsty in danger: first give the food and water goals (lower priority) the
            // chance to eat or drink before walking away from them.
            if (needyInDangerSince < 0) needyInDangerSince = now;
            if (now - needyInDangerSince < FEEDING_GRACE_TICKS) return false;
        } else {
            needyInDangerSince = -1;
        }

        // In mid-jump or mid-fall no path can be checked, and calling every spot unreachable would be
        // a lie that sticks for 200 ticks: try again next tick instead.
        if (!Reachability.canPathNow(critter)) return false;

        int cooldown = DOCServerConfig.ecosystem().comfortScanCooldownTicks();
        this.target = findBetterSpot(here);
        this.nextScanTick = now + (this.target == null ? cooldown * 3L : cooldown);
        return this.target != null;
    }

    private boolean isEligible() {
        if (!critter.climateEnabled()) return false;
        if (critter.isNewborn() || critter.isSedated()) return false;
        if (critter.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (critter.isPassenger() || critter.isLeashed()) return false;
        if (critter.isTame() && (!critter.isWandering() || critter.isOrderedToSit())) return false;
        return true;
    }

    @Override
    public void start() {
        if (target == null) return;
        if (Reachability.repath(critter, target, 1, range(), speed) == Reachability.Repath.UNREACHABLE) {
            unreachable.add(target, critter.level().getGameTime());
            target = null;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !isEligible()) return false;
        if (critter.blockPosition().distSqr(target) <= ARRIVED_DIST_SQ) return false;
        return !critter.getNavigation().isDone();
    }

    @Override
    public void stop() {
        target = null;
        critter.getNavigation().stop();
    }

    private float range() {
        return DOCServerConfig.ecosystem().comfortSearchRadius() * 2.0F;
    }

    private record Candidate(BlockPos pos, int score) {}

    /**
     * The reachable spot with the lowest climate score, below {@code here}: random ground positions
     * in the search radius, plus the ground around every air conditioner and humidifier in it (a
     * small appliance area is easy to miss by random sampling alone).
     */
    private @Nullable BlockPos findBetterSpot(int here) {
        Level level = critter.level();
        BlockPos origin = critter.blockPosition();
        int radius = DOCServerConfig.ecosystem().comfortSearchRadius();
        RandomSource random = critter.getRandom();

        List<BlockPos> columns = new ArrayList<>();
        for (int i = 0; i < RANDOM_SAMPLES; i++) {
            columns.add(origin.offset(random.nextInt(radius * 2 + 1) - radius, 4,
                    random.nextInt(radius * 2 + 1) - radius));
        }
        List<BlockPos> appliances = new ArrayList<>(AirConditionerManager.centersWithin(level, origin, radius));
        appliances.addAll(HumidifierManager.centersWithin(level, origin, radius));
        for (BlockPos center : appliances) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                columns.add(center.relative(dir, 2).above(2));
            }
        }

        List<Candidate> better = new ArrayList<>();
        for (BlockPos column : columns) {
            BlockPos ground = standingSpotBelow(level, column);
            if (ground == null) continue;
            int score = critter.climateScoreAt(ground);
            if (score < here) better.add(new Candidate(ground, score));
        }
        better.sort(Comparator.comparingInt(Candidate::score)
                .thenComparingDouble(c -> c.pos().distSqr(origin)));

        long now = level.getGameTime();
        int checks = 0;
        for (Candidate candidate : better) {
            if (checks >= Reachability.MAX_PATH_CHECKS) break;
            if (unreachable.contains(candidate.pos(), now)) continue;
            checks++;
            if (Reachability.canReach(critter, candidate.pos(), 1, range())) return candidate.pos();
            unreachable.add(candidate.pos(), now);
        }
        return null;
    }

    /** First dry, open spot with firm ground under it, going down from {@code start}. */
    private static @Nullable BlockPos standingSpotBelow(Level level, BlockPos start) {
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int i = 0; i < GROUND_SEARCH_DEPTH && pos.getY() > level.getMinBuildHeight(); i++) {
            BlockPos below = pos.below();
            boolean open = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getFluidState(pos).isEmpty();
            if (open && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return pos.immutable();
            }
            pos.move(Direction.DOWN);
        }
        return null;
    }
}
