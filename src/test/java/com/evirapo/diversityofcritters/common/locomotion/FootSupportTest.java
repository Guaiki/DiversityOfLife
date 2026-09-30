package com.evirapo.diversityofcritters.common.locomotion;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        private final Set<Long> stairs = new HashSet<>();

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

        /** Marks a placed block as a stair; its span stands for the half under the feet. */
        Grid stairs(int x, int y, int z) {
            this.stairs.add(key(x, y, z));
            return this;
        }

        @Override
        public boolean isStairs(int x, int y, int z) {
            return this.stairs.contains(key(x, y, z));
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
        final double[] span = new double[3];
        final double reach = FootSupport.REACH_DOWN;
        assertTrue(Double.isNaN(FootSupport.groundBelow(new Grid().place(0, 65, 0, 0.0, 1.0), span, 0.5, 65.0, 0.5, reach)));
        assertEquals(65.0, FootSupport.groundBelow(new Grid().block(0, 0), span, 0.5, 65.0, 0.5, reach));
        assertTrue(Double.isNaN(FootSupport.groundBelow(new Grid(), span, 0.5, 65.0, 0.5, reach)));
        assertTrue(Double.isNaN(FootSupport.groundBelow(new Grid().block(0, 0), span, 0.5, 65.5, 0.5, 0.25)),
                "half a block down is past a quarter's reach");
    }

    /** A floor of blocks (top 65) round the origin, with a bottom slab on it at (0, 0): top 65.5. */
    private static Grid slabOnAFloor() {
        final Grid grid = new Grid();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                grid.block(x, z);
            }
        }
        return grid.place(0, 65, 0, 0.0, 0.5);
    }

    private static FootSupport onTheSlab(double x, double z, float yaw) {
        final FootSupport feet = new FootSupport(FootSupport.Layout.CIVET);
        feet.check(slabOnAFloor(), x, 65.5, z, yaw, 1.0f, HALF_WIDTH);
        return feet;
    }

    @Test
    void sideOverASlabsEdgeSlidesOff() {
        // Facing south along the slab's east edge: the left feet (+X) reach 1.02, over the floor half a
        // block down. The body cannot lean that far sideways.
        final FootSupport feet = onTheSlab(0.95, 0.5, 0.0f);
        assertEquals(2, feet.unsupported);
        assertFalse(feet.supported[1], "front left");
        assertFalse(feet.supported[3], "hind left");
        assertEquals(1.0, feet.slideX, 1.0e-9);
        assertEquals(0.0, feet.slideZ, 1.0e-9);
    }

    @Test
    void frontFeetDownOffASlabHoldLikeAStair() {
        // Facing south off the slab's south edge: both front feet on the floor half a block down. The
        // body pitches and the legs reach it, as on stairs.
        final FootSupport feet = onTheSlab(0.5, 0.85, 0.0f);
        assertEquals(0, feet.unsupported);
    }

    @Test
    void allFeetOnTheFloorBelowTheSlabSlidesOff() {
        // Centre 0.3 past the slab's south edge: every foot is over the floor half a block down, and
        // only the box's back rim rests on the slab.
        final FootSupport feet = onTheSlab(0.5, 1.3, 0.0f);
        assertEquals(4, feet.unsupported);
        assertEquals(1.0, feet.slideZ, 1.0e-9);
    }

    @Test
    void sideOverAStairsLowHalfHolds() {
        // The slab's case, but the half under the feet is a stair's: standing across steps is the
        // terrain rig's to pose, sideways too.
        final FootSupport feet = new FootSupport(FootSupport.Layout.CIVET);
        assertFalse(feet.check(slabOnAFloor().stairs(0, 65, 0), 0.95, 65.5, 0.5, 0.0f, 1.0f, HALF_WIDTH));
        assertEquals(0, feet.unsupported);
    }

    @Test
    void stairsStillDropOffTheirSide() {
        // A stair with nothing beside it: the left feet over a drop past any leg's reach.
        final Grid grid = new Grid().place(0, 64, 0, 0.0, 0.5).stairs(0, 64, 0);
        final FootSupport feet = new FootSupport(FootSupport.Layout.CIVET);
        assertTrue(feet.check(grid, 0.95, 64.5, 0.5, 0.0f, 1.0f, HALF_WIDTH));
        assertEquals(2, feet.unsupported);
        assertEquals(1.0, feet.slideX, 1.0e-9);
    }

    @Test
    void lowStuffAcrossAPairHolds() {
        // A carpet under the left feet only: 1/16 is nothing to lean over.
        final Grid grid = new Grid().block(0, 0).block(1, 0).place(1, 65, 0, 0.0, 0.0625);
        final FootSupport feet = new FootSupport(FootSupport.Layout.CIVET);
        assertFalse(feet.check(grid, 0.95, 65.0625, 0.5, 0.0f, 1.0f, HALF_WIDTH));
        assertEquals(0, feet.unsupported);
    }
}
