package com.evirapo.diversityofcritters.common.block.entity;

import com.evirapo.diversityofcritters.common.block.DOCBlockEntities;
import com.evirapo.diversityofcritters.common.block.HumidifierMenu;
import com.evirapo.diversityofcritters.common.humidity.HumidifierManager;
import com.evirapo.diversityofcritters.common.humidity.HumidityTier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class HumidifierBlockEntity extends BlockEntity implements MenuProvider {

    public static final int[] ALLOWED_SIZES = {5, 10, 20, 50};

    private HumidityTier tier = HumidityTier.MODERATE;
    private int size = 10;

    public HumidifierBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(DOCBlockEntities.HUMIDIFIER_BE.get(), pPos, pBlockState);
    }

    public HumidityTier getTier() { return tier; }
    public int getSize() { return size; }

    public static boolean isValidSize(int size) {
        for (int allowed : ALLOWED_SIZES) {
            if (allowed == size) return true;
        }
        return false;
    }

    public void setSettings(HumidityTier tier, int size) {
        this.tier = tier;
        this.size = isValidSize(size) ? size : 10;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            HumidifierManager.add(this.level, this.worldPosition, this.tier, this.size);
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            HumidifierManager.add(this.level, this.worldPosition, this.tier, this.size);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide) {
            HumidifierManager.remove(this.level, this.worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (this.level != null && !this.level.isClientSide) {
            HumidifierManager.remove(this.level, this.worldPosition);
        }
        super.onChunkUnloaded();
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("Tier", this.tier.ordinal());
        pTag.putInt("Size", this.size);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        if (pTag.contains("Tier")) this.tier = HumidityTier.byId(pTag.getInt("Tier"));
        if (pTag.contains("Size")) this.size = isValidSize(pTag.getInt("Size")) ? pTag.getInt("Size") : 10;
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
        return Component.translatable("block.diversityofcritters.humidifier");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new HumidifierMenu(pContainerId, pPlayerInventory, this);
    }
}
