package com.evirapo.diversityofcritters.client.guidebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTurnTest {

    private static final float PI = (float) Math.PI;
    private static final float EPS = 1e-4F;

    @Test
    void easingStartsAndEndsGently() {
        assertEquals(0.0F, PageTurn.ease(0.0F), EPS);
        assertEquals(0.5F, PageTurn.ease(0.5F), EPS);
        assertEquals(1.0F, PageTurn.ease(1.0F), EPS);
        assertTrue(PageTurn.ease(0.1F) < 0.05F, "slow out of the start");
        assertTrue(PageTurn.ease(0.9F) > 0.95F, "slow into the end");
    }

    @Test
    void forwardTurnSwingsFromRightToLeftAndBackTheOtherWay() {
        PageTurn forward = new PageTurn(null, 1, 0L);
        assertEquals(0.0F, forward.angle(0L), EPS);
        assertEquals(PI / 2, forward.angle(PageTurn.DURATION_MS / 2), EPS);
        assertEquals(PI, forward.angle(PageTurn.DURATION_MS), EPS);

        PageTurn back = new PageTurn(null, -1, 0L);
        assertEquals(PI, back.angle(0L), EPS);
        assertEquals(0.0F, back.angle(PageTurn.DURATION_MS), EPS);
    }

    @Test
    void flatLeafProjectsOntoThePage() {
        assertEquals(100.0F, PageTurn.projectedX(100.0F, 0.0F), EPS);
        assertEquals(-100.0F, PageTurn.projectedX(100.0F, PI), EPS);
        assertEquals(1.0F, PageTurn.heightScale(100.0F, 0.0F), EPS);
        assertEquals(1.0F, PageTurn.heightScale(100.0F, PI), EPS);
    }

    @Test
    void raisedLeafComesTowardsTheViewer() {
        // Upright, the leaf is edge-on, and its outer edge is nearer the viewer so it looks taller.
        assertEquals(0.0F, PageTurn.projectedX(147.0F, PI / 2), EPS);
        assertTrue(PageTurn.heightScale(147.0F, PI / 2) > 1.2F);
        assertEquals(1.0F, PageTurn.heightScale(0.0F, PI / 2), EPS, "the hinge stays on the spine");
        // Half raised, perspective pushes the outer edge further out than plain foreshortening.
        assertTrue(PageTurn.projectedX(147.0F, PI / 4) > 147.0F * (float) Math.cos(PI / 4));
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
