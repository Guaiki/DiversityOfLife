package com.evirapo.diversityofcritters.client.locomotion;

/**
 * Height of the walkable surface under a point.
 *
 * <p>Kept separate from the world so {@link GroundProbe} can be tested against plain height
 * fields; {@link BlockSurfaceQuery} is the implementation that reads blocks.
 */
@FunctionalInterface
public interface SurfaceQuery {

    /**
     * World Y of the surface a foot at {@code footY} would stand on at column (x, z). May be above
     * the foot (a step it has to rise onto) or below it (a drop).
     */
    double surfaceY(double x, double z, double footY);
}
