package com.evirapo.diversityofcritters.client.guidebook.data;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads every {@code guidebook/animals/*.json} from the resource packs. A broken file is logged and
 * skipped so one typo never empties the book.
 */
public class GuidebookData extends SimplePreparableReloadListener<List<SpeciesEntry>> {

    public static final GuidebookData INSTANCE = new GuidebookData();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ANIMALS = "guidebook/animals";

    private static volatile List<SpeciesEntry> species = List.of();

    /** Every loaded species, in alphabetical order of its name in the current language. */
    public static List<SpeciesEntry> species() {
        List<SpeciesEntry> sorted = new ArrayList<>(species);
        sorted.sort(Comparator.comparing(entry -> I18n.get(entry.nameKey())));
        return sorted;
    }

    public static Optional<SpeciesEntry> species(ResourceLocation entity) {
        return species.stream().filter(entry -> entry.entity().equals(entity)).findFirst();
    }

    @Override
    protected List<SpeciesEntry> prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<SpeciesEntry> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(ANIMALS, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            String path = file.getKey().getPath();
            String id = path.substring(ANIMALS.length() + 1, path.length() - ".json".length());
            try (Reader reader = file.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                loaded.add(SpeciesEntry.fromJson(id, json));
            } catch (Exception e) {
                LOGGER.warn("Skipping guidebook entry {}: {}", file.getKey(), e.getMessage());
            }
        }
        return loaded;
    }

    @Override
    protected void apply(List<SpeciesEntry> loaded, ResourceManager manager, ProfilerFiller profiler) {
        species = List.copyOf(loaded);
    }
}
