package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.locomotion.math.Spring;

/**
 * Reads, from the animated pose alone, how planted each foot is.
 *
 * <p>Nothing in a keyframe says "this foot is on the ground", so it is inferred from how the sole
 * moves relative to the pelvis. The tracker keeps a slowly relaxing envelope of each sole's
 * lowest and highest recent height. A foot near the bottom of its envelope and not rising fast is
 * in stance; one high in its envelope, or rising quickly, is in swing. A foot that has stopped
 * moving is planted wherever it is, so idle poses count as standing.
 *
 * <p>Hysteresis on entry and exit keeps a foot near the threshold from flickering, and the final
 * weight is smoothed so the body never sees a step change.
 */
public final class StanceTracker {

    /** How fast the envelope relaxes toward the current height, per second. */
    static final float ENVELOPE_RELAX = 0.5f;
    /** Envelope narrower than this (model units) means the foot is not cycling at all. */
    static final float MIN_CYCLE = 0.5f;
    /** Height within the envelope, as a fraction, at which the foot counts as fully lifted. */
    static final float LIFTED_AT = 0.3f;
    /** Upward sole speed (model units / s) at which the foot counts as fully in swing. */
    static final float SWING_SPEED = 20.0f;
    /** Below this speed (model units / s) the foot counts as still. */
    static final float STILL_SPEED = 1.0f;
    /** How long a still foot takes to count as planted regardless of height, seconds. */
    static final float STILL_TIME = 0.4f;
    static final float ENTER_STANCE = 0.6f;
    static final float LEAVE_STANCE = 0.4f;
    /** Rate at which the plant weight follows its target, per second. */
    static final float WEIGHT_RATE = 15.0f;

    private StanceTracker() {
    }

    /**
     * @param soleHeight sole height relative to the pelvis in model units, <b>up positive</b>
     * @return the leg's updated plant weight
     */
    public static float update(LocomotionState.LegState leg, float soleHeight, float dt) {
        if (dt <= 0.0f) {
            return leg.plantWeight;
        }

        if (!leg.stanceSeeded) {
            leg.envelopeLow = soleHeight;
            leg.envelopeHigh = soleHeight;
            leg.lastSoleHeight = soleHeight;
            leg.stillSeconds = 0.0f;
            leg.plantWeight = leg.contactOverride >= 0.0f ? leg.contactOverride : 1.0f;
            leg.stanceSeeded = true;
            return leg.plantWeight;
        }

        // Relax toward the present, then let the present push the edges out.
        final float relax = Spring.blendFactor(ENVELOPE_RELAX, dt);
        leg.envelopeLow = Math.min(leg.envelopeLow + (soleHeight - leg.envelopeLow) * relax, soleHeight);
        leg.envelopeHigh = Math.max(leg.envelopeHigh + (soleHeight - leg.envelopeHigh) * relax, soleHeight);

        final float cycle = leg.envelopeHigh - leg.envelopeLow;
        float lifted = cycle < MIN_CYCLE ? 0.0f : (soleHeight - leg.envelopeLow) / cycle;

        final float risingSpeed = (soleHeight - leg.lastSoleHeight) / dt;
        leg.lastSoleHeight = soleHeight;
        leg.stillSeconds = Math.abs(risingSpeed) < STILL_SPEED ? leg.stillSeconds + dt : 0.0f;
        lifted *= 1.0f - smoothstep(leg.stillSeconds / STILL_TIME);

        final float lowEnough = 1.0f - smoothstep(lifted / LIFTED_AT);
        final float slowEnough = 1.0f - clamp01(risingSpeed / SWING_SPEED);
        float stance = lowEnough * slowEnough;
        if (leg.contactOverride >= 0.0f) {
            stance = leg.contactOverride;
        }

        final float threshold = leg.plantWeight > 0.5f ? LEAVE_STANCE : ENTER_STANCE;
        final float target = stance > threshold ? stance : 0.0f;
        leg.plantWeight += (target - leg.plantWeight) * Spring.blendFactor(WEIGHT_RATE, dt);
        return leg.plantWeight;
    }

    private static float clamp01(float x) {
        return x < 0.0f ? 0.0f : Math.min(x, 1.0f);
    }

    /** Smoothstep over [0, 1]. */
    private static float smoothstep(float x) {
        final float t = clamp01(x);
        return t * t * (3.0f - 2.0f * t);
    }
}
