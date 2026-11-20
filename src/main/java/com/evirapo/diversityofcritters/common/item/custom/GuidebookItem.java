package com.evirapo.diversityofcritters.common.item.custom;

import com.evirapo.diversityofcritters.client.guidebook.ClientGuidebook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** The Natural History Guide: right-click opens the guidebook screen. */
public class GuidebookItem extends Item {

    public GuidebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientGuidebook::open);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
