package com.evirapo.diversityofcritters.network;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.ReleaseRegistry;
import com.evirapo.diversityofcritters.common.spawn.SpawnBonusFormula;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReleaseCritterMsg {
    private final int entityId;

    public ReleaseCritterMsg(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(ReleaseCritterMsg msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static ReleaseCritterMsg decode(FriendlyByteBuf buf) {
        return new ReleaseCritterMsg(buf.readInt());
    }

    public int getEntityId() {
        return entityId;
    }

    public static void handle(ReleaseCritterMsg msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity e = player.level().getEntity(msg.getEntityId());
            if (!(e instanceof TamableAnimal ta) || !ta.isOwnedBy(player)) return;

            DOCSpecies species = DOCSpecies.of(ta);
            MinecraftServer server = player.getServer();

            // Species outside the system (the lion) or no server: release without counting.
            if (species == null || server == null || !(ta instanceof DiverseCritter critter)) {
                untame(ta);
                player.displayClientMessage(
                        ta.getDisplayName().copy().append(Component.literal(" was released")),
                        true
                );
                return;
            }

            ReleaseRegistry registry = ReleaseRegistry.get(server);

            // Twofold anti-farming safeguard: the entity's own flag saves us from touching the
            // registry in the common case, and the registry covers animals saved before the flag
            // existed.
            ReleaseRegistry.Result result = critter.wasReleased()
                    ? ReleaseRegistry.Result.DUPLICATE
                    : registry.register(species, ta.getUUID());

            critter.setWasReleased(true);
            untame(ta);

            player.sendSystemMessage(feedback(result, ta, species, registry));
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Returns the animal to its wild state.
     *
     * We deliberately do NOT call setPersistenceRequired(): that flag is private in Mob and cannot
     * be reverted, and it would also override the wild-fauna despawn implemented by
     * {@link DiverseCritter#removeWhenFarAway(double)}.
     */
    private static void untame(TamableAnimal ta) {
        ta.setTame(false);
        ta.setOwnerUUID(null);
        ta.setOrderedToSit(false);
        ta.setInSittingPose(false);
    }

    private static Component feedback(ReleaseRegistry.Result result, TamableAnimal ta,
                                      DOCSpecies species, ReleaseRegistry registry) {
        Component name = ta.getDisplayName();

        return switch (result) {
            case COUNTED -> {
                int count = registry.getCount(species);
                int cap = DOCServerConfig.globals().releaseCap();
                int bonusPercent = (int) Math.round(SpawnBonusFormula.bonus(count) * 100.0D);

                yield Component.translatable("message.diversityofcritters.release.counted",
                        name, species.displayName(), count, cap, bonusPercent);
            }
            case DUPLICATE -> Component.translatable("message.diversityofcritters.release.duplicate", name);
            case CAPPED -> Component.translatable("message.diversityofcritters.release.capped",
                    name, species.displayName());
        };
    }
}
