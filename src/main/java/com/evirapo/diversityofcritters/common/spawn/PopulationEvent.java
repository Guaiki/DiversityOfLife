package com.evirapo.diversityofcritters.common.spawn;

/**
 * Everything that moves a species' wild population, and which way it moves it.
 *
 * How far it moves it is up to the config ({@code [spawn.population.weights]}). The direction is
 * not configurable: a release that subtracted would no longer be a release.
 */
public enum PopulationEvent {

    TAME("tame", -1),
    RELEASE("release", +1),
    WILD_BIRTH("wildBirth", +1),
    STARVATION("starvation", -1),
    PLAYER_KILL("playerKill", -1),
    TEMPERATURE_DEATH("temperatureDeath", -1),
    HUMIDITY_DEATH("humidityDeath", -1);

    private final String configKey;
    private final int sign;

    PopulationEvent(String configKey, int sign) {
        this.configKey = configKey;
        this.sign = sign;
    }

    /** Key of this event's weight in the TOML and in {@code /doc_spawn set weights}. */
    public String configKey() {
        return this.configKey;
    }

    public int sign() {
        return this.sign;
    }

    /** The change this event makes to the population with the given weight. */
    public int delta(int weight) {
        return this.sign * weight;
    }
}
