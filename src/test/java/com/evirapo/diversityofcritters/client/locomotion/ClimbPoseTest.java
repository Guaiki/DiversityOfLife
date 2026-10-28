package com.evirapo.diversityofcritters.client.locomotion;

import com.evirapo.diversityofcritters.client.animations.CivetAnims;
import com.evirapo.diversityofcritters.client.animations.SifakaAnims;
import com.evirapo.diversityofcritters.client.animations.SquirrelAnims;
import com.evirapo.diversityofcritters.client.models.CivetModel;
import com.evirapo.diversityofcritters.client.models.SifakaModel;
import com.evirapo.diversityofcritters.client.models.SquirrelModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Climbing, the paws rest on the bark: not inside the trunk, not off it. The rig stands down while
 * a critter climbs, so that is down to the clip and each model's climb render transform alone, and
 * that is what is measured: every climb clip through its whole cycle, how close each part comes to
 * the wall, in game pixels (1/16 block). The wall is half the hitbox from the centre, where the
 * collision leaves it.
 *
 * <p>Per part, what counts is the closest it comes in the cycle: a paw drawn back to step is fine,
 * one that never reaches the wall floats. A Blockbench re-export that moves the root again (what
 * sank the civet 4 px) breaks these.
 */
class ClimbPoseTest {

    /** A support is on the bark within these, px: further off it floats, further in it sinks. */
    private static final float GAP = 1.5f;
    private static final float SINK = 1.0f;
    /** The most any part may go into the trunk, px (the squirrel's torso goes 1.7 in). */
    private static final float MAX_INTO = 2.0f;

    private static final List<String> QUADRUPED_LEGS = List.of("Leg", "Leg2", "Leg3", "Leg4");
    private static final List<String> SIFAKA_LIMBS = List.of("Leftleg", "Rightleg", "Leftarm", "Rightarm");

    @Test
    void civetPawsRestOnTheBark() {
        final CivetModel<?> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
        for (AnimationDefinition clip : List.of(CivetAnims.CLIMBING_UP, CivetAnims.CLIMB_IDLE)) {
            check("civet", model, clip, climbUp(), 0.375, QUADRUPED_LEGS);
        }
        check("civet down", model, CivetAnims.CLIMBING_DOWN, climbDown(), 0.375, QUADRUPED_LEGS);
    }

    @Test
    void squirrelPawsRestOnTheBark() {
        final SquirrelModel<?> model = new SquirrelModel<>(SquirrelModel.createBodyLayer().bakeRoot());
        for (AnimationDefinition clip : List.of(SquirrelAnims.CLIMBING_UP, SquirrelAnims.CLIMB_IDLE)) {
            check("squirrel", model, clip, climbUp(), 0.35, QUADRUPED_LEGS);
        }
        check("squirrel down", model, SquirrelAnims.CLIMBING_DOWN, climbDown(), 0.35, QUADRUPED_LEGS);
    }

    @Test
    void sifakaLimbsRestOnTheBark() {
        final SifakaModel<?> model = new SifakaModel<>(SifakaModel.createBodyLayer().bakeRoot());
        // The sifaka climbs upright: no render turn, only the offset toward the wall.
        final Consumer<PoseStack> none = ps -> {
        };
        check("sifaka up", model, SifakaAnims.CLIMBING_UP, none, 0.375, SIFAKA_LIMBS);
        check("sifaka down", model, SifakaAnims.CLIMBING_DOWN, none, 0.375, SIFAKA_LIMBS);
        check("sifaka hang", model, SifakaAnims.CLIMB_IDLE, none, 0.375, SIFAKA_LIMBS);
    }

    @Test
    void sifakaBodyIsNotLeftBehind() {
        // Without the offset the whole body stayed 5 px off; the torso is no support, but it must not
        // be left that far back again.
        final SifakaModel<?> model = new SifakaModel<>(SifakaModel.createBodyLayer().bakeRoot());
        final Consumer<PoseStack> none = ps -> {
        };
        for (AnimationDefinition clip : List.of(SifakaAnims.CLIMBING_UP, SifakaAnims.CLIMB_IDLE, SifakaAnims.CLIMBING_DOWN)) {
            final float torso = deepestPerPart(model, clip, none, 0.375).get("Torse");
            assertTrue(torso > -3.0f, String.format("sifaka: torso %.2f px off the bark", -torso));
        }
    }

    @Test
    void civetClimbsDownHeadFirst() {
        final CivetModel<?> model = new CivetModel<>(CivetModel.createBodyLayer().bakeRoot());
        // The render turns the climbing-down body round; a turn in the clip too undid it (head up).
        assertHeadBelowHindLegs("civet", model, CivetAnims.CLIMBING_DOWN, "Leg");
    }

    @Test
    void squirrelClimbsDownHeadFirst() {
        final SquirrelModel<?> model = new SquirrelModel<>(SquirrelModel.createBodyLayer().bakeRoot());
        assertHeadBelowHindLegs("squirrel", model, SquirrelAnims.CLIMBING_DOWN, "Leg");
    }

    private static void assertHeadBelowHindLegs(String name, HierarchicalModel<?> model, AnimationDefinition clip,
                                                String hindLeg) {
        model.root().getAllParts().forEach(ModelPart::resetPose);
        KeyframeAnimations.animate(model, clip, 0L, 1.0f, new Vector3f());
        final PoseStack ps = new PoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f));
        ps.scale(-1, -1, 1);
        ps.translate(0, ModelSpace.RENDER_OFFSET_Y, 0);
        ps.scale(0.5f, 0.5f, 0.5f);
        ps.translate(0, 1.5f, 0);
        climbDown().accept(ps);
        final Map<String, Float> lowest = new HashMap<>();
        model.root().visit(ps, (pose, path, index, cube) -> {
            final String part = path.substring(path.lastIndexOf('/') + 1);
            final Vector3f v = new Vector3f();
            for (int c = 0; c < 8; c++) {
                v.set(((c & 1) == 0 ? cube.minX : cube.maxX) / 16f,
                        ((c & 2) == 0 ? cube.minY : cube.maxY) / 16f,
                        ((c & 4) == 0 ? cube.minZ : cube.maxZ) / 16f);
                pose.pose().transformPosition(v);
                lowest.merge(part, v.y, Math::min);
            }
        });
        assertTrue(lowest.get("Head") < lowest.get(hindLeg),
                String.format("%s climbs down head up: head %.2f, hind leg %.2f", name, lowest.get("Head"), lowest.get(hindLeg)));
    }

    /** What CivetModel and SquirrelModel do while climbing, after their root transform. */
    private static Consumer<PoseStack> climbUp() {
        return ps -> {
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.translate(0, -0.4f, 0.25f);
        };
    }

    private static Consumer<PoseStack> climbDown() {
        return ps -> {
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.translate(0, -0.4f, 0.25f);
        };
    }

    private static void check(String name, HierarchicalModel<?> model, AnimationDefinition clip,
                              Consumer<PoseStack> climbTransform, double halfWidth, List<String> supports) {
        final Map<String, Float> deepest = deepestPerPart(model, clip, climbTransform, halfWidth);
        for (String part : supports) {
            final float into = deepest.get(part);
            assertTrue(into >= -GAP, String.format("%s: %s floats %.2f px off the bark", name, part, -into));
            assertTrue(into <= SINK, String.format("%s: %s sinks %.2f px into the bark", name, part, into));
        }
        deepest.forEach((part, into) -> assertTrue(into <= MAX_INTO,
                String.format("%s: %s goes %.2f px into the bark", name, part, into)));
    }

    /**
     * Per part, the furthest it goes into the wall over the cycle, px; negative is a gap. An adult
     * facing south with the wall at z = halfWidth, through the renderer's transform (see
     * {@link ModelSpace}).
     */
    private static Map<String, Float> deepestPerPart(HierarchicalModel<?> model, AnimationDefinition clip,
                                                     Consumer<PoseStack> climbTransform, double halfWidth) {
        final Map<String, Float> deepest = new HashMap<>();
        final Vector3f cache = new Vector3f();
        final int lengthMs = Math.round(clip.lengthInSeconds() * 1000.0f);
        for (int ms = 0; ms < lengthMs; ms += 25) {
            model.root().getAllParts().forEach(ModelPart::resetPose);
            KeyframeAnimations.animate(model, clip, ms, 1.0f, cache);
            if (model instanceof SifakaModel<?>) {
                model.root().getChild("Thewholesifakamodel").z -= clip == SifakaAnims.CLIMB_IDLE
                        ? SifakaModel.CLIMB_HANG_WALL_OFFSET : SifakaModel.CLIMB_WALL_OFFSET;
            }
            final PoseStack ps = new PoseStack();
            ps.mulPose(Axis.YP.rotationDegrees(180.0f));
            ps.scale(-1, -1, 1);
            ps.translate(0, ModelSpace.RENDER_OFFSET_Y, 0);
            ps.scale(0.5f, 0.5f, 0.5f);
            ps.translate(0, 1.5f, 0);
            climbTransform.accept(ps);
            model.root().visit(ps, (pose, path, index, cube) -> {
                final String part = path.substring(path.lastIndexOf('/') + 1);
                final Vector3f v = new Vector3f();
                for (int c = 0; c < 8; c++) {
                    v.set(((c & 1) == 0 ? cube.minX : cube.maxX) / 16f,
                            ((c & 2) == 0 ? cube.minY : cube.maxY) / 16f,
                            ((c & 4) == 0 ? cube.minZ : cube.maxZ) / 16f);
                    pose.pose().transformPosition(v);
                    final float into = (float) ((v.z - halfWidth) * 16.0);
                    deepest.merge(part, into, Math::max);
                }
            });
        }
        return deepest;
    }
}
