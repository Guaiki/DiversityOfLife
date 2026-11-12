package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.spawn.LivestockRules;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/** {@code /doc_livestock}: livestock around you against the per-area cap. */
public final class LivestockDebugCommand {

    private static final List<EntityType<?>> TYPES =
            List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN);

    private LivestockDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_livestock")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    BlockPos pos = BlockPos.containing(source.getPosition());
                    var eco = DOCServerConfig.ecosystem();
                    StringBuilder out = new StringBuilder("Livestock within " + eco.livestockCapRadius()
                            + " blocks (cap " + eco.livestockCap() + ", self-breeding every "
                            + eco.livestockBreedIntervalSeconds() + " s"
                            + (eco.livestockEnabled() ? ")" : ", DISABLED)") + ":");
                    for (EntityType<?> type : TYPES) {
                        int count = LivestockRules.countNearby(source.getLevel(), type, pos);
                        out.append("\n  ").append(type.getDescription().getString()).append(": ")
                                .append(count).append(" / ").append(eco.livestockCap());
                    }
                    String text = out.toString();
                    source.sendSuccess(() -> Component.literal(text), false);
                    return 1;
                }));
    }
}
