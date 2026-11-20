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
public class GuidebookData extends SimplePreparableReloadListener<GuidebookData.Loaded> {

    public static final GuidebookData INSTANCE = new GuidebookData();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ANIMALS = "guidebook/animals";
    private static final ResourceLocation RECIPES = new ResourceLocation("diversityofcritters", "guidebook/recipes.json");

    /** Everything one reload read. */
    protected record Loaded(List<SpeciesEntry> species, List<ResourceLocation> care, List<ResourceLocation> etc) {
    }

    private static volatile List<SpeciesEntry> species = List.of();
    private static volatile List<ResourceLocation> care = List.of();
    private static volatile List<ResourceLocation> etc = List.of();

    /** Items whose recipes the Care page shows, in order. */
    public static List<ResourceLocation> care() {
        return care;
    }

    /** Items whose recipes the Etc page shows, in order. */
    public static List<ResourceLocation> etc() {
        return etc;
    }

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
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<ResourceLocation> careItems = new ArrayList<>(), etcItems = new ArrayList<>();
        manager.getResource(RECIPES).ifPresent(resource -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                GsonHelper.getAsJsonArray(json, "care").forEach(e -> careItems.add(new ResourceLocation(e.getAsString())));
                GsonHelper.getAsJsonArray(json, "etc").forEach(e -> etcItems.add(new ResourceLocation(e.getAsString())));
            } catch (Exception e) {
                LOGGER.warn("Could not read {}: {}", RECIPES, e.getMessage());
            }
        });
        return new Loaded(loadSpecies(manager), List.copyOf(careItems), List.copyOf(etcItems));
    }

    private static List<SpeciesEntry> loadSpecies(ResourceManager manager) {
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
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
        species = List.copyOf(loaded.species());
        care = loaded.care();
        etc = loaded.etc();
    }
}
