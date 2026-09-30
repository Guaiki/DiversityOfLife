package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.models.CivetModel;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The front legs must not pass through the civet's own head on their way up the wall. A rigid leg
 * reaching high while the body has barely reared points along the body, and its shaft crosses the
 * underside of the head; rearing further before climbing, with the head looking up, keeps it clear.
 *
 * <p>Runs a whole mantle on the real mesh with the civet's rig settings and samples each front
 * leg's centreline against the head's box.
 */
class MantleClearanceTest {

    private static final float DT = 1.0f / 60.0f;
    private static final String[] LEGS = {"Leg4", "Leg3", "Leg", "Leg2"};
    /** The head's box in its own frame (from the civet's layer definition). */
    private static final float[] HEAD = {-3.5f, 3.5f, -2.5f, 2.5f, -7.0f, 0.0f};
    /** The leg's first units start inside the body at the hip; the shaft is checked from here. */
    private static final float FROM = 3.0f;

    private final CivetModel<CivetEntity> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
    // As CivetModel builds it.
    private final QuadrupedRig<CivetEntity> rig = (QuadrupedRig<CivetEntity>) QuadrupedRig.builder(this.model)
            .pelvis("BandedPlamCivet").legs(LEGS)
            .stabilise("Head", 0.6f).lag("Tail", 0.12f, 0.35f, 30.0f)
            .arch("Head", 0.5f).arch("Tail", -0.6f, 0.0f)
            .mantle(MantleProfile.CIVET).build();
    private final MantleState mantle = new MantleState();
    private final LocomotionState state = new LocomotionState();
    private final ModelSpace space = new ModelSpace();

    private void frame(double y, double ticks) {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        final QuadrupedSolver solver = this.rig.solver();
        this.space.begin(0.5, y, 0.625, 0.0f, 1.0f, this.model);
        final float units = this.space.modelUnitsPerBlock();
        final Vector3d w = new Vector3d();
        for (int i = 0; i < 4; i++) {
            final LegChain chain = this.rig.legs().get(i);
            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, w);
            final LocomotionState.LegState leg = this.state.legs()[i];
            leg.soleX = w.x;
            leg.soleY = w.y;
            leg.soleZ = w.z;
            leg.groundY = 0.0;
            leg.plantWeight = 1.0f;
            solver.hipX[i] = chain.hip().x;
            solver.hipZ[i] = chain.hip().z;
            solver.soleUp[i] = (float) (w.y - y) * units;
            solver.groundUp[i] = (float) -y * units;
        }
        this.rig.mantleDriver().update(this.state, this.mantle, 0.5, y, 0.625, ticks, 0.375f, units, DT);
        solver.mantleAuthority = this.state.mantleAuthority.value();
        solver.mantlePitch = this.state.mantlePitch;
        solver.solve(this.state, 1.0f, DT);
        this.rig.space().begin(0.5, y, 0.625, 0.0f, 1.0f, this.model);
        this.rig.writePose(this.state, 1.0f);
        this.state.initialised = true;
    }

    /** How far a point is inside a box; negative outside. */
    private static float depth(Vector3f p, float[] box) {
        return Math.min(Math.min(Math.min(p.x - box[0], box[1] - p.x), Math.min(p.y - box[2], box[3] - p.y)),
                Math.min(p.z - box[4], box[5] - p.z));
    }

    @Test
    void frontLegsStayOutOfTheHeadThroughAWholeMantle() {
        assertClear(1.0);
    }

    @Test
    void frontLegsStayOutOfTheHeadUpASlab() {
        assertClear(0.5);
    }

    /** Mantles a lip {@code lip} high and fails if either front leg enters the head on the way. */
    private void assertClear(double lip) {
        this.mantle.set(0, 0, 1, lip, Direction.SOUTH, 0.0, 0L);
        this.state.ensureLegs(4);
        final ModelPart head = this.model.getAnyDescendantWithName("Head").orElseThrow();
        final List<ModelPart> headChain = ModelParts.chain(this.model.root(), head);
        final Vector3d w = new Vector3d();
        final Vector3f local = new Vector3f();
        float worst = -99.0f;
        String where = "";

        frame(0.0, 0.0);
        // Standing through the reach, then rising and cresting by height, a few frames a step.
        for (int step = 0; step <= 60; step++) {
            final double ticks = step < 12 ? step * 0.5 : 20.0;
            final double y = step < 12 ? 0.0 : Math.min((step - 12) / 48.0, 1.0) * lip;
            for (int f = 0; f < 4; f++) {
                frame(y, ticks);
            }
            this.space.begin(0.5, y, 0.625, 0.0f, 1.0f, this.model);
            final Matrix4f toHead = this.space.frameToWorld(headChain, new Matrix4f()).invert();
            for (int leg = 0; leg < 2; leg++) {
                final LegChain chain = this.rig.legs().get(leg);
                for (float s = FROM; s <= chain.soleOffset(); s += 0.5f) {
                    this.space.toWorld(chain.fromRoot(), 0.0f, s, 0.0f, w);
                    this.space.worldToFrame(toHead, w.x, w.y, w.z, local);
                    final float d = depth(local, HEAD);
                    if (d > worst) {
                        worst = d;
                        final MantlePhase phase = this.state.mantleProgress.phase;
                        where = LEGS[leg] + " in " + phase + " at y " + String.format("%.2f", y);
                    }
                }
            }
        }
        assertTrue(worst <= 0.0f, "a front leg went " + worst + "u into the head (" + where + ")");
    }
}
