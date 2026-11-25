package com.evirapo.diversityofcritters.common.config;

import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.Defaults;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.SkyAccess;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.Snapshot;
import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Collections;
import java.util.EnumMap;
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
            new Defaults(0.45D, 2, 4, 55, 140, 7, SkyAccess.NONE, SpeciesSpawnSettings.BASE_STARTING_POPULATION);

    /** Verreaux's sifaka: savanna, diurnal, open country. Critically endangered in reality, so it starts endangered. */
    private static final Defaults SIFAKA_DEFAULTS =
            new Defaults(0.05D, 2, 4, 60, 120, 15, SkyAccess.REQUIRED, 8);

    /** Red and white giant flying squirrel: forest and taiga, arboreal. */
    private static final Defaults SQUIRREL_DEFAULTS =
            new Defaults(0.65D, 2, 4, 60, 130, 12, SkyAccess.NONE, SpeciesSpawnSettings.BASE_STARTING_POPULATION);

    // ==============================
    //       SPEC
    // ==============================

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue MAX_BONUS;
    public static final ForgeConfigSpec.IntValue GROUP_CHECK_RADIUS;
    public static final ForgeConfigSpec.BooleanValue RELEASED_CAN_DESPAWN;

    public static final ForgeConfigSpec.IntValue BASE_POPULATION;
    public static final ForgeConfigSpec.IntValue POPULATION_FLOOR;
    public static final ForgeConfigSpec.IntValue POPULATION_CAP;
    public static final ForgeConfigSpec.IntValue ENDANGERED_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue WILD_BREEDING_RATE;

    public static final ForgeConfigSpec.IntValue SMALL_THIRST_FROM_FOOD;
    public static final ForgeConfigSpec.IntValue LARGE_THIRST_FROM_FOOD;
    public static final ForgeConfigSpec.IntValue COMFORT_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue COMFORT_SCAN_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue LIVESTOCK_ENABLED;
    public static final ForgeConfigSpec.IntValue LIVESTOCK_CAP;
    public static final ForgeConfigSpec.IntValue LIVESTOCK_CAP_RADIUS;
    public static final ForgeConfigSpec.IntValue LIVESTOCK_BREED_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue CORPSE_PORTIONS;
    public static final ForgeConfigSpec.IntValue CORPSE_ROT_SECONDS;

    private static final Map<String, SpeciesSpawnSettings> SETTINGS = new LinkedHashMap<>();
    private static final Map<PopulationEvent, ForgeConfigSpec.IntValue> WEIGHTS = new EnumMap<>(PopulationEvent.class);

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Natural spawning and the release-to-wild system.").push("spawn");

        MAX_BONUS = builder
                .comment("Cap on the spawn bonus a thriving population gives. 2.0 = at most triple the",
                         "base chance, reached at 3 x basePopulation.")
                .defineInRange("maxBonus", 2.0D, 0.0D, 100.0D);

        GROUP_CHECK_RADIUS = builder
                .comment("Radius in blocks used to count nearby animals of the same species",
                         "and compare them against groupMax.")
                .defineInRange("groupCheckRadius", 24, 1, 128);

        RELEASED_CAN_DESPAWN = builder
                .comment("If true, a released animal despawns again like wild fauna.",
                         "If false, it stays in the world forever (vanilla passive behaviour).",
                         "Does not affect tamed animals nor naturally spawned ones.")
                .define("releasedCanDespawn", true);

        builder.comment("Wild population model: taming, killing and harsh habitats lower it,",
                        "releasing and wild births raise it, and the spawn chance follows it.").push("population");

        BASE_POPULATION = builder
                .comment("Starting population of every species, and the population at which it spawns at",
                         "exactly its base spawnChance. Each animal is worth 1/basePopulation of the base chance.")
                .defineInRange("basePopulation", 20, 1, 1000);

        POPULATION_FLOOR = builder
                .comment("Lowest the population can fall. 0 enables true extinction: at 0 the species stops",
                         "spawning naturally. Spawn eggs and breeding keep working regardless.")
                .defineInRange("populationFloor", 5, 0, 1000);

        POPULATION_CAP = builder
                .comment("Highest the population can climb. With basePopulation = 20 and maxBonus = 2.0,",
                         "60 is exactly where the spawn bonus saturates.")
                .defineInRange("populationCap", 60, 1, 100_000);

        ENDANGERED_THRESHOLD = builder
                .comment("Below this population the species is endangered and every player is warned.")
                .defineInRange("endangeredThreshold", 10, 0, 1000);

        WILD_BREEDING_RATE = builder
                .comment("Multiplier on how often wild animals breed. 0 = wild animals never breed.",
                         "Captive breeding is unaffected.")
                .defineInRange("wildBreedingRate", 1.0D, 0.0D, 10.0D);

        builder.comment("How far each event moves the population. 0 = the event is not counted.").push("weights");
        for (PopulationEvent event : PopulationEvent.values()) {
            WEIGHTS.put(event, builder.defineInRange(event.configKey(), 1, 0, 100));
        }
        builder.pop();

        builder.pop();

        SETTINGS.put("civet",    new SpeciesSpawnSettings(builder, "civet",    "banded palm civet",   CIVET_DEFAULTS));
        SETTINGS.put("sifaka",   new SpeciesSpawnSettings(builder, "sifaka",   "Verreaux's sifaka",   SIFAKA_DEFAULTS));
        SETTINGS.put("squirrel", new SpeciesSpawnSettings(builder, "squirrel", "giant flying squirrel",  SQUIRREL_DEFAULTS));

        builder.pop();

        EcosystemSettings d = EcosystemSettings.DEFAULTS;
        builder.comment("Ecosystem mechanics shared by every critter.").push("ecosystem");
        SMALL_THIRST_FROM_FOOD = builder
                .comment("Percent of max thirst a small animal recovers each time it eats.")
                .defineInRange("smallThirstFromFood", d.smallThirstFromFood(), 0, 100);
        LARGE_THIRST_FROM_FOOD = builder
                .comment("Percent of max thirst a large animal recovers each time it eats.")
                .defineInRange("largeThirstFromFood", d.largeThirstFromFood(), 0, 100);
        COMFORT_SEARCH_RADIUS = builder
                .comment("How far, in blocks, an uncomfortable animal looks for a spot with better",
                        "temperature and humidity.")
                .defineInRange("comfortSearchRadius", d.comfortSearchRadius(), 4, 64);
        COMFORT_SCAN_COOLDOWN_TICKS = builder
                .comment("Ticks between two searches for a better spot. Lower = quicker, but more work.")
                .defineInRange("comfortScanCooldownTicks", d.comfortScanCooldownTicks(), 20, 1200);
        LIVESTOCK_ENABLED = builder
                .comment("Cows, pigs, sheep and chickens breed on their own and are capped per area.")
                .define("livestockEnabled", d.livestockEnabled());
        LIVESTOCK_CAP = builder
                .comment("Most animals of one livestock type (cow, pig, sheep or chicken) in one area.",
                        "Natural spawns and self-breeding stop at this number; breeding by players does not.")
                .defineInRange("livestockCap", d.livestockCap(), 1, 64);
        LIVESTOCK_CAP_RADIUS = builder
                .comment("Radius, in blocks, of the area the livestock cap counts.")
                .defineInRange("livestockCapRadius", d.livestockCapRadius(), 8, 128);
        LIVESTOCK_BREED_INTERVAL_SECONDS = builder
                .comment("How often, in seconds, an adult livestock animal may try to breed on its own.")
                .defineInRange("livestockBreedIntervalSeconds", d.livestockBreedIntervalSeconds(), 1, 72000);
        CORPSE_PORTIONS = builder
                .comment("Meals a fresh corpse holds.")
                .defineInRange("corpsePortions", d.corpsePortions(), 1, 16);
        CORPSE_ROT_SECONDS = builder
                .comment("Seconds before a corpse rots away, eaten or not.")
                .defineInRange("corpseRotSeconds", d.corpseRotSeconds(), 1, 72000);
        builder.pop();

        SPEC = builder.build();
    }

    // ==============================
    //       CACHE
    // ==============================

    /** Already-resolved global settings. */
    public record Globals(double maxBonus, int groupCheckRadius, boolean releasedCanDespawn) {}

    private static final Globals DEFAULT_GLOBALS = new Globals(2.0D, 24, true);

    private static final Map<String, Snapshot> DEFAULT_SPECIES = Map.of(
            "civet",    CIVET_DEFAULTS.toSnapshot(),
            "sifaka",   SIFAKA_DEFAULTS.toSnapshot(),
            "squirrel", SQUIRREL_DEFAULTS.toSnapshot()
    );

    private static final PopulationSettings DEFAULT_POPULATION =
            new PopulationSettings(20, 5, 60, 10, 1.0D, defaultWeights());

    private static Map<PopulationEvent, Integer> defaultWeights() {
        Map<PopulationEvent, Integer> weights = new EnumMap<>(PopulationEvent.class);
        for (PopulationEvent event : PopulationEvent.values()) weights.put(event, 1);
        return Collections.unmodifiableMap(weights);
    }

    private static volatile Globals globals = DEFAULT_GLOBALS;
    private static volatile PopulationSettings population = DEFAULT_POPULATION;
    private static volatile Map<String, Snapshot> species = DEFAULT_SPECIES;
    private static volatile EcosystemSettings ecosystem = EcosystemSettings.DEFAULTS;

    /**
     * Rebuilds the cache from the spec's current values.
     * Called when the world's config is loaded and when it is reloaded.
     */
    static void bake() {
        globals = new Globals(
                MAX_BONUS.get(),
                GROUP_CHECK_RADIUS.get(),
                RELEASED_CAN_DESPAWN.get()
        );

        Map<String, Snapshot> baked = new LinkedHashMap<>();
        SETTINGS.forEach((key, settings) -> baked.put(key, settings.bake()));
        species = Collections.unmodifiableMap(baked);

        Map<PopulationEvent, Integer> weights = new EnumMap<>(PopulationEvent.class);
        WEIGHTS.forEach((event, value) -> weights.put(event, value.get()));
        population = new PopulationSettings(
                BASE_POPULATION.get(),
                POPULATION_FLOOR.get(),
                POPULATION_CAP.get(),
                ENDANGERED_THRESHOLD.get(),
                WILD_BREEDING_RATE.get(),
                Collections.unmodifiableMap(weights)
        );

        ecosystem = new EcosystemSettings(
                SMALL_THIRST_FROM_FOOD.get(),
                LARGE_THIRST_FROM_FOOD.get(),
                COMFORT_SEARCH_RADIUS.get(),
                COMFORT_SCAN_COOLDOWN_TICKS.get(),
                LIVESTOCK_ENABLED.get(),
                LIVESTOCK_CAP.get(),
                LIVESTOCK_CAP_RADIUS.get(),
                LIVESTOCK_BREED_INTERVAL_SECONDS.get(),
                CORPSE_PORTIONS.get(),
                CORPSE_ROT_SECONDS.get()
        );
    }

    /** Falls back to the defaults when a world's config is unloaded. */
    static void unbake() {
        globals = DEFAULT_GLOBALS;
        species = DEFAULT_SPECIES;
        population = DEFAULT_POPULATION;
        ecosystem = EcosystemSettings.DEFAULTS;
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

    /** {@code [ecosystem]}. Never null: falls back to the defaults outside a world. */
    public static EcosystemSettings ecosystem() {
        return ecosystem;
    }

    public static boolean releasedCanDespawn() {
        return globals.releasedCanDespawn();
    }

    /** {@code [spawn.population]}. Never null: falls back to the defaults outside a world. */
    public static PopulationSettings population() {
        return population;
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

    /** An event's weight handle, for {@code /doc_spawn set weights}. Writing only. */
    public static ForgeConfigSpec.IntValue weightValue(PopulationEvent event) {
        return WEIGHTS.get(event);
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
