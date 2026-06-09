package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.client.model.HierarchicalModel;
import com.evirapo.diversityofcritters.client.animations.CivetBabyAnims;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.AnimationState;
import org.joml.Vector3f;

public class SquirrelBabyModel  <T extends SquirrelEntity> extends HierarchicalModel<T>  {

    private final ModelPart Bandedpalmcivetkit;
    private final ModelPart Torse;
    private final ModelPart Head;
    private final ModelPart bone;
    private final ModelPart bone3;
    private final ModelPart eye1;
    private final ModelPart eye2;
    private final ModelPart bone4;
    private final ModelPart bone2;
    private final ModelPart bone5;
    private final ModelPart bone6;
    private final ModelPart bone7;
    private final ModelPart bone8;

    public SquirrelBabyModel(ModelPart root) {
        this.Bandedpalmcivetkit = root.getChild("Bandedpalmcivetkit");
        this.Torse = this.Bandedpalmcivetkit.getChild("Torse");
        this.Head = this.Torse.getChild("Head");
        this.bone = this.Head.getChild("bone");
        this.bone3 = this.Head.getChild("bone3");
        this.eye1 = this.Head.getChild("eye1");
        this.eye2 = this.Head.getChild("eye2");
        this.bone4 = this.Torse.getChild("bone4");
        this.bone2 = this.Torse.getChild("bone2");
        this.bone5 = this.Bandedpalmcivetkit.getChild("bone5");
        this.bone6 = this.Bandedpalmcivetkit.getChild("bone6");
        this.bone7 = this.Bandedpalmcivetkit.getChild("bone7");
        this.bone8 = this.Bandedpalmcivetkit.getChild("bone8");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Bandedpalmcivetkit = partdefinition.addOrReplaceChild("Bandedpalmcivetkit", CubeListBuilder.create(), PartPose.offset(-0.0333F, 22.9167F, 0.1106F));

        PartDefinition Torse = Bandedpalmcivetkit.addOrReplaceChild("Torse", CubeListBuilder.create(), PartPose.offset(-0.1333F, -1.3333F, -0.0578F));

        PartDefinition Head = Torse.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 7).addBox(-1.5F, -1.5F, -2.9083F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1667F, -0.5833F, -2.6444F));

        PartDefinition bone = Head.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(1.5F, -0.5F, -1.4083F));

        PartDefinition cube_r1 = bone.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(12, 16).addBox(0.0F, -1.0F, -0.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));

        PartDefinition bone3 = Head.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offset(-1.5F, -0.5F, -1.4083F));

        PartDefinition cube_r2 = bone3.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(16, 13).addBox(0.0F, -1.0F, -0.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));

        PartDefinition eye1 = Head.addOrReplaceChild("eye1", CubeListBuilder.create().texOffs(14, 2).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(1.025F, 0.0F, -2.4333F));

        PartDefinition eye2 = Head.addOrReplaceChild("eye2", CubeListBuilder.create().texOffs(14, 4).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.025F, 0.0F, -2.4333F));

        PartDefinition bone4 = Torse.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(12, 7).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1667F, -0.0833F, 2.4472F));

        PartDefinition bone2 = Torse.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1667F, -0.0833F, -0.0528F));

        PartDefinition bone5 = Bandedpalmcivetkit.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.7833F, -1.9167F, -1.1106F));

        PartDefinition bone6 = Bandedpalmcivetkit.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(4, 13).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.7167F, -1.9167F, -1.1106F));

        PartDefinition bone7 = Bandedpalmcivetkit.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(14, 0).addBox(-0.5F, 1.0F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(12, 13).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.7167F, -1.9167F, 0.8894F));

        PartDefinition bone8 = Bandedpalmcivetkit.addOrReplaceChild("bone8", CubeListBuilder.create().texOffs(8, 13).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(8, 16).addBox(-0.5F, 1.0F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.7833F, -1.9167F, 0.8894F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void setupAnim(SquirrelEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Bandedpalmcivetkit.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart root() {
        return Bandedpalmcivetkit;
    }
}
