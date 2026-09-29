package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.models.CivetModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoseSnapshotTest {

    private final ModelPart root = CivetModel.createBodyLayer().bakeRoot();
    private final ModelPart leg = this.root.getChild("BandedPlamCivet").getChild("Leg4");
    private final ModelPart spareTail = this.root.getChild("BandedPlamCivet").getChild("Body")
            .getChild("Tail").getChild("Tail_L");
    private final PoseSnapshot snapshot = new PoseSnapshot(this.root);

    @Test
    void fadingAllTheWayUndoesTheMotion() {
        this.snapshot.capture();
        this.leg.xRot += 0.8f;
        this.leg.z -= 2.0f;
        this.snapshot.fadeBack(1.0f);
        assertEquals(0.0f, this.leg.xRot, 1.0e-6f);
        assertEquals(-6.5f, this.leg.z, 1.0e-6f);
    }

    @Test
    void fadingHalfwayLeavesHalfTheMotion() {
        this.snapshot.capture();
        this.leg.xRot += 0.8f;
        this.snapshot.fadeBack(0.5f);
        assertEquals(0.4f, this.leg.xRot, 1.0e-6f);
    }

    @Test
    void partsTheAnimationHidStayHidden() {
        // The civet's walk hides its spare tails with a scale keyframe; a mantle fading the walk
        // out must not bring them back.
        this.snapshot.capture();
        this.spareTail.xScale = 0.0f;
        this.spareTail.yScale = 0.0f;
        this.spareTail.zScale = 0.0f;
        this.snapshot.fadeBack(1.0f);
        assertEquals(0.0f, this.spareTail.xScale);
        assertEquals(0.0f, this.spareTail.yScale);
        assertEquals(0.0f, this.spareTail.zScale);
    }

    @Test
    void noHoldTouchesNothing() {
        this.snapshot.capture();
        this.leg.xRot += 0.8f;
        this.snapshot.fadeBack(0.0f);
        assertEquals(0.8f, this.leg.xRot, 1.0e-6f);
    }
}
