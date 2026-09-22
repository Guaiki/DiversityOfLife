package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.common.locomotion.ILocomotionSubject;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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
    private final String[] legNames;

    private final ModelSpace space = new ModelSpace();
    private final BlockSurfaceQuery surface = new BlockSurfaceQuery();
    private final Matrix4f scratch = new Matrix4f();
    private final Matrix3f rotation = new Matrix3f();
    private final Vector3d world = new Vector3d();
    private final Vector3f shift = new Vector3f();
    private final float[] shiftX;
    private final float[] shiftY;
    private final float[] shiftZ;

    private QuadrupedRig(Builder<T> builder, ModelPart pelvis, List<ModelPart> pelvisChain, List<LegChain> legs) {
        this.locomotionModel = builder.locomotionModel;
        this.pelvis = pelvis;
        this.pelvisChain = pelvisChain;
        this.legs = legs;
        this.legNames = builder.legNames.toArray(new String[0]);
        this.solver = new QuadrupedSolver(legs.size(),
                (float) Math.toRadians(builder.maxPitchDeg), (float) Math.toRadians(builder.maxRollDeg),
                builder.stretchLimit, builder.maxDropFraction, legs.get(0).soleOffset());
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

        solver.solve(state, gate, dt);
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
        if (!this.finite(pitch, "pelvis pitch") || !this.finite(roll, "pelvis roll")
                || !this.finite(drop, "pelvis drop")) {
            return;
        }

        // --- Pelvis: tilt, turning about the supporting hips, then height.
        float totalWeight = 0.0f;
        float meanX = 0.0f;
        float meanY = 0.0f;
        float meanZ = 0.0f;
        for (int i = 0; i < count; i++) {
            final ModelPart hip = this.legs.get(i).hip();
            QuadrupedSolver.hipShift(this.rotation, this.pelvis.xRot, this.pelvis.yRot, this.pelvis.zRot,
                    pitch, roll, hip.x, hip.y, hip.z, this.shift);
            this.shiftX[i] = this.shift.x;
            this.shiftY[i] = this.shift.y;
            this.shiftZ[i] = this.shift.z;
            // A trace of weight on every hip keeps the pivot defined in the air.
            final float w = solver.weight[i] + 1.0e-3f;
            meanX += this.shiftX[i] * w;
            meanY += this.shiftY[i] * w;
            meanZ += this.shiftZ[i] * w;
            totalWeight += w;
        }
        meanX /= totalWeight;
        meanY /= totalWeight;
        meanZ /= totalWeight;

        this.pelvis.xRot += pitch;
        this.pelvis.zRot += roll;
        this.pelvis.x -= meanX;
        this.pelvis.y += drop - meanY;
        this.pelvis.z -= meanZ;

        // --- Legs: stay upright, then take up what is left.
        for (int i = 0; i < count; i++) {
            final LegChain chain = this.legs.get(i);
            final LocomotionState.LegState leg = legStates[i];
            final ModelPart hip = chain.hip();

            hip.xRot -= pitch;
            hip.zRot -= roll;

            // How far the hip ended up moving down (model +Y), which the sole has to make up.
            final float hipDown = this.shiftY[i] - meanY + drop;
            final float residual = leg.footLift.value() + hipDown;
            final float applied = QuadrupedSolver.appliedResidual(
                    residual, Math.max(leg.plantWeight, leg.contactWeight), gate);
            final float stretch = solver.stretchFor(applied);
            if (this.finite(stretch, "leg stretch")) {
                hip.yScale += stretch;
            }
            leg.debugResidual = applied;
            leg.debugStretch = stretch;
        }
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
            return new QuadrupedRig<>(this, pelvis.get(), pelvisChain, legs);
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
