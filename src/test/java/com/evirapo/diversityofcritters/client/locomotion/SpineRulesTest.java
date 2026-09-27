package com.evirapo.diversityofcritters.client.locomotion;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpineRulesTest {

    private static final float DT = 1.0f / 60.0f;

    // root -> Body -> {Head, Tail}
    private final ModelPart head = new ModelPart(List.of(), Map.of());
    private final ModelPart tail = new ModelPart(List.of(), Map.of());
    private final ModelPart body = new ModelPart(List.of(), Map.of("Head", this.head, "Tail", this.tail));
    private final LocomotionState state = new LocomotionState();

    private ModelPart find(String name) {
        return switch (name) {
            case "Body" -> this.body;
            case "Head" -> this.head;
            case "Tail" -> this.tail;
            default -> null;
        };
    }

    private SpineRules bind(SpineRules.Declarations declarations) {
        return declarations.bind(this::find);
    }

    @Test
    void stabiliseGivesBackAShareOfTheTilt() {
        final SpineRules.Declarations d = new SpineRules.Declarations();
        d.stabilise("Head", 0.6f);
        bind(d).write(this.state, 0.3f, 0.1f, 0.0f, 1.0f);
        assertEquals(-0.18f, this.head.xRot, 1.0e-6f);
        assertEquals(-0.06f, this.head.zRot, 1.0e-6f);
    }

    @Test
    void aStabilisedAncestorIsNotGivenBackTwice() {
        final SpineRules.Declarations d = new SpineRules.Declarations();
        d.stabilise("Body", 0.5f);
        d.stabilise("Head", 0.6f);
        bind(d).write(this.state, 0.3f, 0.0f, 0.0f, 1.0f);
        assertEquals(-0.15f, this.body.xRot, 1.0e-6f);
        // The head already receives the body's half; it adds only the remaining tenth.
        assertEquals(-0.03f, this.head.xRot, 1.0e-6f);
    }

    @Test
    void archBendsEachBoneByItsWeight() {
        final SpineRules.Declarations d = new SpineRules.Declarations();
        d.arch("Head", 0.5f);
        d.arch("Tail", -0.6f);
        bind(d).write(this.state, 0.0f, 0.0f, 0.0f, 1.0f, 0.4f);
        assertEquals(0.2f, this.head.xRot, 1.0e-6f);
        assertEquals(-0.24f, this.tail.xRot, 1.0e-6f);
        assertEquals(0.0f, this.body.xRot, 0.0f);
    }

    @Test
    void extendingUsesItsOwnWeight() {
        final SpineRules.Declarations d = new SpineRules.Declarations();
        d.arch("Head", 0.5f);
        d.arch("Tail", -0.6f, 0.0f);
        bind(d).write(this.state, 0.0f, 0.0f, 0.0f, 1.0f, -0.4f);
        assertEquals(-0.2f, this.head.xRot, 1.0e-6f, "the head lifts while the spine extends");
        assertEquals(0.0f, this.tail.xRot, 0.0f, "the tail only counterweights a flex");
    }

    @Test
    void noArchLeavesTheOtherRulesExactlyAsBefore() {
        final SpineRules.Declarations plain = new SpineRules.Declarations();
        plain.stabilise("Head", 0.6f);
        plain.lag("Tail", 0.1f, 0.5f, 30.0f);
        final SpineRules withoutArch = bind(plain);

        final SpineRules.Declarations arched = new SpineRules.Declarations();
        arched.stabilise("Head", 0.6f);
        arched.lag("Tail", 0.1f, 0.5f, 30.0f);
        arched.arch("Head", 0.5f);
        arched.arch("Tail", -0.6f);
        final SpineRules withArch = bind(arched);

        final float[] expected = run(withoutArch, new LocomotionState());
        final float[] actual = run(withArch, new LocomotionState());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(Float.floatToIntBits(expected[i]), Float.floatToIntBits(actual[i]), "value " + i);
        }
    }

    /** A few frames of tilting and turning; returns head and tail rotations. */
    private float[] run(SpineRules rules, LocomotionState state) {
        for (ModelPart part : List.of(this.head, this.tail, this.body)) {
            part.resetPose();
        }
        rules.track(state, 0.0f, 0.0f, 0.0f, DT);
        for (int i = 1; i <= 5; i++) {
            this.head.resetPose();
            this.tail.resetPose();
            rules.track(state, 0.05f * i, 0.02f * i, 10.0f * i, DT);
            rules.write(state, 0.05f * i, 0.02f * i, 10.0f * i, 1.0f, 0.0f);
        }
        return new float[]{this.head.xRot, this.head.yRot, this.head.zRot, this.tail.xRot, this.tail.yRot, this.tail.zRot};
    }

    private SpineRules tailLag(float maxDegrees) {
        final SpineRules.Declarations d = new SpineRules.Declarations();
        d.lag("Tail", 0.1f, 0.5f, maxDegrees);
        return bind(d);
    }

    @Test
    void lagTrailsATiltThenCatchesUp() {
        final SpineRules rules = tailLag(45.0f);
        rules.track(this.state, 0.0f, 0.0f, 0.0f, DT);

        rules.track(this.state, 0.3f, 0.0f, 0.0f, DT);
        rules.write(this.state, 0.3f, 0.0f, 0.0f, 1.0f);
        assertTrue(this.tail.xRot < -0.2f && this.tail.xRot > -0.3f, "trail " + this.tail.xRot);

        for (int i = 0; i < 120; i++) {
            rules.track(this.state, 0.3f, 0.0f, 0.0f, DT);
        }
        this.tail.xRot = 0.0f;
        rules.write(this.state, 0.3f, 0.0f, 0.0f, 1.0f);
        assertEquals(0.0f, this.tail.xRot, 1.0e-3f);
    }

    @Test
    void lagTrailsATurnTheShortWayAcrossTheSeam() {
        final SpineRules rules = tailLag(45.0f);
        rules.track(this.state, 0.0f, 0.0f, 170.0f, DT);
        // A 20 degree turn that crosses +-180.
        rules.track(this.state, 0.0f, 0.0f, -170.0f, DT);
        rules.write(this.state, 0.0f, 0.0f, -170.0f, 1.0f);
        final float trailDegrees = (float) Math.toDegrees(this.tail.yRot);
        assertTrue(trailDegrees < 0.0f && trailDegrees > -20.0f, "trail " + trailDegrees);
    }

    @Test
    void lagIsCapped() {
        final SpineRules rules = tailLag(30.0f);
        rules.track(this.state, 0.0f, 0.0f, 0.0f, DT);
        rules.track(this.state, 0.0f, 0.0f, 120.0f, DT);
        rules.write(this.state, 0.0f, 0.0f, 120.0f, 1.0f);
        assertEquals(-30.0f, (float) Math.toDegrees(this.tail.yRot), 1.0e-3f);
    }

    @Test
    void closedGateWritesNoLag() {
        final SpineRules rules = tailLag(45.0f);
        rules.track(this.state, 0.0f, 0.0f, 0.0f, DT);
        rules.track(this.state, 0.3f, 0.0f, 40.0f, DT);
        rules.write(this.state, 0.3f, 0.0f, 40.0f, 0.0f);
        assertEquals(0.0f, this.tail.xRot);
        assertEquals(0.0f, this.tail.yRot);
    }

    @Test
    void missingBoneIsNamed() {
        final SpineRules.Declarations d = new SpineRules.Declarations();
        d.lag("Tial", 0.1f, 0.5f, 30.0f);
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> bind(d));
        assertTrue(error.getMessage().contains("Tial"));
    }
}
