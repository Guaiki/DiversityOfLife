package com.evirapo.diversityofcritters.common.locomotion;

import net.minecraft.core.Direction;

/**
 * A mantle in progress, as the entity reports it ({@link ILocomotionSubject#readMantle}). The
 * server owns it; the client reads the synchronised copy. Reusable holder.
 */
public final class MantleState {

    /** The block whose top is the lip. */
    public int lipX;
    public int lipBlockY;
    public int lipZ;
    /** World Y of the lip. */
    public double lipY;
    /** Direction the body faces the wall in. */
    public Direction facing = Direction.SOUTH;
    /** The body's Y when the mantle started. */
    public double startY;
    /** Game time the mantle started at. */
    public long startTick;
    /**
     * How fast this mantle plays against the game, 1 normally; below 1 it runs in slow motion
     * (a debug setting). Counting mantle time is {@code (gameTime - startTick) × timeScale}.
     */
    public float timeScale = 1.0f;
    /**
     * Started from a climb, at the top of a trunk: the body is already up against the face, so
     * there is no reach and the hind feet push on the face, not on the ground.
     */
    public boolean fromClimb;

    /** Sets everything but the time scale, which starts at 1, and {@link #fromClimb}, which starts false. */
    public MantleState set(int lipX, int lipBlockY, int lipZ, double lipY, Direction facing, double startY, long startTick) {
        this.lipX = lipX;
        this.lipBlockY = lipBlockY;
        this.lipZ = lipZ;
        this.lipY = lipY;
        this.facing = facing;
        this.startY = startY;
        this.startTick = startTick;
        this.timeScale = 1.0f;
        this.fromClimb = false;
        return this;
    }

    /** Mantle time elapsed at {@code gameTime}, in ticks at the mantle's own pace. */
    public double elapsed(double gameTime) {
        return (gameTime - this.startTick) * this.timeScale;
    }

    /** World X (facing east/west) or Z (north/south) of the face: the lip block's near side. */
    public double wallCoord() {
        return switch (this.facing) {
            case EAST -> this.lipX;
            case WEST -> this.lipX + 1.0;
            case NORTH -> this.lipZ + 1.0;
            default -> this.lipZ;
        };
    }

    /** How far a point is past the face, along the facing; negative in front of it. */
    public double pastFace(double x, double z) {
        return this.facing.getStepX() != 0
                ? this.facing.getStepX() * (x - this.wallCoord())
                : this.facing.getStepZ() * (z - this.wallCoord());
    }
}
