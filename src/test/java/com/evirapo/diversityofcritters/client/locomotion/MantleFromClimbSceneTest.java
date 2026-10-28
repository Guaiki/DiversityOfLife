package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.animations.CivetAnims;
import com.evirapo.diversityofcritters.client.animations.SquirrelAnims;
import com.evirapo.diversityofcritters.client.models.CivetModel;
import com.evirapo.diversityofcritters.client.models.SquirrelModel;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import com.evirapo.diversityofcritters.common.locomotion.MantleMotion;
import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleProgress;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mantle cresting a climb, on the real civet and squirrel meshes: the climb pose's last frame,
 * then the mantle frame by frame through the steps the rig takes, with the entity moved as the
 * server moves it. An adult faces south up a trunk: face at z = 1, top at y = 3, the climb handing
 * over 0.95 below it.
 */
class MantleFromClimbSceneTest {

    private static final float DT = 1.0f / 60.0f;
    private static final int FRAMES_PER_TICK = 3;
    private static final String[] LEGS = {"Leg4", "Leg3", "Leg", "Leg2"};
    private static final int[] FRONT = {0, 1};
    private static final double X = 0.5;
    private static final double LIP_Y = 3.0;
    private static final double HAND_OVER = LIP_Y - 0.95;
    /** How far a hip may move between the climb's last frame and the mantle's first, blocks (1.6 px). */
    private static final double HIP_JUMP = 0.1;

    @Test
    void civetTakesOverFromTheClimbPose() {
        handOver(civet());
    }

    @Test
    void squirrelTakesOverFromTheClimbPose() {
        handOver(squirrel());
    }

    @Test
    void civetCrestsLevelOntoTheTop() {
        crestsOntoTheTop(civet());
    }

    @Test
    void squirrelCrestsLevelOntoTheTop() {
        crestsOntoTheTop(squirrel());
    }

    private static Scene civet() {
        final CivetModel<CivetEntity> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
        final LocomotionRig<CivetEntity> rig = QuadrupedRig.builder(model).pelvis("BandedPlamCivet").legs(LEGS)
                .mantle(MantleProfile.CIVET).build();
        return new Scene(model, model, (QuadrupedRig<?>) rig, CivetAnims.CLIMBING_UP, MantleProfile.CIVET, 0.375f);
    }

    private static Scene squirrel() {
        final SquirrelModel<SquirrelEntity> model = new SquirrelModel<>(SquirrelModel.createBodyLayer().bakeRoot());
        final LocomotionRig<SquirrelEntity> rig = QuadrupedRig.builder(model).pelvis("root").legs(LEGS)
                .mantle(MantleProfile.SQUIRREL).build();
        return new Scene(model, model, (QuadrupedRig<?>) rig, SquirrelAnims.CLIMBING_UP, MantleProfile.SQUIRREL, 0.35f);
    }

    private static void handOver(Scene scene) {
        final double[][] climbHips = new double[4][];
        for (int i = 0; i < 4; i++) {
            climbHips[i] = scene.climbHip(i);
        }
        scene.frame(0);
        assertEquals(-Math.PI / 2.0, scene.state.pelvisPitch.value(), 1.0e-3, "upright from the first frame");
        assertEquals(1.0f, scene.state.mantleAuthority.value(), 1.0e-6f, "the mantle holds the pose at once");
        for (int i = 0; i < 4; i++) {
            final Vector3d hip = scene.hip(i);
            final double moved = hip.distance(climbHips[i][0], climbHips[i][1], climbHips[i][2]);
            assertTrue(moved < HIP_JUMP, String.format("%s hip jumps %.3f blocks at the hand-over", LEGS[i], moved));
        }
        for (int leg : FRONT) {
            final LocomotionState.LegState s = scene.state.legs()[leg];
            assertEquals(LIP_Y, s.anchorY, 1.0e-6, LEGS[leg] + " starts on the lip");
            assertEquals(1.0 + scene.profile.lipInset, s.anchorZ, 1.0e-6, LEGS[leg] + " just past the face");
        }
    }

    private static void crestsOntoTheTop(Scene scene) {
        final List<MantlePhase> phases = new ArrayList<>();
        for (int frame = 0; frame < 40 * FRAMES_PER_TICK; frame++) {
            scene.frame(frame);
            if (phases.isEmpty() || phases.get(phases.size() - 1) != scene.progress.phase) {
                phases.add(scene.progress.phase);
            }
            for (int i = 0; i < 4; i++) {
                final Vector3d hip = scene.hip(i);
                // Below the top, every hip stays in front of the face: the body never goes into the trunk.
                if (hip.y < LIP_Y) {
                    assertTrue(hip.z < 1.0, String.format("%s hip inside the trunk at frame %d: z %.3f, y %.3f",
                            LEGS[i], frame, hip.z, hip.y));
                }
            }
        }
        assertEquals(List.of(MantlePhase.RISE, MantlePhase.CREST, MantlePhase.RECOVER), phases, "no reach from a climb");
        assertEquals(1.0f, scene.progress.t, 1.0e-6f, "walked onto the top");
        assertEquals(0.0, scene.state.pelvisPitch.value(), Math.toRadians(2.0), "level on the top");
        for (int i = 0; i < 4; i++) {
            final Vector3d sole = scene.sole(i);
            assertEquals(LIP_Y, sole.y, 0.05, LEGS[i] + " stands on the top");
            assertTrue(sole.z > 1.0, LEGS[i] + " over the top, z " + sole.z);
        }
    }

    /** One critter cresting the trunk, the server's motion included. */
    private static final class Scene {
        final HierarchicalModel<?> model;
        final QuadrupedRig<?> rig;
        final AnimationDefinition climb;
        final MantleProfile profile;
        final float halfWidth;
        final MantleState mantle;
        final LocomotionState state = new LocomotionState();
        final ModelSpace space = new ModelSpace();
        final MantleMotion motion = new MantleMotion();
        MantleProgress progress = new MantleProgress();
        double y = HAND_OVER;
        double z;

        final ILocomotionModel<?> locomotionModel;

        Scene(HierarchicalModel<?> model, ILocomotionModel<?> locomotionModel, QuadrupedRig<?> rig,
              AnimationDefinition climb, MantleProfile profile, float halfWidth) {
            this.model = model;
            this.locomotionModel = locomotionModel;
            this.rig = rig;
            this.climb = climb;
            this.profile = profile;
            this.halfWidth = halfWidth;
            this.z = 1.0 - halfWidth;
            // As MantleController starts it: the hind feet where the climb pose has them.
            this.mantle = new MantleState().set(0, 2, 1, LIP_Y, Direction.SOUTH, HAND_OVER + profile.climbLift, 0L);
            this.mantle.fromClimb = true;
            this.state.ensureLegs(4);
        }

        private ILocomotionModel<?> locomotionModel() {
            return this.locomotionModel;
        }

        /** Where the climb render draws a hip, at the hand-over. */
        double[] climbHip(int leg) {
            this.model.root().getAllParts().forEach(ModelPart::resetPose);
            KeyframeAnimations.animate(this.model, this.climb, 0L, 1.0f, new Vector3f());
            final PoseStack ps = new PoseStack();
            ps.translate(X, HAND_OVER, this.z);
            ps.mulPose(Axis.YP.rotationDegrees(180.0f));
            ps.scale(-1, -1, 1);
            ps.translate(0, ModelSpace.RENDER_OFFSET_Y, 0);
            this.locomotionModel().applyRootTransform(ps);
            // CivetModel and SquirrelModel, climbing up.
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.translate(0, -0.4f, 0.25f);
            for (ModelPart part : this.rig.legs().get(leg).fromRoot()) {
                part.translateAndRotate(ps);
            }
            final Vector3f v = new Vector3f();
            ps.last().pose().transformPosition(v);
            return new double[]{v.x, v.y, v.z};
        }

        /** One render frame: the server's tick when one is due, then the rig. */
        void frame(int frame) {
            final double ticks = frame / (double) FRAMES_PER_TICK;
            this.progress = MantleProgress.of(this.y, this.mantle.startY, LIP_Y, ticks, this.z - 1.0, this.halfWidth,
                    this.profile, true, this.progress);
            if (frame > 0 && frame % FRAMES_PER_TICK == 0) {
                MantleMotion.plan(this.progress, this.y, this.mantle.startY, LIP_Y, this.z - 1.0, this.halfWidth,
                        false, true, this.profile, this.motion);
                if (this.motion.holdsVertical) {
                    this.y = Math.min(LIP_Y, this.y + this.motion.up);
                }
                this.z += this.motion.along;
            }

            this.model.root().getAllParts().forEach(ModelPart::resetPose);
            this.space.begin(X, this.y, this.z, 0.0f, 1.0f, this.locomotionModel());
            final float units = this.space.modelUnitsPerBlock();
            final QuadrupedSolver solver = this.rig.solver();
            final Vector3d w = new Vector3d();
            for (int i = 0; i < 4; i++) {
                final LegChain chain = this.rig.legs().get(i);
                final LocomotionState.LegState leg = this.state.legs()[i];
                this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, w);
                leg.soleX = w.x;
                leg.soleY = w.y;
                leg.soleZ = w.z;
                // The top of the trunk, or the ground two blocks below the hand-over.
                leg.groundY = w.z > 1.0 ? LIP_Y : HAND_OVER - 2.0;
                final ModelPart hip = chain.hip();
                solver.hipX[i] = hip.x;
                solver.hipZ[i] = hip.z;
                solver.soleUp[i] = (float) (w.y - this.y) * units;
                solver.groundUp[i] = (float) (leg.groundY - this.y) * units;
            }
            if (frame == 0) {
                this.rig.takeOverFromClimb(this.state);
            }
            this.rig.mantleDriver().update(this.state, this.mantle, X, this.y, this.z, ticks, this.halfWidth, units, DT);
            solver.mantleAuthority = this.state.mantleAuthority.value();
            solver.mantlePitch = this.state.mantlePitch;
            solver.solve(this.state, 1.0f, DT);
            solver.snap = false;
            this.rig.space().begin(X, this.y, this.z, 0.0f, 1.0f, this.locomotionModel());
            this.rig.writePose(this.state, 1.0f);
            this.state.initialised = true;
        }

        Vector3d hip(int leg) {
            return this.point(leg, 0.0f);
        }

        Vector3d sole(int leg) {
            return this.point(leg, this.rig.legs().get(leg).soleOffset());
        }

        private Vector3d point(int leg, float down) {
            this.space.begin(X, this.y, this.z, 0.0f, 1.0f, this.locomotionModel());
            return this.space.toWorld(this.rig.legs().get(leg).fromRoot(), 0.0f, down, 0.0f, new Vector3d());
        }
    }
}
