package com.evirapo.diversityofcritters.client.guidebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookLayoutTest {

    /** Small text is 0.75 of the 8 px font: 6 GUI px tall. */
    private static final int SMALL_TEXT_H = 6;

    @Test
    void boxTitlesClearTheStatusRings() {
        int ringBottom = BookLayout.DOT_Y + BookLayout.MAX_RING_RADIUS;
        assertTrue(BookLayout.BOX_TITLE_Y > ringBottom + 1, "title touches the ring");
        assertTrue(BookLayout.BOX_TITLE_Y + SMALL_TEXT_H < BookLayout.BOX_Y0, "title runs into the box");
    }

    @Test
    void crowdedBoxesTurnPageEveryFiveSeconds() {
        assertEquals(100, IconGrid.AUTO_PAGE_TICKS);
    }
}
