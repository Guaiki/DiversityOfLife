package com.evirapo.diversityofcritters.client.locomotion;

/**
 * Height of the ground under a whole foot, not under one point.
 *
 * <p>The footprint is laid over a fixed world grid and each overlapping cell is sampled at the
 * middle of its overlap. A foot rests on the highest things it touches, so the reading is the
 * area-weighted mean of the highest surfaces covering {@link #SUPPORT_SHARE} of the footprint.
 * As a foot slides onto a step, the step's share of that slice grows with its overlap, so the
 * reading rises smoothly instead of flipping when the foot's centre crosses the edge.
 *
 * <p>Allocation free: all scratch lives in the {@link Reading}.
 */
public final class GroundProbe {

    /** Grid cell size, in blocks. Fine enough to catch half-block features. */
    public static final float CELL = 0.25f;
    /** Share of the footprint, taken from the top, that the foot is considered to rest on. */
    public static final float SUPPORT_SHARE = 0.3f;

    static final int MAX_CELLS_PER_AXIS = 8;
    static final int MAX_CELLS = MAX_CELLS_PER_AXIS * MAX_CELLS_PER_AXIS;

    private GroundProbe() {
    }

    /** Result plus reusable scratch. One per leg. */
    public static final class Reading {
        /** World Y the foot should rest at. */
        public double height;
        /** Column of the cell the footprint overlaps most, for debug display. */
        public double mainX;
        public double mainZ;

        final double[] heights = new double[MAX_CELLS];
        final double[] areas = new double[MAX_CELLS];
        final int[] order = new int[MAX_CELLS];
    }

    /**
     * @param footX       world X of the sole point
     * @param footY       world Y of the sole point
     * @param footZ       world Z of the sole point
     * @param bodyYawDeg  body yaw, which orients the footprint
     * @param minX        footprint extent in the model's X axis relative to the sole, in blocks
     * @param maxX        (the model's X/Z axes, not the world's)
     * @param minZ        footprint extent in the model's Z axis relative to the sole, in blocks
     * @param maxZ
     */
    public static void sample(SurfaceQuery surface, double footX, double footY, double footZ, float bodyYawDeg,
                              float minX, float maxX, float minZ, float maxZ, Reading out) {
        // Model axes to world: x' = x cos(yaw) + z sin(yaw), z' = x sin(yaw) - z cos(yaw).
        final double yaw = Math.toRadians(bodyYawDeg);
        final double cos = Math.cos(yaw);
        final double sin = Math.sin(yaw);

        double lowX = Double.MAX_VALUE;
        double highX = -Double.MAX_VALUE;
        double lowZ = Double.MAX_VALUE;
        double highZ = -Double.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            final float mx = (corner & 1) == 0 ? minX : maxX;
            final float mz = (corner & 2) == 0 ? minZ : maxZ;
            final double wx = footX + mx * cos + mz * sin;
            final double wz = footZ + mx * sin - mz * cos;
            lowX = Math.min(lowX, wx);
            highX = Math.max(highX, wx);
            lowZ = Math.min(lowZ, wz);
            highZ = Math.max(highZ, wz);
        }

        final int firstCellX = (int) Math.floor(lowX / CELL);
        final int firstCellZ = (int) Math.floor(lowZ / CELL);
        final int lastCellX = Math.min((int) Math.floor(highX / CELL), firstCellX + MAX_CELLS_PER_AXIS - 1);
        final int lastCellZ = Math.min((int) Math.floor(highZ / CELL), firstCellZ + MAX_CELLS_PER_AXIS - 1);

        int count = 0;
        double largest = -1.0;
        double total = 0.0;
        for (int cx = firstCellX; cx <= lastCellX; cx++) {
            final double fromX = Math.max(lowX, cx * CELL);
            final double toX = Math.min(highX, (cx + 1) * CELL);
            for (int cz = firstCellZ; cz <= lastCellZ; cz++) {
                final double fromZ = Math.max(lowZ, cz * CELL);
                final double toZ = Math.min(highZ, (cz + 1) * CELL);

                // A zero-size footprint still gets sampled; it just carries no area.
                final double area = Math.max(toX - fromX, 0.0) * Math.max(toZ - fromZ, 0.0);
                final double sampleX = (fromX + toX) * 0.5;
                final double sampleZ = (fromZ + toZ) * 0.5;

                out.heights[count] = surface.surfaceY(sampleX, sampleZ, footY);
                out.areas[count] = area;
                total += area;
                if (area > largest) {
                    largest = area;
                    out.mainX = sampleX;
                    out.mainZ = sampleZ;
                }
                count++;
            }
        }

        out.height = supportHeight(out, count, total);
    }

    /** Area-weighted mean of the highest cells covering {@link #SUPPORT_SHARE} of the total. */
    private static double supportHeight(Reading out, int count, double total) {
        if (total <= 0.0) {
            double highest = out.heights[0];
            for (int i = 1; i < count; i++) {
                highest = Math.max(highest, out.heights[i]);
            }
            return highest;
        }

        // Highest first.
        final int[] order = out.order;
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        for (int i = 1; i < count; i++) {
            final int current = order[i];
            final double h = out.heights[current];
            int j = i - 1;
            while (j >= 0 && out.heights[order[j]] < h) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = current;
        }

        final double slice = SUPPORT_SHARE * total;
        double taken = 0.0;
        double sum = 0.0;
        for (int k = 0; k < count && taken < slice; k++) {
            final int i = order[k];
            final double share = Math.min(out.areas[i], slice - taken);
            sum += share * out.heights[i];
            taken += share;
        }
        return sum / taken;
    }
}
