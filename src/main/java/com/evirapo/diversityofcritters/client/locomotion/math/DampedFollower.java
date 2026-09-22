package com.evirapo.diversityofcritters.client.locomotion.math;

/**
 * Under-damped spring for secondary motion: a tail or head that trails its driver, overshoots a
 * little and settles.
 *
 * <p>Two knobs: the half-life of the oscillation envelope (how quickly it settles) and the damping
 * ratio (how much it swings on the way). Integrated exactly, so frame rate does not change the
 * motion. The damping ratio is kept strictly inside (0, 1); a critically damped follower is
 * {@link Spring}.
 */
public final class DampedFollower {

    public static final float MIN_DAMPING = 0.05f;
    public static final float MAX_DAMPING = 0.95f;

    private static final float LN2 = 0.6931472f;
    private static final float INSTANT = 1.0e-5f;

    private float halfLife;
    private float damping;
    private float value;
    private float velocity;

    public DampedFollower(float halfLifeSeconds, float dampingRatio) {
        this.halfLife = Math.max(0.0f, halfLifeSeconds);
        this.damping = clampDamping(dampingRatio);
    }

    private static float clampDamping(float ratio) {
        return Math.max(MIN_DAMPING, Math.min(MAX_DAMPING, ratio));
    }

    public void setHalfLife(float halfLifeSeconds) {
        this.halfLife = Math.max(0.0f, halfLifeSeconds);
    }

    public void setDamping(float dampingRatio) {
        this.damping = clampDamping(dampingRatio);
    }

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
        if (this.halfLife <= INSTANT) {
            this.snap(target);
            return this.value;
        }

        // Envelope decays at sigma = zeta * omega; the half-life pins sigma.
        final float sigma = LN2 / this.halfLife;
        final float omega = sigma / this.damping;
        final float omegaD = omega * (float) Math.sqrt(1.0f - this.damping * this.damping);

        // x(t) = target + e^(-sigma t) (A cos wd t + B sin wd t)
        final float a = this.value - target;
        final float b = (this.velocity + sigma * a) / omegaD;
        final float decay = (float) Math.exp(-sigma * dt);
        final float cos = (float) Math.cos(omegaD * dt);
        final float sin = (float) Math.sin(omegaD * dt);

        final float oscillation = a * cos + b * sin;
        final float oscillationRate = omegaD * (b * cos - a * sin);

        this.value = target + decay * oscillation;
        this.velocity = decay * (oscillationRate - sigma * oscillation);
        return this.value;
    }
}
