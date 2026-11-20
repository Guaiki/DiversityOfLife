package com.evirapo.diversityofcritters.client.guidebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTurnTest {

    @Test
    void leafIsFlatAtTheEndsAndEdgeOnHalfway() {
        assertEquals(147, PageTurn.leafWidth(0.0F, 147));
        assertEquals(0, PageTurn.leafWidth(0.5F, 147));
        assertEquals(147, PageTurn.leafWidth(1.0F, 147));
        assertEquals(104, PageTurn.leafWidth(0.25F, 147)); // 147 * cos(45°)
        assertEquals(104, PageTurn.leafWidth(0.75F, 147));
    }

    @Test
    void backOfTheLeafShowsFromHalfway() {
        assertFalse(PageTurn.showsBack(0.49F));
        assertTrue(PageTurn.showsBack(0.5F));
    }

    @Test
    void progressRunsOverTheDurationAndThenIsDone() {
        PageTurn turn = new PageTurn(null, 1, 1000L);
        assertEquals(0.0F, turn.progress(1000L));
        assertEquals(0.5F, turn.progress(1000L + PageTurn.DURATION_MS / 2));
        assertFalse(turn.done(1000L + PageTurn.DURATION_MS - 1));
        assertTrue(turn.done(1000L + PageTurn.DURATION_MS));
        assertEquals(1.0F, turn.progress(1000L + PageTurn.DURATION_MS * 3));
    }
}
