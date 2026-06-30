package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.client.animations.SifakaAnims;
import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
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

public class SifakaModel<T extends SifakaEntity> extends HierarchicalModel<T> {
	private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();

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
	public void setupAnim(SifakaEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		boolean sleepingLike      = entity.getSleepState() != DiverseCritter.SleepState.AWAKE;
		boolean orderedSitPlaying = entity.isOrderedSitPlaying();

		if (entity.isJuvenile()) {
			this.Head.xScale = 1.05F;
			this.Head.yScale = 1.05F;
			this.Head.zScale = 1.05F;
		} else {
			this.Head.xScale = 1.0F;
			this.Head.yScale = 1.0F;
			this.Head.zScale = 1.0F;
		}

		this.animate(entity.preparingSleepState, SifakaAnims.preparing_sleep, ageInTicks, 1f);
		this.animate(entity.sleepState,          SifakaAnims.Sleep,           ageInTicks, 1f);
		this.animate(entity.awakeningState,      SifakaAnims.awakening,       ageInTicks, 1f);

		if (sleepingLike) {
			limbSwing = 0f;
			limbSwingAmount = 0f;
			return;
		}

		boolean climbing = entity.isClimbing();

		if (orderedSitPlaying) {
			limbSwing = 0f;
			limbSwingAmount = 0f;
			this.Head.xRot = headPitch * ((float)Math.PI / 180F);
			this.Head.yRot = netHeadYaw * ((float)Math.PI / 180F);
		}

		if (!climbing && (entity.idleAnimationState.isStarted() || limbSwingAmount > 0.01f)) {
			this.Head.xRot = headPitch * ((float)Math.PI / 180F);
			this.Head.yRot = netHeadYaw * ((float)Math.PI / 180F);
		}

		if (climbing) {
			this.animate(entity.climbingUpState,   SifakaAnims.CLIMBING_UP,   ageInTicks, 1.0F);
			this.animate(entity.climbingDownState, SifakaAnims.CLIMBING_DOWN, ageInTicks, 1.0F);
			this.animate(entity.climbIdleState,    SifakaAnims.CLIMB_IDLE,    ageInTicks, 1.0F);
		} else {
			if (entity.isInWaterOrBubble()) {
				float swimSpeedFactor = 1.0F + limbSwingAmount;
				this.animate(entity.swimAnimationState, SifakaAnims.Swim, ageInTicks, swimSpeedFactor);
				this.Thewholesifakamodel.y += 4.0F;
				this.Head.xRot -= 0.3F;
			} else {
				boolean busyBodyAnim = entity.isCleaning()
				                   || entity.IsDrinking()
				                   || entity.isVariantActive;

				float moveFraction = Math.min(1.0F, limbSwingAmount * 4.0F);
				float idleWeight   = busyBodyAnim ? 0.0F : (1.0F - moveFraction) * (1.0F - moveFraction);
				float sprintWeight = entity.sprintProgress;
				float walkWeight   = 1.0F - sprintWeight;

				if (idleWeight > 0.01F) {
					this.animateWithWeight(entity.idleAnimationState, SifakaAnims.Idle, ageInTicks, idleWeight);
				}
				if (walkWeight > 0.01F) {
					this.animateHop(SifakaAnims.Walk, ageInTicks, limbSwingAmount, walkWeight);
				}
				if (sprintWeight > 0.01F) {
					this.animateHop(SifakaAnims.Run, ageInTicks, limbSwingAmount, sprintWeight);
				}

				this.animate(entity.idleStandUpState,    SifakaAnims.stand_up,    ageInTicks, 1.0F);
				this.animate(entity.idleSniffLeftState,  SifakaAnims.SniffRight,  ageInTicks, 1.0F);
				this.animate(entity.idleSniffRightState, SifakaAnims.SniffRight2, ageInTicks, 1.0F);

				this.animate(entity.idleSitStartingState, SifakaAnims.SIT_STARTING, ageInTicks, 1.0F);
				this.animate(entity.idleSitState,         SifakaAnims.SIT_IDLE,     ageInTicks, 1.0F);
				this.animate(entity.idleSitEndingState,   SifakaAnims.SIT_ENDING,   ageInTicks, 1.0F);

				this.animate(entity.idleLayStartingState, SifakaAnims.LAY_STARTING, ageInTicks, 1.0F);
				this.animate(entity.idleLayState,         SifakaAnims.LAY_IDLE,     ageInTicks, 1.0F);
				this.animate(entity.idleLayEndingState,   SifakaAnims.LAY_ENDING,   ageInTicks, 1.0F);
			}
		}

		this.animate(entity.cleanStartingState,   SifakaAnims.GROOMING_STARTING, ageInTicks, 1.0F);
		this.animate(entity.cleanIdleState,       SifakaAnims.GROOMING_IDLE,     ageInTicks, 1.0F);
		this.animate(entity.cleanEndingState,     SifakaAnims.GROOMING_ENDING,   ageInTicks, 1.0F);
		this.animate(entity.attackAnimationState, SifakaAnims.Attack,            ageInTicks, 1.0F);
		this.animate(entity.eatPlantStartingState,  SifakaAnims.EATING_STARTING,   ageInTicks, 1.0F);
		this.animate(entity.eatPlantIdleState,      SifakaAnims.EATING_IDLE,       ageInTicks, 1.0F);
		this.animate(entity.eatPlantEndingState,    SifakaAnims.EATING_ENDING,     ageInTicks, 1.0F);
		this.animate(entity.drinkStartingState,   SifakaAnims.DRINK_STARTING,    ageInTicks, 1.0F);
		this.animate(entity.drinkIdleState,       SifakaAnims.DRINK_IDLE,        ageInTicks, 1.0F);
		this.animate(entity.drinkEndingState,     SifakaAnims.DRINK_ENDING,      ageInTicks, 1.0F);

		this.animate(entity.orderedSitStartingState, SifakaAnims.SIT_STARTING, ageInTicks, 1.0F);
		this.animate(entity.orderedSitIdleState,     SifakaAnims.SIT_IDLE,     ageInTicks, 1.0F);
		this.animate(entity.orderedSitEndingState,   SifakaAnims.SIT_ENDING,   ageInTicks, 1.0F);
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

	private void animateWithWeight(AnimationState state, AnimationDefinition definition,
	                               float ageInTicks, float weight) {
		state.updateTime(ageInTicks, 1.0F);
		state.ifStarted(s -> KeyframeAnimations.animate(
				this, definition, s.getAccumulatedTime(), weight, ANIMATION_VECTOR_CACHE));
	}

	private void animateHop(AnimationDefinition definition, float ageInTicks, float limbSwingAmount, float weight) {
		long time        = (long)(ageInTicks * 50.0F * 0.5F);
		float finalScale = Math.min(limbSwingAmount * 2.5F, 1.0F) * weight;
		KeyframeAnimations.animate(this, definition, time, finalScale, ANIMATION_VECTOR_CACHE);
	}
}
