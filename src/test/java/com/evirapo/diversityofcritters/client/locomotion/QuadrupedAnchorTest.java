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
 * World-anchored legs on the real civet mesh, standing on flat ground at the origin, yaw 0: give
 * the front-left leg a target in the world, solve, write the pose, then measure where its sole
 * actually ended up.
 */
class QuadrupedAnchorTest {

    private static final float DT = 1.0f / 60.0f;
    private static final String[] LEGS = {"Leg4", "Leg3", "Leg", "Leg2"};
    private static final int FRONT_LEFT = 0;

    private final CivetModel<CivetEntity> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
    private final QuadrupedRig<CivetEntity> rig;
    private final ModelSpace space = new ModelSpace();
    private final Vector3d world = new Vector3d();
    private float unitsPerBlock;

    QuadrupedAnchorTest() {
        final LocomotionRig<CivetEntity> built = QuadrupedRig.builder(this.model).pelvis("root").legs(LEGS).build();
        assertFalse(built.isDisabled(), "rig failed to bind to the civet mesh");
        this.rig = (QuadrupedRig<CivetEntity>) built;
    }

    /** A sole's world position, in blocks. */
    private Vector3d sole(int leg) {
        this.space.begin(0.0, 0.0, 0.0, 0.0f, 1.0f, this.model);
        this.unitsPerBlock = this.space.modelUnitsPerBlock();
        final LegChain chain = this.rig.legs().get(leg);
        return this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, new Vector3d());
    }

    private LocomotionState restingState() {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        final LocomotionState state = new LocomotionState();
        state.ensureLegs(4);
        final QuadrupedSolver solver = this.rig.solver();
        for (int i = 0; i < 4; i++) {
            final ModelPart hip = this.rig.legs().get(i).hip();
            final Vector3d at = sole(i);
            state.legs()[i].plantWeight = 1.0f;
            solver.hipX[i] = hip.x;
            solver.hipZ[i] = hip.z;
            solver.soleUp[i] = (float) at.y * this.unitsPerBlock;
            solver.groundUp[i] = solver.soleUp[i];
        }
        return state;
    }

    /** Solves on flat ground with the given anchor on the front-left leg and writes the pose. */
    private LocomotionState scene(double x, double y, double z, float weight) {
        final LocomotionState state = restingState();
        final LocomotionState.LegState leg = state.legs()[FRONT_LEFT];
        leg.anchorX = x;
        leg.anchorY = y;
        leg.anchorZ = z;
        leg.anchorWeight = weight;
        this.rig.solver().solve(state, 1.0f, DT);
        this.rig.space().begin(0.0, 0.0, 0.0, 0.0f, 1.0f, this.model);
        this.rig.writePose(state, 1.0f);
        return state;
    }

    /** Distance between a world point and the front-left sole, in model units. */
    private float missUnits(double x, double y, double z) {
        final Vector3d at = sole(FRONT_LEFT);
        return (float) (at.distance(x, y, z) * this.unitsPerBlock);
    }

    /** The anchor this many model units off the resting sole (x, up, z). */
    private double[] offsetFromRest(float dx, float up, float dz) {
        restingState();
        final Vector3d rest = sole(FRONT_LEFT);
        return new double[]{rest.x + dx / this.unitsPerBlock, rest.y + up / this.unitsPerBlock,
                rest.z + dz / this.unitsPerBlock};
    }

    @Test
    void anchorAtTheRestingSoleChangesNothingThatMatters() {
        final double[] t = offsetFromRest(0.0f, 0.0f, 0.0f);
        final LocomotionState state = scene(t[0], t[1], t[2], 1.0f);
        assertTrue(missUnits(t[0], t[1], t[2]) < 0.05f);
        assertEquals(0.0f, state.legs()[FRONT_LEFT].anchorShortfall, 1.0e-3f);
    }

    @Test
    void anchorWithinReachIsReached() {
        // Forward, outward and up: the leg swings and shortens.
        final double[] t = offsetFromRest(-1.5f, 2.0f, 3.0f);
        final LocomotionState state = scene(t[0], t[1], t[2], 1.0f);
        assertTrue(missUnits(t[0], t[1], t[2]) < 0.25f, "missed by " + missUnits(t[0], t[1], t[2]));
        assertEquals(0.0f, state.legs()[FRONT_LEFT].anchorShortfall, 1.0e-3f);
    }

    @Test
    void anchorTooCloseToFoldIntoPushesThePelvisUp() {
        // 5 units under the hip is shorter than the leg can fold (8.25); the body rises instead.
        final double[] t = offsetFromRest(0.0f, 6.0f, 0.0f);
        final float pelvisYBefore = this.model.root().y;
        final LocomotionState state = scene(t[0], t[1], t[2], 1.0f);
        assertTrue(missUnits(t[0], t[1], t[2]) < 0.25f, "missed by " + missUnits(t[0], t[1], t[2]));
        assertTrue(this.model.root().y < pelvisYBefore - 1.0f, "the pelvis rose (model -Y)");
        assertEquals(0.0f, state.legs()[FRONT_LEFT].anchorShortfall, 1.0e-3f);
    }

    @Test
    void anchorALittleOutOfReachPullsThePelvis() {
        // 14 units forward and down is past the stretched leg (13.75); the pelvis leans toward it.
        final double[] t = offsetFromRest(0.0f, -1.0f, 8.0f);
        final float pelvisZBefore = this.model.root().z;
        final LocomotionState state = scene(t[0], t[1], t[2], 1.0f);
        assertTrue(missUnits(t[0], t[1], t[2]) < 0.25f, "missed by " + missUnits(t[0], t[1], t[2]));
        assertTrue(this.model.root().z < pelvisZBefore - 0.2f, "the pelvis moved forward (model -Z)");
        assertEquals(0.0f, state.legs()[FRONT_LEFT].anchorShortfall, 1.0e-3f);
    }

    @Test
    void anchorFarOutOfReachReportsWhatIsMissing() {
        final double[] t = offsetFromRest(0.0f, -2.0f, 20.0f);
        final LocomotionState state = scene(t[0], t[1], t[2], 1.0f);
        final float shortfall = state.legs()[FRONT_LEFT].anchorShortfall;
        assertTrue(shortfall > 1.0f, "shortfall " + shortfall);
        // The leg points straight at the anchor, so what is left is exactly the shortfall.
        assertEquals(shortfall, missUnits(t[0], t[1], t[2]), 0.25f);
    }

    @Test
    void zeroWeightLeavesThePoseBitForBit() {
        final LocomotionState plain = scene(0.0, 0.0, 0.0, 0.0f);
        final float[] expected = pose();
        final LocomotionState anchored = scene(5.0, 5.0, 5.0, 0.0f);
        final float[] actual = pose();
        for (int i = 0; i < expected.length; i++) {
            assertEquals(Float.floatToIntBits(expected[i]), Float.floatToIntBits(actual[i]), "value " + i);
        }
        assertEquals(0.0f, plain.legs()[FRONT_LEFT].anchorShortfall);
        assertEquals(0.0f, anchored.legs()[FRONT_LEFT].anchorShortfall);
    }

    @Test
    void halfWeightLandsBetweenGroundAndAnchor() {
        final double[] t = offsetFromRest(0.0f, 2.0f, 4.0f);
        scene(t[0], t[1], t[2], 1.0f);
        final float full = missUnits(t[0], t[1], t[2]);
        scene(t[0], t[1], t[2], 0.5f);
        final float half = missUnits(t[0], t[1], t[2]);
        scene(t[0], t[1], t[2], 0.0f);
        final float none = missUnits(t[0], t[1], t[2]);
        assertTrue(full < half && half < none, full + " < " + half + " < " + none);
    }

    /** Every bone's pose values, in a fixed order. */
    private float[] pose() {
        final List<ModelPart> parts = this.model.root().getAllParts().toList();
        final float[] out = new float[parts.size() * 9];
        for (int i = 0; i < parts.size(); i++) {
            final ModelPart p = parts.get(i);
            final float[] v = {p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale};
            System.arraycopy(v, 0, out, i * 9, 9);
        }
        return out;
    }
}
