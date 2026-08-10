package com.evirapo.diversityofcritters.network;

import com.evirapo.diversityofcritters.common.block.entity.HumidifierBlockEntity;
import com.evirapo.diversityofcritters.common.humidity.HumidityTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetHumidifierMsg {
    private final BlockPos pos;
    private final int tierId;
    private final int size;

    public SetHumidifierMsg(BlockPos pos, int tierId, int size) {
        this.pos = pos;
        this.tierId = tierId;
        this.size = size;
    }

    public static void encode(SetHumidifierMsg msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.tierId);
        buf.writeVarInt(msg.size);
    }

    public static SetHumidifierMsg decode(FriendlyByteBuf buf) {
        return new SetHumidifierMsg(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SetHumidifierMsg msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player == null) return;

            var level = player.level();
            if (!level.isLoaded(msg.pos)) return;
            if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64.0) return;

            if (level.getBlockEntity(msg.pos) instanceof HumidifierBlockEntity humidifierEntity
                    && HumidifierBlockEntity.isValidSize(msg.size)) {
                humidifierEntity.setSettings(HumidityTier.byId(msg.tierId), msg.size);
            }
        });
        ctx.setPacketHandled(true);
    }
}
