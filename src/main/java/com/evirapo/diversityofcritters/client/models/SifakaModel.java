package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.darkblade.deluxelib.anim.Animation;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.deluxelib.client.anim.AnimBlend;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.component.LookAtAdditive;

import java.util.Set;

public class SifakaModel<T extends SifakaEntity> extends HierarchicalModel<T> {
	/** The head follows what it looks at only in these; any other pose owns the head. */
	private static final Set<String> HEAD_TRACKING_ANIMS = Set.of("idle", "walk", "run", "ordered_sit_loop");

	private final LookAtAdditive<T, SifakaModel<T>> lookAt =
			new LookAtAdditive<T, SifakaModel<T>>(m -> m.Head, 75.0F, 40.0F)
					.enabledWhile(entity -> {
						Animation current = entity.animator().getCurrent(0);
						return current != MobAnimator.EMPTY
								&& HEAD_TRACKING_ANIMS.contains(current.getName());
					});

	private final ModelPart meshRoot;
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
		this.meshRoot = root;
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
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		float headScale = entity.isJuvenile() ? 1.05F : 1.0F;
		this.Head.xScale = headScale;
		this.Head.yScale = headScale;
		this.Head.zScale = headScale;

		AnimBlend.applyBlended(this, entity.animator(), ageInTicks, 200L, 0);
		this.lookAt.apply(entity, this, AnimContext.of(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch));

		// Render offset, not a keyframe: the swim clip is authored on land height.
		if (entity.isInWaterOrBubble() && !entity.isClimbing()
				&& entity.getSleepState() == DiverseCritter.SleepState.AWAKE) {
			this.Thewholesifakamodel.y += 4.0F;
			this.Head.xRot -= 0.3F;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		poseStack.pushPose();

		float baseScale = 0.5f;
		poseStack.scale(baseScale, baseScale, baseScale);
		poseStack.translate(0, 1.5F, 0);

		Thewholesifakamodel.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);

		poseStack.popPose();
	}

	@Override
	public ModelPart root() {
		return meshRoot;
	}

	public void translateToRightHand(com.mojang.blaze3d.vertex.PoseStack poseStack) {
		poseStack.scale(0.5f, 0.5f, 0.5f);
		poseStack.translate(0.0, 1.5, 0.0);
		this.Thewholesifakamodel.translateAndRotate(poseStack);
		this.Body.translateAndRotate(poseStack);
		this.Rightarm.translateAndRotate(poseStack);
		poseStack.translate(0.45, 0.0, -0.45);
	}
}
