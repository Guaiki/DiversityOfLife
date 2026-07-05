package com.evirapo.diversityofcritters.common.block.entity;

import com.evirapo.diversityofcritters.common.block.AirConditionerMenu;
import com.evirapo.diversityofcritters.common.block.DOCBlockEntities;
import com.evirapo.diversityofcritters.common.temperature.AirConditionerManager;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public class AirConditionerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int[] ALLOWED_SIZES = {5, 10, 20, 50};
    private static final int WORLD_EFFECT_INTERVAL = 20;

    private TemperatureTier tier = TemperatureTier.MODERATE;
    private int size = 10;
    private boolean snowEnabled = true;
    private boolean iceEnabled = true;
    private int tickCounter = 0;

    public AirConditionerBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(DOCBlockEntities.AIR_CONDITIONER_BE.get(), pPos, pBlockState);
    }

    public TemperatureTier getTier() { return tier; }
    public int getSize() { return size; }
    public boolean isSnowEnabled() { return snowEnabled; }
    public boolean isIceEnabled() { return iceEnabled; }

    public static boolean isValidSize(int size) {
        for (int allowed : ALLOWED_SIZES) {
            if (allowed == size) return true;
        }
        return false;
    }

    public void setSettings(TemperatureTier tier, int size, boolean snowEnabled, boolean iceEnabled) {
        this.tier = tier;
        this.size = isValidSize(size) ? size : 10;
        this.snowEnabled = snowEnabled;
        this.iceEnabled = iceEnabled;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            AirConditionerManager.add(this.level, this.worldPosition, this.tier, this.size);
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            AirConditionerManager.add(this.level, this.worldPosition, this.tier, this.size);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide) {
            AirConditionerManager.remove(this.level, this.worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (this.level != null && !this.level.isClientSide) {
            AirConditionerManager.remove(this.level, this.worldPosition);
        }
        super.onChunkUnloaded();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirConditionerBlockEntity be) {
        be.tickCounter++;
        if (be.tickCounter % WORLD_EFFECT_INTERVAL != 0) return;

        boolean freeze = be.tier == TemperatureTier.FREEZING || be.tier == TemperatureTier.COLD;
        boolean melt = be.tier == TemperatureTier.HOT || be.tier == TemperatureTier.SCORCHING;
        if (freeze && !be.snowEnabled && !be.iceEnabled) return;
        if (!freeze && !melt) return;

        int radius = be.size;
        RandomSource random = level.random;
        int samples = Math.min(40, Math.max(8, radius));

        for (int i = 0; i < samples; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos.offset(dx, 0, dz));
            if (Math.abs(surface.getY() - pos.getY()) > radius) continue;

            if (freeze) {
                BlockPos below = surface.below();
                if (be.iceEnabled && level.getBlockState(below).is(Blocks.WATER) && level.getFluidState(below).isSource()) {
                    level.setBlockAndUpdate(below, Blocks.ICE.defaultBlockState());
                } else if (be.snowEnabled && level.isEmptyBlock(surface) && Blocks.SNOW.defaultBlockState().canSurvive(level, surface)) {
                    level.setBlockAndUpdate(surface, Blocks.SNOW.defaultBlockState());
                }
            } else {
                BlockPos below = surface.below();
                if (level.getBlockState(surface).is(Blocks.SNOW)) {
                    level.removeBlock(surface, false);
                } else if (level.getBlockState(below).is(Blocks.SNOW)) {
                    level.removeBlock(below, false);
                } else if (level.getBlockState(below).is(Blocks.ICE) || level.getBlockState(below).is(Blocks.FROSTED_ICE)) {
                    level.setBlockAndUpdate(below, Blocks.WATER.defaultBlockState());
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("Tier", this.tier.ordinal());
        pTag.putInt("Size", this.size);
        pTag.putBoolean("SnowEnabled", this.snowEnabled);
        pTag.putBoolean("IceEnabled", this.iceEnabled);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        if (pTag.contains("Tier")) this.tier = TemperatureTier.byId(pTag.getInt("Tier"));
        if (pTag.contains("Size")) this.size = isValidSize(pTag.getInt("Size")) ? pTag.getInt("Size") : 10;
        if (pTag.contains("SnowEnabled")) this.snowEnabled = pTag.getBoolean("SnowEnabled");
        if (pTag.contains("IceEnabled")) this.iceEnabled = pTag.getBoolean("IceEnabled");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.diversityofcritters.air_conditioner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new AirConditionerMenu(pContainerId, pPlayerInventory, this);
    }
}
