package com.evirapo.diversityofcritters.client.guidebook;

import org.jetbrains.annotations.Nullable;

/**
 * A page being turned. The GUI is flat, so the leaf is faked: it narrows against the spine as if
 * it were rotating ({@code width = half * |cos(pi * progress)|}), showing the old page on its front
 * until it is edge-on halfway, then the new page on its back as it opens out on the other side.
 *
 * @param from      the page that was showing before the turn
 * @param direction +1 when a page was opened (the right leaf turns left), -1 for Back
 */
public record PageTurn(@Nullable BookPage from, int direction, long startMillis) {

    public static final long DURATION_MS = 400L;

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

    /** On-screen width of a leaf {@code half} wide at {@code progress}. */
    public static int leafWidth(float progress, int half) {
        return Math.round(half * Math.abs((float) Math.cos(Math.PI * progress)));
    }

    /** From halfway on, the leaf shows its back: the new page. */
    public static boolean showsBack(float progress) {
        return progress >= 0.5F;
    }
}
