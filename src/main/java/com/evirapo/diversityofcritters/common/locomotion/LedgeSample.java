package com.evirapo.diversityofcritters.common.locomotion;

import net.minecraft.core.Direction;

/**
 * What {@link LedgeSampler} found ahead of a body. Reused across calls: one per caller.
 *
 * <p>Every field is written on every call. When a wall was reached but rejected ({@link #reason}
 * other than {@link Reason#NO_WALL}), the geometry still describes it, for the debug overlay.
 */
public final class LedgeSample {

    /** Why there is no ledge to mantle, or {@link #NONE} when there is. */
    public enum Reason {
        NONE,
        /** Nothing high enough within reach. */
        NO_WALL,
        /** A step of at most a half block comes first: plain stairs, walked normally. */
        STEPPED,
        /** The wall rises past what the species can mantle (fence, wall, two blocks). */
        TOO_HIGH,
        /** No room for the body on top. */
        NO_HEADROOM,
        /** The top is liquid or fire. */
        HAZARD
    }

    public boolean found;
    public Reason reason = Reason.NO_WALL;

    /** Horizontal direction the body faces the wall in; the wall's face points the other way. */
    public Direction facing = Direction.SOUTH;
    /** World X (facing east/west) or Z (facing north/south) of the wall's face. */
    public double wallCoord;
    /** World Y of the lip: the top of the wall. */
    public double lipY;
    /** World Y of the surface the body would stand on past the lip. */
    public double topY;
    /** {@code lipY} minus the feet's Y. */
    public double lipHeight;
    /** Free height above the lip, capped at {@link LedgeSampler#MAX_HEADROOM}. */
    public double headroom;
    /** The wall carries on up as something the species can climb, rather than ending in a lip. */
    public boolean continuesUp;
    /**
     * How squarely the body faces the wall: 1 head on, falling to 0 at 45 degrees, where the
     * probe's axis flips. Lower when only part of the body's width meets the lip.
     */
    public float confidence;
    /** The block whose top is the lip. */
    public int lipX;
    public int lipBlockY;
    public int lipZ;

    /** Span scratch; slot 2 carries the block Y of the last obstacle top. */
    final double[] span = new double[3];
    final LedgeSampler.BlockWorld blocks = new LedgeSampler.BlockWorld();

    /** A wall was reached, whether or not it can be mantled; the geometry fields describe it. */
    public boolean hasWall() {
        return this.lipHeight > 0.0;
    }

    void clear() {
        this.found = false;
        this.reason = Reason.NO_WALL;
        this.wallCoord = 0.0;
        this.lipY = 0.0;
        this.topY = 0.0;
        this.lipHeight = 0.0;
        this.headroom = 0.0;
        this.continuesUp = false;
        this.confidence = 0.0f;
        this.lipX = 0;
        this.lipBlockY = 0;
        this.lipZ = 0;
    }
}
