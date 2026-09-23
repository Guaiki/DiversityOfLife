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
