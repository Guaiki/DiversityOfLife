package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.client.animations.CivetAnims;
import com.evirapo.diversityofcritters.client.locomotion.ILocomotionModel;
import com.evirapo.diversityofcritters.client.locomotion.LocomotionRig;
import com.evirapo.diversityofcritters.client.locomotion.PoseSnapshot;
import com.evirapo.diversityofcritters.client.locomotion.QuadrupedRig;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.client.renderer.CivetRenderer;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.darkblade.deluxelib.anim.Animation;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.deluxelib.client.anim.AnimBlend;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.component.LookAtAdditive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

import java.util.Set;

public class CivetModel<T extends CivetEntity> extends HierarchicalModel<T> implements ILocomotionModel<T> {

    /** The model is authored at twice its in-game size. */
    public static final float MODEL_SCALE = 0.5F;

    private static final Set<String> HEAD_TRACKING_ANIMS = Set.of("idle", "walk", "run");
    /** The walk cycle, which a mantle holds back while it poses the legs itself. */
    private static final Set<String> GAIT_ANIMS = Set.of("walk", "run");

    private final LookAtAdditive<T, CivetModel<T>> lookAt =
            new LookAtAdditive<T, CivetModel<T>>(m -> m.Head, 75.0F, 40.0F)
                    .enabledWhile(entity -> {
                        Animation current = entity.animator().getCurrent(0);
                        return current != MobAnimator.EMPTY
                                && HEAD_TRACKING_ANIMS.contains(current.getName());
                    });

    private boolean isClimbingRender = false;
    private boolean isClimbingDownRender = false;

    /**
     * El {@code ModelPart} que envuelve al hueso raiz, y lo que devuelve {@link #root()}.
     *
     * <p>No es cosmetico. {@code KeyframeBlender} resuelve cada canal con
     * {@code HierarchicalModel.getAnyDescendantWithName(nombre)}, que busca <b>una parte que tenga un
     * hijo con ese nombre</b>. Si {@code root()} devuelve directamente el hueso animado, ese hueso no
     * es hijo de nadie dentro del arbol que se recorre: la busqueda falla, el {@code ifPresent} no
     * entra y el canal <b>se descarta en silencio</b> — sin excepcion, sin log, sin nada visible
     * salvo una animacion que no se mueve del sitio.
     *
     * <p>Devolviendo el envoltorio, el hueso pasa a ser hijo suyo y resuelve. Es el mismo patron que
     * ya usaba {@code SifakaModel}.
     */
    private final ModelPart meshRoot;

    private final ModelPart BandedPlamCivet;
    private final ModelPart Body;
    private final ModelPart Head;
    private final ModelPart Jaw;
    private final ModelPart BottomJaw;
    private final ModelPart Tongue;
    private final ModelPart TopJaw;
    private final ModelPart Eyes;
    private final ModelPart Eye1;
    private final ModelPart Eye2;
    private final ModelPart Sleep_eye1;
    private final ModelPart Sleep_eye2;
    private final ModelPart Ear2;
    private final ModelPart Torse;
    private final ModelPart Torse3;
    private final ModelPart Torse2;
    private final ModelPart Tail;
    private final ModelPart Tail_I;
    private final ModelPart Tail_L;
    private final ModelPart Tail_L3;
    private final ModelPart Tail_L2;
    private final ModelPart Leg4;
    private final ModelPart Leg;
    private final ModelPart Leg2;
    private final ModelPart Leg3;

    /** Keeps the feet on uneven ground. Built once; per-entity state lives in the rig's cache. */
    private final LocomotionRig<T> locomotionRig;
    /** The pose before the walk cycle, for a mantle to fade it back out. */
    private final PoseSnapshot beforeGait;

    public CivetModel(ModelPart root) {
        this.meshRoot        = root;
        this.BandedPlamCivet = root.getChild("BandedPlamCivet");
        this.Body            = this.BandedPlamCivet.getChild("Body");
        this.Head            = this.Body.getChild("Head");
        this.Jaw             = this.Head.getChild("Jaw");
        this.BottomJaw       = this.Jaw.getChild("BottomJaw");
        this.Tongue          = this.BottomJaw.getChild("Tongue");
        this.TopJaw          = this.Jaw.getChild("TopJaw");
        this.Eyes            = this.Head.getChild("Eyes");
        this.Eye1            = this.Eyes.getChild("Eye1");
        this.Eye2            = this.Eyes.getChild("Eye2");
        this.Sleep_eye1      = this.Eyes.getChild("Sleep_eye1");
        this.Sleep_eye2      = this.Eyes.getChild("Sleep_eye2");
        this.Ear2            = this.Head.getChild("Ear2");
        this.Torse           = this.Body.getChild("Torse");
        this.Torse3          = this.Torse.getChild("Torse3");
        this.Torse2          = this.Torse.getChild("Torse2");
        this.Tail            = this.Body.getChild("Tail");
        this.Tail_I          = this.Tail.getChild("Tail_I");
        this.Tail_L          = this.Tail.getChild("Tail_L");
        this.Tail_L3         = this.Tail.getChild("Tail_L3");
        this.Tail_L2         = this.Tail.getChild("Tail_L2");
        this.Leg4            = this.BandedPlamCivet.getChild("Leg4");
        this.Leg             = this.BandedPlamCivet.getChild("Leg");
        this.Leg2            = this.BandedPlamCivet.getChild("Leg2");
        this.Leg3            = this.BandedPlamCivet.getChild("Leg3");

        // The legs hang straight from the civet's bone, which is also what the body tilts on. Not
        // "root": that is the wrapper around it, which renderToBuffer never draws.
        this.locomotionRig = QuadrupedRig.<T, CivetModel<T>>builder(this)
                .pelvis("BandedPlamCivet")
                .legs("Leg4", "Leg3", "Leg", "Leg2")
                // Starting values, to be tuned by eye with the overlay (F6) and bypass (F9).
                .stabilise("Head", 0.6F)
                .lag("Tail", 0.12F, 0.35F, 30.0F)
                // The rigid torso cannot bend, so the head and tail do. Rearing up the head looks up
                // the face, clear of the paws; over the crest it dips toward the top and the tail
                // swings down as a counterweight (xRot + raises a tail, lowers a head).
                .arch("Head", 0.5F)
                .arch("Tail", -0.6F, 0.0F)
                .mantle(MantleProfile.CIVET)
                .build();
        this.beforeGait = new PoseSnapshot(this.root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition BandedPlamCivet = partdefinition.addOrReplaceChild("BandedPlamCivet",
                CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition Body = BandedPlamCivet.addOrReplaceChild("Body",
                CubeListBuilder.create(), PartPose.offset(0.275F, -12.0F, 0.0F));

        PartDefinition Head = Body.addOrReplaceChild("Head",
                CubeListBuilder.create()
                        .texOffs(54, 8) .addBox(-3.575F, -2.5F, -7.0F, 7.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 75).addBox(2.425F, -5.5F, -4.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-0.2F, -3.5F, -9.0F));

        PartDefinition Jaw = Head.addOrReplaceChild("Jaw",
                CubeListBuilder.create()
                        .texOffs(26, 62).addBox(-1.575F, -6.75F, 3.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.0F, 1.25F, -7.0F));

        PartDefinition BottomJaw = Jaw.addOrReplaceChild("BottomJaw",
                CubeListBuilder.create()
                        .texOffs(68, 20).addBox(-1.5188F, -0.0125F, -3.975F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(46, 54).addBox(-1.2688F, -0.5125F, -3.725F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(46, 56).addBox(1.2312F, -0.5125F, -3.725F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.9438F, 0.2625F, -0.025F));

        BottomJaw.addOrReplaceChild("Tongue",
                CubeListBuilder.create()
                        .texOffs(54, 25).addBox(2.925F, 0.0F, -3.0F, 2.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.9437F, -0.2625F, 0.025F));

        Jaw.addOrReplaceChild("TopJaw",
                CubeListBuilder.create()
                        .texOffs(54, 20).addBox(2.425F, -2.5F, -4.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(46, 56).addBox(2.925F, -2.0F, -3.75F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(46, 54).addBox(5.175F, -2.0F, -3.75F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.0F, 1.75F, 0.0F));

        PartDefinition Eyes = Head.addOrReplaceChild("Eyes",
                CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -7.0F));

        Eyes.addOrReplaceChild("Eye1",
                CubeListBuilder.create()
                        .texOffs(28, 81).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.6F, 0.0F, 0.475F));

        Eyes.addOrReplaceChild("Eye2",
                CubeListBuilder.create()
                        .texOffs(34, 81).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.45F, 0.0F, 0.475F));

        Eyes.addOrReplaceChild("Sleep_eye1",
                CubeListBuilder.create()
                        .texOffs(64, 25).addBox(-1.5F, 22.0F, -1.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.1F, -22.5F, 0.975F));

        Eyes.addOrReplaceChild("Sleep_eye2",
                CubeListBuilder.create()
                        .texOffs(74, 25).addBox(-1.5F, -0.5F, -1.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.95F, 0.0F, 0.975F));

        Head.addOrReplaceChild("Ear2", CubeListBuilder.create(), PartPose.offset(-3.075F, -2.5F, -3.0F));

        PartDefinition Torse = Body.addOrReplaceChild("Torse",
                CubeListBuilder.create(), PartPose.offset(-0.2681F, 0.436F, 1.0F));

        Torse.addOrReplaceChild("Torse3",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0069F, -5.936F, -2.0F, 7.0F, 9.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-0.5F, 1.5F, -8.0F));

        Torse.addOrReplaceChild("Torse2",
                CubeListBuilder.create()
                        .texOffs(0, 29) .addBox(-3.5F, -2.5F, -12.5F, 7.0F, 8.0F, 17.0F, new CubeDeformation(0.0F))
                        .texOffs(48, 53).addBox(-3.5F, 5.5F, -3.5F, 7.0F, 7.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-0.0069F, -6.936F, -0.5F, 0.6981F, 0.0F, 0.0F));

        PartDefinition Tail = Body.addOrReplaceChild("Tail",
                CubeListBuilder.create(), PartPose.offset(-0.25F, -1.0F, 11.0F));

        Tail.addOrReplaceChild("Tail_I",
                CubeListBuilder.create()
                        .texOffs(48, 29).addBox(-1.0F, -6.0F, -2.0F, 4.0F, 4.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.0F, 4.0F, 2.0F));

        Tail.addOrReplaceChild("Tail_L",
                CubeListBuilder.create()
                        .texOffs(0, 62).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 54).addBox(-2.0F, -2.0F, 9.0F, 13.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        Tail.addOrReplaceChild("Tail_L3",
                CubeListBuilder.create()
                        .texOffs(52, 68).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(54, 0) .addBox(-11.0F, -2.0F, 9.0F, 13.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        Tail.addOrReplaceChild("Tail_L2",
                CubeListBuilder.create()
                        .texOffs(26, 68).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 75) .addBox(-2.0F, -11.0F, 9.0F, 4.0F, 13.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        BandedPlamCivet.addOrReplaceChild("Leg4",
                CubeListBuilder.create()
                        .texOffs(34, 54).addBox(-1.75F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.0F, -11.0F, -6.5F));

        BandedPlamCivet.addOrReplaceChild("Leg",
                CubeListBuilder.create()
                        .texOffs(16, 81).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.25F, -11.0F, 6.5F));

        BandedPlamCivet.addOrReplaceChild("Leg2",
                CubeListBuilder.create()
                        .texOffs(78, 67).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.25F, -11.0F, 6.5F));

        BandedPlamCivet.addOrReplaceChild("Leg3",
                CubeListBuilder.create()
                        .texOffs(78, 53).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.25F, -11.0F, -6.5F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ANIMATION
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animatePose(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // Last, so it corrects the finished pose. Runs even when the animation stood the rig down
        // (sleeping, sitting, climbing): that is what lets it fade out instead of snapping off.
        this.locomotionRig.apply(entity, Minecraft.getInstance().getPartialTick());
    }

    private void animatePose(T entity, float limbSwing, float limbSwingAmount,
                             float ageInTicks, float netHeadYaw, float headPitch) {

        boolean sleepingLike = entity.getSleepState() != DiverseCritter.SleepState.AWAKE;
        boolean climbing     = entity.isClimbing();
        boolean swimming     = entity.isInWaterOrBubble();

        float headScale = entity.isJuvenile() ? 1.05F : 1.0F;
        this.Head.xScale = headScale;
        this.Head.yScale = headScale;
        this.Head.zScale = headScale;

        if (this.young) {
            this.applyStatic(CivetAnims.BABY);
        }

        // A mantle poses the legs itself; the walk cycle steps aside for it.
        Animation current = entity.animator().getCurrent(0);
        float gaitHold = current != MobAnimator.EMPTY && GAIT_ANIMS.contains(current.getName())
                ? this.locomotionRig.mantleCycleHold(entity) : 0.0F;
        if (gaitHold > 0.0F) {
            this.beforeGait.capture();
        }
        AnimBlend.applyBlended(this, entity.animator(), ageInTicks, 200L, 0);
        this.beforeGait.fadeBack(gaitHold);

        this.lookAt.apply(entity, this,
                AnimContext.of(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch));

        if (!entity.isAlive()) {
            this.applyDefaultPartVisibility();
        }

        this.isClimbingRender     = climbing;
        this.isClimbingDownRender = entity.isClimbingDown();

        if (!climbing && swimming && !sleepingLike) {
            this.BandedPlamCivet.y += 4.0F;
            this.Head.xRot -= 0.3F;
        }
    }


    private void applyDefaultPartVisibility() {
        hide(this.Torse2);
        hide(this.Tail_L);
        hide(this.Tail_L2);
        hide(this.Tail_L3);
        this.Sleep_eye1.yScale = 0.0F;
        this.Sleep_eye2.yScale = 0.0F;
    }

    private static void hide(ModelPart part) {
        part.xScale = 0.0F;
        part.yScale = 0.0F;
        part.zScale = 0.0F;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RENDER
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        poseStack.pushPose();

        this.applyRootTransform(poseStack);

        if (this.isClimbingRender) {
            if (this.isClimbingDownRender) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                poseStack.translate(0, -0.4f, 0.25f);
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                poseStack.translate(0, -0.4f, 0.25f);
            }
        }

        BandedPlamCivet.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);

        poseStack.popPose();
    }

    @Override
    public ModelPart root() {
        return meshRoot;
    }

    /** Shared with the locomotion rig, so bones are placed in the world exactly where they are drawn. */
    @Override
    public void applyRootTransform(PoseStack poseStack) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0F, 1.5F, 0.0F);
    }

    @Override
    public float rendererScale(T entity) {
        return CivetRenderer.scaleFor(entity);
    }
}
