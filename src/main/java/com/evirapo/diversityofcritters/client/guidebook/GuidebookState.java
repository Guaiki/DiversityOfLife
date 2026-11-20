package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.common.guidebook.ConservationTier;

import java.util.Map;
import java.util.Set;

/**
 * What the server told the client when the book was opened.
 *
 * @param discovered      entity type ids the player has registered in the book
 * @param worldTiers      entity type id to the species' conservation status in this world
 * @param tracksDiscovery false while discovery is not wired up: every animal counts as discovered
 */
public record GuidebookState(Set<String> discovered, Map<String, ConservationTier> worldTiers, boolean tracksDiscovery) {

    public static final GuidebookState EVERYTHING_KNOWN = new GuidebookState(Set.of(), Map.of(), false);

    public boolean isDiscovered(String entityId) {
        return !this.tracksDiscovery || this.discovered.contains(entityId);
    }
}
