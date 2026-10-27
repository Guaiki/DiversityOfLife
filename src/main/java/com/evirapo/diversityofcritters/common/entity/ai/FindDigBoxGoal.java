package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.DigBoxBlockEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.IDiggingCritter;
import com.evirapo.diversityofcritters.common.entity.util.CritterClaims;
import com.evirapo.diversityofcritters.common.entity.util.Reachability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class FindDigBoxGoal extends Goal {

    private final DiverseCritter critter;
    private final IDiggingCritter digger;
    private final double speed;
    private final int searchRadius;

    private BlockPos boxPos;
    private int digTimer = 0;
    private static final int DIG_DURATION = 80;
    private static final double MAX_DIST_SQ = 2.0D;

    private boolean isFinishing = false;
    private int finishingTimer = 0;

    // Cada barrido cuesta hasta MAX_PATH_CHECKS pathfindings: no se repite en cada canUse().
    private long lastScanTime = -999;

    // Cajas que no se alcanzaron hace poco: el siguiente barrido sigue por los de detrás.
    private final Reachability.Unreachable unreachable = new Reachability.Unreachable();
    private static final int SCAN_INTERVAL_TICKS = 20;

    public <E extends DiverseCritter & IDiggingCritter> FindDigBoxGoal(
            E critter, double speed, int searchRadius) {
        this.critter = critter;
        this.digger = critter;
        this.speed = speed;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (critter.level().isClientSide() || critter.isNewborn()) return false;
        if (!critter.isEnrichmentNeeded()) return false;

        long now = critter.level().getGameTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS) return false;
        lastScanTime = now;

        // Saltarse las cajas que ya tiene apartadas otro critter: si la mas cercana esta ocupada se
        // coge la siguiente, y si no queda ninguna libre este goal cede y le toca a cavar en tierra.
        // De las libres, la mas cercana a la que se llegue andando.
        boxPos = Reachability.nearestReachable(critter,
                findValidDigBoxes(CritterClaims.digClaims(critter, searchRadius * 2.0D)),
                Function.identity(), 1, pathRange(), unreachable);
        if (boxPos == null) return false;

        // Reservar al elegir, no al llegar: durante el camino es cuando otro critter buscaria.
        this.digger.setDigTarget(boxPos);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (critter.level().isClientSide() || critter.isNewborn()) return false;
        if (boxPos == null) return false;

        if (isFinishing) return finishingTimer < 10;

        if (!critter.isEnrichmentNeeded()) return false;
        return isValidDigBox(boxPos);
    }

    @Override
    public void start() {
        digTimer = 0;
        isFinishing = false;
        finishingTimer = 0;
        if (boxPos != null) navigateToBox();
    }

    /**
     * False si ya no hay camino completo hasta la caja: le han puesto una pared delante a mitad de
     * camino. En pleno salto no se puede recalcular y se sigue con la ruta que hubiera.
     */
    private boolean navigateToBox() {
        return Reachability.repath(critter, boxPos, 1, pathRange(), speed) != Reachability.Repath.UNREACHABLE;
    }

    private float pathRange() {
        return this.searchRadius * 2.0F;
    }

    @Override
    public void stop() {
        digTimer = 0;
        boxPos = null;
        isFinishing = false;
        finishingTimer = 0;
        critter.getNavigation().stop();
        setCritterDigging(false);
        this.digger.setDigTarget(null);
    }

    @Override
    public void tick() {
        if (isFinishing) {
            finishingTimer++;
            if (finishingTimer >= 10) {
                this.stop();
            }
            return;
        }

        if (boxPos == null) return;

        Vec3 boxCenter = new Vec3(boxPos.getX() + 0.5, boxPos.getY() + 0.5, boxPos.getZ() + 0.5);
        double distSq = critter.distanceToSqr(boxCenter);

        boolean isOnTop = (distSq < MAX_DIST_SQ) && (critter.getY() >= boxPos.getY() + 0.4);

        if (!isOnTop) {
            setCritterDigging(false);
            if (!critter.getNavigation().isInProgress() && !navigateToBox()) {
                // Le han puesto una pared delante a mitad de camino: soltar la caja (stop() libera la
                // reserva) en vez de empujarla.
                unreachable.add(boxPos, critter.level().getGameTime());
                this.stop();
                return;
            }
            if (distSq < MAX_DIST_SQ && critter.getY() < boxPos.getY() + 0.4) {
                critter.getJumpControl().jump();
            }
            return;
        }

        critter.getNavigation().stop();
        critter.getLookControl().setLookAt(boxCenter.x, boxCenter.y - 0.5, boxCenter.z);

        if (digTimer == 0) {
            critter.setPos(boxCenter.x, critter.getY(), boxCenter.z);
            critter.setDeltaMovement(Vec3.ZERO);
        }

        setCritterDigging(true);
        digTimer++;

        if (digTimer >= DIG_DURATION) {
            performDigDrop();
            setCritterDigging(false);
            isFinishing = true;
        }
    }

    private void setCritterDigging(boolean isDigging) {
        this.digger.setDigging(isDigging);
    }

    private void performDigDrop() {
        Level level = (Level) critter.level();
        BlockEntity be = level.getBlockEntity(boxPos);

        if (be instanceof DigBoxBlockEntity) {
            be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stackInSlot = handler.getStackInSlot(i);
                    if (!stackInSlot.isEmpty()) {
                        ItemStack extracted = handler.extractItem(i, 1, false);
                        if (!extracted.isEmpty()) {
                            ItemEntity itemEntity = new ItemEntity(level, critter.getX(), critter.getY() + 0.5, critter.getZ(), extracted);
                            itemEntity.setDeltaMovement(0, 0.2, 0);
                            level.addFreshEntity(itemEntity);

                            critter.setEnrichment(critter.maxEnrichment());
                            return;
                        }
                    }
                }
            });
        }
    }

    /** Las cajas con items y sin reserva; cuál se alcanza lo decide {@link Reachability}. */
    private List<BlockPos> findValidDigBoxes(Set<BlockPos> claimed) {
        BlockPos origin = critter.blockPosition();
        List<BlockPos> boxes = new ArrayList<>();
        int r = this.searchRadius;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);

                    if (isValidDigBox(pos) && !claimed.contains(pos)) {
                        boxes.add(pos.immutable());
                    }
                }
            }
        }
        return boxes;
    }

    private boolean isValidDigBox(BlockPos pos) {
        Level level = (Level) critter.level();
        if (!level.getBlockState(pos).is(DOCBlocks.DIG_BOX.get())) return false;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DigBoxBlockEntity)) return false;

        AtomicReference<Boolean> hasItems = new AtomicReference<>(false);
        be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!handler.getStackInSlot(i).isEmpty()) {
                    hasItems.set(true);
                    break;
                }
            }
        });

        return hasItems.get();
    }
}