package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.CorpseBlockEntity;
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** {@code /doc_corpse <category>}: a fresh corpse of that prey category at your feet. */
public final class CorpseDebugCommand {

    private CorpseDebugCommand() {
    }

    private static String shortName(TagKey<EntityType<?>> category) {
        return category.location().getPath().replace("category/", "");
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_corpse")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                DoCTags.EntityTypes.CATEGORIES.stream().map(CorpseDebugCommand::shortName), builder))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            String name = StringArgumentType.getString(context, "category");
                            TagKey<EntityType<?>> category = DoCTags.EntityTypes.CATEGORIES.stream()
                                    .filter(c -> shortName(c).equals(name)).findFirst().orElse(null);
                            if (category == null) {
                                source.sendFailure(Component.literal("Unknown category: " + name));
                                return 0;
                            }
                            ServerLevel level = source.getLevel();
                            BlockPos pos = BlockPos.containing(source.getPosition());
                            level.setBlock(pos, DOCBlocks.CORPSE.get().defaultBlockState(), 3);
                            if (!(level.getBlockEntity(pos) instanceof CorpseBlockEntity corpse)) {
                                source.sendFailure(Component.literal("Could not place a corpse here."));
                                return 0;
                            }
                            int portions = DOCServerConfig.ecosystem().corpsePortions();
                            corpse.init(category, portions, level.getGameTime());
                            int rot = DOCServerConfig.ecosystem().corpseRotSeconds();
                            source.sendSuccess(() -> Component.literal("Placed a " + name + " corpse with "
                                    + portions + " portions; it rots in " + rot + " s."), true);
                            return 1;
                        })));
    }
}
