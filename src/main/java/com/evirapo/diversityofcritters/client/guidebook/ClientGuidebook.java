package com.evirapo.diversityofcritters.client.guidebook;

import com.evirapo.diversityofcritters.common.guidebook.ConservationTier;

import java.util.Map;
import java.util.Set;

/**
 * Client-only entry point, so the network handler can open the book without loading client classes
 * on a dedicated server.
 */
public final class ClientGuidebook {

    public static void open(Set<String> discovered, Map<String, ConservationTier> worldTiers) {
        GuidebookScreen.open(new GuidebookState(Set.copyOf(discovered), Map.copyOf(worldTiers), true));
    }

    private ClientGuidebook() {
    }
}
