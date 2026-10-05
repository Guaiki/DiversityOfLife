package com.evirapo.diversityofcritters.network;

import com.evirapo.diversityofcritters.common.item.custom.TranquilizerRifleItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor: "he hecho clic izquierdo apuntando con el rifle". Sin carga, a propósito: la
 * dirección, la munición y el cooldown los mira el servidor con lo que ya sabe del jugador, así que un
 * cliente no puede disparar desde donde no está ni saltarse la cadencia.
 */
public class FireTranquilizerMsg {

    public static void encode(FireTranquilizerMsg msg, FriendlyByteBuf buf) {
    }

    public static FireTranquilizerMsg decode(FriendlyByteBuf buf) {
        return new FireTranquilizerMsg();
    }

    public static void handle(FireTranquilizerMsg msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null) {
                TranquilizerRifleItem.fire(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
