package com.evirapo.diversityofcritters.common.config;

import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.Defaults;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.SkyAccess;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.Snapshot;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's SERVER-type configuration.
 *
 * Lives in {@code saves/<world>/serverconfig/diversityofcritters-server.toml}, so every save can
 * have its own values and it is synced to the client in multiplayer.
 *
 * Values are read through this class' static methods, which return an immutable cache rebuilt on
 * every config load or reload. Never call {@code ConfigValue.get()} directly from the spawn cycle.
 */
public final class DOCServerConfig {

    // ==============================
    //       PER-SPECIES DEFAULTS
    // ==============================

    /** Banded palm civet: jungle, nocturnal, arboreal. */
    private static final Defaults CIVET_DEFAULTS =
            new Defaults(0.35D, 2, 4, 55, 140, 7, SkyAccess.NONE);

    /** Verreaux's sifaka: savanna, diurnal, open country. */
    private static final Defaults SIFAKA_DEFAULTS =
            new Defaults(0.30D, 2, 4, 60, 120, 15, SkyAccess.REQUIRED);

    /** Red and white giant flying squirrel: forest and taiga, arboreal. */
    private static final Defaults SQUIRREL_DEFAULTS =
            new Defaults(0.30D, 2, 4, 60, 130, 12, SkyAccess.NONE);

    // ==============================
    //       SPEC
    // ==============================

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue BONUS_PER_RELEASE;
    public static final ForgeConfigSpec.DoubleValue MAX_BONUS;
    public static final ForgeConfigSpec.IntValue RELEASE_CAP;
    public static final ForgeConfigSpec.IntValue GROUP_CHECK_RADIUS;
    public static final ForgeConfigSpec.BooleanValue RELEASED_CAN_DESPAWN;

    private static final Map<String, SpeciesSpawnSettings> SETTINGS = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Natural spawning and the release-to-wild system.").push("spawn");

        BONUS_PER_RELEASE = builder
                .comment("Spawn bonus contributed by each released animal, as an additive multiplier.",
                         "0.05 = +5% of the base chance per animal.")
                .defineInRange("bonusPerRelease", 0.05D, 0.0D, 10.0D);

        MAX_BONUS = builder
                .comment("Cap on the accumulated bonus. 2.0 = at most triple the base chance.")
                .defineInRange("maxBonus", 2.0D, 0.0D, 100.0D);

        RELEASE_CAP = builder
                .comment("Hard cap on the released-animal counter, per species.",
                         "Once reached, releasing more animals no longer raises the counter.")
                .defineInRange("releaseCap", 50, 0, 100_000);

        GROUP_CHECK_RADIUS = builder
                .comment("Radius in blocks used to count nearby animals of the same species",
                         "and compare them against groupMax.")
                .defineInRange("groupCheckRadius", 24, 1, 128);

        RELEASED_CAN_DESPAWN = builder
                .comment("If true, a released animal despawns again like wild fauna.",
                         "If false, it stays in the world forever (vanilla passive behaviour).",
                         "Does not affect tamed animals nor naturally spawned ones.")
                .define("releasedCanDespawn", true);

        SETTINGS.put("civet",    new SpeciesSpawnSettings(builder, "civet",    "banded palm civet",   CIVET_DEFAULTS));
        SETTINGS.put("sifaka",   new SpeciesSpawnSettings(builder, "sifaka",   "Verreaux's sifaka",   SIFAKA_DEFAULTS));
        SETTINGS.put("squirrel", new SpeciesSpawnSettings(builder, "squirrel", "giant flying squirrel",  SQUIRREL_DEFAULTS));

        builder.pop();

        SPEC = builder.build();
    }

    // ==============================
    //       CACHE
    // ==============================

    /** Already-resolved global settings. */
    public record Globals(double bonusPerRelease, double maxBonus, int releaseCap,
                          int groupCheckRadius, boolean releasedCanDespawn) {}

    private static final Globals DEFAULT_GLOBALS = new Globals(0.05D, 2.0D, 50, 24, true);

    private static final Map<String, Snapshot> DEFAULT_SPECIES = Map.of(
            "civet",    CIVET_DEFAULTS.toSnapshot(),
            "sifaka",   SIFAKA_DEFAULTS.toSnapshot(),
            "squirrel", SQUIRREL_DEFAULTS.toSnapshot()
    );

    private static volatile Globals globals = DEFAULT_GLOBALS;
    private static volatile Map<String, Snapshot> species = DEFAULT_SPECIES;

    /**
     * Rebuilds the cache from the spec's current values.
     * Called when the world's config is loaded and when it is reloaded.
     */
    static void bake() {
        globals = new Globals(
                BONUS_PER_RELEASE.get(),
                MAX_BONUS.get(),
                RELEASE_CAP.get(),
                GROUP_CHECK_RADIUS.get(),
                RELEASED_CAN_DESPAWN.get()
        );

        Map<String, Snapshot> baked = new LinkedHashMap<>();
        SETTINGS.forEach((key, settings) -> baked.put(key, settings.bake()));
        species = Collections.unmodifiableMap(baked);
    }

    /** Falls back to the defaults when a world's config is unloaded. */
    static void unbake() {
        globals = DEFAULT_GLOBALS;
        species = DEFAULT_SPECIES;
    }

    // ==============================
    //       READ ACCESS
    // ==============================

    public static Globals globals() {
        return globals;
    }

    /**
     * One species' settings. Never returns null: if the config has not been loaded yet
     * (datagen, early startup) it returns the default values.
     */
    public static Snapshot forSpecies(String key) {
        Snapshot snapshot = species.get(key);
        if (snapshot != null) return snapshot;

        Snapshot fallback = DEFAULT_SPECIES.get(key);
        if (fallback != null) return fallback;

        throw new IllegalArgumentException("Unknown species in the spawn config: " + key);
    }

    public static boolean releasedCanDespawn() {
        return globals.releasedCanDespawn();
    }

    // ==============================
    //       WRITE ACCESS (commands)
    // ==============================

    /**
     * A species' {@code ConfigValue} handles, so the editing commands can write to them.
     *
     * Careful: to READ use {@link #forSpecies(String)}, which returns the cache. This is for
     * writing only.
     */
    public static SpeciesSpawnSettings settings(String key) {
        SpeciesSpawnSettings settings = SETTINGS.get(key);
        if (settings == null) {
            throw new IllegalArgumentException("Unknown species in the spawn config: " + key);
        }
        return settings;
    }

    /** False before entering a world: SERVER configs do not exist until a save is open. */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    /**
     * Writes a value, persists it to the world's TOML and refreshes the cache.
     *
     * The explicit rebake is needed because we cannot rely on Forge's file watcher firing the
     * reload event in time, and we want the change to take effect on the same tick.
     */
    public static <T> void set(ForgeConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        SPEC.save();
        bake();
    }

    private DOCServerConfig() {}
}
