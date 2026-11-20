package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.common.guidebook.ConservationTier;

import java.util.Map;
import java.util.Set;

/**
 * What the server told the client when the book was opened.
 *
 * @param discovered      entity type ids the player has registered in the book
 * @param worldTiers      entity type id to the species' conservation status in this world
 * @param tracksDiscovery false shows every animal as discovered (kept for previews and tests)
 */
public record GuidebookState(Set<String> discovered, Map<String, ConservationTier> worldTiers, boolean tracksDiscovery) {


    public boolean isDiscovered(String entityId) {
        return !this.tracksDiscovery || this.discovered.contains(entityId);
    }
}
