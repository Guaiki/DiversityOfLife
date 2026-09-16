package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Permanent registry of animals returned to the wild.
 *
 * Lives in {@code saves/<world>/data/doc_release_registry.dat}, attached to the Overworld, so the
 * counter is global to the whole save rather than per dimension.
 *
 * The counter only ever goes up. There is no decrement API: the only way to lower it is
 * {@code /doc_spawn reset}, which exists for testing.
 */
public class ReleaseRegistry extends SavedData {

    public static final String DATA_NAME = "doc_release_registry";
    private static final int SCHEMA_VERSION = 1;

    /** What happened when trying to count a release. */
    public enum Result {
        /** Counted: the counter went up. */
        COUNTED,
        /** This animal was released before. The counter does not move. */
        DUPLICATE,
        /** The counter is already at the configured cap. */
        CAPPED
    }

    private final Map<DOCSpecies, Integer> counts = new EnumMap<>(DOCSpecies.class);
    private final Map<DOCSpecies, Set<UUID>> releasedUuids = new EnumMap<>(DOCSpecies.class);

    // ==============================
    //       LOCK-FREE CACHE
    // ==============================

    // The spawn cycle reads the counter from the chunk generation threads, where touching the
    // server's DataStorage is not safe. We mirror the counters into a volatile array that is
    // swapped wholesale on every write: lock-free reads from any thread.
    private static volatile int[] countCache = new int[DOCSpecies.values().length];

    /** Released counter, safe to read from any thread. */
    public static int cachedCount(DOCSpecies species) {
        return countCache[species.ordinal()];
    }

    /** Called when the server stops, so one world's data does not leak into the next. */
    public static void resetCache() {
        countCache = new int[DOCSpecies.values().length];
    }

    private void syncCache() {
        int[] snapshot = new int[DOCSpecies.values().length];
        this.counts.forEach((species, count) -> snapshot[species.ordinal()] = count);
        countCache = snapshot;
    }

    // ==============================
    //       ACCESS
    // ==============================

    /**
     * The save's registry. Creates it if it did not exist.
     *
     * Server thread only: DataStorage is not thread-safe. To read the counter from another thread
     * use {@link #cachedCount(DOCSpecies)}.
     */
    public static ReleaseRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ReleaseRegistry::load, ReleaseRegistry::new, DATA_NAME);
    }

    public int getCount(DOCSpecies species) {
        return this.counts.getOrDefault(species, 0);
    }

    public boolean isAlreadyReleased(DOCSpecies species, UUID entityUuid) {
        Set<UUID> set = this.releasedUuids.get(species);
        return set != null && set.contains(entityUuid);
    }

    /**
     * Counts the release of an animal.
     *
     * Each UUID counts exactly once: taming the same animal again and releasing it a second time
     * adds nothing. This is the main anti-farming safeguard.
     *
     * Once the cap is reached we stop storing UUIDs: the counter can no longer rise, so the
     * deduplication stops buying us anything and the file cannot grow without bound.
     */
    public Result register(DOCSpecies species, UUID entityUuid) {
        if (isAlreadyReleased(species, entityUuid)) return Result.DUPLICATE;

        int cap = DOCServerConfig.globals().releaseCap();
        int current = getCount(species);
        if (current >= cap) return Result.CAPPED;

        this.counts.put(species, current + 1);
        this.releasedUuids.computeIfAbsent(species, key -> new HashSet<>()).add(entityUuid);

        syncCache();
        setDirty();

        return Result.COUNTED;
    }

    /**
     * Sets the counter directly. Only for {@code /doc_spawn max}.
     *
     * It deliberately skips the UUID deduplication: this is an admin tool for reaching the cap
     * without having to tame and release 50 animals by hand.
     */
    public void debugSetCount(DOCSpecies species, int count) {
        int clamped = Math.max(0, Math.min(count, DOCServerConfig.globals().releaseCap()));
        this.counts.put(species, clamped);
        syncCache();
        setDirty();
    }

    /** Only for {@code /doc_spawn reset}. */
    public void reset(DOCSpecies species) {
        this.counts.remove(species);
        this.releasedUuids.remove(species);
        syncCache();
        setDirty();
    }

    public void resetAll() {
        this.counts.clear();
        this.releasedUuids.clear();
        syncCache();
        setDirty();
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
            entry.putInt("Count", getCount(species));

            ListTag uuids = new ListTag();
            Set<UUID> set = this.releasedUuids.get(species);
            if (set != null) {
                for (UUID uuid : set) uuids.add(NbtUtils.createUUID(uuid));
            }
            entry.put("Released", uuids);

            speciesTag.put(species.key(), entry);
        }
        tag.put("Species", speciesTag);

        return tag;
    }

    public static ReleaseRegistry load(CompoundTag tag) {
        ReleaseRegistry registry = new ReleaseRegistry();
        CompoundTag speciesTag = tag.getCompound("Species");

        for (DOCSpecies species : DOCSpecies.values()) {
            if (!speciesTag.contains(species.key())) continue;

            CompoundTag entry = speciesTag.getCompound(species.key());
            registry.counts.put(species, entry.getInt("Count"));

            Set<UUID> set = new HashSet<>();
            ListTag uuids = entry.getList("Released", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < uuids.size(); i++) {
                set.add(NbtUtils.loadUUID(uuids.get(i)));
            }
            registry.releasedUuids.put(species, set);
        }

        registry.syncCache();
        return registry;
    }
}
