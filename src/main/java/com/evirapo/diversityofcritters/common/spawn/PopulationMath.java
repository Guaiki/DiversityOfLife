package com.evirapo.diversityofcritters.common.spawn;

/**
 * All the arithmetic of the wild population model, with no Minecraft types so it can be unit
 * tested. {@link WildPopulation} persists, this decides.
 */
public final class PopulationMath {

    /** Days of history kept for the trend shown by {@code /doc_spawn}. */
    public static final int HISTORY_DAYS = 7;

    public enum Crossing { NONE, BECAME_ENDANGERED, RECOVERED }

    /**
     * Keeps a population between the floor and the cap. A floor above the cap is a broken config;
     * the cap wins, so the population can never exceed what the config says is the maximum.
     */
    public static int clamp(int population, int floor, int cap) {
        int effectiveFloor = Math.min(floor, cap);
        return Math.max(effectiveFloor, Math.min(cap, population));
    }

    /** {@code population / basePopulation}, between 0 and {@code 1 + maxBonus}. */
    public static double multiplier(int population, int basePopulation, double maxBonus) {
        if (basePopulation <= 0) return 1.0D;
        double raw = (double) population / basePopulation;
        return Math.max(0.0D, Math.min(1.0D + maxBonus, raw));
    }

    public static double finalChance(double chanceBase, double multiplier) {
        return Math.max(0.0D, Math.min(1.0D, chanceBase * multiplier));
    }

    /** A species' starting population: its own if configured (0 or more), else the base; clamped. */
    public static int startingPopulation(int configured, int basePopulation, int floor, int cap) {
        return clamp(configured >= 0 ? configured : basePopulation, floor, cap);
    }

    /**
     * Version 1 stored how many animals had been released, on top of an implicit baseline. The
     * baseline is now explicit, so it gets added.
     */
    public static int migrateV1(int releasedCount, int basePopulation, int floor, int cap) {
        return clamp(basePopulation + Math.max(0, releasedCount), floor, cap);
    }

    /** Appends a daily sample, keeping the last {@link #HISTORY_DAYS}, oldest first. */
    public static int[] pushSample(int[] history, int value) {
        int keep = Math.min(history.length, HISTORY_DAYS - 1);
        int[] next = new int[keep + 1];
        System.arraycopy(history, history.length - keep, next, 0, keep);
        next[keep] = value;
        return next;
    }

    /** Change against the oldest sample kept: a week ago once there is a week of history. */
    public static int trend(int current, int[] history) {
        if (history.length == 0) return 0;
        return current - history[0];
    }

    /** "Endangered" means strictly below the threshold. */
    public static Crossing crossing(int before, int after, int threshold) {
        boolean wasEndangered = before < threshold;
        boolean isEndangered = after < threshold;
        if (!wasEndangered && isEndangered) return Crossing.BECAME_ENDANGERED;
        if (wasEndangered && !isEndangered) return Crossing.RECOVERED;
        return Crossing.NONE;
    }

    /**
     * Bound of the 1-in-N roll a wild animal makes to look for a mate. A faster rate means a
     * smaller N. Returns 0 for "never".
     */
    public static int mateRollBound(int baseBound, double rate) {
        if (rate <= 0.0D) return 0;
        return Math.max(1, (int) Math.round(baseBound / rate));
    }

    /** Breeding cooldown at the given rate. With rate 0 nothing breeds anyway, so it stays as is. */
    public static int scaledCooldown(int baseTicks, double rate) {
        if (rate <= 0.0D) return baseTicks;
        return Math.max(1, (int) Math.round(baseTicks / rate));
    }

    private PopulationMath() {}
}
