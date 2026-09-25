package com.evirapo.diversityofcritters.common.locomotion;

/** Where a body is in climbing a ledge. See {@link MantleProgress}. */
public enum MantlePhase {
    NONE,
    /** Standing against the face, front paws going up to the lip. */
    REACH,
    /** Hind legs push, the body rears up the face. */
    RISE,
    /** Front paws pass the lip and plant on top; the hind legs leave the ground. */
    CREST,
    /** On top, walking forward until the whole body is over the ledge. */
    RECOVER
}
