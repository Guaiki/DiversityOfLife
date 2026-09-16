package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.SkyAccess;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.Snapshot;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.ReleaseRegistry;
import com.evirapo.diversityofcritters.common.spawn.SpawnBonusFormula;
import com.evirapo.diversityofcritters.common.spawn.SpawnCheckResult;
import com.evirapo.diversityofcritters.common.spawn.SpawnRuleEngine;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /doc_spawn} - diagnostic tool for the spawn system.
 *
 * It answers the two questions that come up while balancing this: "how much bonus have I built
 * up?" and "why is nothing appearing here?".
 */
public class SpawnDebugCommand {

    private static final SuggestionProvider<CommandSourceStack> SPECIES_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(DOCSpecies.values()).map(DOCSpecies::key).toList(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_spawn")
                .requires(source -> source.hasPermission(2))

                .executes(ctx -> summary(ctx.getSource()))

                .then(Commands.literal("here")
                        .executes(ctx -> here(ctx.getSource(), null, false))
                        .then(Commands.literal("details")
                                .executes(ctx -> here(ctx.getSource(), null, true)))
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests(SPECIES_SUGGESTIONS)
                                .executes(ctx -> here(ctx.getSource(), requireSpecies(ctx), false))
                                .then(Commands.literal("details")
                                        .executes(ctx -> here(ctx.getSource(), requireSpecies(ctx), true)))))

                .then(Commands.literal("set")
                        // --- global settings ---
                        .then(Commands.literal("bonusPerRelease")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 10.0D))
                                        .executes(ctx -> apply(ctx.getSource(), "bonusPerRelease",
                                                DOCServerConfig.BONUS_PER_RELEASE,
                                                DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("maxBonus")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                        .executes(ctx -> apply(ctx.getSource(), "maxBonus",
                                                DOCServerConfig.MAX_BONUS,
                                                DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("releaseCap")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100_000))
                                        .executes(ctx -> apply(ctx.getSource(), "releaseCap",
                                                DOCServerConfig.RELEASE_CAP,
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("groupCheckRadius")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> apply(ctx.getSource(), "groupCheckRadius",
                                                DOCServerConfig.GROUP_CHECK_RADIUS,
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("releasedCanDespawn")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> apply(ctx.getSource(), "releasedCanDespawn",
                                                DOCServerConfig.RELEASED_CAN_DESPAWN,
                                                BoolArgumentType.getBool(ctx, "value")))))
                        // --- per-species settings ---
                        .then(speciesSetNode(DOCSpecies.CIVET))
                        .then(speciesSetNode(DOCSpecies.SIFAKA))
                        .then(speciesSetNode(DOCSpecies.SQUIRREL)))

                .then(Commands.literal("max")
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests(SPECIES_SUGGESTIONS)
                                .executes(ctx -> max(ctx.getSource(), requireSpecies(ctx)))))

                .then(Commands.literal("chance")
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests(SPECIES_SUGGESTIONS)
                                .then(Commands.literal("clear")
                                        .executes(ctx -> chance(ctx.getSource(), requireSpecies(ctx), -1.0D)))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 1.0D))
                                        .executes(ctx -> chance(ctx.getSource(), requireSpecies(ctx),
                                                DoubleArgumentType.getDouble(ctx, "value"))))))

                .then(Commands.literal("why")
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests(SPECIES_SUGGESTIONS)
                                .executes(ctx -> why(ctx.getSource(), requireSpecies(ctx)))))

                .then(Commands.literal("trace")
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests(SPECIES_SUGGESTIONS)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> trace(ctx.getSource(), requireSpecies(ctx),
                                                BoolArgumentType.getBool(ctx, "enabled"))))))

                .then(Commands.literal("reset")
                        .then(Commands.literal("confirm")
                                .executes(ctx -> reset(ctx.getSource(), null)))
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests(SPECIES_SUGGESTIONS)
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> reset(ctx.getSource(), requireSpecies(ctx))))))
        );
    }

    // ==============================
    //       CONFIG EDITING
    // ==============================

    private static final SuggestionProvider<CommandSourceStack> SKY_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(SkyAccess.values()).map(Enum::name).toList(), builder);

    /** The {@code /doc_spawn set <species> <key> <value>} branch. */
    private static LiteralArgumentBuilder<CommandSourceStack> speciesSetNode(DOCSpecies species) {
        SpeciesSpawnSettings cfg = DOCServerConfig.settings(species.key());
        String prefix = species.key() + ".";

        return Commands.literal(species.key())
                .then(Commands.literal("enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> apply(ctx.getSource(), prefix + "enabled", cfg.enabled,
                                        BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("spawnChance")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 1.0D))
                                .executes(ctx -> apply(ctx.getSource(), prefix + "spawnChance", cfg.spawnChance,
                                        DoubleArgumentType.getDouble(ctx, "value")))))
                .then(Commands.literal("groupMin")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> apply(ctx.getSource(), prefix + "groupMin", cfg.groupMin,
                                        IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("groupMax")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> apply(ctx.getSource(), prefix + "groupMax", cfg.groupMax,
                                        IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("minY")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-64, 320))
                                .executes(ctx -> apply(ctx.getSource(), prefix + "minY", cfg.minY,
                                        IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("maxY")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-64, 320))
                                .executes(ctx -> apply(ctx.getSource(), prefix + "maxY", cfg.maxY,
                                        IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("maxBlockLight")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 15))
                                .executes(ctx -> apply(ctx.getSource(), prefix + "maxBlockLight", cfg.maxBlockLight,
                                        IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("skyAccess")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests(SKY_SUGGESTIONS)
                                .executes(ctx -> apply(ctx.getSource(), prefix + "skyAccess", cfg.skyAccess,
                                        requireSkyAccess(ctx)))));
    }

    /**
     * Writes the value, persists it and reports the change.
     *
     * Refuses the change when the config is not loaded yet: SERVER configs do not exist until a
     * world is open, and {@code ConfigValue.set()} would blow up.
     */
    private static <T> int apply(CommandSourceStack source, String label,
                                 ForgeConfigSpec.ConfigValue<T> configValue, T value) {
        if (!DOCServerConfig.isLoaded()) {
            source.sendFailure(Component.literal(
                    "Config is not loaded. Server configs only exist once a world is open."));
            return 0;
        }

        T previous = configValue.get();
        DOCServerConfig.set(configValue, value);

        send(source, "§a" + label + "§r: §7" + previous + " §r-> §f" + value);
        send(source, "  §7Written to this world's serverconfig TOML and applied immediately.");

        if (SpawnRuleEngine.hasChanceOverride(DOCSpecies.CIVET)
                || SpawnRuleEngine.hasChanceOverride(DOCSpecies.SIFAKA)
                || SpawnRuleEngine.hasChanceOverride(DOCSpecies.SQUIRREL)) {
            send(source, "  §6Heads up: a chance override is active and takes priority over the config.");
        }

        return 1;
    }

    private static SkyAccess requireSkyAccess(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "value");
        for (SkyAccess option : SkyAccess.values()) {
            if (option.name().equalsIgnoreCase(raw)) return option;
        }
        throw UNKNOWN_SKY_ACCESS.create(raw);
    }

    private static final DynamicCommandExceptionType UNKNOWN_SKY_ACCESS = new DynamicCommandExceptionType(
            value -> Component.literal("Invalid skyAccess: " + value + ". Valid: NONE, REQUIRED, FORBIDDEN"));

    // ==============================
    //       SUBCOMMANDS
    // ==============================

    /** Global summary: state, counter and bonus for every species. */
    private static int summary(CommandSourceStack source) {
        DOCServerConfig.Globals globals = DOCServerConfig.globals();

        send(source, "§e=== DOC SPAWN ===");
        send(source, "§7bonusPerRelease=" + globals.bonusPerRelease()
                + "  maxBonus=" + globals.maxBonus()
                + "  releaseCap=" + globals.releaseCap()
                + "  groupRadius=" + globals.groupCheckRadius());

        for (DOCSpecies species : DOCSpecies.values()) {
            Snapshot cfg = species.settings();
            int released = ReleaseRegistry.cachedCount(species);
            double bonus = SpawnBonusFormula.bonus(released);
            double finalChance = SpawnBonusFormula.finalChance(cfg.spawnChance(), released);

            send(source, "");
            send(source, "§b" + species.key() + "§r  " + (cfg.enabled() ? "§aenabled" : "§cdisabled"));
            send(source, "  §7released: §f" + released + "/" + globals.releaseCap()
                    + "   §7bonus: §f+" + Math.round(bonus * 100) + "%");
            if (SpawnRuleEngine.hasChanceOverride(species)) {
                send(source, String.format("  §6chance: §f%.3f §7(OVERRIDE — bonus ignored)",
                        SpawnRuleEngine.chanceOverride(species)));
            } else {
                send(source, String.format("  §7chance: §f%.3f §7-> §f%.3f", cfg.spawnChance(), finalChance));
            }
            send(source, "  §7Y " + cfg.minY() + ".." + cfg.maxY()
                    + "   light<=" + cfg.maxBlockLight()
                    + "   sky=" + cfg.skyAccess()
                    + "   groupMax=" + cfg.groupMax());
            if (SpawnRuleEngine.isTracing(species)) send(source, "  §dtrace: ON");
        }

        return 1;
    }

    /**
     * Spawn and release status at the player's position.
     *
     * By default it shows only what matters to whoever is playing: whether it appears here, at what
     * chance, how many have been released and how much bonus that grants. The filter-by-filter
     * breakdown sits behind {@code details}, because that is debugging information.
     */
    private static int here(CommandSourceStack source, @Nullable DOCSpecies only, boolean detailed) {
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        BlockPos pos = BlockPos.containing(position);
        int cap = DOCServerConfig.globals().releaseCap();

        for (DOCSpecies species : DOCSpecies.values()) {
            if (only != null && species != only) continue;

            SpawnRuleEngine.SpawnDiagnostic diagnostic = SpawnRuleEngine.diagnose(species, level, pos);

            send(source, "");
            send(source, "§e=== " + species.displayName() + " ===");
            send(source, " §7Location:      §f"
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());

            if (diagnostic.wouldSpawn()) {
                send(source, " §7Can spawn here: §aYES");
            } else {
                send(source, " §7Can spawn here: §cNO §7— " + diagnostic.firstFailure().description());
            }

            send(source, String.format(" §7Spawn chance:  §f%d%%",
                    Math.round(diagnostic.chanceFinal() * 100.0D)));
            send(source, " §7Released:      §f" + diagnostic.released() + " §7/ §f" + cap);
            send(source, String.format(" §7Release bonus: §f+%d%%",
                    Math.round(diagnostic.bonus() * 100.0D)));

            if (SpawnRuleEngine.hasChanceOverride(species)) {
                send(source, " §6Chance override active — the release bonus is being ignored.");
            }

            if (detailed) {
                send(source, " §7" + "-".repeat(40));
                for (SpawnRuleEngine.DiagnosticLine line : diagnostic.lines()) {
                    send(source, (line.passed() ? " §a[OK]  §r" : " §c[FAIL]§r")
                            + " " + pad(line.label()) + " " + line.detail());
                }
            }
        }

        return 1;
    }

    /**
     * Sets the species to maximum output: chance at 100% and the release counter at its cap.
     *
     * The other filters (biome, height, light, ground, density) still apply. Skipping them would
     * make the animal appear in biomes that are not its own, which is exactly what we do not want
     * to test.
     */
    private static int max(CommandSourceStack source, DOCSpecies species) {
        SpawnRuleEngine.setChanceOverride(species, 1.0D);

        int cap = DOCServerConfig.globals().releaseCap();
        if (source.getServer() != null) {
            ReleaseRegistry.get(source.getServer()).debugSetCount(species, cap);
        }

        send(source, "§a" + species.key() + " set to maximum spawn.");
        send(source, "  §7chance: §f100%§7 (override)   released: §f" + cap + "/" + cap);
        send(source, "  §7Biome, Y, light, ground and density filters still apply.");
        send(source, "  §7Undo with: §f/doc_spawn chance " + species.key() + " clear");
        send(source, "  §7The release counter is persistent — clear it with: §f/doc_spawn reset "
                + species.key() + " confirm");

        return 1;
    }

    /** Temporary override of the final chance, or {@code clear} to remove it. */
    private static int chance(CommandSourceStack source, DOCSpecies species, double value) {
        SpawnRuleEngine.setChanceOverride(species, value);

        if (value < 0.0D) {
            send(source, "Chance override cleared for " + species.key()
                    + ". Back to config + release bonus.");
        } else {
            send(source, String.format("§a%s spawn chance overridden to %.0f%%.",
                    species.key(), value * 100.0D));
            send(source, "  §7Temporary: not saved to the config, gone when the server stops.");
            send(source, "  §7While set, the release bonus is ignored for this species.");
        }

        return 1;
    }

    /** Dumps the buffer of the most recent real spawn attempts. */
    private static int why(CommandSourceStack source, DOCSpecies species) {
        if (!SpawnRuleEngine.isTracing(species)) {
            source.sendFailure(Component.literal(
                    "Trace is off for " + species.key() + ". Enable it with: /doc_spawn trace "
                            + species.key() + " true"));
            return 0;
        }

        List<SpawnCheckResult> results = SpawnRuleEngine.trace(species);
        if (results.isEmpty()) {
            send(source, "§7No spawn attempts recorded yet for " + species.key()
                    + ". Travel into ungenerated chunks in its biome.");
            return 1;
        }

        send(source, "§e=== DOC SPAWN TRACE — " + species.key()
                + " (" + results.size() + " most recent) ===");

        for (SpawnCheckResult result : results) {
            BlockPos pos = result.pos();
            send(source, (result.allowed() ? " §a[OK]  §r" : " §c[FAIL]§r")
                    + " " + pad(result.reason().name())
                    + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + "  §7" + result.detail());
        }

        return 1;
    }

    private static int trace(CommandSourceStack source, DOCSpecies species, boolean enabled) {
        SpawnRuleEngine.setTracing(species, enabled);
        send(source, "Trace for " + species.key() + " is now " + (enabled ? "§aON" : "§cOFF"));
        if (enabled) {
            send(source, "§7Read it back with: /doc_spawn why " + species.key());
        }
        return 1;
    }

    private static int reset(CommandSourceStack source, @Nullable DOCSpecies species) {
        if (source.getServer() == null) return 0;

        ReleaseRegistry registry = ReleaseRegistry.get(source.getServer());

        if (species == null) {
            registry.resetAll();
            send(source, "§cRelease counters reset for ALL species.");
        } else {
            registry.reset(species);
            send(source, "§cRelease counter reset for " + species.key() + ".");
        }

        return 1;
    }

    // ==============================
    //       HELPERS
    // ==============================

    private static final DynamicCommandExceptionType UNKNOWN_SPECIES = new DynamicCommandExceptionType(
            key -> Component.literal("Unknown species: " + key + ". Valid: civet, sifaka, squirrel"));

    private static DOCSpecies requireSpecies(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        String key = StringArgumentType.getString(ctx, "species");
        DOCSpecies species = DOCSpecies.byKey(key);
        if (species == null) throw UNKNOWN_SPECIES.create(key);
        return species;
    }

    private static String pad(String label) {
        StringBuilder sb = new StringBuilder(label);
        while (sb.length() < 18) sb.append('.');
        return sb.toString();
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
