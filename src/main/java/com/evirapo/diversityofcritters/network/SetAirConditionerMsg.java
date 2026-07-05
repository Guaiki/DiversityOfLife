package com.evirapo.diversityofcritters.network;

import com.evirapo.diversityofcritters.common.block.entity.AirConditionerBlockEntity;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetAirConditionerMsg {
    private final BlockPos pos;
    private final int tierId;
    private final int size;
    private final boolean snowEnabled;
    private final boolean iceEnabled;

    public SetAirConditionerMsg(BlockPos pos, int tierId, int size, boolean snowEnabled, boolean iceEnabled) {
        this.pos = pos;
        this.tierId = tierId;
        this.size = size;
        this.snowEnabled = snowEnabled;
        this.iceEnabled = iceEnabled;
    }

    public static void encode(SetAirConditionerMsg msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.tierId);
        buf.writeVarInt(msg.size);
        buf.writeBoolean(msg.snowEnabled);
        buf.writeBoolean(msg.iceEnabled);
    }

    public static SetAirConditionerMsg decode(FriendlyByteBuf buf) {
        return new SetAirConditionerMsg(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(SetAirConditionerMsg msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player == null) return;

            var level = player.level();
            if (!level.isLoaded(msg.pos)) return;
            if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64.0) return;

            if (level.getBlockEntity(msg.pos) instanceof AirConditionerBlockEntity acEntity
                    && AirConditionerBlockEntity.isValidSize(msg.size)) {
                acEntity.setSettings(TemperatureTier.byId(msg.tierId), msg.size, msg.snowEnabled, msg.iceEnabled);
            }
        });
        ctx.setPacketHandled(true);
    }
}
