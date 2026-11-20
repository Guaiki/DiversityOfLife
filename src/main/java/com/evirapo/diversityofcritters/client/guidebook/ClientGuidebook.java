package com.evirapo.diversityofcritters.client.guidebook;

/**
 * Client-only entry point, so common code can open the book without loading client classes on a
 * dedicated server.
 */
public final class ClientGuidebook {

    public static void open() {
        GuidebookScreen.open(GuidebookState.EVERYTHING_KNOWN);
    }

    private ClientGuidebook() {
    }
}
