package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.models.SquirrelModel;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quadruped rig on the real squirrel mesh: a different root height, leg length and hip spacing
 * from the civet, and legs with rigid feet and toes hanging off them. Terrain first, then a mantle.
 */
class SquirrelRigSceneTest {

    private static final float DT = 1.0f / 60.0f;
    private static final String[] LEGS = {"Leg4", "Leg3", "Leg", "Leg2"};
    private static final int[] HIND = {2, 3};

    private final SquirrelModel<SquirrelEntity> model = new SquirrelModel<>(SquirrelModel.createBodyLayer().bakeRoot());
    private final QuadrupedRig<SquirrelEntity> rig;
    private final ModelSpace space = new ModelSpace();
    private final float[][] before = new float[4][3];
    private final float[][] after = new float[4][3];
    private final float[] ground = new float[4];

    SquirrelRigSceneTest() {
        final LocomotionRig<SquirrelEntity> built = QuadrupedRig.builder(this.model).pelvis("root").legs(LEGS)
                .mantle(MantleProfile.SQUIRREL).build();
        assertFalse(built.isDisabled(), "rig failed to bind to the squirrel mesh");
        this.rig = (QuadrupedRig<SquirrelEntity>) built;
    }

    /** Sole positions in model units relative to the entity at (0, y, 0), x/up/z. */
    private void measure(float[][] out, double y) {
        this.space.begin(0.0, y, 0.0, 0.0f, 1.0f, this.model);
        final float units = this.space.modelUnitsPerBlock();
        final Vector3d world = new Vector3d();
        for (int i = 0; i < 4; i++) {
            final LegChain chain = this.rig.legs().get(i);
            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, world);
            out[i][0] = (float) world.x * units;
            out[i][1] = (float) (world.y - y) * units;
            out[i][2] = (float) world.z * units;
        }
    }

    private void terrain(float... lifts) {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        measure(this.before, 0.0);
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
        this.rig.space().begin(0.0, 0.0, 0.0, 0.0f, 1.0f, this.model);
        this.rig.writePose(state, 1.0f);
        measure(this.after, 0.0);
    }

    @Test
    void soleIsAtTheBottomOfTheFoot() {
        // Leg bone 12 units long; the foot and toes are children that end at the same depth.
        assertEquals(12.0167f, this.rig.legs().get(1).soleOffset(), 1.0e-3f);
        assertEquals(12.0f, this.rig.legs().get(2).soleOffset(), 1.0e-3f);
    }

    @Test
    void restingSolesStandOnTheGround() {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        measure(this.before, 0.0);
        for (int i = 0; i < 4; i++) {
            assertEquals(0.0f, this.before[i][1], 0.15f, LEGS[i]);
        }
        assertTrue(this.before[0][2] > this.before[2][2], "front legs ahead of the hind ones");
    }

    @Test
    void frontOnHigherGround() {
        terrain(3.0f, 3.0f, 0.0f, 0.0f);
        for (int i = 0; i < 4; i++) {
            assertEquals(this.ground[i], this.after[i][1], 0.15f, LEGS[i] + " sole height");
            final float dx = this.after[i][0] - this.before[i][0];
            final float dz = this.after[i][2] - this.before[i][2];
            assertTrue(Math.sqrt(dx * dx + dz * dz) < 0.5, LEGS[i] + " slid");
        }
    }

    @Test
    void risingOnAMantleKeepsTheHindFeetPlanted() {
        // Facing south at a block: face at z = 1, lip at 1, centre 0.35 in front of it.
        final double x = 0.5;
        final double z = 0.65;
        final MantleState mantle = new MantleState().set(0, 0, 1, 1.0, Direction.SOUTH, 0.0, 0L);
        final LocomotionState state = new LocomotionState();
        state.ensureLegs(4);
        final QuadrupedSolver solver = this.rig.solver();
        final Vector3d world = new Vector3d();
        for (int frame = 0; frame < 180; frame++) {
            final double y = frame == 0 ? 0.0 : 0.25;
            this.model.root().getAllParts().forEach(ModelPart::resetPose);
            this.space.begin(x, y, z, 0.0f, 1.0f, this.model);
            final float units = this.space.modelUnitsPerBlock();
            for (int i = 0; i < 4; i++) {
                final LegChain chain = this.rig.legs().get(i);
                this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, world);
                final LocomotionState.LegState leg = state.legs()[i];
                leg.soleX = world.x;
                leg.soleY = world.y;
                leg.soleZ = world.z;
                leg.groundY = 0.0;
                leg.plantWeight = 1.0f;
                solver.hipX[i] = chain.hip().x;
                solver.hipZ[i] = chain.hip().z;
                solver.soleUp[i] = (float) (world.y - y) * units;
                solver.groundUp[i] = (float) -y * units;
            }
            this.rig.mantleDriver().update(state, mantle, x, y, z, frame == 0 ? 0.0 : 20.0, 0.35f, units, DT);
            solver.mantleAuthority = state.mantleAuthority.value();
            solver.mantlePitch = state.mantlePitch;
            solver.solve(state, 1.0f, DT);
            this.rig.space().begin(x, y, z, 0.0f, 1.0f, this.model);
            this.rig.writePose(state, 1.0f);
            state.initialised = true;
        }
        for (int leg : HIND) {
            final LocomotionState.LegState s = state.legs()[leg];
            this.space.begin(x, 0.25, z, 0.0f, 1.0f, this.model);
            final LegChain chain = this.rig.legs().get(leg);
            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, world);
            final double miss = world.distance(s.anchorX, s.anchorY, s.anchorZ) * this.space.modelUnitsPerBlock();
            assertTrue(miss < 0.5, LEGS[leg] + " off its footing by " + miss + "u");
        }
        assertTrue(state.pelvisPitch.value() < -0.8f, "reared up: " + state.pelvisPitch.value());
    }
}
