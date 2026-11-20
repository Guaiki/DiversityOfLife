package com.evirapo.diversityofcritters.common.item.custom;

import com.evirapo.diversityofcritters.network.DOCNetworkHandler;
import com.evirapo.diversityofcritters.network.OpenGuidebookMsg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Natural History Guide. Right-click opens it: the server sends the player's discoveries and
 * the world's conservation status, and the client opens the screen with them. Right-clicking an
 * animal with it registers that animal instead (see {@code DiverseCritter#mobInteract}).
 */
public class GuidebookItem extends Item {

    public GuidebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            DOCNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), OpenGuidebookMsg.forPlayer(serverPlayer));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
