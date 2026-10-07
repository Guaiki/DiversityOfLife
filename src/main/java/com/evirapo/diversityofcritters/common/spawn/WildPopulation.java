package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.config.PopulationSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * The wild population of every species, for the whole save.
 *
 * Lives in {@code saves/<world>/data/doc_release_registry.dat}, attached to the Overworld. The file
 * name is the module 2 release registry's: version 1 data is migrated on load, so worlds keep the
 * progress they already made.
 *
 * Floor and cap are applied on write, not on read, so lowering them in the config never leaves an
 * out-of-range value stored.
 */
public class WildPopulation extends SavedData {

    public static final String DATA_NAME = "doc_release_registry";
    private static final int SCHEMA_VERSION = 2;

    private final Map<DOCSpecies, Integer> populations = new EnumMap<>(DOCSpecies.class);

    // One sample per in-game day, oldest first, for the trend /doc_spawn shows.
    private final Map<DOCSpecies, int[]> history = new EnumMap<>(DOCSpecies.class);
    private long lastSampledDay = Long.MIN_VALUE;

    // ==============================
    //       LOCK-FREE CACHE
    // ==============================

    // The spawn cycle reads the population from the chunk generation threads, where touching the
    // server's DataStorage is not safe. We mirror it into a volatile array that is swapped
    // wholesale on every write: lock-free reads from any thread.
    private static final int UNKNOWN = -1;
    private static volatile int[] populationCache = freshCache();

    private static int[] freshCache() {
        int[] cache = new int[DOCSpecies.values().length];
        Arrays.fill(cache, UNKNOWN);
        return cache;
    }

    /** Population, safe to read from any thread. Before the save is loaded, the base population. */
    public static int cachedPopulation(DOCSpecies species) {
        int cached = populationCache[species.ordinal()];
        return cached != UNKNOWN ? cached : defaultPopulation();
    }

    /** Called when the server stops, so one world's data does not leak into the next. */
    public static void resetCache() {
        populationCache = freshCache();
    }

    private void syncCache() {
        int[] snapshot = new int[DOCSpecies.values().length];
        for (DOCSpecies species : DOCSpecies.values()) {
            snapshot[species.ordinal()] = get(species);
        }
        populationCache = snapshot;
    }

    private static int defaultPopulation() {
        PopulationSettings settings = DOCServerConfig.population();
        return settings.clamp(settings.basePopulation());
    }

    // ==============================
    //       ACCESS
    // ==============================

    /**
     * The save's population. Creates it if it did not exist.
     *
     * Server thread only: DataStorage is not thread-safe. From another thread use
     * {@link #cachedPopulation(DOCSpecies)}.
     */
    public static WildPopulation get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WildPopulation::load, WildPopulation::new, DATA_NAME);
    }

    /**
     * Entry point for the entity hooks: counts an event for whatever species the entity is.
     * Does nothing for species outside the system (the lion) or off the server.
     */
    public static void record(Entity entity, PopulationEvent event) {
        DOCSpecies species = DOCSpecies.of(entity);
        MinecraftServer server = entity.getServer();
        if (species == null || server == null) return;
        get(server).apply(species, event, server);
    }

    public int get(DOCSpecies species) {
        Integer stored = this.populations.get(species);
        return stored != null ? stored : defaultPopulation();
    }

    /**
     * Moves the population by the event's configured weight.
     *
     * @return the change actually made, after floor and cap. 0 if the weight is 0 or it was
     *         already at the limit.
     */
    public int apply(DOCSpecies species, PopulationEvent event, MinecraftServer server) {
        int delta = DOCServerConfig.population().delta(event);
        if (delta == 0) return 0;

        int before = get(species);
        int after = write(species, before + delta, server);
        return after - before;
    }

    /** Sets the population directly, clamped. For the debug commands. Returns what was stored. */
    public int set(DOCSpecies species, int population, MinecraftServer server) {
        return write(species, population, server);
    }

    /** Back to the base population, with a clean history. Only for {@code /doc_spawn reset}. */
    public void reset(DOCSpecies species, MinecraftServer server) {
        this.history.remove(species);
        set(species, DOCServerConfig.population().basePopulation(), server);
    }

    public void resetAll(MinecraftServer server) {
        for (DOCSpecies species : DOCSpecies.values()) {
            reset(species, server);
        }
    }

    /**
     * Records today's population, once per in-game day. Called from the Overworld tick; it compares
     * the day number for inequality rather than "greater than", so {@code /time set} going back
     * still starts a new sample instead of freezing the history.
     */
    public void sampleDay(long day) {
        if (day == this.lastSampledDay) return;
        this.lastSampledDay = day;

        for (DOCSpecies species : DOCSpecies.values()) {
            int[] previous = this.history.getOrDefault(species, new int[0]);
            this.history.put(species, PopulationMath.pushSample(previous, get(species)));
        }
        setDirty();
    }

    /** Change over the last week (or since the oldest sample, if the save is younger). */
    public int trend(DOCSpecies species) {
        return PopulationMath.trend(get(species), this.history.getOrDefault(species, new int[0]));
    }

    private int write(DOCSpecies species, int population, MinecraftServer server) {
        int before = get(species);
        int after = DOCServerConfig.population().clamp(population);

        this.populations.put(species, after);
        syncCache();
        setDirty();

        announce(server, species, before, after);
        return after;
    }

    /**
     * Tells every player when a species crosses the endangered line, either way. Only on the
     * crossing itself: a warning on every death would be noise.
     */
    private static void announce(MinecraftServer server, DOCSpecies species, int before, int after) {
        PopulationMath.Crossing crossing = PopulationMath.crossing(
                before, after, DOCServerConfig.population().endangeredThreshold());
        if (crossing == PopulationMath.Crossing.NONE) return;

        boolean endangered = crossing == PopulationMath.Crossing.BECAME_ENDANGERED;
        Component message = Component.translatable(
                endangered ? "message.diversityofcritters.population.endangered"
                           : "message.diversityofcritters.population.recovering",
                species.displayName(), after
        ).withStyle(endangered ? ChatFormatting.GOLD : ChatFormatting.GREEN);

        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    // ==============================
    //       PERSISTENCE
    // ==============================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", SCHEMA_VERSION);

        CompoundTag speciesTag = new CompoundTag();
        for (DOCSpecies species : DOCSpecies.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Population", get(species));
            entry.putIntArray("History", this.history.getOrDefault(species, new int[0]));
            speciesTag.put(species.key(), entry);
        }
        tag.put("Species", speciesTag);
        tag.putLong("LastSampledDay", this.lastSampledDay);

        return tag;
    }

    public static WildPopulation load(CompoundTag tag) {
        WildPopulation registry = new WildPopulation();
        PopulationSettings settings = DOCServerConfig.population();
        boolean legacy = tag.getInt("Version") < SCHEMA_VERSION;
        CompoundTag speciesTag = tag.getCompound("Species");

        for (DOCSpecies species : DOCSpecies.values()) {
            if (!speciesTag.contains(species.key())) continue;

            CompoundTag entry = speciesTag.getCompound(species.key());
            int population = legacy
                    ? PopulationMath.migrateV1(entry.getInt("Count"),
                            settings.basePopulation(), settings.floor(), settings.cap())
                    : settings.clamp(entry.getInt("Population"));
            registry.populations.put(species, population);

            if (entry.contains("History")) {
                registry.history.put(species, entry.getIntArray("History"));
            }
        }

        if (tag.contains("LastSampledDay")) {
            registry.lastSampledDay = tag.getLong("LastSampledDay");
        }

        // Rewrite in the new format on the next save, so the migration only ever runs once.
        if (legacy) registry.setDirty();

        registry.syncCache();
        return registry;
    }
}
