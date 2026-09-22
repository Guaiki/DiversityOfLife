package com.evirapo.diversityofcritters.client.locomotion.math;

/**
 * Weighted least-squares plane {@code y = slopeX * x + slopeZ * z + height} through the feet.
 *
 * <p>Solved about the weighted centroid, which reduces the problem to a 2x2 system for the two
 * slopes. Both slopes are pulled toward zero by a small ridge term scaled to the footprint's size:
 * well-planted feet overwhelm it, while feet carrying only a trace of weight cannot tilt the body
 * on their own. The ridge also covers the degenerate layouts. With two contacts (a trotting
 * diagonal) the slope along the pair is still measured and the slope across it goes to zero; with
 * one contact the plane is flat at that foot's height.
 */
public final class PlaneFit {

    /**
     * How much weight, spread over the footprint, argues for a level body. Relative to plant
     * weights in [0, 1] per foot.
     *
     * <p>The bias is isotropic, so on a narrow body it bites harder across (roll) than along
     * (pitch): on a 4 x 13 unit stance with all four feet planted it costs about 5% of roll and
     * under 1% of pitch. Leg stretch absorbs that remainder. Raising it trades more of that loss
     * for stronger rejection of residual contacts.
     */
    public static final float LEVEL_BIAS = 0.02f;

    private PlaneFit() {
    }

    /** Result holder, reused by the caller so the fit allocates nothing. */
    public static final class Plane {
        public float slopeX;
        public float slopeZ;
        /** Height of the plane at x = z = 0, which is under the pelvis. */
        public float height;

        void set(float slopeX, float slopeZ, float height) {
            this.slopeX = slopeX;
            this.slopeZ = slopeZ;
            this.height = height;
        }
    }

    /**
     * @param xs      per-foot X offset from the pelvis
     * @param zs      per-foot Z offset from the pelvis
     * @param ys      per-foot height the plane should pass through
     * @param weights per-foot weight; zero or less ignores the foot
     * @param count   number of feet to read
     * @param out     receives the plane; flat at zero when nothing carries weight
     */
    public static void fit(float[] xs, float[] zs, float[] ys, float[] weights, int count, Plane out) {
        float total = 0.0f;
        float meanX = 0.0f;
        float meanZ = 0.0f;
        float meanY = 0.0f;
        for (int i = 0; i < count; i++) {
            final float w = weights[i];
            if (w > 0.0f) {
                total += w;
                meanX += w * xs[i];
                meanZ += w * zs[i];
                meanY += w * ys[i];
            }
        }
        if (total <= 0.0f) {
            out.set(0.0f, 0.0f, 0.0f);
            return;
        }
        meanX /= total;
        meanZ /= total;
        meanY /= total;

        float cxx = 0.0f;
        float cxz = 0.0f;
        float czz = 0.0f;
        float cxy = 0.0f;
        float czy = 0.0f;
        for (int i = 0; i < count; i++) {
            final float w = weights[i];
            if (w <= 0.0f) {
                continue;
            }
            final float dx = xs[i] - meanX;
            final float dz = zs[i] - meanZ;
            final float dy = ys[i] - meanY;
            cxx += w * dx * dx;
            cxz += w * dx * dz;
            czz += w * dz * dz;
            cxy += w * dx * dy;
            czy += w * dz * dy;
        }

        // The ridge is sized from the footprint alone, not from the weights, so it stays fixed
        // while contacts fade in and out.
        final float ridge = LEVEL_BIAS * footprintSpread(xs, zs, count);
        final float m00 = cxx + ridge;
        final float m11 = czz + ridge;
        final float det = m00 * m11 - cxz * cxz;
        final float scale = Math.max(m00, m11);

        if (scale <= 0.0f || det <= 1.0e-6f * scale * scale) {
            // Every foot on the same spot: height is known, tilt is not.
            out.set(0.0f, 0.0f, meanY);
            return;
        }

        final float slopeX = (m11 * cxy - cxz * czy) / det;
        final float slopeZ = (m00 * czy - cxz * cxy) / det;
        out.set(slopeX, slopeZ, meanY - slopeX * meanX - slopeZ * meanZ);
    }

    /** Mean squared distance of the feet from their unweighted centroid. */
    private static float footprintSpread(float[] xs, float[] zs, int count) {
        if (count <= 0) {
            return 0.0f;
        }
        float cx = 0.0f;
        float cz = 0.0f;
        for (int i = 0; i < count; i++) {
            cx += xs[i];
            cz += zs[i];
        }
        cx /= count;
        cz /= count;

        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            final float dx = xs[i] - cx;
            final float dz = zs[i] - cz;
            sum += dx * dx + dz * dz;
        }
        return sum / count;
    }
}
