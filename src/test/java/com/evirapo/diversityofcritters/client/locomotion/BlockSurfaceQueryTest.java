package com.evirapo.diversityofcritters.client.locomotion;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The column scan, against blocks described as solid spans. Reading real {@code VoxelShape}s
 * needs a bootstrapped game, so that last step is checked in game with the debug overlay.
 */
class BlockSurfaceQueryTest {

    private static final double[] FULL = {0.0, 1.0};
    private static final double[] BOTTOM_SLAB = {0.0, 0.5};
    private static final double[] TOP_SLAB = {0.5, 1.0};

    /** Floor with its top at y = 1 (solid at y <= 0), plus whatever each test places. */
    private static final class Column implements BlockSurfaceQuery.ShapeReader {
        private final Map<Integer, double[]> placed = new HashMap<>();

        Column place(int y, double[] span) {
            this.placed.put(y, span);
            return this;
        }

        @Override
        public boolean read(int x, int y, int z, double inX, double inZ, double[] span) {
            final double[] found = this.placed.containsKey(y) ? this.placed.get(y) : (y <= 0 ? FULL : null);
            if (found == null) {
                return false;
            }
            span[0] = found[0];
            span[1] = found[1];
            return true;
        }
    }

    private static double surface(Column column, double footY) {
        return BlockSurfaceQuery.scan(column, new double[2], 0.5, 0.5, footY);
    }

    @Test
    void floorReadsItsTop() {
        assertEquals(1.0, surface(new Column(), 1.0), 1.0e-9);
    }

    @Test
    void fullBlockAtFootLevelIsAStepUp() {
        assertEquals(2.0, surface(new Column().place(1, FULL), 1.0), 1.0e-9);
    }

    @Test
    void bottomSlabIsAHalfStep() {
        assertEquals(1.5, surface(new Column().place(1, BOTTOM_SLAB), 1.0), 1.0e-9);
    }

    @Test
    void topSlabOverheadIsNotGround() {
        // Hangs from 1.5 to 2.0 above a foot standing at 1.0.
        assertEquals(1.0, surface(new Column().place(1, TOP_SLAB), 1.0), 1.0e-9);
    }

    @Test
    void ceilingAboveAStepIsSkipped() {
        // Step up to 2.0, with a solid block hanging at 3..4 that the scan starts inside of.
        final Column column = new Column().place(1, FULL).place(2, FULL);
        assertEquals(3.0, surface(column, 1.9), 1.0e-9, "a block starting at the foot is still a step");
        assertEquals(1.0, surface(new Column().place(2, FULL), 1.0), 1.0e-9, "a block a whole level up is overhead");
    }

    @Test
    void dropReadsTheLowerFloor() {
        assertEquals(0.0, surface(new Column().place(0, null), 1.0), 1.0e-9);
    }

    @Test
    void bottomlessColumnGivesUpBelowTheScan() {
        final Column pit = new Column();
        for (int y = -40; y <= 0; y++) {
            pit.place(y, null);
        }
        assertEquals(Math.floor(1.0 + BlockSurfaceQuery.SCAN_ABOVE) - BlockSurfaceQuery.SCAN_DEPTH,
                surface(pit, 1.0), 1.0e-9);
    }
}
