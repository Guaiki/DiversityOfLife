package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.client.animations.SquirrelAnims;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
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

public class SquirrelModel<T extends SquirrelEntity> extends HierarchicalModel<T> {

    /** Animaciones de la capa 0 durante las cuales la ardilla sigue al jugador con la cabeza. */
    private static final Set<String> HEAD_TRACKING_ANIMS = Set.of("idle", "walk", "run");

    /**
     * Seguimiento de cabeza como aditivo sobre los keyframes, en vez de la asignación directa que
     * había antes.
     *
     * <p>El gate es una <b>whitelist</b>: solo sigue durante locomoción normal. Antes era una lista
     * de excepciones a mano (`!climbing && !gliding && !orderedSitPlaying`) que ya se había quedado
     * corta — no excluía beber, acicalarse, comer ni las variantes de reposo, así que la ardilla te
     * seguía con la cabeza mientras bebía. Una whitelist falla del lado seguro: una cadena nueva no
     * hereda seguimiento por olvidarse de marcarla.
     *
     * <p>Cubre también trepar, planear, nadar y dormir sin nombrarlos: durante esos estados la capa
     * 0 está vacía, así que no hay animación actual y la whitelist no casa.
     *
     * <p>Los límites son los de vanilla ({@code getMaxHeadYRot} 75, {@code getMaxHeadXRot} 40), para
     * no cambiar el alcance del giro respecto a lo que había.
     */
    private final LookAtAdditive<T, SquirrelModel<T>> lookAt =
            new LookAtAdditive<T, SquirrelModel<T>>(m -> m.Head, 75.0F, 40.0F)
                    .enabledWhile(entity -> {
                        Animation current = entity.animator().getCurrent(0);
                        return current != MobAnimator.EMPTY
                                && HEAD_TRACKING_ANIMS.contains(current.getName());
                    });

    // Flags para renderToBuffer — se actualizan en setupAnim cada frame.
    private boolean isClimbingRender = false;
    private boolean isClimbingDownRender = false;

    private final ModelPart squirrel;
    private final ModelPart Glide;
    private final ModelPart Body;
    private final ModelPart Glide_torse;
    private final ModelPart Head;
    private final ModelPart Eyes;
    private final ModelPart Eye1;
    private final ModelPart Eye2;
    /** Mallas de ojo cerrado. Ver {@link #hideSleepEyes()}. */
    private final ModelPart Sleep_eye1;
    private final ModelPart Sleep_eye2;
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
        this.Eyes        = this.Head.getChild("Eyes");
        this.Eye1        = this.Eyes.getChild("Eye1");
        this.Eye2        = this.Eyes.getChild("Eye2");
        this.Sleep_eye1  = this.Eyes.getChild("Sleep_eye1");
        this.Sleep_eye2  = this.Eyes.getChild("Sleep_eye2");
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

        // Los cuatro ojos cuelgan de un grupo comun. Eye1/Eye2 son los abiertos y Sleep_eye1/2 los
        // cerrados: las animaciones no mueven parpados, intercambian la malla por escala. Al dormir,
        // `Sleep` aplasta los abiertos (yScale 0) y muestra los cerrados.
        PartDefinition Eyes = Head.addOrReplaceChild("Eyes",
                CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition Eye1 = Eyes.addOrReplaceChild("Eye1",
                CubeListBuilder.create().texOffs(24, 83).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.525F, -0.0805F, -5.4821F));

        PartDefinition Eye2 = Eyes.addOrReplaceChild("Eye2",
                CubeListBuilder.create().texOffs(94, 87).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.525F, -0.0805F, -5.4821F));

        PartDefinition Sleep_eye1 = Eyes.addOrReplaceChild("Sleep_eye1",
                CubeListBuilder.create().texOffs(13, 101).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.525F, -0.0805F, -5.4821F));

        PartDefinition Sleep_eye2 = Eyes.addOrReplaceChild("Sleep_eye2",
                CubeListBuilder.create().texOffs(1, 99).mirror().addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
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

        PartDefinition Tail = Body.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, 9.0F));

        Tail.addOrReplaceChild("bone6",
                CubeListBuilder.create()
                        .texOffs(62, 83).addBox(-3.5F, -12.0833F, 9.0F, 7.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(48, 54).addBox(-3.5F, -17.0833F, 2.0F, 7.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 77) .addBox(-3.5F, -12.0833F, 2.0F, 7.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(62, 71).addBox(-3.5F, -4.0833F, 0.0F, 7.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 1.0833F, 0.0F));

        Tail.addOrReplaceChild("bone12",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -1.0F, 7.0F, 5.0F, 25.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-0.5F, 3.0F, 1.0F));

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

    // El parámetro es T, no SquirrelEntity: con `T extends SquirrelEntity` las dos borran a lo mismo
    // y Java lo aceptaba generando un puente sintético, pero entonces `entity` no encaja donde se
    // espera un T (como en LookAtAdditive.apply).
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {

        this.root().getAllParts().forEach(ModelPart::resetPose);

        boolean sleepingLike = entity.getSleepState() != DiverseCritter.SleepState.AWAKE;
        boolean gliding      = entity.isGliding();
        boolean climbing     = entity.isClimbing();
        boolean swimming     = entity.isInWaterOrBubble();

        float headScale = entity.isJuvenile() ? 1.05F : 1.0F;
        this.Head.xScale = headScale;
        this.Head.yScale = headScale;
        this.Head.zScale = headScale;

        if (this.young) {
            this.applyStatic(SquirrelAnims.BABY);
        }

        // ─── ANIMACIÓN ───────────────────────────────────────────────────────
        // Una sola llamada, sin ramas: TODAS las animaciones de la ardilla adulta viven en la capa 0
        // del MobAnimator —locomoción, cadenas de actividad, variantes de reposo, trepada, planeo,
        // natación y dormir— y es la prioridad la que decide cuál se ve. Este modelo ya no elige
        // ninguna.
        AnimBlend.applyBlended(this, entity.animator(), ageInTicks, 200L, 0);

        // Aditivo, después de los keyframes. Su whitelist decide cuándo aplica (ver el campo).
        this.lookAt.apply(entity, this,
                AnimContext.of(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch));

        // ─── AJUSTES DE RENDER ───────────────────────────────────────────────
        // Lo único que queda aquí: visibilidad de partes, desplazamientos de pose y las banderas que
        // lee renderToBuffer. Nada de esto son keyframes.

        // Muerta no hay clip de locomocion (groundLocomotionAllowed pide isAlive) y ningun otro
        // gana la capa 0, asi que las mallas de ojo cerrado se quedan en su escala de reposo, que
        // es 1: visibles encima de las abiertas. Es el unico hueco que queda sin cubrir por
        // keyframes, y por eso es el unico sitio donde el modelo las apaga a mano.
        if (!entity.isAlive()) {
            this.hideSleepEyes();
        }

        if (sleepingLike) {
            this.hideGlideMembrane();
            this.isClimbingRender     = false;
            this.isClimbingDownRender = false;
            return;
        }

        this.isClimbingRender     = climbing && !gliding;
        this.isClimbingDownRender = entity.isClimbingDown() && !gliding;

        if (gliding) {
            return;   // planeando la membrana se queda visible
        }

        if (climbing) {
            if (entity.isClimbingUp()) {
                // bone12's pivot lands on the wall surface after the global -90° X rotation.
                // Moving Tail -3 in model Y pushes it +3 world-Z (away from wall), clearing the geometry.
                this.Tail.y -= 3.0F;
            }
        } else if (swimming) {
            this.squirrel.y += 4.0F;
            this.Head.xRot -= 0.3F;
        }

        this.hideGlideMembrane();

        // La cola tiene dos mallas: bone6 la peluda y bone12 la plana. Cada clip decide cual se ve,
        // pero en reposo bone12 esta a escala 1, asi que hay que apagarla salvo donde manda el clip
        // que la usa: CLIMBING_UP y Swim, los dos unicos que esconden bone6 sin salir antes por el
        // return de dormir o planear. Nadar mientras trepa no cuenta: ahi gana el clip de trepada
        // (prioridad 1 contra 2) y ese deja bone6 visible. Muerta tampoco: Swim pide isAlive().
        boolean flatTail = entity.isClimbingUp()
                || (swimming && !climbing && entity.isAlive());
        if (!flatTail) {
            this.bone12.xScale = 0.0F; this.bone12.yScale = 0.0F; this.bone12.zScale = 0.0F;
        }
    }

    /**
     * Apaga las mallas de ojo cerrado. Sólo para la ardilla muerta; el resto lo llevan los clips.
     *
     * <p>Antes se llamaba en todo frame que no fuera dormir, y eso mataba el parpadeo: el guiño no
     * es un estado sino un intercambio con keyframes —{@code Eye1/2} bajan a yScale 0 y
     * {@code Sleep_eye1/2} suben a 1 durante ~0.17 s—, así que forzarlas a 0 cada frame lo borraba
     * antes de dibujarlo. Los clips de trepada, que eran los únicos sin canal propio y por los que
     * existía este apagón, ya lo traen (yScale 0, como los demás), y al ir por keyframes se funden
     * bien en los crossfades en vez de saltar.
     */
    private void hideSleepEyes() {
        this.Sleep_eye1.xScale = 0.0F; this.Sleep_eye1.yScale = 0.0F; this.Sleep_eye1.zScale = 0.0F;
        this.Sleep_eye2.xScale = 0.0F; this.Sleep_eye2.yScale = 0.0F; this.Sleep_eye2.zScale = 0.0F;
    }

    private void hideGlideMembrane() {
        this.Glide.xScale       = 0.0F; this.Glide.yScale       = 0.0F; this.Glide.zScale       = 0.0F;
        this.Glide_torse.xScale = 0.0F; this.Glide_torse.yScale = 0.0F; this.Glide_torse.zScale = 0.0F;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RENDER
    // ─────────────────────────────────────────────────────────────────────────

    public void translateToBone11(PoseStack poseStack) {
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.translate(0.0, 1.5, 0.0);
        this.squirrel.translateAndRotate(poseStack);
        this.Leg3.translateAndRotate(poseStack);
        this.bone11.translateAndRotate(poseStack);
    }

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

        squirrel.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);

        poseStack.popPose();
    }

    @Override
    public ModelPart root() {
        return squirrel;
    }

}
