package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.client.animations.SquirrelAnims;
import com.evirapo.diversityofcritters.client.locomotion.ILocomotionModel;
import com.evirapo.diversityofcritters.client.locomotion.LocomotionRig;
import com.evirapo.diversityofcritters.client.locomotion.QuadrupedRig;
import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import net.minecraft.client.Minecraft;
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
import org.joml.Vector3f;

public class SquirrelModel<T extends SquirrelEntity> extends HierarchicalModel<T> implements ILocomotionModel<T> {

    private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();
    /** The model is drawn at half size, like the civet's. */
    private static final float MODEL_SCALE = 0.5F;

    private final LocomotionRig<T> locomotionRig;

    // Flags para renderToBuffer — se actualizan en setupAnim cada frame.
    private boolean isClimbingRender = false;
    private boolean isClimbingDownRender = false;

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
        this.squirrel    = root.getChild("Giant flying squirrel");
        this.Glide       = this.squirrel.getChild("Glide");
        this.Body        = this.squirrel.getChild("Body");
        this.Glide_torse = this.Body.getChild("Glide_torse");
        this.Head        = this.Body.getChild("Head");
        this.Eye1        = this.Head.getChild("Eye1");
        this.Eye2        = this.Head.getChild("Eye2");
        this.Ear1        = this.Head.getChild("Ear1");
        this.Ear2        = this.Head.getChild("Ear2");
        this.Mouth       = this.Head.getChild("Mouth");
        this.bone18      = this.Mouth.getChild("bone18");
        this.Grab        = this.bone18.getChild("Grab");
        this.bone22      = this.bone18.getChild("bone22");
        this.bone2       = this.Mouth.getChild("bone2");
        this.bone3       = this.bone2.getChild("bone3");
        this.Tail        = this.Body.getChild("Tail");
        this.bone6       = this.Tail.getChild("bone6");
        this.bone12      = this.Tail.getChild("bone12");
        this.Torse       = this.Body.getChild("Torse");
        this.Leg3        = this.squirrel.getChild("Leg3");
        this.bone11      = this.Leg3.getChild("bone11");
        this.bone16      = this.Leg3.getChild("bone16");
        this.Leg4        = this.squirrel.getChild("Leg4");
        this.bone10      = this.Leg4.getChild("bone10");
        this.bone17      = this.Leg4.getChild("bone17");
        this.Leg         = this.squirrel.getChild("Leg");
        this.Toe1        = this.Leg.getChild("Toe1");
        this.Leg2        = this.squirrel.getChild("Leg2");
        this.Toe2        = this.Leg2.getChild("Toe2");

        // The legs hang straight from the root; their feet and toes ride along rigidly.
        // Front left/right, rear left/right; the first leads the grips of a mantle.
        this.locomotionRig = QuadrupedRig.<T, SquirrelModel<T>>builder(this)
                .pelvis("root")
                .legs("Leg4", "Leg3", "Leg", "Leg2")
                // Starting values, to be tuned by eye with the overlay (F6) and bypass (F9).
                .stabilise("Head", 0.6F)
                .lag("Tail", 0.1F, 0.3F, 35.0F)
                .arch("Head", 0.5F)
                .arch("Tail", -0.6F)
                .mantle(MantleProfile.SQUIRREL)
                .build();
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition squirrel = partdefinition.addOrReplaceChild("Giant flying squirrel",
                CubeListBuilder.create(), PartPose.offset(0.0F, 13.1639F, 0.35F));

        PartDefinition Glide = squirrel.addOrReplaceChild("Glide",
                CubeListBuilder.create()
                        .texOffs(19, 118).addBox(-23.5F, -3.0F, -13.1F, 47.0F, 2.0F, 32.0F, new CubeDeformation(0.0F))
                        .texOffs(42, 66) .addBox(15.5F, -1.0F, -13.1F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(78, 93) .addBox(-17.5F, -1.0F, -13.1F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(24, 77) .addBox(-23.5F, -1.0F, 15.9F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(14, 90) .addBox(21.5F, -1.0F, 15.9F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 2.8361F, 6.75F));

        PartDefinition Body = squirrel.addOrReplaceChild("Body",
                CubeListBuilder.create(), PartPose.offset(0.0F, 1.8361F, 0.65F));

        PartDefinition Glide_torse = Body.addOrReplaceChild("Glide_torse",
                CubeListBuilder.create()
                        .texOffs(48, 30).addBox(-3.5F, -4.5F, -8.5F, 7.0F, 7.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.5F, 0.5F));

        PartDefinition Head = Body.addOrReplaceChild("Head",
                CubeListBuilder.create()
                        .texOffs(34, 71).addBox(-3.5F, -3.5805F, -5.9571F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
                        .texOffs(90, 77).addBox(-3.5F, -6.5805F, -1.9571F, 7.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 90) .addBox(3.5F, -3.5805F, -1.9571F, 7.0F, 7.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(90, 70).addBox(-10.5F, -3.5805F, -1.9571F, 7.0F, 7.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(24, 85).addBox(-3.5F, -3.5805F, 1.0429F, 7.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -4.4195F, -8.0429F));

        PartDefinition cube_r1 = Head.addOrReplaceChild("cube_r1",
                CubeListBuilder.create().texOffs(42, 56).addBox(-3.0F, -2.5F, 0.0F, 3.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.5F, 0.9195F, -3.9571F, 0.0F, 0.7854F, 0.0F));

        PartDefinition cube_r2 = Head.addOrReplaceChild("cube_r2",
                CubeListBuilder.create().texOffs(42, 61).addBox(0.0F, -2.5F, 0.0F, 3.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.5F, 0.9195F, -3.9571F, 0.0F, -0.7854F, 0.0F));

        PartDefinition Eye1 = Head.addOrReplaceChild("Eye1",
                CubeListBuilder.create().texOffs(24, 83).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.525F, -0.0805F, -5.4821F));

        PartDefinition Eye2 = Head.addOrReplaceChild("Eye2",
                CubeListBuilder.create().texOffs(94, 87).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.525F, -0.0805F, -5.4821F));

        Head.addOrReplaceChild("Ear1", CubeListBuilder.create(), PartPose.offset(3.5F, -0.5805F, -1.9571F));
        Head.addOrReplaceChild("Ear2", CubeListBuilder.create(), PartPose.offset(-3.5F, -0.5805F, -1.9571F));

        PartDefinition Mouth = Head.addOrReplaceChild("Mouth",
                CubeListBuilder.create()
                        .texOffs(58, 93).addBox(-1.0F, -3.0F, 2.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(66, 29).addBox(0.0F, -1.25F, 2.25F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-0.5F, 3.4195F, -9.9571F));

        PartDefinition bone18 = Mouth.addOrReplaceChild("bone18",
                CubeListBuilder.create()
                        .texOffs(94, 84).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 29).addBox(-0.5F, -0.75F, -1.75F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.5F, -1.0F, 4.0F));

        bone18.addOrReplaceChild("Grab",
                CubeListBuilder.create().texOffs(96, 86).addBox(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -2.0F));

        bone18.addOrReplaceChild("bone22",
                CubeListBuilder.create().texOffs(94, 89).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.25F, 0.5F));

        PartDefinition bone2 = Mouth.addOrReplaceChild("bone2",
                CubeListBuilder.create()
                        .texOffs(94, 84).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 29).addBox(-0.5F, -0.75F, -1.75F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.5F, -1.0F, 4.0F));

        bone2.addOrReplaceChild("bone3",
                CubeListBuilder.create().texOffs(94, 89).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.25F, 0.5F));

        PartDefinition Tail = Body.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offset(-0.5F, -3.0F, 9.0F));

        Tail.addOrReplaceChild("bone6",
                CubeListBuilder.create()
                        .texOffs(62, 83).addBox(-3.5F, -12.0833F, 9.0F, 7.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(48, 54).addBox(-3.5F, -17.0833F, 2.0F, 7.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 77) .addBox(-3.5F, -12.0833F, 2.0F, 7.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(62, 71).addBox(-3.5F, -4.0833F, 0.0F, 7.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.5F, 1.0833F, 0.0F));

        Tail.addOrReplaceChild("bone12",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -1.0F, 7.0F, 5.0F, 25.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 3.0F, 1.0F));

        Body.addOrReplaceChild("Torse",
                CubeListBuilder.create()
                        .texOffs(0, 30) .addBox(-3.5F, -4.5F, -8.5F, 7.0F, 9.0F, 17.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 56) .addBox(3.5F, 4.5F, -8.5F, 0.0F, 4.0F, 17.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 0) .addBox(-3.5F, 4.5F, -8.5F, 0.0F, 4.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.5F, 0.5F));

        PartDefinition Leg3 = squirrel.addOrReplaceChild("Leg3",
                CubeListBuilder.create().texOffs(34, 56).addBox(-1.0F, 0.0167F, -1.0F, 2.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.5F, -1.1806F, -5.35F));
        Leg3.addOrReplaceChild("bone11",
                CubeListBuilder.create().texOffs(68, 93).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 11.0167F, -1.0F));
        Leg3.addOrReplaceChild("bone16",
                CubeListBuilder.create().texOffs(58, 85).addBox(0.0F, -4.0F, -0.5F, 0.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(1.0F, 12.0167F, -0.5F, 0.0F, 0.0F, 0.3927F));

        PartDefinition Leg4 = squirrel.addOrReplaceChild("Leg4",
                CubeListBuilder.create().texOffs(86, 83).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.5F, -1.1639F, -5.35F));
        Leg4.addOrReplaceChild("bone10",
                CubeListBuilder.create().texOffs(94, 80).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 11.0F, -1.0F));
        Leg4.addOrReplaceChild("bone17",
                CubeListBuilder.create().texOffs(60, 85).addBox(0.0F, -4.0F, -0.5F, 0.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 12.0F, -0.5F, 0.0F, 0.0F, -0.3927F));

        PartDefinition Leg = squirrel.addOrReplaceChild("Leg",
                CubeListBuilder.create()
                        .texOffs(44, 85).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(78, 21).addBox(-1.5F, 0.0F, 2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-4.0F, -1.1639F, 1.65F));
        Leg.addOrReplaceChild("Toe1",
                CubeListBuilder.create().texOffs(92, 21).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 11.0F, -2.0F));

        PartDefinition Leg2 = squirrel.addOrReplaceChild("Leg2",
                CubeListBuilder.create()
                        .texOffs(86, 54).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 21).addBox(-1.5F, 0.0F, 2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(4.0F, -1.1639F, 1.65F));
        Leg2.addOrReplaceChild("Toe2",
                CubeListBuilder.create().texOffs(92, 25).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 11.0F, -2.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
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
        // (sleeping, gliding, climbing): that is what lets it fade out instead of snapping off.
        this.locomotionRig.apply(entity, Minecraft.getInstance().getPartialTick());
    }

    private void animatePose(T entity, float limbSwing, float limbSwingAmount,
                             float ageInTicks, float netHeadYaw, float headPitch) {

        boolean sleepingLike    = entity.getSleepState() != DiverseCritter.SleepState.AWAKE;
        boolean gliding         = entity.isGliding();
        boolean climbing        = entity.isClimbing();
        boolean swimming        = entity.isInWaterOrBubble();
        boolean orderedSitPlaying = entity.isOrderedSitPlaying();

        float headScale = entity.isJuvenile() ? 1.05F : 1.0F;
        this.Head.xScale = headScale;
        this.Head.yScale = headScale;
        this.Head.zScale = headScale;

        if (this.young) {
            this.applyStatic(SquirrelAnims.BABY);
        }

        this.animate(entity.preparingSleepState, SquirrelAnims.preparing_sleep, ageInTicks, 1f);
        this.animate(entity.sleepState,          SquirrelAnims.Sleep,           ageInTicks, 1f);
        this.animate(entity.awakeningState,      SquirrelAnims.awakening,       ageInTicks, 1f);

        if (sleepingLike) {
            this.Glide.xScale       = 0.0F; this.Glide.yScale       = 0.0F; this.Glide.zScale       = 0.0F;
            this.Glide_torse.xScale = 0.0F; this.Glide_torse.yScale = 0.0F; this.Glide_torse.zScale = 0.0F;
            this.isClimbingRender     = false;
            this.isClimbingDownRender = false;
            return;
        }

        this.isClimbingRender     = climbing && !gliding;
        this.isClimbingDownRender = entity.isClimbingDown() && !gliding;

        if (!climbing && !gliding && !orderedSitPlaying) {
            this.Head.xRot = headPitch  * ((float) Math.PI / 180F);
            this.Head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        }

        if (gliding) {
            this.animate(entity.glideState, SquirrelAnims.GLIDING, ageInTicks, 1.0F);
            return;
        }

        // ─── CLIMB ───────────────────────────────────────────────────────────
        if (climbing) {
            this.animate(entity.climbingUpState,   SquirrelAnims.CLIMBING_UP,   ageInTicks, 1.0F);
            this.animate(entity.climbingDownState, SquirrelAnims.CLIMBING_DOWN, ageInTicks, 1.0F);
            this.animate(entity.climbIdleState,    SquirrelAnims.CLIMB_IDLE,    ageInTicks, 1.0F);
            if (entity.climbingUpState.isStarted()) {
                // bone12's pivot lands on the wall surface after the global -90° X rotation.
                // Moving Tail -3 in model Y pushes it +3 world-Z (away from wall), clearing the geometry.
                this.Tail.y -= 3.0F;
            }
        }
        else if (swimming) {
            float swimSpeed = 1.0F + limbSwingAmount;
            this.animate(entity.swimAnimationState, SquirrelAnims.Swim, ageInTicks, swimSpeed);
            this.squirrel.y += 4.0F;
            this.Head.xRot -= 0.3F;
        }
        else {
            boolean busyBody = entity.isCleaning()
                    || entity.IsDrinking()
                    || entity.drinkEndingState.isStarted()
                    || entity.cleanEndingState.isStarted()
                    || entity.eatPlantEndingState.isStarted()
                    || entity.isVariantActive
                    || entity.isEatingPlant();

            float moveFraction = Math.min(1.0F, limbSwingAmount * 4.0F);
            float idleWeight   = busyBody ? 0.0F : (1.0F - moveFraction) * (1.0F - moveFraction);
            // A mantle poses the legs itself; the walk cycle steps aside for it.
            float cycleWeight  = 1.0F - this.locomotionRig.mantleAuthority(entity);
            float sprintWeight = entity.sprintProgress * cycleWeight;
            float walkWeight   = (1.0F - entity.sprintProgress) * cycleWeight;

            if (idleWeight > 0.01F) {
                animateWithWeight(entity.idleAnimationState, SquirrelAnims.Idle, ageInTicks, idleWeight);
            }
            if (walkWeight > 0.01F) {
                animateWalkWithWeight(SquirrelAnims.Walk, limbSwing, limbSwingAmount, 2.0F, 2.5F, walkWeight);
            }
            if (sprintWeight > 0.01F) {
                animateWalkWithWeight(SquirrelAnims.Run, limbSwing, limbSwingAmount, 2.0F, 2.5F, sprintWeight);
            }

            this.animate(entity.idleStandUpState,    SquirrelAnims.stand_up,    ageInTicks, 1.0F);
            this.animate(entity.idleSniffLeftState,  SquirrelAnims.SniffLeft,   ageInTicks, 1.0F);
            this.animate(entity.idleSniffRightState, SquirrelAnims.SniffRight,  ageInTicks, 1.0F);

            this.animate(entity.idleSitStartingState, SquirrelAnims.SIT_STARTING, ageInTicks, 1.0F);
            this.animate(entity.idleSitState,         SquirrelAnims.SIT_IDLE,     ageInTicks, 1.0F);
            this.animate(entity.idleSitEndingState,   SquirrelAnims.SIT_ENDING,   ageInTicks, 1.0F);

            this.animate(entity.idleLayStartingState, SquirrelAnims.LAY_STARTING, ageInTicks, 1.0F);
            this.animate(entity.idleLayState,         SquirrelAnims.LAY_IDLE,     ageInTicks, 1.0F);
            this.animate(entity.idleLayEndingState,   SquirrelAnims.LAY_ENDING,   ageInTicks, 1.0F);

            this.animate(entity.eatPlantStartingState, SquirrelAnims.EATING_STARTING, ageInTicks, 1.0F);
            this.animate(entity.eatPlantIdleState,     SquirrelAnims.EATING_IDLE,     ageInTicks, 1.0F);
            this.animate(entity.eatPlantEndingState,   SquirrelAnims.EATING_ENDING,   ageInTicks, 1.0F);
        }

        this.animate(entity.cleanStartingState, SquirrelAnims.GROOMING_STARTING, ageInTicks, 1.0F);
        this.animate(entity.cleanIdleState,     SquirrelAnims.GROOMING_IDLE,     ageInTicks, 1.0F);
        this.animate(entity.cleanEndingState,   SquirrelAnims.GROOMING_ENDING,   ageInTicks, 1.0F);

        this.animate(entity.drinkStartingState, SquirrelAnims.DRINK_STARTING, ageInTicks, 1.0F);
        this.animate(entity.drinkIdleState,     SquirrelAnims.DRINK_IDLE,     ageInTicks, 1.0F);
        this.animate(entity.drinkEndingState,   SquirrelAnims.DRINK_ENDING,   ageInTicks, 1.0F);

        this.animate(entity.orderedSitStartingState, SquirrelAnims.SIT_STARTING, ageInTicks, 1.0F);
        this.animate(entity.orderedSitIdleState,     SquirrelAnims.SIT_IDLE,     ageInTicks, 1.0F);
        this.animate(entity.orderedSitEndingState,   SquirrelAnims.SIT_ENDING,   ageInTicks, 1.0F);

        this.Glide.xScale       = 0.0F; this.Glide.yScale       = 0.0F; this.Glide.zScale       = 0.0F;
        this.Glide_torse.xScale = 0.0F; this.Glide_torse.yScale = 0.0F; this.Glide_torse.zScale = 0.0F;
        if (!entity.climbingUpState.isStarted()) {
            this.bone12.xScale  = 0.0F; this.bone12.yScale      = 0.0F; this.bone12.zScale      = 0.0F;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RENDER
    // ─────────────────────────────────────────────────────────────────────────

    public void translateToBone11(PoseStack poseStack) {
        this.applyRootTransform(poseStack);
        this.squirrel.translateAndRotate(poseStack);
        this.Leg3.translateAndRotate(poseStack);
        this.bone11.translateAndRotate(poseStack);
    }

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

        squirrel.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);

        poseStack.popPose();
    }

    @Override
    public ModelPart root() {
        return squirrel;
    }

    /**
     * Shared with the locomotion rig and the leaf layer, so bones are placed in the world exactly
     * where they are drawn.
     */
    @Override
    public void applyRootTransform(PoseStack poseStack) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0F, 1.5F, 0.0F);
    }

    @Override
    public float rendererScale(T entity) {
        return entity.growthScale();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void animateWithWeight(net.minecraft.world.entity.AnimationState state,
                                   AnimationDefinition definition,
                                   float ageInTicks, float weight) {
        state.updateTime(ageInTicks, 1.0F);
        state.ifStarted(s -> KeyframeAnimations.animate(
                this, definition, s.getAccumulatedTime(), weight, ANIMATION_VECTOR_CACHE));
    }

    private void animateWalkWithWeight(AnimationDefinition definition,
                                       float limbSwing, float limbSwingAmount,
                                       float speed, float intensity, float weight) {
        long time       = (long) (limbSwing * 50.0F * speed);
        float finalScale = Math.min(limbSwingAmount * intensity, 1.0F) * weight;
        KeyframeAnimations.animate(this, definition, time, finalScale, ANIMATION_VECTOR_CACHE);
    }
}
