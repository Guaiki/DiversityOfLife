package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.models.CivetModel;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end on the real civet mesh: ask for ground at given heights under each foot, solve,
 * write the pose, then measure where the soles actually ended up in the world. This is where a
 * wrong sign anywhere (tilt, hip compensation, stretch) shows up as a foot in the air or in the
 * ground.
 */
class QuadrupedRigSceneTest {

    private static final float DT = 1.0f / 60.0f;
    /** Front-left, front-right, rear-left, rear-right. Model -Z is the head end. */
    private static final String[] LEGS = {"Leg4", "Leg3", "Leg", "Leg2"};

    private final CivetModel<CivetEntity> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
    private final QuadrupedRig<CivetEntity> rig;
    private final ModelSpace space = new ModelSpace();
    private final Vector3d world = new Vector3d();
    private final float[][] before = new float[4][3];
    private final float[][] after = new float[4][3];
    private final float[] ground = new float[4];

    QuadrupedRigSceneTest() {
        final LocomotionRig<CivetEntity> built = QuadrupedRig.builder(this.model).pelvis("BandedPlamCivet").legs(LEGS).build();
        assertFalse(built.isDisabled(), "rig failed to bind to the civet mesh");
        this.rig = (QuadrupedRig<CivetEntity>) built;
    }

    /** Sole positions in model units relative to the entity, x/up/z. */
    private void measure(float[][] out) {
        this.space.begin(0.0, 0.0, 0.0, 0.0f, 1.0f, this.model);
        final float units = this.space.modelUnitsPerBlock();
        final List<LegChain> legs = this.rig.legs();
        for (int i = 0; i < 4; i++) {
            final LegChain chain = legs.get(i);
            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, this.world);
            out[i][0] = (float) this.world.x * units;
            out[i][1] = (float) this.world.y * units;
            out[i][2] = (float) this.world.z * units;
        }
    }

    /** Ground {@code lifts} units above each animated sole; solve and write once. */
    private void scene(float... lifts) {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        measure(this.before);

        final LocomotionState state = new LocomotionState();
        state.ensureLegs(4);
        final QuadrupedSolver solver = this.rig.solver();
        for (int i = 0; i < 4; i++) {
            final ModelPart hip = this.rig.legs().get(i).hip();
            state.legs()[i].plantWeight = 1.0f;
            solver.hipX[i] = hip.x;
            solver.hipZ[i] = hip.z;
            solver.soleUp[i] = this.before[i][1];
            this.ground[i] = this.before[i][1] + lifts[i];
            solver.groundUp[i] = this.ground[i];
        }
        solver.solve(state, 1.0f, DT);
        this.rig.writePose(state, 1.0f);
        measure(this.after);
    }

    private void assertFeetOnGround(float tolerance) {
        for (int i = 0; i < 4; i++) {
            assertEquals(this.ground[i], this.after[i][1], tolerance, LEGS[i] + " sole height");
        }
    }

    private void assertFeetDidNotSlide(float tolerance) {
        for (int i = 0; i < 4; i++) {
            final float dx = this.after[i][0] - this.before[i][0];
            final float dz = this.after[i][2] - this.before[i][2];
            assertTrue(Math.sqrt(dx * dx + dz * dz) < tolerance, LEGS[i] + " slid by " + Math.sqrt(dx * dx + dz * dz));
        }
    }

    @Test
    void legsAreFoundInTheExpectedPlaces() {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        measure(this.before);
        // At rest every sole is on the ground, fronts ahead (+Z in the world at yaw 0).
        for (int i = 0; i < 4; i++) {
            assertEquals(0.0f, this.before[i][1], 0.1f, LEGS[i]);
        }
        assertTrue(this.before[0][2] > 0 && this.before[1][2] > 0, "front legs ahead");
        assertTrue(this.before[2][2] < 0 && this.before[3][2] < 0, "rear legs behind");
        assertEquals(11.0f, this.rig.legs().get(0).soleOffset(), 1.0e-4f);
    }

    @Test
    void flatGroundLeavesThePoseAlone() {
        scene(0.0f, 0.0f, 0.0f, 0.0f);
        assertFeetOnGround(1.0e-3f);
        assertFeetDidNotSlide(1.0e-3f);
    }

    @Test
    void frontOnHigherGround() {
        // 4 units is an eighth of a block for the adult civet: a path block edge, a carpet.
        scene(4.0f, 4.0f, 0.0f, 0.0f);
        assertFeetOnGround(0.15f);
        assertFeetDidNotSlide(0.5f);
    }

    @Test
    void rearOnHigherGround() {
        scene(0.0f, 0.0f, 3.0f, 3.0f);
        assertFeetOnGround(0.15f);
        assertFeetDidNotSlide(0.5f);
    }

    @Test
    void sideSlope() {
        scene(0.0f, 1.5f, 0.0f, 1.5f);
        assertFeetOnGround(0.15f);
        assertFeetDidNotSlide(0.5f);
    }

    @Test
    void wholeBodyOnASlab() {
        scene(5.0f, 5.0f, 5.0f, 5.0f);
        assertFeetOnGround(1.0e-3f);
        assertFeetDidNotSlide(1.0e-3f);
    }

    @Test
    void oneFootOnAPebbleIsTakenUpByTheLeg() {
        scene(1.2f, 0.0f, 0.0f, 0.0f);
        assertFeetOnGround(0.15f);
    }
}
