package com.evirapo.diversityofcritters.client.guidebook;

import org.jetbrains.annotations.Nullable;

/**
 * A page being turned. The leaf is a rigid sheet hinged on the spine: its angle runs from 0 (lying
 * flat on the right half) to pi (lying on the left half), eased so it lifts off and settles down
 * gently. While it is raised it comes towards the viewer, so a simple perspective makes its outer
 * edge taller and pushes it outwards. Its front shows the old page until it stands upright, then its
 * back shows the new one.
 *
 * @param from      the page that was showing before the turn
 * @param direction +1 when a page was opened (the right leaf turns left), -1 for Back
 */
public record PageTurn(@Nullable BookPage from, int direction, long startMillis) {

    public static final long DURATION_MS = 650L;
    /** Distance from the viewer to the book, in GUI pixels: smaller is a stronger perspective. */
    public static final float FOCAL = 550.0F;

    public float progress(long nowMillis) {
        float p = (nowMillis - this.startMillis) / (float) DURATION_MS;
        return Math.max(0.0F, Math.min(1.0F, p));
    }

    public boolean done(long nowMillis) {
        return nowMillis - this.startMillis >= DURATION_MS;
    }

    public boolean forward() {
        return this.direction > 0;
    }

    /** Leaf angle around the spine: 0 lying on the right half, pi lying on the left half. */
    public float angle(long nowMillis) {
        float e = ease(progress(nowMillis));
        return (float) Math.PI * (forward() ? e : 1.0F - e);
    }

    /** Ease in and out: slow off the page, fastest upright, slow onto the other page. */
    public static float ease(float p) {
        return (1.0F - (float) Math.cos(Math.PI * p)) / 2.0F;
    }

    /** How much taller a point {@code d} out from the spine looks when the leaf is at {@code angle}. */
    public static float heightScale(float d, float angle) {
        return FOCAL / (FOCAL - d * (float) Math.sin(angle));
    }

    /** Horizontal screen offset from the spine of a point {@code d} out along the leaf. */
    public static float projectedX(float d, float angle) {
        return d * (float) Math.cos(angle) * heightScale(d, angle);
    }

    /** From halfway on, the leaf shows its back: the new page. */
    public static boolean showsBack(float progress) {
        return progress >= 0.5F;
    }
}
