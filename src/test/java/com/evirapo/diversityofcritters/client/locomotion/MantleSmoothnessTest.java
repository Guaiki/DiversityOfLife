package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.models.CivetModel;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.locomotion.MantleMotion;
import com.evirapo.diversityofcritters.common.locomotion.MantlePhase;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.locomotion.MantleProgress;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole mantle the way it plays in game: the server moves the body tick by tick, the client gets
 * its position every few ticks and interpolates it, and the rig poses the model at 60 frames a
 * second. Every sole and the body's pitch are recorded per frame; none may jerk.
 */
class MantleSmoothnessTest {

    static final int FRAMES_PER_TICK = 3;
    static final int SEND_EVERY = 3;
    static final float DT = 1.0f / 60.0f;
    static final String[] LEGS = {"Leg4", "Leg3", "Leg", "Leg2"};
    static final MantleProfile PROFILE = MantleProfile.CIVET;
    static final double HALF_WIDTH = 0.375;

    /** One rendered frame. */
    record Frame(int index, double y, MantlePhase phase, float t, double[][] soles, float pitch, float drop, float target) {
    }

    final CivetModel<CivetEntity> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
    final QuadrupedRig<CivetEntity> rig = (QuadrupedRig<CivetEntity>) QuadrupedRig.builder(this.model)
            .pelvis("root").legs(LEGS)
            .stabilise("Head", 0.6f).lag("Tail", 0.12f, 0.35f, 30.0f)
            .arch("Head", 0.5f).arch("Tail", -0.6f, 0.0f)
            .mantle(PROFILE).build();
    final ModelSpace space = new ModelSpace();

    /** Server positions per tick: y and distance past the face, and whether the mantle is on. */
    static List<double[]> serverTicks(double startPastFace) {
        final List<double[]> ticks = new ArrayList<>();
        final MantleProgress progress = new MantleProgress();
        final MantleMotion motion = new MantleMotion();
        double y = 0.0;
        double past = startPastFace;
        // A few ticks walking up to it first.
        for (int i = 0; i < 6; i++) {
            ticks.add(new double[]{y, past - 0.1 * (6 - i), 0});
        }
        for (int tick = 0; tick <= PROFILE.maxTicks; tick++) {
            MantleProgress.of(y, 0.0, 1.0, tick, past, HALF_WIDTH, PROFILE, progress);
            MantleMotion.plan(progress, y, 0.0, 1.0, past, HALF_WIDTH, false, PROFILE, motion);
            if (motion.done) {
                break;
            }
            ticks.add(new double[]{y, past, 1});
            past += motion.along;
            if (motion.holdsVertical) {
                y += motion.up;
            }
        }
        // Then walking on over the top.
        for (int i = 0; i < 20; i++) {
            ticks.add(new double[]{y, past, 0});
            past += 0.1;
        }
        return ticks;
    }

    List<Frame> play(double startPastFace) {
        final List<double[]> server = serverTicks(startPastFace);
        final LocomotionState state = new LocomotionState();
        state.ensureLegs(4);
        final List<Frame> frames = new ArrayList<>();
        final Vector3d w = new Vector3d();
        final MantleState mantle = new MantleState();

        // Client interpolation as a vanilla mob: a new target every few ticks, reached over 3 ticks.
        double cy = server.get(0)[0];
        double cp = server.get(0)[1];
        double ty = cy;
        double tp = cp;
        int steps = 0;
        double prevY = cy;
        double prevP = cp;
        int startTick = -1;
        for (int tick = 0; tick < server.size(); tick++) {
            final double[] s = server.get(tick);
            if (tick % SEND_EVERY == 0) {
                ty = s[0];
                tp = s[1];
                steps = 3;
            }
            prevY = cy;
            prevP = cp;
            if (steps > 0) {
                cy += (ty - cy) / steps;
                cp += (tp - cp) / steps;
                steps--;
            }
            final boolean mantling = s[2] > 0;
            if (mantling && startTick < 0) {
                startTick = tick;
            }
            for (int f = 0; f < FRAMES_PER_TICK; f++) {
                final float partial = f / (float) FRAMES_PER_TICK;
                final double y = prevY + (cy - prevY) * partial;
                final double past = prevP + (cp - prevP) * partial;
                final double z = 1.0 + past;
                frames.add(frame(state, mantling ? mantle.set(0, 0, 1, 1.0, Direction.SOUTH, 0.0, startTick) : null,
                        y, z, tick + partial, frames.size(), w));
            }
        }
        return frames;
    }

    Frame frame(LocomotionState state, MantleState mantle, double y, double z, double gameTime, int index, Vector3d w) {
        this.model.root().getAllParts().forEach(ModelPart::resetPose);
        final QuadrupedSolver solver = this.rig.solver();
        this.space.begin(0.5, y, z, 0.0f, 1.0f, this.model);
        final float units = this.space.modelUnitsPerBlock();
        for (int i = 0; i < 4; i++) {
            final LegChain chain = this.rig.legs().get(i);
            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, w);
            final LocomotionState.LegState leg = state.legs()[i];
            leg.soleX = w.x;
            leg.soleY = w.y;
            leg.soleZ = w.z;
            // Ground under each foot: the top past the face, the floor before it.
            leg.groundY = w.z >= 1.0 ? 1.0 : 0.0;
            leg.plantWeight = 1.0f;
            solver.hipX[i] = chain.hip().x;
            solver.hipZ[i] = chain.hip().z;
            solver.soleUp[i] = (float) (w.y - y) * units;
            solver.groundUp[i] = (float) (leg.groundY - y) * units;
        }
        this.rig.mantleDriver().update(state, mantle, 0.5, y, z, gameTime, (float) HALF_WIDTH, units, DT);
        solver.mantleAuthority = state.mantleAuthority.value();
        solver.mantlePitch = state.mantlePitch;
        solver.solve(state, 1.0f, DT);
        this.rig.space().begin(0.5, y, z, 0.0f, 1.0f, this.model);
        this.rig.writePose(state, 1.0f);
        state.initialised = true;

        final double[][] soles = new double[4][3];
        this.space.begin(0.5, y, z, 0.0f, 1.0f, this.model);
        for (int i = 0; i < 4; i++) {
            final LegChain chain = this.rig.legs().get(i);
            this.space.toWorld(chain.fromRoot(), 0.0f, chain.soleOffset(), 0.0f, w);
            soles[i][0] = w.x * units;
            soles[i][1] = w.y * units;
            soles[i][2] = w.z * units;
        }
        return new Frame(index, y, state.mantleProgress.phase, state.mantleProgress.t, soles,
                state.pelvisPitch.value(), state.mantleBodyDrop, state.mantlePitch * state.mantleAuthority.value());
    }

    /** Frame-to-frame change of velocity of each sole, model units per frame per frame. */
    static double[][] soleJerks(List<Frame> frames) {
        final double[][] out = new double[frames.size()][4];
        for (int n = 2; n < frames.size(); n++) {
            for (int leg = 0; leg < 4; leg++) {
                double sum = 0.0;
                for (int k = 0; k < 3; k++) {
                    final double a = frames.get(n).soles()[leg][k] - 2.0 * frames.get(n - 1).soles()[leg][k]
                            + frames.get(n - 2).soles()[leg][k];
                    sum += a * a;
                }
                out[n][leg] = Math.sqrt(sum);
            }
        }
        return out;
    }

    /**
     * No sole may change its velocity by more than this from one frame to the next, model units per
     * frame squared. A paw swinging to its next hold peaks around 1–2; the jerks this test was
     * written against reached 9 (a leg's share of the body's move switching on, the targets
     * following the client's interpolation kinks, the body's pitch far behind its legs).
     */
    static final double MAX_SOLE_JERK = 2.5;
    /** Nor may the body's pitch, degrees per frame squared. */
    static final double MAX_PITCH_JERK = 1.0;

    @Test
    void aWholeMantlePlaysSmoothly() {
        final List<Frame> frames = play(-0.5);
        final double[][] jerk = soleJerks(frames);
        for (int n = 2; n < frames.size(); n++) {
            final Frame f = frames.get(n);
            for (int leg = 0; leg < 4; leg++) {
                org.junit.jupiter.api.Assertions.assertTrue(jerk[n][leg] < MAX_SOLE_JERK, String.format(
                        "%s jerks %.2f at frame %d (%s, t %.2f, y %.3f)", LEGS[leg], jerk[n][leg], n, f.phase(), f.t(), f.y()));
            }
            final double pitchJerk = Math.toDegrees(f.pitch() - 2.0 * frames.get(n - 1).pitch() + frames.get(n - 2).pitch());
            org.junit.jupiter.api.Assertions.assertTrue(Math.abs(pitchJerk) < MAX_PITCH_JERK,
                    String.format("pitch jerks %.2f at frame %d (%s)", pitchJerk, n, f.phase()));
        }
    }
}
