package com.evirapo.diversityofcritters.client.locomotion.math;

/**
 * Critically damped spring, integrated with its exact closed-form solution.
 *
 * <p>Tuned by half-life: from rest, the value covers half the distance to a fixed target in
 * {@code halfLife} seconds and never overshoots. Because the step is exact, the result does not
 * depend on how a span of time is sliced into frames, so the smoothing looks the same at 30 and
 * at 240 FPS.
 *
 * <p>A zero or negative {@code dt} is a no-op. The rig relies on that to re-run a frame (extra
 * render passes) without advancing time twice.
 */
public final class Spring {

    /**
     * Solution of {@code (1 + u) e^-u = 1/2}: the step response of a critically damped system
     * reaches one half at {@code u = omega * t}.
     */
    static final float HALF_LIFE_TO_OMEGA = 1.678347f;

    private static final float INSTANT = 1.0e-5f;

    private float omega;
    private float value;
    private float velocity;

    public Spring(float halfLifeSeconds) {
        this.setHalfLife(halfLifeSeconds);
    }

    /** A half-life of zero makes the spring follow its target instantly. */
    public void setHalfLife(float halfLifeSeconds) {
        this.omega = halfLifeSeconds <= INSTANT ? Float.POSITIVE_INFINITY : HALF_LIFE_TO_OMEGA / halfLifeSeconds;
    }

    /** Jumps to {@code newValue} and stops. */
    public void snap(float newValue) {
        this.value = newValue;
        this.velocity = 0.0f;
    }

    public float value() {
        return this.value;
    }

    public float velocity() {
        return this.velocity;
    }

    public float update(float target, float dt) {
        if (dt <= 0.0f) {
            return this.value;
        }
        if (Float.isInfinite(this.omega)) {
            this.snap(target);
            return this.value;
        }

        // x(t) = target + (c1 + c2 t) e^(-w t), with c1 = x0 - target and c2 = v0 + w c1.
        final float c1 = this.value - target;
        final float c2 = this.velocity + this.omega * c1;
        final float decay = (float) Math.exp(-this.omega * dt);

        this.value = target + (c1 + c2 * dt) * decay;
        this.velocity = (this.velocity - this.omega * c2 * dt) * decay;
        return this.value;
    }

    /**
     * Blend factor for a first-order exponential approach at {@code rate} per second over
     * {@code dt}: {@code lerp(factor, current, target)} is frame-rate independent.
     */
    public static float blendFactor(float rate, float dt) {
        return dt <= 0.0f ? 0.0f : 1.0f - (float) Math.exp(-rate * dt);
    }
}
