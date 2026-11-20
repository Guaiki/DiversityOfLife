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
}
