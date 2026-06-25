package com.evirapo.diversityofcritters.client.models;// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.evirapo.diversityofcritters.client.animations.CivetAnims;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
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

public class SifakaModel<T extends CivetEntity> extends HierarchicalModel<T> {

	private final ModelPart Thewholesifakamodel;
	private final ModelPart Rightleg;
	private final ModelPart Rightthumb;
	private final ModelPart rightfingers;
	private final ModelPart Leftleg;
	private final ModelPart leftthumb;
	private final ModelPart Leftfingers;
	private final ModelPart Body;
	private final ModelPart Head;
	private final ModelPart Eyes;
	private final ModelPart Rightear;
	private final ModelPart Leftear;
	private final ModelPart Mouth;
	private final ModelPart Topmouth;
	private final ModelPart Bottomjaw;
	private final ModelPart Tongue;
	private final ModelPart Torse;
	private final ModelPart Tail;
	private final ModelPart Tailnormal;
	private final ModelPart Tailcurl;
	private final ModelPart Leftarm;
	private final ModelPart Rightarm;

	public SifakaModel(ModelPart root) {
		this.Thewholesifakamodel = root.getChild("Thewholesifakamodel");
		this.Rightleg = this.Thewholesifakamodel.getChild("Rightleg");
		this.Rightthumb = this.Rightleg.getChild("Rightthumb");
		this.rightfingers = this.Rightleg.getChild("rightfingers");
		this.Leftleg = this.Thewholesifakamodel.getChild("Leftleg");
		this.leftthumb = this.Leftleg.getChild("leftthumb");
		this.Leftfingers = this.Leftleg.getChild("Leftfingers");
		this.Body = this.Thewholesifakamodel.getChild("Body");
		this.Head = this.Body.getChild("Head");
		this.Eyes = this.Head.getChild("Eyes");
		this.Rightear = this.Head.getChild("Rightear");
		this.Leftear = this.Head.getChild("Leftear");
		this.Mouth = this.Head.getChild("Mouth");
		this.Topmouth = this.Mouth.getChild("Topmouth");
		this.Bottomjaw = this.Mouth.getChild("Bottomjaw");
		this.Tongue = this.Bottomjaw.getChild("Tongue");
		this.Torse = this.Body.getChild("Torse");
		this.Tail = this.Torse.getChild("Tail");
		this.Tailnormal = this.Tail.getChild("Tailnormal");
		this.Tailcurl = this.Tail.getChild("Tailcurl");
		this.Leftarm = this.Body.getChild("Leftarm");
		this.Rightarm = this.Body.getChild("Rightarm");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition Thewholesifakamodel = partdefinition.addOrReplaceChild("Thewholesifakamodel", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition Rightleg = Thewholesifakamodel.addOrReplaceChild("Rightleg", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -1.5F, 4.0F, 12.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, -12.0F, 0.5F));

		PartDefinition Rightthumb = Rightleg.addOrReplaceChild("Rightthumb", CubeListBuilder.create().texOffs(25, 45).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 11.5F, -0.5F));

		PartDefinition rightfingers = Rightleg.addOrReplaceChild("rightfingers", CubeListBuilder.create().texOffs(16, 44).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 11.0F, -1.5F));

		PartDefinition Leftleg = Thewholesifakamodel.addOrReplaceChild("Leftleg", CubeListBuilder.create().texOffs(30, 30).addBox(-2.0F, 0.0F, -1.5F, 4.0F, 12.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, -12.0F, 0.5F));

		PartDefinition leftthumb = Leftleg.addOrReplaceChild("leftthumb", CubeListBuilder.create().texOffs(46, 13).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(1.5F, 11.5F, -0.5F));

		PartDefinition Leftfingers = Leftleg.addOrReplaceChild("Leftfingers", CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 11.0F, -1.5F));

		PartDefinition Body = Thewholesifakamodel.addOrReplaceChild("Body", CubeListBuilder.create(), PartPose.offset(0.375F, -19.25F, 0.5083F));

		PartDefinition Head = Body.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(20, 0).addBox(-4.5F, -7.0F, -3.0F, 9.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.375F, -7.75F, -0.5083F));

		PartDefinition Eyes = Head.addOrReplaceChild("Eyes", CubeListBuilder.create().texOffs(1, 59).addBox(1.55F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 54).addBox(-4.55F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.5F, -2.55F));

		PartDefinition Rightear = Head.addOrReplaceChild("Rightear", CubeListBuilder.create().texOffs(52, 0).addBox(0.0F, -0.5F, -1.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(53, 40).mirror().addBox(0.0F, -3.5F, 1.0F, 5.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(4.5F, -5.5F, 0.0F));

		PartDefinition Leftear = Head.addOrReplaceChild("Leftear", CubeListBuilder.create().texOffs(52, 6).addBox(-4.0F, -0.5F, -1.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(53, 36).addBox(-5.0F, -3.5F, 1.0F, 5.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.5F, -5.5F, 0.0F));

		PartDefinition Mouth = Head.addOrReplaceChild("Mouth", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, -3.0F));

		PartDefinition Topmouth = Mouth.addOrReplaceChild("Topmouth", CubeListBuilder.create().texOffs(44, 30).addBox(-1.5F, -1.0F, -3.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(13, 55).addBox(-1.25F, 1.0F, -2.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(13, 57).addBox(0.25F, 1.0F, -2.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition Bottomjaw = Mouth.addOrReplaceChild("Bottomjaw", CubeListBuilder.create().texOffs(48, 48).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, 0.0F));

		PartDefinition Tongue = Bottomjaw.addOrReplaceChild("Tongue", CubeListBuilder.create().texOffs(48, 53).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.5F));

		PartDefinition Torse = Body.addOrReplaceChild("Torse", CubeListBuilder.create().texOffs(0, 18).addBox(-6.0F, -17.0F, -2.0F, 9.0F, 15.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(1.125F, 9.25F, -0.5083F));

		PartDefinition Tail = Torse.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offset(-1.5F, -3.0F, 4.0F));

		PartDefinition Tailnormal = Tail.addOrReplaceChild("Tailnormal", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, 4.0F, 2.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(47, 51).addBox(0.0F, -2.5F, 12.0F, 0.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -4.0F));

		PartDefinition Tailcurl = Tail.addOrReplaceChild("Tailcurl", CubeListBuilder.create().texOffs(29, 50).addBox(-1.0F, -1.25F, 0.25F, 2.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(19, 52).addBox(-1.0F, 0.75F, 7.25F, 2.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.25F, -0.25F));

		PartDefinition Leftarm = Body.addOrReplaceChild("Leftarm", CubeListBuilder.create().texOffs(30, 24).addBox(-8.9833F, -1.0F, -2.0167F, 9.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(39, 40).addBox(-8.9833F, -1.0F, -7.0167F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(36, 13).addBox(-8.9833F, -1.0F, -10.0167F, 4.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.8917F, -6.75F, 0.5083F));

		PartDefinition Rightarm = Body.addOrReplaceChild("Rightarm", CubeListBuilder.create().texOffs(24, 18).addBox(-0.0167F, -1.0F, -2.0167F, 9.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(1, 40).addBox(4.9833F, -1.0F, -7.0167F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(14, 39).addBox(4.9833F, -1.0F, -10.0167F, 4.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(4.1417F, -6.75F, 0.5083F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(CivetEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		Thewholesifakamodel.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	@Override
	public ModelPart root() {
		return null;
	}
}