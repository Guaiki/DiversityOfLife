package com.evirapo.diversityofcritters.client.event;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.item.custom.TranquilizerRifleItem;
import com.evirapo.diversityofcritters.network.DOCNetworkHandler;
import com.evirapo.diversityofcritters.network.FireTranquilizerMsg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Clic izquierdo apuntando con el rifle = disparar.
 *
 * <p><b>Por qué en el tick y no en un evento de input.</b> Mientras se usa un item,
 * {@code Minecraft.handleKeybinds()} vacía los clics de {@code keyAttack} sin hacer nada con ellos, y
 * ni siquiera llega a lanzar {@code InputEvent.InteractionKeyMappingTriggered}. Pero
 * {@code ClientTickEvent} en fase START corre antes que eso en el mismo tick, así que aquí los clics
 * siguen en la cola: se consumen y cada tanda se convierte en un disparo. Usar el keymapping y no el
 * botón del ratón en crudo respeta si el jugador lo ha reasignado.
 */
@Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TranquilizerClientEvents {

    private TranquilizerClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || mc.getOverlay() != null || !player.isUsingItem()) {
            return;
        }
        ItemStack rifle = player.getUseItem();
        if (!(rifle.getItem() instanceof TranquilizerRifleItem)) {
            return;
        }

        boolean clicked = false;
        while (mc.options.keyAttack.consumeClick()) {
            clicked = true;
        }
        // El servidor vuelve a mirar el cooldown; mirarlo aquí sólo ahorra paquetes de más.
        if (clicked && !player.getCooldowns().isOnCooldown(rifle.getItem())) {
            DOCNetworkHandler.CHANNEL.sendToServer(new FireTranquilizerMsg());
        }
    }
}
