package com.evirapo.diversityofcritters.common.spawn;

/**
 * Why a spawn attempt was accepted or rejected.
 *
 * Consumed by {@code /doc_spawn} so it can tell the player exactly which filter failed.
 */
public enum SpawnRejectReason {

    /** Passed every filter and won the roll. */
    PASSED("passed", true),
    /** Spawn egg, spawner, command, breeding... never filtered. */
    BYPASS_EXPLICIT("explicit spawn, not gated", true),

    /** The species is disabled in the config. */
    DISABLED("species disabled in config", false),
    /** Not the Overworld. */
    WRONG_DIMENSION("not the overworld", false),
    /** Outside the [minY, maxY] range. */
    Y_OUT_OF_RANGE("Y out of configured range", false),
    /** Block light above maxBlockLight. */
    TOO_BRIGHT("block light too high", false),
    /** skyAccess = REQUIRED and the block cannot see the sky. */
    SKY_REQUIRED("needs open sky", false),
    /** skyAccess = FORBIDDEN and the block can see the sky. */
    SKY_FORBIDDEN("needs cover, sky is open", false),
    /** The block below is not in the valid-ground tag. */
    NO_VALID_GROUND("ground block not spawnable", false),
    /** Arboreal species: no logs nearby. */
    NO_TREES_NEARBY("no trees nearby", false),
    /** There are already groupMax or more animals within groupCheckRadius. */
    GROUP_LIMIT("too many nearby already", false),
    /** Passed every filter but lost the probability roll. */
    CHANCE_ROLL_FAILED("lost the spawn chance roll", false);

    private final String description;
    private final boolean allowed;

    SpawnRejectReason(String description, boolean allowed) {
        this.description = description;
        this.allowed = allowed;
    }

    public String description() {
        return this.description;
    }

    public boolean allowed() {
        return this.allowed;
    }
}
