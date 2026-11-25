package com.evirapo.diversityofcritters.common.guidebook;

/**
 * IUCN Red List categories, in the order the guidebook paints its status dots, left to right.
 */
public enum ConservationTier {
    EX, EW, CR, EN, VU, NT, LC, DD;

    /** Centre of this tier's dot on the species page, in guidebook texture pixels. */
    public int dotX() {
        return 164 + this.ordinal() * 14;
    }

    /**
     * How a species is doing in this world, from its wild population. No wild animals left is
     * "extinct in the wild"; below the endangered threshold it is CR (half the threshold or less, so a
     * species held at the default floor of 5 reads CR) or EN; below the base population it is VU (under three quarters of the base) or NT; otherwise LC.
     */
    public static ConservationTier forWildPopulation(int population, int basePopulation, int endangeredThreshold) {
        if (population <= 0) return EW;
        if (population < endangeredThreshold) return population * 2 <= endangeredThreshold ? CR : EN;
        if (population < basePopulation) return population * 4 < basePopulation * 3 ? VU : NT;
        return LC;
    }
}
