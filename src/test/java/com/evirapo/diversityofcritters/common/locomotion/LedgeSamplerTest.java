package com.evirapo.diversityofcritters.common.locomotion;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledge probe against a synthetic world described as collision spans. Reading real
 * {@code VoxelShape}s and tags needs a bootstrapped game, so that last step is checked in game.
 *
 * <p>Unless a test says otherwise: a civet-sized body (0.75 wide, 0.4 tall) stands at
 * (0.5, 64, 0.5) on a floor whose top is y = 64, facing south (+Z), with the wall's face at z = 1.
 */
class LedgeSamplerTest {

    private static final float HALF_WIDTH = 0.375f;
    private static final float HEIGHT = 0.4f;
    private static final MantleProfile PROFILE = MantleProfile.CIVET;

    @FunctionalInterface
    private interface Shape {
        /** {bottom, top} at (inX, inZ), or null where the block is empty. */
        double[] at(double inX, double inZ);
    }

    private static final Shape FULL = (x, z) -> new double[]{0.0, 1.0};
    private static final Shape BOTTOM_SLAB = (x, z) -> new double[]{0.0, 0.5};
    private static final Shape TOP_SLAB = (x, z) -> new double[]{0.5, 1.0};
    /** Stairs rising away from a body facing south: low half at the front. */
    private static final Shape STAIRS_SOUTH = (x, z) -> new double[]{0.0, z < 0.5 ? 0.5 : 1.0};
    private static final Shape FENCE_POST = (x, z) ->
            x >= 0.375 && x <= 0.625 && z >= 0.375 && z <= 0.625 ? new double[]{0.0, 1.5} : null;

    /** Solid floor at y <= 63 plus whatever each test places. */
    private static final class Grid implements LedgeSampler.World {
        private final Map<Long, Shape> shapes = new HashMap<>();
        private final Set<Long> climbable = new HashSet<>();
        private final Set<Long> hazards = new HashSet<>();

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0xFFFFF) << 21 | ((long) z & 0x1FFFFF);
        }

        Grid place(int x, int y, int z, Shape shape) {
            this.shapes.put(key(x, y, z), shape);
            return this;
        }

        Grid climbable(int x, int y, int z) {
            this.climbable.add(key(x, y, z));
            return place(x, y, z, FULL);
        }

        Grid hazard(int x, int y, int z) {
            this.hazards.add(key(x, y, z));
            return this;
        }

        @Override
        public boolean span(int x, int y, int z, double inX, double inZ, double[] span) {
            final Shape shape = this.shapes.containsKey(key(x, y, z))
                    ? this.shapes.get(key(x, y, z))
                    : (y <= 63 ? FULL : null);
            final double[] found = shape == null ? null : shape.at(inX, inZ);
            if (found == null) {
                return false;
            }
            span[0] = found[0];
            span[1] = found[1];
            return true;
        }

        @Override
        public boolean isClimbable(int x, int y, int z) {
            return this.climbable.contains(key(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return this.hazards.contains(key(x, y, z));
        }
    }

    private static LedgeSample probe(Grid grid) {
        return probe(grid, 0.5, 64.0, 0.5, 0.0f, HEIGHT);
    }

    private static LedgeSample probe(Grid grid, double x, double y, double z, float yaw, float height) {
        final LedgeSample out = new LedgeSample();
        LedgeSampler.sample(grid, x, y, z, yaw, HALF_WIDTH, height, PROFILE, out);
        return out;
    }

    /** A wall one block high running along X, face at z = 1. */
    private static Grid wideWall() {
        final Grid grid = new Grid();
        for (int x = -4; x <= 4; x++) {
            grid.place(x, 64, 1, FULL);
        }
        return grid;
    }

    @Test
    void fullBlockIsALedge() {
        final LedgeSample s = probe(new Grid().place(0, 64, 1, FULL));
        assertTrue(s.found);
        assertEquals(LedgeSample.Reason.NONE, s.reason);
        assertEquals(Direction.SOUTH, s.facing);
        assertEquals(1.0, s.wallCoord, 1.0e-3);
        assertEquals(65.0, s.lipY, 1.0e-9);
        assertEquals(1.0, s.lipHeight, 1.0e-9);
        assertEquals(65.0, s.topY, 1.0e-9);
        assertEquals(LedgeSampler.MAX_HEADROOM, s.headroom, 1.0e-9);
        assertEquals(1.0f, s.confidence, 1.0e-6f);
        assertEquals(0, s.lipX);
        assertEquals(64, s.lipBlockY);
        assertEquals(1, s.lipZ);
        assertFalse(s.continuesUp);
    }

    @Test
    void bottomSlabIsALedge() {
        final LedgeSample s = probe(new Grid().place(0, 64, 1, BOTTOM_SLAB));
        assertTrue(s.found);
        assertEquals(LedgeSample.Reason.NONE, s.reason);
        assertEquals(1.0, s.wallCoord, 1.0e-3);
        assertEquals(64.5, s.lipY, 1.0e-9);
        assertEquals(0.5, s.lipHeight, 1.0e-9);
        assertEquals(64.5, s.topY, 1.0e-9);
        assertEquals(64, s.lipBlockY);
        assertEquals(1.0f, s.confidence, 1.0e-6f);
    }

    @Test
    void stepLowerThanASlabIsAStep() {
        // A quarter block, then nothing higher within reach.
        final LedgeSample s = probe(new Grid().place(0, 64, 1, (x, z) -> new double[]{0.0, 0.25}));
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.STEPPED, s.reason);
    }

    @Test
    void stairsAreSteps() {
        // The low half reads as a slab, but the top rises again half a block past the face.
        final LedgeSample s = probe(new Grid().place(0, 64, 1, STAIRS_SOUTH));
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.STEPPED, s.reason);
        assertEquals(64.5, s.lipY, 1.0e-9);
    }

    @Test
    void slabBeforeABlockIsALedge() {
        // Slab, then a full block behind it: the body walks onto the slab before it meets the block.
        final LedgeSample s = probe(new Grid().place(0, 64, 1, BOTTOM_SLAB).place(0, 64, 2, FULL));
        assertTrue(s.found);
        assertEquals(64.5, s.lipY, 1.0e-9);
    }

    @Test
    void stepBeforeAWallIsStillSteps() {
        // Half-block step, then a wall whose top is a block above the feet: stairs.
        final Grid grid = new Grid()
                .place(0, 64, 0, (x, z) -> z >= 0.75 ? new double[]{0.0, 0.5} : null)
                .place(0, 64, 1, (x, z) -> new double[]{0.0, z < 0.25 ? 0.5 : 1.0});
        final LedgeSample s = probe(grid);
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.STEPPED, s.reason);
        assertEquals(64.5, s.lipY, 1.0e-9, "the step is described");
    }

    @Test
    void topSlabAtFootLevelDependsOnBodyHeight() {
        // Hangs from 64.5 to 65: a 0.4 tall body walks under it, a 0.6 tall one meets a wall.
        final Grid grid = new Grid().place(0, 64, 1, TOP_SLAB);
        assertEquals(LedgeSample.Reason.NO_WALL, probe(grid).reason);

        final LedgeSample tall = probe(grid, 0.5, 64.0, 0.5, 0.0f, 0.6f);
        assertTrue(tall.found);
        assertEquals(65.0, tall.lipY, 1.0e-9);
    }

    @Test
    void ledgeMeasuredFromFeetOnASlab() {
        // Standing on a bottom slab at 64.5; the wall's top is a slab on a block, at 65.5.
        final Grid grid = new Grid()
                .place(0, 64, 0, BOTTOM_SLAB)
                .place(0, 64, 1, FULL)
                .place(0, 65, 1, BOTTOM_SLAB);
        final LedgeSample s = probe(grid, 0.5, 64.5, 0.5, 0.0f, HEIGHT);
        assertTrue(s.found);
        assertEquals(65.5, s.lipY, 1.0e-9);
        assertEquals(1.0, s.lipHeight, 1.0e-9);
        assertEquals(65, s.lipBlockY);
    }

    @Test
    void fenceIsTooHigh() {
        final LedgeSample s = probe(new Grid().place(0, 64, 1, FENCE_POST));
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.TOO_HIGH, s.reason);
        assertEquals(65.5, s.lipY, 1.0e-9);
    }

    @Test
    void twoBlockWallIsTooHigh() {
        final LedgeSample s = probe(new Grid().place(0, 64, 1, FULL).place(0, 65, 1, FULL));
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.TOO_HIGH, s.reason);
        assertEquals(66.0, s.lipY, 1.0e-9);
    }

    @Test
    void lowCeilingOverTheLipLeavesNoHeadroom() {
        // Solid from 65.25 over the lip at 65: 0.25 of room for a 0.4 tall body.
        final Grid grid = new Grid().place(0, 64, 1, FULL).place(0, 65, 1, (x, z) -> new double[]{0.25, 1.0});
        final LedgeSample s = probe(grid);
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.NO_HEADROOM, s.reason);
        assertEquals(0.25, s.headroom, 1.0e-9);
    }

    @Test
    void topSlabOverTheLipDependsOnBodyHeight() {
        final Grid grid = new Grid().place(0, 64, 1, FULL).place(0, 65, 1, TOP_SLAB);
        final LedgeSample civet = probe(grid);
        assertTrue(civet.found);
        assertEquals(0.5, civet.headroom, 1.0e-9);

        final LedgeSample tall = probe(grid, 0.5, 64.0, 0.5, 0.0f, 0.6f);
        assertEquals(LedgeSample.Reason.NO_HEADROOM, tall.reason);
    }

    @Test
    void ceilingOneBlockUpLeavesRoom() {
        final LedgeSample s = probe(new Grid().place(0, 64, 1, FULL).place(0, 66, 1, FULL));
        assertTrue(s.found);
        assertEquals(1.0, s.headroom, 1.0e-9);
    }

    @Test
    void liquidOnTopIsAHazard() {
        final LedgeSample s = probe(new Grid().place(0, 64, 1, FULL).hazard(0, 65, 1));
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.HAZARD, s.reason);
    }

    @Test
    void wallOutOfReachIsNoWall() {
        // Face at z = 2: 1.5 ahead of the centre, past half width + probe distance (0.875).
        final LedgeSample s = probe(new Grid().place(0, 64, 2, FULL));
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.NO_WALL, s.reason);
    }

    @Test
    void flatGroundIsNoWall() {
        assertEquals(LedgeSample.Reason.NO_WALL, probe(new Grid()).reason);
    }

    @Test
    void confidenceFallsAsTheWallTurnsAway() {
        final Grid grid = wideWall();
        final LedgeSample square = probe(grid, 0.5, 64.0, 0.5, 0.0f, HEIGHT);
        final LedgeSample thirty = probe(grid, 0.5, 64.0, 0.5, 30.0f, HEIGHT);
        final LedgeSample steep = probe(grid, 0.5, 64.0, 0.5, 44.0f, HEIGHT);

        assertTrue(square.found && thirty.found && steep.found);
        assertEquals(Direction.SOUTH, thirty.facing);
        assertEquals(Direction.SOUTH, steep.facing);
        assertEquals(1.0f, square.confidence, 1.0e-6f);
        assertTrue(thirty.confidence < square.confidence && thirty.confidence > 0.4f,
                "30 degrees: " + thirty.confidence);
        assertTrue(steep.confidence < thirty.confidence && steep.confidence < 0.1f,
                "44 degrees: " + steep.confidence);
    }

    @Test
    void confidenceIsSymmetricAcrossTheAxis() {
        final Grid grid = wideWall();
        assertEquals(probe(grid, 0.5, 64.0, 0.5, 30.0f, HEIGHT).confidence,
                probe(grid, 0.5, 64.0, 0.5, -30.0f, HEIGHT).confidence, 1.0e-6f);
    }

    @Test
    void lipUnderOnlyPartOfTheBodyLowersConfidence() {
        // One block; the body stands off its east edge, so one side lane meets air.
        final LedgeSample s = probe(new Grid().place(0, 64, 1, FULL), 0.9, 64.0, 0.5, 0.0f, HEIGHT);
        assertTrue(s.found);
        assertEquals(2.0f / 3.0f, s.confidence, 1.0e-6f);
    }

    @Test
    void worksFacingWestAtNegativeCoordinates() {
        // Centre at x = -3.5, wall block at x = -5: face at x = -4.
        final LedgeSample s = probe(new Grid().place(-5, 64, -8, FULL), -3.5, 64.0, -7.5, 90.0f, HEIGHT);
        assertTrue(s.found);
        assertEquals(Direction.WEST, s.facing);
        assertEquals(-4.0, s.wallCoord, 1.0e-3);
        assertEquals(-5, s.lipX);
        assertEquals(-8, s.lipZ);
        assertEquals(65.0, s.lipY, 1.0e-9);
    }

    @Test
    void oneBlockLogIsALedge() {
        final LedgeSample s = probe(new Grid().climbable(0, 64, 1));
        assertTrue(s.found);
        assertFalse(s.continuesUp);
    }

    @Test
    void tallLogContinuesUp() {
        final LedgeSample s = probe(new Grid().climbable(0, 64, 1).climbable(0, 65, 1).climbable(0, 66, 1));
        assertFalse(s.found);
        assertEquals(LedgeSample.Reason.TOO_HIGH, s.reason);
        assertTrue(s.continuesUp);
    }

    @Test
    void sampleIsResetBetweenCalls() {
        final LedgeSample out = new LedgeSample();
        LedgeSampler.sample(new Grid().place(0, 64, 1, FULL), 0.5, 64.0, 0.5, 0.0f, HALF_WIDTH, HEIGHT, PROFILE, out);
        assertTrue(out.found);
        LedgeSampler.sample(new Grid(), 0.5, 64.0, 0.5, 0.0f, HALF_WIDTH, HEIGHT, PROFILE, out);
        assertFalse(out.found);
        assertEquals(0.0, out.lipY);
        assertEquals(0.0f, out.confidence);
    }
}
