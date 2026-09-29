package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.locomotion.mantle.MantleDriver;
import com.evirapo.diversityofcritters.client.models.CivetModel;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mantle pose on the real civet mesh, frame by frame through the same steps the rig takes:
 * measure the animated soles, drive the mantle, solve, write. An adult civet faces south against
 * a one-block wall: face at z = 1, lip at y = 1, body centre at (0.5, y, 0.625), started at y = 0.
 */
class QuadrupedMantleSceneTest {

    private static final float DT = 1.0f / 60.0f;
    private static final String[] LEGS = {"Leg4", "Leg3", "Leg", "Leg2"};
    private static final int[] FRONT = {0, 1};
    private static final int[] HIND = {2, 3};
    private static final double X = 0.5;
    private static final double Z = 0.625;
    private static final float HALF_WIDTH = 0.375f;
    private static final MantleProfile PROFILE = MantleProfile.CIVET;

    private final CivetModel<CivetEntity> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
    private final QuadrupedRig<CivetEntity> rig = bind(true);
    private final MantleState mantle = new MantleState().set(0, 0, 1, 1.0, Direction.SOUTH, 0.0, 0L);
    private final LocomotionState state = new LocomotionState();
    private final ModelSpace space = new ModelSpace();
    private final Vector3d world = new Vector3d();
    private float units;

    QuadrupedMantleSceneTest() {
        this.state.ensureLegs(4);
    }

    private QuadrupedRig<CivetEntity> bind(boolean withMantle) {
        final QuadrupedRig.Builder<CivetEntity> builder = QuadrupedRig.builder(this.model).pelvis("BandedPlamCivet").legs(LEGS);
        if (withMantle) {
            builder.mantle(PROFILE);
        }
        final LocomotionRig<CivetEntity> built = builder.build();
        assertFalse(built.isDisabled(), "rig failed to bind to the civet mesh");
        return (QuadrupedRig<CivetEntity>) built;
    }

    private Vector3d sole(QuadrupedRig<CivetEntity> rig, int leg, double y) {
        this.space.begin(X, y, Z, 0.0f, 1.0f, this.model);
        this.units = this.space.modelUnitsPerBlock();
        final LegChain chain = rig.legs().get(leg);
        return this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, this.world);
    }

    /** One frame at body height {@code y}, {@code ticks} after the start, with or without a mantle. */
    private void frame(QuadrupedRig<CivetEntity> rig, LocomotionState state, double y, double ticks, boolean mantling) {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        final QuadrupedSolver solver = rig.solver();
        for (int i = 0; i < 4; i++) {
            final LocomotionState.LegState leg = state.legs()[i];
            final Vector3d at = sole(rig, i, y);
            leg.soleX = at.x;
            leg.soleY = at.y;
            leg.soleZ = at.z;
            leg.groundY = 0.0;
            leg.plantWeight = 1.0f;
            final ModelPart hip = rig.legs().get(i).hip();
            solver.hipX[i] = hip.x;
            solver.hipZ[i] = hip.z;
            solver.soleUp[i] = (float) (at.y - y) * this.units;
            solver.groundUp[i] = (float) (0.0 - y) * this.units;
        }
        final MantleDriver driver = rig.mantleDriver();
        if (driver != null) {
            driver.update(state, mantling ? this.mantle : null, X, y, Z, ticks, HALF_WIDTH, this.units, DT);
        }
        solver.mantleAuthority = state.mantleAuthority.value();
        solver.mantlePitch = state.mantlePitch;
        solver.solve(state, 1.0f, DT);
        rig.space().begin(X, y, Z, 0.0f, 1.0f, this.model);
        rig.writePose(state, 1.0f);
        state.initialised = true;
    }

    /** Starts a mantle standing at the foot of the wall, then holds at {@code y} long enough to settle. */
    private void settle(double y, double ticks) {
        frame(this.rig, this.state, 0.0, 0.0, true);
        for (int i = 0; i < 180; i++) {
            frame(this.rig, this.state, y, ticks, true);
        }
    }

    private double distanceUnits(int leg, double x, double y, double z, double bodyY) {
        return sole(this.rig, leg, bodyY).distance(x, y, z) * this.units;
    }

    @Test
    void driverIsBuiltOnlyWhenAsked() {
        assertNotNull(this.rig.mantleDriver());
        assertEquals(null, bind(false).mantleDriver());
    }

    @Test
    void reachLiftsTheFrontAndKeepsTheHindLegsPlanted() {
        settle(0.0, 3.0);
        assertEquals(MantlePhase.REACH, this.state.mantleProgress.phase);
        assertTrue(this.state.pelvisPitch.value() < -0.2f, "nose up: " + this.state.pelvisPitch.value());
        for (int leg : HIND) {
            final LocomotionState.LegState s = this.state.legs()[leg];
            assertTrue(distanceUnits(leg, s.anchorX, s.anchorY, s.anchorZ, 0.0) < 0.5, LEGS[leg] + " slid off its anchor");
        }
    }

    @Test
    void riseRearsUpOnPlantedHindLegs() {
        final double y = 0.3;
        settle(y, 20.0);
        assertEquals(MantlePhase.RISE, this.state.mantleProgress.phase);
        assertTrue(this.state.pelvisPitch.value() < -0.9f, "reared up: " + this.state.pelvisPitch.value());

        for (int leg : HIND) {
            final LocomotionState.LegState s = this.state.legs()[leg];
            assertTrue(s.anchorY >= 0.0 && s.anchorY <= PROFILE.hindStepLift + 1.0e-9,
                    LEGS[leg] + " hind foot on or just over the ground: " + s.anchorY);
            final double miss = distanceUnits(leg, s.anchorX, s.anchorY, s.anchorZ, y);
            assertTrue(miss < 0.5, LEGS[leg] + " off its footing by " + miss + "u");
        }
        // Past half the rise the leading paw has reached the lip; the other is climbing to it.
        final LocomotionState.LegState leading = this.state.legs()[FRONT[0]];
        // Anchors pass through a float spring: equal to within its precision.
        assertEquals(1.0, leading.anchorY, 1.0e-6, "leading paw on the lip");
        assertEquals(1.0 + PROFILE.lipInset, leading.anchorZ, 1.0e-6, "just past the face");
        final LocomotionState.LegState trailing = this.state.legs()[FRONT[1]];
        assertTrue(trailing.anchorY < 1.0 && trailing.anchorY > 0.4, "trailing paw on its way: " + trailing.anchorY);
        for (int leg : FRONT) {
            final LocomotionState.LegState s = this.state.legs()[leg];
            // Whatever the paw cannot reach, it reports.
            final double miss = distanceUnits(leg, s.anchorX, s.anchorY, s.anchorZ, y);
            assertEquals(Math.max(s.anchorShortfall, 0.0f), miss, 0.3, LEGS[leg] + " shortfall vs actual miss");
        }
    }

    @Test
    void bothHindFeetStepTowardTheWallWhileRearing() {
        settle(0.0, PROFILE.reachTicks);
        final double[] start = new double[4];
        for (int leg : HIND) {
            start[leg] = this.state.legs()[leg].anchorZ;
        }
        // Just before the crest, each hind foot has taken its step toward the face (+Z).
        settle(PROFILE.crestStart - 0.01, 20.0);
        assertEquals(MantlePhase.RISE, this.state.mantleProgress.phase);
        for (int leg : HIND) {
            final double stepped = this.state.legs()[leg].anchorZ - start[leg];
            assertEquals(PROFILE.hindStepLength, stepped, 0.01, LEGS[leg] + " stepped by " + stepped);
            assertTrue(this.state.legs()[leg].anchorZ < 1.0, LEGS[leg] + " stays in front of the face");
        }
    }

    @Test
    void crestScramblesTheHindFeetOntoTheFace() {
        settle(0.9, 30.0);
        assertEquals(MantlePhase.CREST, this.state.mantleProgress.phase);
        // Late in the crest both hind feet are up the face, high up with the body, pushing on it.
        for (int leg : HIND) {
            final LocomotionState.LegState s = this.state.legs()[leg];
            assertTrue(s.anchorY > PROFILE.hindFaceGrip && s.anchorY < 1.0, LEGS[leg] + " up the face: " + s.anchorY);
            assertEquals(1.0 - com.evirapo.diversityofcritters.client.locomotion.mantle.GripPlanner.FACE_GAP,
                    s.anchorZ, 1.0e-6, LEGS[leg] + " against the face");
            assertEquals(1.0f, s.contactOverride, 1.0e-6f, LEGS[leg] + " planted on it");
        }
        assertTrue(this.state.mantleLock < 0.5f, "the body is going up with the entity");
        for (int leg : FRONT) {
            assertTrue(this.state.legs()[leg].anchorZ > 1.0 + PROFILE.lipInset, "front paws move onto the top");
        }
    }

    @Test
    void hindFeetPushAgainstTheWallAllTheWayUp() {
        // Through the crest, every hind foot that is not mid-swing reaches its hold on the face:
        // none is left hanging in the air below the rising body.
        frame(this.rig, this.state, 0.0, 0.0, true);
        for (double y = PROFILE.crestStart + 0.02; y < 1.0; y += 0.05) {
            for (int i = 0; i < 90; i++) {
                frame(this.rig, this.state, y, 30.0, true);
            }
            assertEquals(MantlePhase.CREST, this.state.mantleProgress.phase);
            for (int leg : HIND) {
                final LocomotionState.LegState s = this.state.legs()[leg];
                if (s.contactOverride < 0.9f || s.anchorY <= 1.0e-9) {
                    continue;
                }
                final double miss = distanceUnits(leg, s.anchorX, s.anchorY, s.anchorZ, y);
                assertTrue(miss < 0.5, LEGS[leg] + " hangs " + miss + "u off the wall at y " + String.format("%.2f", y));
            }
        }
    }

    @Test
    void endingTheMantleHandsEverythingBack() {
        settle(0.3, 20.0);
        for (int i = 0; i < 180; i++) {
            frame(this.rig, this.state, 0.0, 20.0, false);
        }
        assertFalse(this.state.mantleActive);
        assertTrue(this.state.mantleAuthority.value() < 0.01f);
        assertEquals(0.0f, this.state.mantleBodyDrop);
        for (LocomotionState.LegState leg : this.state.legs()) {
            assertEquals(0.0f, leg.anchorWeight);
            assertEquals(LocomotionState.LegState.NO_OVERRIDE, leg.contactOverride);
        }
        assertEquals(0.0f, this.state.pelvisPitch.value(), 0.02f);
    }

    @Test
    void withoutAMantleThePoseMatchesARigWithoutTheDriver() {
        final QuadrupedRig<CivetEntity> plain = bind(false);
        final LocomotionState plainState = new LocomotionState();
        plainState.ensureLegs(4);
        for (int i = 0; i < 10; i++) {
            frame(plain, plainState, 0.2, 0.0, false);
        }
        final float[] expected = pose();
        for (int i = 0; i < 10; i++) {
            frame(this.rig, this.state, 0.2, 0.0, false);
        }
        final float[] actual = pose();
        for (int i = 0; i < expected.length; i++) {
            assertEquals(Float.floatToIntBits(expected[i]), Float.floatToIntBits(actual[i]), "value " + i);
        }
    }

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
