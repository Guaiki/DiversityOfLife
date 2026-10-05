package com.evirapo.diversityofcritters.common.item.custom;

import com.evirapo.diversityofcritters.common.entity.custom.TranquilizerDartEntity;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Rifle de dardos tranquilizantes. Clic derecho mantenido apunta, clic izquierdo apuntando dispara.
 *
 * <p><b>Apuntar es el catalejo vanilla.</b> Zoom, overlay, sensibilidad y mano oculta cuelgan de
 * {@code Player.isScoping()}, y {@code PlayerScopingMixin} hace que también sea cierto con este item
 * en uso. Aquí sólo queda lo propio de usar un item: duración, sonidos y la pose.
 *
 * <p><b>Disparar no pasa por aquí de forma normal.</b> Minecraft tira los clics de ataque mientras se
 * usa un item, así que {@code TranquilizerClientEvents} los recoge antes y manda
 * {@code FireTranquilizerMsg}, que acaba en {@link #fire(ServerPlayer)}.
 */
public class TranquilizerRifleItem extends Item {

    /** Uno por segundo. */
    public static final int FIRE_COOLDOWN_TICKS = 20;
    private static final float DART_SPEED = 3.5F;
    /** Lo que dura apuntar sin soltar, como el arco. El catalejo para al minuto. */
    private static final int USE_DURATION = 72000;

    public TranquilizerRifleItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    /** La del arco: en tercera persona se ve apuntando con las dos manos, que es como se coge un rifle.
     *  En primera persona da igual, porque al apuntar la mano no se dibuja. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        user.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        user.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        return stack;
    }

    /**
     * Servidor: dispara si el jugador sigue apuntando con el rifle. El cliente no decide nada; sólo
     * avisa del clic. Todo se vuelve a comprobar aquí.
     */
    public static void fire(ServerPlayer player) {
        ItemStack rifle = player.getUseItem();
        if (!player.isUsingItem() || !(rifle.getItem() instanceof TranquilizerRifleItem)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(rifle.getItem())) {
            return;
        }

        Level level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = findAmmo(player);
        if (ammo.isEmpty() && !creative) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 0.6F, 1.4F);
            player.displayClientMessage(Component.translatable(
                    "message.diversityofcritters.tranquilizer.no_darts"), true);
            player.getCooldowns().addCooldown(rifle.getItem(), FIRE_COOLDOWN_TICKS);
            return;
        }

        TranquilizerDartEntity dart = new TranquilizerDartEntity(level, player);
        dart.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, DART_SPEED, 0.0F);
        level.addFreshEntity(dart);

        if (!creative) {
            ammo.shrink(1);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT,
                SoundSource.PLAYERS, 0.8F, 1.6F);
        player.getCooldowns().addCooldown(rifle.getItem(), FIRE_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(rifle.getItem()));
    }

    /** Primero la mano secundaria, como el arco; luego el inventario en orden. */
    private static ItemStack findAmmo(Player player) {
        Item dart = DOCItems.TRANQUILIZER_DART.get();
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(dart)) {
            return offhand;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(dart)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
