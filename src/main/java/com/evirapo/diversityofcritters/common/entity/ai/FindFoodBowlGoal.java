package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.util.BowlFeedingHelper;
import com.evirapo.diversityofcritters.common.entity.util.Reachability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

public class FindFoodBowlGoal extends Goal implements FeedingGoal {

    private final DiverseCritter critter;
    private final double speed;
    private final int searchRadius;

    private BlockPos bowlPos;
    private int eatTimer = 0;
    private int cooldown = 0;
    private static final int EAT_INTERVAL = 40;

    private int navigationRetryTimer = 0;
    private static final int NAV_RETRY_INTERVAL = 40;

    private long lastScanTime = -999;

    // Bowls que no se alcanzaron hace poco: el siguiente barrido sigue por los de detrás.
    private final Reachability.Unreachable unreachable = new Reachability.Unreachable();
    private static final int SCAN_INTERVAL_TICKS = 20;

    public FindFoodBowlGoal(DiverseCritter critter, double speed, int searchRadius) {
        this.critter = critter;
        this.speed = speed;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (critter.isNewborn()) return false;
        if (critter.level().isClientSide()) return false;
        if (!critter.isHungry()) return false;

        long now = critter.level().getGameTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS) return false;
        lastScanTime = now;

        bowlPos = Reachability.nearestReachable(critter, findFoodBowls(), Function.identity(), 1, pathRange(), unreachable);
        return bowlPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (critter.level().isClientSide()) return false;
        if (bowlPos == null) return false;
        boolean hasFood = BowlFeedingHelper.hasFoodFor(critter, (Level) critter.level(), bowlPos);
        if (!hasFood) return false;
        return critter.getHunger() < critter.maxHunger();
    }

    @Override
    public void start() {
        eatTimer = 0;
        navigationRetryTimer = 0;
        critter.setIsDrinking(false);
        if (bowlPos != null) navigateToBowl();
    }

    private float pathRange() {
        return this.searchRadius * 2.0F;
    }

    /**
     * False si ya no hay camino completo hasta el bowl: le han puesto una pared delante a mitad de
     * camino. Mismo objetivo y precisión que la comprobación de {@code canUse()}. En pleno salto no se
     * puede recalcular y se sigue con la ruta que hubiera.
     */
    private boolean navigateToBowl() {
        if (bowlPos == null) return false;
        return Reachability.repath(critter, bowlPos, 1, pathRange(), speed) != Reachability.Repath.UNREACHABLE;
    }

    @Override
    public void stop() {
        eatTimer = 0;
        critter.setIsDrinking(false);
        bowlPos = null;
        critter.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (bowlPos == null) return;

        boolean isCloseEnough = BowlFeedingHelper.isWithinReach(critter, bowlPos);
        Vec3 bowlCenter = Vec3.atBottomCenterOf(bowlPos);

        if (!isCloseEnough) {
            critter.setIsDrinking(false);
            navigationRetryTimer++;
            if (!critter.getNavigation().isInProgress() || navigationRetryTimer >= NAV_RETRY_INTERVAL) {
                navigationRetryTimer = 0;
                if (!navigateToBowl()) {
                    unreachable.add(bowlPos, critter.level().getGameTime());
                    this.cooldown = 100;
                    this.stop();
                }
            }
            return;
        }

        critter.setIsDrinking(true);
        critter.getNavigation().stop();
        Vec3 dm = critter.getDeltaMovement();
        critter.setDeltaMovement(0, dm.y, 0);
        critter.getLookControl().setLookAt(bowlCenter.x, bowlPos.getY() + 0.1D, bowlCenter.z);

        eatTimer++;
        boolean hasFood = BowlFeedingHelper.hasFoodFor(critter, (Level) critter.level(), bowlPos);
        if (!hasFood) {
            critter.setIsDrinking(false);
            this.cooldown = 100;
            this.stop();
            return;
        }
        if (eatTimer % EAT_INTERVAL == 0) {
            boolean ate = BowlFeedingHelper.consumeFoodFor(critter, (Level) critter.level(), bowlPos);
            if (!ate || critter.getHunger() >= critter.maxHunger()) this.stop();
        }
    }

    @Nullable
    public BlockPos getBowlPos() { return this.bowlPos; }

    /** Todos los bowls con comida para esta dieta; cuál se alcanza lo decide {@link Reachability}. */
    private List<BlockPos> findFoodBowls() {
        Level level = (Level) critter.level();
        BlockPos origin = critter.blockPosition();
        List<BlockPos> bowls = new ArrayList<>();

        int r = this.searchRadius;
        int vy = Math.min(r, 6);

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -vy, -r),
                origin.offset(r,   vy,  r))) {
            if (BowlFeedingHelper.hasFoodFor(critter, level, pos)) {
                bowls.add(pos.immutable());
            }
        }
        return bowls;
    }
}
