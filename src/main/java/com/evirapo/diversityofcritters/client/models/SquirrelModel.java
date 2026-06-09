package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.AnimationState;
import org.joml.Vector3f;

public class SquirrelModel <T extends SquirrelEntity> extends HierarchicalModel<T> {

    private final ModelPart squirrel;
    private final ModelPart Glide;
    private final ModelPart Body;
    private final ModelPart Glide_torse;
    private final ModelPart Head;
    private final ModelPart Eye1;
    private final ModelPart Eye2;
    private final ModelPart Ear1;
    private final ModelPart Ear2;
    private final ModelPart Mouth;
    private final ModelPart bone18;
    private final ModelPart Grab;
    private final ModelPart bone22;
    private final ModelPart bone2;
    private final ModelPart bone3;
    private final ModelPart Tail;
    private final ModelPart bone6;
    private final ModelPart bone12;
    private final ModelPart Torse;
    private final ModelPart Leg3;
    private final ModelPart bone11;
    private final ModelPart bone16;
    private final ModelPart Leg4;
    private final ModelPart bone10;
    private final ModelPart bone17;
    private final ModelPart Leg;
    private final ModelPart Toe1;
    private final ModelPart Leg2;
    private final ModelPart Toe2;

    public SquirrelModel(ModelPart root) {
        this.squirrel = root.getChild("Giant flying squirrel");
        this.Glide = this.squirrel.getChild("Glide");
        this.Body = this.squirrel.getChild("Body");
        this.Glide_torse = this.Body.getChild("Glide_torse");
        this.Head = this.Body.getChild("Head");
        this.Eye1 = this.Head.getChild("Eye1");
        this.Eye2 = this.Head.getChild("Eye2");
        this.Ear1 = this.Head.getChild("Ear1");
        this.Ear2 = this.Head.getChild("Ear2");
        this.Mouth = this.Head.getChild("Mouth");
        this.bone18 = this.Mouth.getChild("bone18");
        this.Grab = this.bone18.getChild("Grab");
        this.bone22 = this.bone18.getChild("bone22");
        this.bone2 = this.Mouth.getChild("bone2");
        this.bone3 = this.bone2.getChild("bone3");
        this.Tail = this.Body.getChild("Tail");
        this.bone6 = this.Tail.getChild("bone6");
        this.bone12 = this.Tail.getChild("bone12");
        this.Torse = this.Body.getChild("Torse");
        this.Leg3 = this.squirrel.getChild("Leg3");
        this.bone11 = this.Leg3.getChild("bone11");
        this.bone16 = this.Leg3.getChild("bone16");
        this.Leg4 = this.squirrel.getChild("Leg4");
        this.bone10 = this.Leg4.getChild("bone10");
        this.bone17 = this.Leg4.getChild("bone17");
        this.Leg = this.squirrel.getChild("Leg");
        this.Toe1 = this.Leg.getChild("Toe1");
        this.Leg2 = this.squirrel.getChild("Leg2");
        this.Toe2 = this.Leg2.getChild("Toe2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition squirrel = partdefinition.addOrReplaceChild("Giant flying squirrel", CubeListBuilder.create(), PartPose.offset(0.0F, 13.1639F, 0.35F));

        PartDefinition Glide = squirrel.addOrReplaceChild("Glide", CubeListBuilder.create().texOffs(19, 118).addBox(-23.5F, -3.0F, -13.1F, 47.0F, 2.0F, 32.0F, new CubeDeformation(0.0F))
                .texOffs(42, 66).addBox(15.5F, -1.0F, -13.1F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(78, 93).addBox(-17.5F, -1.0F, -13.1F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(24, 77).addBox(-23.5F, -1.0F, 15.9F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(14, 90).addBox(21.5F, -1.0F, 15.9F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.8361F, 6.75F));

        PartDefinition Body = squirrel.addOrReplaceChild("Body", CubeListBuilder.create(), PartPose.offset(0.0F, 1.8361F, 0.65F));

        PartDefinition Glide_torse = Body.addOrReplaceChild("Glide_torse", CubeListBuilder.create().texOffs(48, 30).addBox(-3.5F, -4.5F, -8.5F, 7.0F, 7.0F, 17.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 0.5F));

        PartDefinition Head = Body.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(34, 71).addBox(-3.5F, -3.5805F, -5.9571F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(90, 77).addBox(-3.5F, -6.5805F, -1.9571F, 7.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 90).addBox(3.5F, -3.5805F, -1.9571F, 7.0F, 7.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(90, 70).addBox(-10.5F, -3.5805F, -1.9571F, 7.0F, 7.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(24, 85).addBox(-3.5F, -3.5805F, 1.0429F, 7.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.4195F, -8.0429F));

        PartDefinition cube_r1 = Head.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(42, 56).addBox(-3.0F, -2.5F, 0.0F, 3.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 0.9195F, -3.9571F, 0.0F, 0.7854F, 0.0F));

        PartDefinition cube_r2 = Head.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(42, 61).addBox(0.0F, -2.5F, 0.0F, 3.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 0.9195F, -3.9571F, 0.0F, -0.7854F, 0.0F));

        PartDefinition Eye1 = Head.addOrReplaceChild("Eye1", CubeListBuilder.create().texOffs(24, 83).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.525F, -0.0805F, -5.4821F));

        PartDefinition Eye2 = Head.addOrReplaceChild("Eye2", CubeListBuilder.create().texOffs(94, 87).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(2.525F, -0.0805F, -5.4821F));

        PartDefinition Ear1 = Head.addOrReplaceChild("Ear1", CubeListBuilder.create(), PartPose.offset(3.5F, -0.5805F, -1.9571F));

        PartDefinition Ear2 = Head.addOrReplaceChild("Ear2", CubeListBuilder.create(), PartPose.offset(-3.5F, -0.5805F, -1.9571F));

        PartDefinition Mouth = Head.addOrReplaceChild("Mouth", CubeListBuilder.create().texOffs(58, 93).addBox(-1.0F, -3.0F, 2.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(66, 29).addBox(0.0F, -1.25F, 2.25F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 3.4195F, -9.9571F));

        PartDefinition bone18 = Mouth.addOrReplaceChild("bone18", CubeListBuilder.create().texOffs(94, 84).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(64, 29).addBox(-0.5F, -0.75F, -1.75F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -1.0F, 4.0F));

        PartDefinition Grab = bone18.addOrReplaceChild("Grab", CubeListBuilder.create().texOffs(96, 86).addBox(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -2.0F));

        PartDefinition bone22 = bone18.addOrReplaceChild("bone22", CubeListBuilder.create().texOffs(94, 89).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.25F, 0.5F));

        PartDefinition bone2 = Mouth.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(94, 84).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(64, 29).addBox(-0.5F, -0.75F, -1.75F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -1.0F, 4.0F));

        PartDefinition bone3 = bone2.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(94, 89).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.25F, 0.5F));

        PartDefinition Tail = Body.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offset(-0.5F, -3.0F, 9.0F));

        PartDefinition bone6 = Tail.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(62, 83).addBox(-3.5F, -12.0833F, 9.0F, 7.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(48, 54).addBox(-3.5F, -17.0833F, 2.0F, 7.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(0, 77).addBox(-3.5F, -12.0833F, 2.0F, 7.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(62, 71).addBox(-3.5F, -4.0833F, 0.0F, 7.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 1.0833F, 0.0F));

        PartDefinition bone12 = Tail.addOrReplaceChild("bone12", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -1.0F, 7.0F, 5.0F, 25.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, 1.0F));

        PartDefinition Torse = Body.addOrReplaceChild("Torse", CubeListBuilder.create().texOffs(0, 30).addBox(-3.5F, -4.5F, -8.5F, 7.0F, 9.0F, 17.0F, new CubeDeformation(0.0F))
                .texOffs(0, 56).addBox(3.5F, 4.5F, -8.5F, 0.0F, 4.0F, 17.0F, new CubeDeformation(0.0F))
                .texOffs(64, 0).addBox(-3.5F, 4.5F, -8.5F, 0.0F, 4.0F, 17.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 0.5F));

        PartDefinition Leg3 = squirrel.addOrReplaceChild("Leg3", CubeListBuilder.create().texOffs(34, 56).addBox(-1.0F, 0.0167F, -1.0F, 2.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(3.5F, -1.1806F, -5.35F));

        PartDefinition bone11 = Leg3.addOrReplaceChild("bone11", CubeListBuilder.create().texOffs(68, 93).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0167F, -1.0F));

        PartDefinition bone16 = Leg3.addOrReplaceChild("bone16", CubeListBuilder.create().texOffs(58, 85).addBox(0.0F, -4.0F, -0.5F, 0.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 12.0167F, -0.5F, 0.0F, 0.0F, 0.3927F));

        PartDefinition Leg4 = squirrel.addOrReplaceChild("Leg4", CubeListBuilder.create().texOffs(86, 83).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.5F, -1.1639F, -5.35F));

        PartDefinition bone10 = Leg4.addOrReplaceChild("bone10", CubeListBuilder.create().texOffs(94, 80).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, -1.0F));

        PartDefinition bone17 = Leg4.addOrReplaceChild("bone17", CubeListBuilder.create().texOffs(60, 85).addBox(0.0F, -4.0F, -0.5F, 0.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 12.0F, -0.5F, 0.0F, 0.0F, -0.3927F));

        PartDefinition Leg = squirrel.addOrReplaceChild("Leg", CubeListBuilder.create().texOffs(44, 85).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(78, 21).addBox(-1.5F, 0.0F, 2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, -1.1639F, 1.65F));

        PartDefinition Toe1 = Leg.addOrReplaceChild("Toe1", CubeListBuilder.create().texOffs(92, 21).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, -2.0F));

        PartDefinition Leg2 = squirrel.addOrReplaceChild("Leg2", CubeListBuilder.create().texOffs(86, 54).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(64, 21).addBox(-1.5F, 0.0F, 2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -1.1639F, 1.65F));

        PartDefinition Toe2 = Leg2.addOrReplaceChild("Toe2", CubeListBuilder.create().texOffs(92, 25).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, -2.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(SquirrelEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        squirrel.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart root() {
        return squirrel;
    }

}
