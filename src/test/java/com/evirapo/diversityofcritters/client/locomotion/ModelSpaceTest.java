package com.evirapo.diversityofcritters.client.locomotion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the civet's sole lands in the world. The expected numbers are worked out by hand from the
 * model: root pivot at y = 24, hind leg "Leg" pivot at (-2.25, -11, 6.5), sole 11 units below it,
 * model drawn at half size and lifted 1.5 units (in scaled space) back onto the ground.
 */
class ModelSpaceTest {

    private static final double EPS = 1.0e-4;

    /** The civet's own root transform. */
    private static final ILocomotionModel<Entity> CIVET_LIKE = new ILocomotionModel<>() {
        @Override
        public void applyRootTransform(PoseStack poseStack) {
            poseStack.scale(0.5f, 0.5f, 0.5f);
            poseStack.translate(0.0f, 1.5f, 0.0f);
        }

        @Override
        public float rendererScale(Entity entity) {
            return 1.0f;
        }
    };

    private final ModelPart leg = new ModelPart(List.of(), Map.of());
    private final ModelPart root = new ModelPart(List.of(), Map.of("Leg", this.leg));
    private final List<ModelPart> chain = List.of(this.root, this.leg);
    private final ModelSpace space = new ModelSpace();
    private final Vector3d out = new Vector3d();

    ModelSpaceTest() {
        this.root.setPos(0.0f, 24.0f, 0.0f);
        this.leg.setPos(-2.25f, -11.0f, 6.5f);
    }

    private Vector3d sole(float yaw, float rendererScale) {
        this.space.begin(100.0, 64.0, -30.0, yaw, rendererScale, CIVET_LIKE);
        return this.space.toWorld(this.chain, 0.0f, 11.0f, 0.0f, this.out);
    }

    @Test
    void soleRestsOnTheGroundFacingSouth() {
        // Yaw 0 faces +Z, so a hind leg (model +Z) sits behind the body, toward -Z.
        final Vector3d at = sole(0.0f, 1.0f);
        assertEquals(100.0 - 2.25 / 32.0, at.x, EPS);
        assertEquals(64.0 + 0.001, at.y, EPS);
        assertEquals(-30.0 - 6.5 / 32.0, at.z, EPS);
    }

    @Test
    void soleTurnsWithTheBody() {
        // Yaw 90 faces -X, so the hind leg is toward +X.
        final Vector3d at = sole(90.0f, 1.0f);
        assertEquals(100.0 + 6.5 / 32.0, at.x, EPS);
        assertEquals(64.0 + 0.001, at.y, EPS);
        assertEquals(-30.0 - 2.25 / 32.0, at.z, EPS);
    }

    @Test
    void juvenileScaleShrinksTowardTheFeet() {
        final Vector3d at = sole(0.0f, 0.65f);
        assertEquals(100.0 - 0.65 * 2.25 / 32.0, at.x, EPS);
        assertEquals(64.0 + 0.65 * 0.001, at.y, EPS);
        assertEquals(-30.0 - 0.65 * 6.5 / 32.0, at.z, EPS);
    }

    @Test
    void liftingTheLegRaisesTheSoleInTheWorld() {
        final double grounded = sole(37.0f, 1.0f).y;
        // Model +Y is down, so a leg scaled shorter pulls its sole up.
        this.leg.yScale = 0.5f;
        final double lifted = sole(37.0f, 1.0f).y;
        assertEquals(grounded + 5.5 / 32.0, lifted, EPS);
    }

    @Test
    void applyPartMatchesTranslateAndRotate() {
        final ModelPart part = new ModelPart(List.of(), Map.of());
        part.setPos(1.5f, -7.0f, 3.25f);
        part.setRotation(0.4f, -1.1f, 0.25f);
        part.xScale = 1.2f;
        part.yScale = 0.8f;
        part.zScale = 1.05f;

        final PoseStack vanilla = new PoseStack();
        part.translateAndRotate(vanilla);
        final Matrix4f ours = new Matrix4f();
        ModelSpace.applyPart(ours, part);

        for (Vector3f probe : List.of(new Vector3f(0, 0, 0), new Vector3f(1, 2, 3), new Vector3f(-0.5f, 0.7f, -2))) {
            final Vector3f a = vanilla.last().pose().transformPosition(new Vector3f(probe));
            final Vector3f b = ours.transformPosition(new Vector3f(probe));
            assertEquals(a.x, b.x, 1.0e-5f);
            assertEquals(a.y, b.y, 1.0e-5f);
            assertEquals(a.z, b.z, 1.0e-5f);
        }
    }

    @Test
    void modelYOfTheSoleIsTheRootHeight() {
        // 24 down to the root, 11 up to the hip, 11 down to the sole.
        assertEquals(24.0f, LegChain.modelY(new Matrix4f(), this.chain, 11.0f), 1.0e-4f);
    }
}
