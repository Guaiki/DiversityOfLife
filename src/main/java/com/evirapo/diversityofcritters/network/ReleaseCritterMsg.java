package com.evirapo.diversityofcritters.network;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
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

            // No per-animal anti-farming needed any more: taming took this animal out of the
            // population, releasing puts it back. Tame-release loops net zero. What really adds is
            // releasing an animal born in captivity, which is the point; the cap bounds it.
            WildPopulation population = WildPopulation.get(server);
            population.apply(species, PopulationEvent.RELEASE, server);

            critter.setWasReleased(true);
            critter.setInWild(true);
            untame(ta);

            player.sendSystemMessage(feedback(ta, species, population));
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

    private static Component feedback(TamableAnimal ta, DOCSpecies species, WildPopulation population) {
        Component name = ta.getDisplayName();
        int count = population.get(species);

        if (count >= DOCServerConfig.population().cap()) {
            return Component.translatable("message.diversityofcritters.release.capped",
                    name, species.displayName());
        }

        return Component.translatable("message.diversityofcritters.release.counted",
                name, species.displayName(), count, SpawnBonusFormula.bonusLabel(count));
    }
}
