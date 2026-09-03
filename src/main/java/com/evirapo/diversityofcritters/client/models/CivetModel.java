package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.client.animations.CivetAnims;
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
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

import java.util.Set;

public class CivetModel<T extends CivetEntity> extends HierarchicalModel<T> {

    /** Sólo en locomoción de suelo se sigue al jugador con la cabeza; ver {@link #lookAt}. */
    private static final Set<String> HEAD_TRACKING_ANIMS = Set.of("idle", "walk", "run");

    /**
     * Sustituye a la asignación directa {@code Head.xRot = headPitch} del modelo anterior, que había
     * que ir apagando a mano en cada estado (dormir, trepar, sentarse por orden…). Como aditivo se
     * suma encima de los keyframes y se atenúa con rampa en vez de cortar, y el gate es una lista de
     * animaciones en vez de una cadena de negaciones.
     */
    private final LookAtAdditive<T, CivetModel<T>> lookAt =
            new LookAtAdditive<T, CivetModel<T>>(m -> m.Head, 75.0F, 40.0F)
                    .enabledWhile(entity -> {
                        Animation current = entity.animator().getCurrent(0);
                        return current != MobAnimator.EMPTY
                                && HEAD_TRACKING_ANIMS.contains(current.getName());
                    });

    private boolean isClimbingRender = false;
    private boolean isClimbingDownRender = false;

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

    public CivetModel(ModelPart root) {
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

        boolean sleepingLike = entity.getSleepState() != DiverseCritter.SleepState.AWAKE;
        boolean climbing     = entity.isClimbing();
        boolean swimming     = entity.isInWaterOrBubble();

        float headScale = entity.isJuvenile() ? 1.05F : 1.0F;
        this.Head.xScale = headScale;
        this.Head.yScale = headScale;
        this.Head.zScale = headScale;

        // Antes del blend: la pose de bebé es la base sobre la que los keyframes suman offsets.
        if (this.young) {
            this.applyStatic(CivetAnims.BABY);
        }

        AnimBlend.applyBlended(this, entity.animator(), ageInTicks, 200L, 0);

        this.lookAt.apply(entity, this,
                AnimContext.of(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch));

        // Al morir, toda playCondition de la capa 0 lleva isAlive(), así que la capa se vacía y las
        // partes intercambiables vuelven a la pose de horneado, que las tiene TODAS a escala 1: dos
        // torsos superpuestos y las cuatro colas a la vez.
        //
        // Va DESPUÉS del blend y decidido por estado de la entidad, no por "el animator está vacío":
        // el animator queda vacío en cuanto cae la condición, pero la pila de desvanecimiento sigue
        // sumando sus offsets de escala durante otros 150-300 ms. Ponerlo antes dejaba esos offsets
        // cayendo encima de un cero (escala negativa = geometría invertida) durante ese hueco.
        if (!entity.isAlive()) {
            this.applyDefaultPartVisibility();
        }

        // renderToBuffer gira el modelo −90° al trepar; se lee aquí porque allí no llega la entidad.
        this.isClimbingRender     = climbing;
        this.isClimbingDownRender = entity.isClimbingDown();

        if (!climbing && swimming && !sleepingLike) {
            this.BandedPlamCivet.y += 4.0F;
            this.Head.xRot -= 0.3F;
        }
    }

    /**
     * Las partes intercambiables del rig nuevo: cada animación elige por keyframes de escala qué
     * torso (recto o encorvado), qué cola (recta o en las tres curvas) y qué ojos (abiertos o
     * dormidos) se ven. Esto es la pose por defecto para cuando no suena ninguna.
     */
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

        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.translate(0, 1.5F, 0);

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
        return BandedPlamCivet;
    }
}
