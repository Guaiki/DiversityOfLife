package com.evirapo.diversityofcritters.common.locomotion;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feet over an edge, against a synthetic world of whole blocks and slabs. A civet-sized body
 * (0.75 wide) facing south (+Z), its feet ±0.07 to the sides and ±0.2 ahead and behind.
 */
class FootSupportTest {

    private static final float HALF_WIDTH = 0.375f;

    /** Blocks at y = 64 wherever placed, their top at 65; everything else is air. */
    private static final class Grid implements FootSupport.World {
        private final Map<Long, double[]> spans = new HashMap<>();

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0xFFFFF) << 21 | ((long) z & 0x1FFFFF);
        }

        Grid block(int x, int z) {
            return this.place(x, 64, z, 0.0, 1.0);
        }

        Grid place(int x, int y, int z, double bottom, double top) {
            this.spans.put(key(x, y, z), new double[]{bottom, top});
            return this;
        }

        @Override
        public boolean span(int x, int y, int z, double inX, double inZ, double[] span) {
            final double[] found = this.spans.get(key(x, y, z));
            if (found == null) {
                return false;
            }
            span[0] = found[0];
            span[1] = found[1];
            return true;
        }
    }

    private static FootSupport check(Grid grid, double x, double z, float yaw) {
        final FootSupport feet = new FootSupport(FootSupport.Layout.CIVET);
        feet.check(grid, x, 65.0, z, yaw, 1.0f, HALF_WIDTH);
        return feet;
    }

    @Test
    void standingInTheMiddleHolds() {
        final FootSupport feet = check(new Grid().block(0, 0), 0.5, 0.5, 0.0f);
        assertEquals(0, feet.unsupported);
        assertEquals(0.0, feet.slideX);
        assertEquals(0.0, feet.slideZ);
    }

    @Test
    void oneFootOverTheEdgeHolds() {
        // An inside corner: blocks all round but at (1, 1). Only the front left foot (+X, +Z) is over it.
        final Grid grid = new Grid().block(0, 0).block(1, 0).block(0, 1);
        final FootSupport feet = new FootSupport(FootSupport.Layout.CIVET);
        assertFalse(feet.check(grid, 0.95, 65.0, 0.85, 0.0f, 1.0f, HALF_WIDTH));
        assertEquals(1, feet.unsupported);
    }

    @Test
    void frontFeetOverTheEdgeSlidesForward() {
        // Block ends at z = 1; centre at z 0.85: front feet at 1.05, hind feet at 0.65.
        final FootSupport feet = check(new Grid().block(0, 0), 0.5, 0.85, 0.0f);
        assertEquals(2, feet.unsupported);
        assertEquals(0.0, feet.slideX, 1.0e-9);
        assertEquals(1.0, feet.slideZ, 1.0e-9, "toward the drop, +Z");
    }

    @Test
    void sideOverTheEdgeSlidesSideways() {
        // Block ends at x = 1; centre at x 0.95: the left feet (+X) are at 1.01-1.02, off it.
        final FootSupport feet = check(new Grid().block(0, 0), 0.95, 0.5, 0.0f);
        assertEquals(2, feet.unsupported);
        assertEquals(1.0, feet.slideX, 1.0e-9);
        assertEquals(0.0, feet.slideZ, 1.0e-9);
    }

    @Test
    void hangingOnByTheBoxAloneSlidesOff() {
        // Centre 0.3 past the edge: every foot is over air, only the box's back rim rests on the block.
        final FootSupport feet = check(new Grid().block(0, 0), 0.5, 1.3, 0.0f);
        assertEquals(4, feet.unsupported);
        assertTrue(feet.slideZ > 0.99, "slides off forward: " + feet.slideZ);
    }

    @Test
    void turnedBodyUsesItsOwnFeet() {
        // Facing east (yaw -90): the feet run along X. Centre 0.85 into the block along X.
        final FootSupport feet = check(new Grid().block(0, 0), 0.85, 0.5, -90.0f);
        assertEquals(2, feet.unsupported);
        assertEquals(1.0, feet.slideX, 1.0e-9);
    }

    @Test
    void stepDownOfASlabStillHolds() {
        // Standing on a block with the hind feet over a slab half a block lower.
        final Grid grid = new Grid().block(0, 1).place(0, 64, 0, 0.0, 0.5);
        final FootSupport feet = check(grid, 0.5, 1.1, 0.0f);
        assertEquals(0, feet.unsupported);
    }

    @Test
    void youngFeetAreCloserTogether() {
        // At 0.45 the front feet reach only 0.09 ahead: still on the block where an adult's are not.
        final FootSupport feet = new FootSupport(FootSupport.Layout.CIVET);
        assertFalse(feet.check(new Grid().block(0, 0), 0.5, 65.0, 0.85, 0.0f, 0.45f, HALF_WIDTH));
        assertEquals(0, feet.unsupported);
    }

    @Test
    void groundAboveTheFeetIsNotUnderThem() {
        assertFalse(FootSupport.hasGround(new Grid().place(0, 65, 0, 0.0, 1.0), new double[2], 0.5, 65.0, 0.5));
        assertTrue(FootSupport.hasGround(new Grid().block(0, 0), new double[2], 0.5, 65.0, 0.5));
        assertFalse(FootSupport.hasGround(new Grid(), new double[2], 0.5, 65.0, 0.5));
    }
}
