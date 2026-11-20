package com.evirapo.diversityofcritters.client.guidebook;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidebookSearchTest {

    private static final String CIVET = "Banded Palm Civet";
    private static final List<String> CIVET_TAGS = List.of("civet", "carnivore", "carnivora", "mammal", "nocturnal");
    private static final List<String> SIFAKA_TAGS = List.of("lemur", "herbivore", "primates", "mammal", "diurnal");

    @Test
    void matchesNameIgnoringCaseAndSpaces() {
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, true, "  CIVET ", Set.of()));
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, true, "palm", Set.of()));
    }

    @Test
    void matchesTag() {
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, true, "carnivora", Set.of()));
        assertFalse(GuidebookSearch.matches(CIVET, CIVET_TAGS, true, "primates", Set.of()));
    }

    @Test
    void undiscoveredNameIsHidden() {
        assertFalse(GuidebookSearch.matches(CIVET, CIVET_TAGS, false, "Banded", Set.of()));
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, false, "carnivora", Set.of()));
    }

    @Test
    void undiscoveredCommonNameTagIsHidden() {
        // "civet" is a tag too; matching it would single out the civet's "???" row.
        assertFalse(GuidebookSearch.matches(CIVET, CIVET_TAGS, false, "civet", Set.of()));
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, true, "civet", Set.of()));
    }

    @Test
    void allFiltersMustMatch() {
        assertFalse(GuidebookSearch.matches("Verreaux's Sifaka", SIFAKA_TAGS, true, "", Set.of("mammal", "nocturnal")));
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, true, "", Set.of("mammal", "nocturnal")));
    }

    @Test
    void emptyQueryAndNoFiltersMatchesAll() {
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, true, "", Set.of()));
        assertTrue(GuidebookSearch.matches(CIVET, CIVET_TAGS, false, "   ", Set.of()));
    }
}
