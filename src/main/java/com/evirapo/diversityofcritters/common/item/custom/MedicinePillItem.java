package com.evirapo.diversityofcritters.common.item.custom;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.item.TranquilizerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Sedante para mascotas: el mismo efecto que el dardo, sin knockback y sin rifle. Sólo sobre
 * critters domesticados, de cualquier dueño; a uno salvaje no se le puede meter una píldora en la
 * boca, para eso está el dardo.
 */
public class MedicinePillItem extends Item {

    public MedicinePillItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DiverseCritter critter)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            if (!critter.isTame()) {
                player.displayClientMessage(Component.translatable(
                        "message.diversityofcritters.tranquilizer.not_tamed"), true);
            } else if (TranquilizerHelper.dose(critter, player)) {
                player.level().playSound(null, critter.blockPosition(), SoundEvents.GENERIC_EAT,
                        SoundSource.PLAYERS, 0.8F, 1.2F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
