package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.common.locomotion.ILocomotionSubject;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

/**
 * Base of a locomotion rig: corrects an already animated pose so the feet meet the terrain.
 *
 * <p>A rig is built once per model and shared by every entity drawn with it; per-entity memory
 * lives in {@link LocomotionState}. Call {@link #apply} at the very end of {@code setupAnim},
 * after every animation has been applied, so the rig corrects the pose that is about to be drawn.
 *
 * <p>This runs inside the render loop, where an exception ends the session. Everything degrades
 * instead: a missing bone disables the rig with one log line, and a non-finite value is refused
 * rather than written onto a bone.
 */
@OnlyIn(Dist.CLIENT)
public abstract class LocomotionRig<T extends LivingEntity & ILocomotionSubject> {

    protected static final Logger LOGGER = LogUtils.getLogger();

    /** Vertical movement per frame below which an entity counts as not moving, in blocks. */
    private static final double STILL_BLOCKS = 1.0e-4;

    private boolean disabled;
    private boolean nonFiniteReported;

    /** Solves one pass for one entity. {@code dt} is zero on repeat passes within a frame. */
    protected abstract void solve(T entity, LocomotionState state, float partialTick, float dt);

    /** Sizes the state for this rig (e.g. its leg count). Called before every solve. */
    protected void prepareState(LocomotionState state) {
    }

    public final void apply(T entity, float partialTick) {
        if (this.disabled) {
            return;
        }

        final LocomotionState state = LocomotionStateCache.of(entity);
        state.rigName = this.getClass().getSimpleName();
        this.prepareState(state);

        final boolean firstPass = state.beginFrame(LocomotionClock.frame(), LocomotionClock.frameNanos());
        final float dt = state.frameSeconds;

        final double y = entity.getY();
        double verticalMove = 0.0;
        if (firstPass) {
            verticalMove = Math.abs(y - state.lastEntityY);
            if (state.initialised && verticalMove > LocomotionState.TELEPORT_BLOCKS) {
                state.reset();
            }
            state.lastEntityY = y;
        }

        final float gateTarget = this.isActive(entity) ? 1.0f : 0.0f;
        if (state.initialised) {
            state.gate.update(gateTarget, dt);
        } else {
            state.gate.snap(gateTarget);
        }

        // Only count airborne time while actually moving vertically: a client-side entity can sit
        // with onGround false for a while after it is received, without going anywhere.
        if (entity.onGround()) {
            state.airborneSeconds = 0.0f;
        } else if (firstPass && verticalMove > STILL_BLOCKS) {
            state.airborneSeconds += dt;
        }

        state.bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);

        this.solve(entity, state, partialTick, dt);
        state.initialised = true;
    }

    /**
     * Whether the rig should be correcting this entity right now. Copies that are drawn without
     * being in the level (an entity carried by a player, a preview in a screen) have no ground to
     * read and are left alone.
     */
    protected boolean isActive(T entity) {
        return !LocomotionDebugFlags.bypass()
                && !entity.isLocomotionSuspended()
                && entity.level().getEntity(entity.getId()) == entity;
    }

    /** Turns the rig off for good, logging why once. */
    protected final void disable(String reason) {
        if (!this.disabled) {
            this.disabled = true;
            LOGGER.warn("[Locomotion] {} disabled: {}", this.getClass().getSimpleName(), reason);
        }
    }

    public final boolean isDisabled() {
        return this.disabled;
    }

    /**
     * Whether a computed value is safe to write onto a bone. A NaN reaching a model part makes
     * the whole mob vanish, and a NaN reaching a spring stays there, so it is refused and reported
     * once.
     */
    protected final boolean finite(float value, String what) {
        if (Float.isFinite(value)) {
            return true;
        }
        if (!this.nonFiniteReported) {
            this.nonFiniteReported = true;
            LOGGER.warn("[Locomotion] {} produced a non-finite {}; leaving the pose uncorrected",
                    this.getClass().getSimpleName(), what);
        }
        return false;
    }
}
