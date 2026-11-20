package com.evirapo.diversityofcritters.client.guidebook;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Search and tag filters of the animal list. No Minecraft types, so it is unit tested. */
public final class GuidebookSearch {

    /** The tags offered by the "F" filter panel, in display order. */
    public static final List<String> FILTER_TAGS = List.of("mammal", "carnivora", "primates", "rodentia",
            "carnivore", "herbivore", "diurnal", "nocturnal");

    /**
     * True when the animal passes the search box and every ticked filter. The query matches part of
     * the name or of any tag, ignoring case and surrounding spaces; an undiscovered animal's name
     * never matches, so searching cannot reveal it.
     */
    public static boolean matches(String name, List<String> tags, boolean discovered, String query, Set<String> filters) {
        if (!tags.containsAll(filters)) return false;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        if (discovered && name.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (String tag : tags) {
            if (tag.contains(q)) return true;
        }
        return false;
    }

    private GuidebookSearch() {
    }
}
