package com.evirapo.diversityofcritters.common.locomotion;

/**
 * An entity whose legs the client-side locomotion rig may adapt to the terrain.
 *
 * <p>Lives in common code because the entity implements it; only the client reads it.
 */
public interface ILocomotionSubject {

    /**
     * True while something other than walking on the ground poses the body: sleeping, sitting,
     * swimming, climbing a wall, being carried, dying. The rig fades out while this holds and
     * fades back in afterwards.
     */
    boolean isLocomotionSuspended();
}
