package com.evirapo.diversityofcritters.common.block;

import com.evirapo.diversityofcritters.client.menu.DOCMenuTypes;
import com.evirapo.diversityofcritters.common.block.entity.AirConditionerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AirConditionerMenu extends AbstractContainerMenu {
    public final AirConditionerBlockEntity blockEntity;
    private final Level level;

    public AirConditionerMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public AirConditionerMenu(int pContainerId, Inventory inv, BlockEntity entity) {
        super(DOCMenuTypes.AIR_CONDITIONER_MENU.get(), pContainerId);
        this.blockEntity = (AirConditionerBlockEntity) entity;
        this.level = inv.player.level();
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), pPlayer, DOCBlocks.AIR_CONDITIONER.get());
    }
}
