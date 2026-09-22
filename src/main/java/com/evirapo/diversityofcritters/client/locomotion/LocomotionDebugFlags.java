package com.evirapo.diversityofcritters.client.locomotion;

/**
 * Switches the debug tools flip and the rig reads. Kept apart from {@link LocomotionDebug} so the
 * rig never loads key mappings or render hooks (and stays testable without a game).
 */
public final class LocomotionDebugFlags {

    /** Overlay on: rigs also measure where the soles ended up after correction. */
    static volatile boolean overlay;
    /** Bypass on: every rig fades out, showing the pose exactly as animated. */
    static volatile boolean bypass;
    /** One frame of capture requested for a dump, even with the overlay off. */
    static volatile boolean captureOnce;

    private LocomotionDebugFlags() {
    }

    public static boolean overlay() {
        return overlay;
    }

    public static boolean bypass() {
        return bypass;
    }

    /** Whether rigs should record corrected positions this frame. */
    public static boolean capturing() {
        return overlay || captureOnce;
    }
}
