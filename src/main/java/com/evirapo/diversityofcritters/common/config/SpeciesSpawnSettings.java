package com.evirapo.diversityofcritters.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The spawn settings block for one species.
 *
 * Instantiated once per species inside {@link DOCServerConfig}, each one in its own TOML
 * subsection ({@code [spawn.civet]}, {@code [spawn.sifaka]}, ...).
 */
public final class SpeciesSpawnSettings {

    /** Sky visibility requirement at the spawn position. */
    public enum SkyAccess {
        /** Don't care. */
        NONE,
        /** The block must see the sky (open-country species). */
        REQUIRED,
        /** The block must NOT see the sky (canopy or cave species). */
        FORBIDDEN
    }

    /** Marks a species that starts at the shared {@code basePopulation}. */
    public static final int BASE_STARTING_POPULATION = -1;

    /**
     * A species' default values, used both by the spec and by the initial cache.
     * {@code startingPopulation} is {@link #BASE_STARTING_POPULATION} to start at the shared base.
     */
    public record Defaults(double spawnChance, int groupMin, int groupMax,
                           int minY, int maxY, int maxBlockLight, SkyAccess skyAccess, int startingPopulation) {

        public Snapshot toSnapshot() {
            return new Snapshot(true, spawnChance, groupMin, groupMax, minY, maxY, maxBlockLight, skyAccess, startingPopulation);
        }
    }

    /**
     * An immutable copy of the already-resolved values.
     *
     * It exists so we never call {@code ConfigValue.get()} inside {@code checkSpawnRules}, a hot
     * path invoked hundreds of times per tick during the natural spawn cycle.
     */
    public record Snapshot(boolean enabled, double spawnChance, int groupMin, int groupMax,
                           int minY, int maxY, int maxBlockLight, SkyAccess skyAccess, int startingPopulation) {}

    public final ForgeConfigSpec.BooleanValue enabled;
    public final ForgeConfigSpec.DoubleValue spawnChance;
    public final ForgeConfigSpec.IntValue groupMin;
    public final ForgeConfigSpec.IntValue groupMax;
    public final ForgeConfigSpec.IntValue minY;
    public final ForgeConfigSpec.IntValue maxY;
    public final ForgeConfigSpec.IntValue maxBlockLight;
    public final ForgeConfigSpec.EnumValue<SkyAccess> skyAccess;
    public final ForgeConfigSpec.IntValue startingPopulation;

    SpeciesSpawnSettings(ForgeConfigSpec.Builder builder, String key, String displayName, Defaults d) {
        builder.comment("Natural spawn settings for the " + displayName + ".").push(key);

        this.enabled = builder
                .comment("If false, this species no longer spawns naturally.",
                         "Spawn eggs, /summon and breeding keep working regardless.")
                .define("enabled", true);

        this.spawnChance = builder
                .comment("Base probability that a natural spawn attempt is accepted (0.0 - 1.0).",
                         "The bonus accumulated from released animals multiplies this value.")
                .defineInRange("spawnChance", d.spawnChance(), 0.0D, 1.0D);

        this.groupMin = builder
                .comment("Minimum group size. Informational: the real pack size is set by",
                         "minCount in the biome modifier JSON, which cannot be changed at runtime.")
                .defineInRange("groupMin", d.groupMin(), 1, 64);

        this.groupMax = builder
                .comment("Maximum number of this species allowed within groupCheckRadius.",
                         "Acts as a density cap: above this number the spawn is rejected.")
                .defineInRange("groupMax", d.groupMax(), 1, 64);

        this.minY = builder
                .comment("Minimum spawn height (inclusive).")
                .defineInRange("minY", d.minY(), -64, 320);

        this.maxY = builder
                .comment("Maximum spawn height (inclusive).")
                .defineInRange("maxY", d.maxY(), -64, 320);

        this.maxBlockLight = builder
                .comment("Maximum block light level allowed (0-15). 15 disables the filter.")
                .defineInRange("maxBlockLight", d.maxBlockLight(), 0, 15);

        this.skyAccess = builder
                .comment("Open-sky requirement at the spawn position:",
                         "  NONE      = don't care",
                         "  REQUIRED  = must see the sky",
                         "  FORBIDDEN = must be under cover or canopy")
                .defineEnum("skyAccess", d.skyAccess());

        this.startingPopulation = builder
                .comment("Wild population a new world starts with for this species. -1 = the shared basePopulation.",
                         "The defaults match each species' real-world IUCN status: with the default population settings,",
                         "5 or less is CR, 6-9 EN, 10-14 VU, 15-19 NT and 20 or more LC. Below basePopulation it spawns less.",
                         "Only applies while the world has no stored population for the species.")
                .defineInRange("startingPopulation", d.startingPopulation(), BASE_STARTING_POPULATION, 100_000);

        builder.pop();
    }

    /** Reads the current spec values and freezes them into a {@link Snapshot}. */
    Snapshot bake() {
        return new Snapshot(
                this.enabled.get(),
                this.spawnChance.get(),
                this.groupMin.get(),
                this.groupMax.get(),
                this.minY.get(),
                this.maxY.get(),
                this.maxBlockLight.get(),
                this.skyAccess.get(),
                this.startingPopulation.get()
        );
    }
}
