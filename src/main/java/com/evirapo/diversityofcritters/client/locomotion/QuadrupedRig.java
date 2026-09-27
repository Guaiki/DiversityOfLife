package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.locomotion.mantle.MantleDriver;
import com.evirapo.diversityofcritters.common.locomotion.ILocomotionSubject;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Terrain adaptation for a body standing on rigid, single-bone legs that hang from one pelvis bone:
 * the compact quadruped.
 *
 * <p>Each frame it reads the animated pose, finds the ground under every foot, lets
 * {@link QuadrupedSolver} decide the body's tilt and height, then writes:
 * <ol>
 *   <li>the tilt onto the pelvis, shifting it so the body turns about its supporting hips rather
 *       than about the pelvis pivot (for many models the pivot sits at ground level, and turning
 *       about it would slide the whole body);</li>
 *   <li>the height onto the pelvis;</li>
 *   <li>per leg: undo the tilt so the leg stays upright, then stretch or shrink it within a small
 *       limit for whatever is left between sole and ground.</li>
 * </ol>
 *
 * <p>A leg with an anchor ({@link LocomotionState.LegState#anchorWeight}) instead aims at a point
 * in the world and stretches toward it, within a looser limit. What the anchored legs cannot reach
 * moves the pelvis first; the legs then take up the rest, and report what is still missing in
 * {@link LocomotionState.LegState#anchorShortfall}.
 *
 * <p>With a {@link MantleProfile}, a {@link MantleDriver} poses the body while the entity climbs a
 * ledge: the pitch comes from the manoeuvre, the body turns about its hind hips, and the legs are
 * anchored to the lip and to the ground they stood on.
 */
@OnlyIn(Dist.CLIENT)
public final class QuadrupedRig<T extends LivingEntity & ILocomotionSubject> extends LocomotionRig<T> {

    /** Below this the gate is closed enough to leave the pose alone. */
    private static final float GATE_EPSILON = 1.0e-3f;

    private final ILocomotionModel<T> locomotionModel;
    private final ModelPart pelvis;
    private final List<ModelPart> pelvisChain;
    private final List<LegChain> legs;
    private final QuadrupedSolver solver;
    private final SpineRules spine;
    private final String[] legNames;

    private final ModelSpace space = new ModelSpace();
    private final BlockSurfaceQuery surface = new BlockSurfaceQuery();
    private final Matrix4f scratch = new Matrix4f();
    private final Matrix3f rotation = new Matrix3f();
    private final Vector3d world = new Vector3d();
    private final Vector3f shift = new Vector3f();
    private final Matrix4f worldToPelvis = new Matrix4f();
    private final Vector3f target = new Vector3f();
    private final Matrix3f aimRotation = new Matrix3f();
    private final Quaternionf aimTurn = new Quaternionf();
    private final Quaternionf aimShare = new Quaternionf();
    private final Vector3f aimAxis = new Vector3f();
    private final float anchorStretchLimit;
    private final float maxAnchorShift;
    /** Legs behind the body's middle (model +Z): the ones a mantle pivots on. */
    private final boolean[] rear;
    private final @org.jetbrains.annotations.Nullable MantleDriver mantle;
    private final MantleState mantleState = new MantleState();
    private final float[] shiftX;
    private final float[] shiftY;
    private final float[] shiftZ;

    private QuadrupedRig(Builder<T> builder, ModelPart pelvis, List<ModelPart> pelvisChain, List<LegChain> legs,
                         SpineRules spine) {
        this.locomotionModel = builder.locomotionModel;
        this.spine = spine;
        this.pelvis = pelvis;
        this.pelvisChain = pelvisChain;
        this.legs = legs;
        this.legNames = builder.legNames.toArray(new String[0]);
        this.solver = new QuadrupedSolver(legs.size(),
                (float) Math.toRadians(builder.maxPitchDeg), (float) Math.toRadians(builder.maxRollDeg),
                builder.stretchLimit, builder.maxDropFraction, legs.get(0).soleOffset());
        this.anchorStretchLimit = builder.anchorStretchLimit;
        this.maxAnchorShift = builder.maxAnchorShiftFraction * legs.get(0).soleOffset();
        this.rear = new boolean[legs.size()];
        final float[] side = new float[legs.size()];
        for (int i = 0; i < legs.size(); i++) {
            this.rear[i] = legs.get(i).hip().z > 0.0f;
            side[i] = legs.get(i).hip().x;
        }
        this.mantle = builder.mantleProfile == null ? null
                : new MantleDriver(builder.mantleProfile, this.rear, side);
        this.shiftX = new float[legs.size()];
        this.shiftY = new float[legs.size()];
        this.shiftZ = new float[legs.size()];
    }

    @Override
    protected void prepareState(LocomotionState state) {
        final LocomotionState.LegState[] legStates = state.ensureLegs(this.legs.size());
        for (int i = 0; i < legStates.length; i++) {
            legStates[i].name = this.legNames[i];
        }
    }

    @Override
    protected void solve(T entity, LocomotionState state, float partialTick, float dt) {
        final float gate = state.gate.value();
        final int count = this.legs.size();
        final LocomotionState.LegState[] legStates = state.legs();
        final QuadrupedSolver solver = this.solver;

        // --- Read the animated pose and the ground. Nothing is written until the solve is done.
        this.space.begin(entity, this.locomotionModel, partialTick, state.bodyYaw);
        final float unitsPerBlock = this.space.modelUnitsPerBlock();
        final double originY = entity.getY();
        final float pelvisModelY = LegChain.modelY(this.scratch, this.pelvisChain, 0.0f);
        this.surface.with(entity.level());

        for (int i = 0; i < count; i++) {
            final LegChain chain = this.legs.get(i);
            final LocomotionState.LegState leg = legStates[i];
            final ModelPart hip = chain.hip();

            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, this.world);
            leg.soleX = this.world.x;
            leg.soleY = this.world.y;
            leg.soleZ = this.world.z;

            GroundProbe.sample(this.surface, leg.soleX, leg.soleY, leg.soleZ, state.bodyYaw,
                    chain.footMinX() / unitsPerBlock, chain.footMaxX() / unitsPerBlock,
                    chain.footMinZ() / unitsPerBlock, chain.footMaxZ() / unitsPerBlock, leg.ground);
            leg.groundY = leg.ground.height;

            // Stance from the pose alone: sole height above the pelvis, up positive.
            StanceTracker.update(leg, pelvisModelY - chain.soleModelY(this.scratch), dt);

            solver.hipX[i] = hip.x;
            solver.hipZ[i] = hip.z;
            solver.soleUp[i] = (float) (leg.soleY - originY) * unitsPerBlock;
            solver.groundUp[i] = (float) (leg.groundY - originY) * unitsPerBlock;
        }

        updateDebugPin(legStates[0]);

        if (this.mantle != null) {
            final MantleState mantle = entity.readMantle(this.mantleState) ? this.mantleState : null;
            this.mantle.update(state, mantle,
                    Mth.lerp(partialTick, entity.xOld, entity.getX()),
                    Mth.lerp(partialTick, entity.yOld, entity.getY()),
                    Mth.lerp(partialTick, entity.zOld, entity.getZ()),
                    entity.level().getGameTime() + partialTick, entity.getBbWidth() * 0.5f, unitsPerBlock, dt);
        }
        solver.mantleAuthority = state.mantleAuthority.value();
        solver.mantlePitch = state.mantlePitch;

        solver.solve(state, gate, dt);
        this.spine.track(state, state.pelvisPitch.value(), state.pelvisRoll.value(), state.bodyYaw, dt);
        state.debugAuthority = solver.authority;
        state.debugUnitsPerBlock = unitsPerBlock;

        // Keep measuring while closed, so stance tracking and the overlay stay current, but leave
        // the animated pose exactly as it is.
        if (gate > GATE_EPSILON) {
            this.writePose(state, gate);
        }
        if (LocomotionDebugFlags.capturing()) {
            this.captureCorrected(legStates);
        }
    }

    /** Where each sole ended up after the correction, for the debug overlay. */
    private void captureCorrected(LocomotionState.LegState[] legStates) {
        for (int i = 0; i < this.legs.size(); i++) {
            final LegChain chain = this.legs.get(i);
            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, this.world);
            legStates[i].debugCorrectedSoleX = this.world.x;
            legStates[i].debugCorrectedSoleY = this.world.y;
            legStates[i].debugCorrectedSoleZ = this.world.z;
        }
    }

    /**
     * Writes the solved pelvis springs and each leg's remaining correction onto the bones. Split
     * from {@link #solve} so the geometry can be checked against a baked model without an entity.
     */
    void writePose(LocomotionState state, float gate) {
        final int count = this.legs.size();
        final LocomotionState.LegState[] legStates = state.legs();
        final QuadrupedSolver solver = this.solver;

        final float pitch = state.pelvisPitch.value();
        final float roll = state.pelvisRoll.value();
        final float drop = state.pelvisDrop.value();
        final float mantle = state.mantleAuthority.value();
        final float bodyDrop = state.mantleBodyDrop * gate;
        if (!this.finite(pitch, "pelvis pitch") || !this.finite(roll, "pelvis roll")
                || !this.finite(drop, "pelvis drop")) {
            return;
        }

        // --- Pelvis: tilt, turning about the supporting hips, then height.
        float totalWeight = 0.0f;
        float meanX = 0.0f;
        float meanY = 0.0f;
        float meanZ = 0.0f;
        float pivotX = 0.0f;
        float pivotY = 0.0f;
        float pivotZ = 0.0f;
        for (int i = 0; i < count; i++) {
            final ModelPart hip = this.legs.get(i).hip();
            QuadrupedSolver.hipShift(this.rotation, this.pelvis.xRot, this.pelvis.yRot, this.pelvis.zRot,
                    pitch, roll, hip.x, hip.y, hip.z, this.shift);
            this.shiftX[i] = this.shift.x;
            this.shiftY[i] = this.shift.y;
            this.shiftZ[i] = this.shift.z;
            // A trace of weight on every hip keeps the pivot defined in the air. A mantle turns
            // the body about its hind hips, whatever the feet say.
            float w = solver.weight[i] + 1.0e-3f;
            if (mantle > 0.0f) {
                w += ((this.rear[i] ? 1.0f : 1.0e-3f) - w) * mantle;
            }
            meanX += this.shiftX[i] * w;
            meanY += this.shiftY[i] * w;
            meanZ += this.shiftZ[i] * w;
            pivotX += hip.x * w;
            pivotY += hip.y * w;
            pivotZ += hip.z * w;
            totalWeight += w;
        }
        meanX /= totalWeight;
        meanY /= totalWeight;
        meanZ /= totalWeight;

        this.pelvis.xRot += pitch;
        this.pelvis.zRot += roll;
        this.pelvis.x -= meanX;
        this.pelvis.y += drop - meanY + bodyDrop;
        this.pelvis.z -= meanZ;

        state.debugPivotValid = mantle > 0.01f && LocomotionDebugFlags.capturing();
        if (state.debugPivotValid) {
            this.space.toWorld(this.pelvisChain, pivotX / totalWeight, pivotY / totalWeight, pivotZ / totalWeight,
                    this.world);
            state.debugPivotX = this.world.x;
            state.debugPivotY = this.world.y;
            state.debugPivotZ = this.world.z;
        }

        // --- Anchored legs: the pelvis goes first, by what they cannot reach.
        final float anchorDown = this.anchorPelvis(legStates, gate);

        // --- Legs: stay upright, then take up what is left.
        for (int i = 0; i < count; i++) {
            final LegChain chain = this.legs.get(i);
            final LocomotionState.LegState leg = legStates[i];
            final ModelPart hip = chain.hip();

            hip.xRot -= pitch;
            hip.zRot -= roll;

            // How far the hip ended up moving down (model +Y), which the sole has to make up.
            final float hipDown = this.shiftY[i] - meanY + drop + bodyDrop + anchorDown;
            final float residual = leg.footLift.value() + hipDown;
            final float applied = QuadrupedSolver.appliedResidual(
                    residual, Math.max(leg.plantWeight, leg.contactWeight), gate);
            final float stretch = solver.stretchFor(applied);
            if (this.finite(stretch, "leg stretch")) {
                hip.yScale += stretch;
            }
            leg.debugResidual = applied;
            leg.debugStretch = stretch;

            final float anchor = anchorWeight(leg, gate);
            if (anchor > GATE_EPSILON) {
                this.aim(chain, leg, anchor);
            } else {
                leg.anchorShortfall = 0.0f;
            }
        }

        // --- Head and tail answer what was done to the body.
        this.spine.write(state, pitch, roll, state.bodyYaw, gate, state.mantleArch * mantle * gate);
    }

    /**
     * Moves the pelvis by the weighted mean of what the anchored legs cannot reach, capped, and
     * leaves {@link #worldToPelvis} current for {@link #aim}.
     *
     * @return how far that moved the pelvis down (model +Y), for the ground legs to make up
     */
    private float anchorPelvis(LocomotionState.LegState[] legStates, float gate) {
        boolean anchored = false;
        for (LocomotionState.LegState leg : legStates) {
            anchored |= anchorWeight(leg, gate) > GATE_EPSILON;
        }
        if (!anchored) {
            return 0.0f;
        }
        this.updateWorldToPelvis();

        float missX = 0.0f;
        float missY = 0.0f;
        float missZ = 0.0f;
        float total = 0.0f;
        for (int i = 0; i < legStates.length; i++) {
            final LocomotionState.LegState leg = legStates[i];
            final float w = anchorWeight(leg, gate);
            if (w <= GATE_EPSILON) {
                continue;
            }
            final LegChain chain = this.legs.get(i);
            final float distance = this.hipToAnchor(chain.hip(), leg);
            if (distance < 1.0e-4f) {
                continue;
            }
            final float miss = distance - this.reachable(distance, chain.soleOffset());
            final float k = w * miss / distance;
            missX += this.target.x * k;
            missY += this.target.y * k;
            missZ += this.target.z * k;
            total += w;
        }
        // A half-weighted anchor moves the pelvis half as far.
        final float share = 1.0f / Math.max(total, 1.0f);

        // Pelvis frame to its parent's, where the pelvis position lives.
        this.rotation.rotationZYX(this.pelvis.zRot, this.pelvis.yRot, this.pelvis.xRot)
                .transform(missX * share, missY * share, missZ * share, this.shift);
        final float length = this.shift.length();
        if (length > this.maxAnchorShift) {
            this.shift.mul(this.maxAnchorShift / length);
        }
        if (!this.finite(this.shift.x + this.shift.y + this.shift.z, "anchor shift")) {
            return 0.0f;
        }
        this.pelvis.x += this.shift.x;
        this.pelvis.y += this.shift.y;
        this.pelvis.z += this.shift.z;
        this.updateWorldToPelvis();
        return this.shift.y;
    }

    /** Points the leg at its anchor and stretches it there, blended over the ground result by {@code w}. */
    private void aim(LegChain chain, LocomotionState.LegState leg, float w) {
        final ModelPart hip = chain.hip();
        final float distance = this.hipToAnchor(hip, leg);
        final float length = chain.soleOffset();
        if (distance < 1.0e-4f || length <= 0.0f) {
            leg.anchorShortfall = 0.0f;
            return;
        }
        // The sole hangs down the bone's +Y. Turn that axis from where the ground pose left it to the
        // anchor along the shortest arc, and only as far as the weight says. Blending the turn, not
        // Euler angles, keeps the paw on a straight path: two angles blended apart swing it
        // sideways, and solving for them directly breaks down (a large roll out of a tiny sideways
        // offset) when the leg points along the body, as it does reaching up a wall.
        this.aimRotation.rotationZYX(hip.zRot, hip.yRot, hip.xRot);
        this.aimAxis.set(0.0f, 1.0f, 0.0f);
        this.aimRotation.transform(this.aimAxis);
        this.aimTurn.rotationTo(this.aimAxis.x, this.aimAxis.y, this.aimAxis.z,
                this.target.x / distance, this.target.y / distance, this.target.z / distance);
        if (w < 1.0f) {
            this.aimTurn.set(this.aimShare.identity().nlerp(this.aimTurn, w));
        }
        this.aimRotation.rotateLocal(this.aimTurn).getEulerAnglesZYX(this.aimAxis);
        if (!this.finite(this.aimAxis.x + this.aimAxis.y + this.aimAxis.z, "leg aim")) {
            return;
        }
        final float reach = this.reachable(distance, length);
        leg.anchorShortfall = distance - reach;

        hip.xRot = this.aimAxis.x;
        hip.yRot = this.aimAxis.y;
        hip.zRot = this.aimAxis.z;
        hip.yScale = Mth.lerp(w, hip.yScale, reach / length);
    }

    /** Hip to anchor in the pelvis frame, left in {@link #target}; returns its length. */
    private float hipToAnchor(ModelPart hip, LocomotionState.LegState leg) {
        this.space.worldToFrame(this.worldToPelvis, leg.anchorX, leg.anchorY, leg.anchorZ, this.target);
        this.target.sub(hip.x, hip.y, hip.z);
        return this.target.length();
    }

    /** The nearest length to {@code distance} a leg of {@code length} can take. */
    private float reachable(float distance, float length) {
        return Mth.clamp(distance, length * (1.0f - this.anchorStretchLimit),
                length * (1.0f + this.anchorStretchLimit));
    }

    private void updateWorldToPelvis() {
        this.space.frameToWorld(this.pelvisChain, this.worldToPelvis).invert();
    }

    private static float anchorWeight(LocomotionState.LegState leg, float gate) {
        final float w = leg.anchorWeight;
        return w > 0.0f ? Math.min(w, 1.0f) * gate : 0.0f;
    }

    /** The debug pin holds the first leg where its sole was when the pin went on. */
    private static void updateDebugPin(LocomotionState.LegState leg) {
        final boolean pin = LocomotionDebugFlags.pin();
        if (pin == leg.debugPinned) {
            return;
        }
        leg.debugPinned = pin;
        if (pin) {
            leg.anchorX = leg.soleX;
            leg.anchorY = leg.soleY;
            leg.anchorZ = leg.soleZ;
            leg.anchorWeight = 1.0f;
        } else {
            leg.anchorWeight = 0.0f;
        }
    }

    @org.jetbrains.annotations.Nullable
    MantleDriver mantleDriver() {
        return this.mantle;
    }

    /** The space the next {@link #writePose} measures anchors in; tests begin it by hand. */
    ModelSpace space() {
        return this.space;
    }

    QuadrupedSolver solver() {
        return this.solver;
    }

    List<LegChain> legs() {
        return this.legs;
    }

    public static <T extends LivingEntity & ILocomotionSubject, M extends HierarchicalModel<T> & ILocomotionModel<T>>
    Builder<T> builder(M model) {
        return new Builder<>(model);
    }

    public static final class Builder<T extends LivingEntity & ILocomotionSubject> {
        private final HierarchicalModel<T> model;
        private final ILocomotionModel<T> locomotionModel;
        private String pelvisName = "root";
        private final List<String> legNames = new ArrayList<>();
        private float maxPitchDeg = 35.0f;
        private float maxRollDeg = 20.0f;
        private float stretchLimit = 0.15f;
        private float maxDropFraction = 0.5f;
        private float anchorStretchLimit = 0.25f;
        private float maxAnchorShiftFraction = 0.5f;
        private MantleProfile mantleProfile;
        private final SpineRules.Declarations spine = new SpineRules.Declarations();

        private <M extends HierarchicalModel<T> & ILocomotionModel<T>> Builder(M model) {
            this.model = model;
            this.locomotionModel = model;
        }

        /** Bone the legs hang from; {@code "root"} names the model's root part. */
        public Builder<T> pelvis(String boneName) {
            this.pelvisName = boneName;
            return this;
        }

        /** One single-bone leg per name. Each must be a direct child of the pelvis. */
        public Builder<T> legs(String... boneNames) {
            this.legNames.addAll(List.of(boneNames));
            return this;
        }

        public Builder<T> maxPitch(float degrees) {
            this.maxPitchDeg = degrees;
            return this;
        }

        public Builder<T> maxRoll(float degrees) {
            this.maxRollDeg = degrees;
            return this;
        }

        /** How far a leg may lengthen or shorten, as a fraction of its length. */
        public Builder<T> stretchLimit(float fraction) {
            this.stretchLimit = fraction;
            return this;
        }

        /** How far the pelvis may rise or sink, as a fraction of leg length. */
        public Builder<T> maxDrop(float fraction) {
            this.maxDropFraction = fraction;
            return this;
        }

        /** How far an anchored leg may lengthen or shorten, as a fraction of its length. */
        public Builder<T> anchorStretchLimit(float fraction) {
            this.anchorStretchLimit = fraction;
            return this;
        }

        /**
         * The bone takes {@code weight} of the spine curvature a mantle asks for, on its xRot. With
         * a rigid torso: head toward the top, tail as a counterweight.
         */
        public Builder<T> arch(String boneName, float weight) {
            this.spine.arch(boneName, weight);
            return this;
        }

        /**
         * As {@link #arch(String, float)}, with a different weight while the spine extends (the
         * body rearing up) than while it flexes (over the top).
         */
        public Builder<T> arch(String boneName, float flexWeight, float extendWeight) {
            this.spine.arch(boneName, flexWeight, extendWeight);
            return this;
        }

        /** Lets the rig pose the body climbing a ledge, with the species' numbers. */
        public Builder<T> mantle(MantleProfile profile) {
            this.mantleProfile = profile;
            return this;
        }

        /** How far anchored legs may pull the pelvis, as a fraction of leg length. */
        public Builder<T> maxAnchorShift(float fraction) {
            this.maxAnchorShiftFraction = fraction;
            return this;
        }

        /**
         * The bone gives back {@code fraction} of the terrain tilt it inherits, in the world: 0.6
         * keeps a head 60% nearer its animated orientation while the body climbs.
         */
        public Builder<T> stabilise(String boneName, float fraction) {
            this.spine.stabilise(boneName, fraction);
            return this;
        }

        /**
         * The bone trails the body's tilt and turning, settling with a little overshoot.
         *
         * @param halfLifeSeconds how quickly it catches up
         * @param damping         0.05 (whippy) to 0.95 (sluggish, no overshoot)
         * @param maxDegrees      the most it may trail by
         */
        public Builder<T> lag(String boneName, float halfLifeSeconds, float damping, float maxDegrees) {
            this.spine.lag(boneName, halfLifeSeconds, damping, maxDegrees);
            return this;
        }

        /**
         * Resolves the bones. Never throws: a model that does not match gets a rig that disables
         * itself on first use and says why once.
         */
        public LocomotionRig<T> build() {
            final Optional<ModelPart> pelvis = this.model.getAnyDescendantWithName(this.pelvisName);
            final List<ModelPart> pelvisChain = pelvis.map(p -> ModelParts.chain(this.model.root(), p)).orElse(null);
            if (pelvisChain == null) {
                return disabled("pelvis bone '" + this.pelvisName + "' not found");
            }
            if (this.legNames.isEmpty()) {
                return disabled("no legs declared");
            }

            final List<LegChain> legs = new ArrayList<>();
            for (String name : this.legNames) {
                final LegChain leg = LegChain.of(this.model, name);
                if (leg == null) {
                    return disabled("leg bone '" + name + "' not found or has no geometry");
                }
                final List<ModelPart> path = leg.fromRoot();
                if (path.size() < 2 || path.get(path.size() - 2) != pelvis.get()) {
                    return disabled("leg bone '" + name + "' does not hang directly from the pelvis");
                }
                legs.add(leg);
            }
            final SpineRules spineRules;
            try {
                spineRules = this.spine.bind(name -> this.model.getAnyDescendantWithName(name).orElse(null));
            } catch (IllegalArgumentException e) {
                return disabled(e.getMessage());
            }
            return new QuadrupedRig<>(this, pelvis.get(), pelvisChain, legs, spineRules);
        }

        private LocomotionRig<T> disabled(String reason) {
            final LocomotionRig<T> rig = new LocomotionRig<T>() {
                @Override
                protected void solve(T entity, LocomotionState state, float partialTick, float dt) {
                }
            };
            rig.disable(reason);
            return rig;
        }
    }
}
