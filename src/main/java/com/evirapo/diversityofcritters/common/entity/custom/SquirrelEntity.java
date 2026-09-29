package com.evirapo.diversityofcritters.common.entity.custom;

import com.evirapo.diversityofcritters.common.locomotion.MantleProfile;
import com.evirapo.diversityofcritters.common.entity.ai.mantle.MantleController;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.ai.*;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.util.CritterDietConfig;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import com.evirapo.diversityofcritters.client.animations.SquirrelAnims;
import com.evirapo.diversityofcritters.client.animations.SquirrelBabyAnims;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.SpawnRuleEngine;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class SquirrelEntity extends DiverseCritter {

    // Ardilla voladora gigante: bosques templados de montaña
    @Override
    public com.evirapo.diversityofcritters.common.temperature.TemperatureTier comfortTierMin() {
        return com.evirapo.diversityofcritters.common.temperature.TemperatureTier.COOL;
    }
    @Override
    public com.evirapo.diversityofcritters.common.temperature.TemperatureTier comfortTierMax() {
        return com.evirapo.diversityofcritters.common.temperature.TemperatureTier.WARM;
    }

    // Bosques templados de montaña, húmedos pero no tropicales
    @Override
    public com.evirapo.diversityofcritters.common.humidity.HumidityTier comfortHumidityMin() {
        return com.evirapo.diversityofcritters.common.humidity.HumidityTier.MODERATE;
    }
    @Override
    public com.evirapo.diversityofcritters.common.humidity.HumidityTier comfortHumidityMax() {
        return com.evirapo.diversityofcritters.common.humidity.HumidityTier.HUMID;
    }

    // Sin AnimationState: TODAS las animaciones de la ardilla, adulta y bebé, viven en el
    // MobAnimator — el adulto en la capa 0 y el bebé en la 1.

    // --- SYNCED DATA ---
    private static final EntityDataAccessor<Byte> CLIMB_STATE = SynchedEntityData.defineId(SquirrelEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> CLIMB_FACING_YAW = SynchedEntityData.defineId(SquirrelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_GLIDING = SynchedEntityData.defineId(SquirrelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_EATING_PLANT = SynchedEntityData.defineId(SquirrelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> EATING_ITEM = SynchedEntityData.defineId(SquirrelEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> MORPH = SynchedEntityData.defineId(SquirrelEntity.class, EntityDataSerializers.BYTE);

    public static final String[] MORPH_NAMES = { "Dark red" };

    public static final byte CLIMB_NONE = 0;
    public static final byte CLIMB_UP   = 1;
    public static final byte CLIMB_DOWN = 2;
    public static final byte CLIMB_HANG = 3;

    // --- VARIABLES ---
    private CritterEatItemChainGoal forFoodGoal;
    private int tamingFeedsLeft = 0;

    private boolean climbRewardArmed = false;
    private int climbRewardCooldown = 0;


    private int postDescentCooldown = 0;
    private static final int POST_DESCENT_COOLDOWN = 20;

    private int postGlideCooldown = 0;
    private static final int POST_GLIDE_COOLDOWN = 20;

    private int postCrestCooldown = 0;
    private static final int POST_CREST_COOLDOWN = 10;
    private int climbUpStartY = 0;

    private boolean cachedHasClimbable = false;

    public SquirrelEntity(EntityType<? extends TamableAnimal> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.setMaxUpStep(1);
        this.mantle = new MantleController(this, MantleProfile.SQUIRREL);
        this.moveControl = new SquirrelMoveControl(this);
    }

    // --- SETUP ---
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CLIMB_STATE, CLIMB_NONE);
        this.entityData.define(CLIMB_FACING_YAW, 0.0F);
        this.entityData.define(IS_GLIDING, false);
        this.entityData.define(IS_EATING_PLANT, false);
        this.entityData.define(EATING_ITEM, ItemStack.EMPTY);
        this.entityData.define(MORPH, (byte) 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    protected PathNavigation createNavigation(Level pLevel) {
        return new SquirrelNavigation(this, pLevel);
    }

    // --- GOALS ---
    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(0, new CustomFloatGoal(this));
        this.goalSelector.addGoal(1, new SleepBehaviorGoal(this));

        this.goalSelector.addGoal(2, new SquirrelGlideGoal(this));

        this.goalSelector.addGoal(3, new CritterCryChainGoal(this));
        this.goalSelector.addGoal(3, new FindCryingBabyGoal(this, 1.4D, 24.0D));
        this.goalSelector.addGoal(4, new CritterOrderedSitGoal(this));
        this.goalSelector.addGoal(5, new NaturalMateGoal(this, 1.0D));

        // Solo domesticados: en un salvaje pelearia con la alerta por el flag MOVE, tirando del bicho
        // HACIA la amenaza de la que el otro sistema lo aparta, y dos goals con intenciones opuestas
        // dan tembleque, no comportamiento. Gateado por isTame() no llegan a coexistir nunca: el
        // sistema de wariness se apaga entero al domesticar.
        //
        // Sigue valiendo para llamar con comida a una mascota en modo wandering, donde FollowOwnerGoal
        // no corre. Y deja el sigilo como canal para domesticar: agachado se congela el temporizador
        // de alerta y el critter se queda vigilandote mientras te acercas.
        this.goalSelector.addGoal(6, new TemptGoal(this, 1.15D, Ingredient.of(DoCTags.Items.SQUIRREL_FOOD), false) {
            @Override public boolean canUse() { return isTame() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isTame() && super.canContinueToUse(); }
        });

        this.goalSelector.addGoal(7, new FollowOwnerGoal(this, 1.2D, 8.0F, 2.0F, false) {
            @Override public boolean canUse() { return isFollowing() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isFollowing() && super.canContinueToUse(); }
        });

        this.goalSelector.addGoal(8, new CritterCleanChainGoal(this));
        this.goalSelector.addGoal(9, new CritterMotherCleanBabyGoal<>(this, SquirrelEntity.class, 1.2D, 16.0D));
        this.forFoodGoal = new CritterEatItemChainGoal(this, DoCTags.Items.SQUIRREL_FOOD);
        this.goalSelector.addGoal(10, this.forFoodGoal);

        this.goalSelector.addGoal(11, new FindFoodBowlGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(12, new FindWaterBowlGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(13, new CritterDrinkChainGoal(this));

        this.goalSelector.addGoal(14, new SquirrelEatLeavesChainGoal(this, 1.1D, 12));

        this.goalSelector.addGoal(16, new SquirrelClimbGoal(this));

        this.goalSelector.addGoal(17, new CritterIdleVariantGoal(this));
        this.goalSelector.addGoal(18, new FollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(19, new CritterExploreHeightGoal(this));
        this.goalSelector.addGoal(19, new WaterAvoidingRandomStrollGoal(this, 1.0D) {
            @Override public boolean canUse() {
                boolean canWander = !isTame() || isWandering();
                return canWander && super.canUse();
            }
        });
        this.goalSelector.addGoal(20, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(21, new RandomLookAroundGoal(this));

        // Sin flags: la prioridad da igual, va el último por legibilidad. Los pesos son los del
        // sorteo original (20 / 25 / 25); sentarse y tumbarse los lleva CritterIdleVariantGoal.
        this.goalSelector.addGoal(22, new CritterIdleGestureGoal(this, 120, 120)
                .add(IdleVariant.STRETCH, "stretch", 20)
                .add(IdleVariant.SNIFF_LEFT, "sniff_left", 25)
                .add(IdleVariant.SNIFF_RIGHT, "sniff_right", 25)
                .condition(c -> !this.isClimbing() && !this.isGliding()));

        // Sin targetSelector: el squirrel es herbívoro, no caza.
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            this.updateClimbState();

            if (this.isGliding() && (this.onGround() || this.isInWaterOrBubble())) {
                this.setGliding(false);
            }

            boolean isNewborn = this.isNewborn();
            double desiredSpeed = isNewborn ? 0.1D : 0.25D;
            // A mantle lifts the body itself; vanilla stepping would jump it up the block at once.
            float desiredStep   = this.isMantling() ? 0.0F : (isNewborn ? 0.5F : 1.0F);

            if (this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() != desiredSpeed) {
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(desiredSpeed);
            }
            if (this.maxUpStep() != desiredStep) {
                this.setMaxUpStep(desiredStep);
            }

            if (this.isNewborn() && this.getHunger() < 3000) {
                if (!this.isCrying()) {
                    this.setCrying(true);
                    this.playSound(SoundEvents.FOX_SCREECH, 0.4F, 2.0F);
                }
                if (this.tickCount % 80 == 0) {
                    this.playSound(SoundEvents.FOX_SCREECH, 0.4F, 2.0F);
                }
            } else {
                if (this.isCrying()) {
                    this.setCrying(false);
                }
            }
        }

        if (IsDrinking() && getDrinkPos() != null) {
            BlockPos washingPos = getDrinkPos();
            if (this.distanceToSqr(washingPos.getX() + 0.5D, washingPos.getY() + 0.5D, washingPos.getZ() + 0.5D) < 3) {
                if (this.getRandom().nextInt(3)==0){
                    for (int j = 0; j < 4; ++j) {
                        double d2 = this.random.nextDouble()/2;
                        double d3 = this.random.nextDouble()/2;
                        Vec3 v = this.getDeltaMovement();
                        this.level().addParticle(ParticleTypes.SPLASH, washingPos.getX() + d2, washingPos.getY() + 0.8F, washingPos.getZ() + d3, v.x, v.y, v.z);
                    }
                }
            } else {
                setIsDrinking(false);
            }
        }

    }

    // --- ANIMACIONES ---

    /**
     * Locomoción de suelo al estilo Athenian/Arpy: tres animaciones REPEATING registradas en la capa
     * 0 con playCondition mutuamente excluyentes. El crossfade entre ellas lo hace el BlendState de
     * DeluxeLib, no pesos calculados a mano en el modelo, y el clip corre a su propio ritmo en vez de
     * ir atado a limbSwing.
     *
     * <p>Las duraciones son las longitudes reales de los clips en {@link SquirrelAnims}; si los
     * vuelves a exportar desde Blockbench con otra longitud hay que actualizarlas aquí.
     */
    @Override
    public void registerAnimations() {
        StandardAnimation idle = new StandardAnimation("idle",
                new AnimSource(() -> SquirrelAnims.Idle), Loop.REPEATING, 0, 9, 2.0F);
        StandardAnimation walk = new StandardAnimation("walk",
                new AnimSource(() -> SquirrelAnims.Walk), Loop.REPEATING, 0, 8, 0.6441F);
        StandardAnimation run = new StandardAnimation("run",
                new AnimSource(() -> SquirrelAnims.Run), Loop.REPEATING, 0, 7, 0.3333F);

        idle.blendInMs(250).blendOutMs(150);
        walk.blendInMs(150).blendOutMs(150);
        run.blendInMs(150).blendOutMs(150);

        // Un único signo de movimiento: isMoving(), que ya combina velocidad y camino pendiente
        // pasados por la histéresis (ver DiverseCritter.aiStep). Leer la navegación aquí en crudo
        // hacía parpadear idle/walk cada vez que algo recreaba la ruta desde fuera de un goal.
        idle.setPlayCondition(a -> groundLocomotionAllowed() && !this.isMoving());
        walk.setPlayCondition(a -> groundLocomotionAllowed() && this.isMoving() && !this.isSprinting());
        run.setPlayCondition(a -> groundLocomotionAllowed() && this.isMoving() && this.isSprinting());

        // --- Sentarse por orden: cadena de 3 fases dirigida por CritterOrderedSitGoal ---
        // La entrada y la salida son PLAY_ONCE y sólo arrancan con play() desde el goal; eso es lo
        // que permite que la salida suene entera cuando isOrderedToSit() ya es false.
        //
        // El bucle SÍ necesita playCondition. MobAnimator.tick() auto-arranca toda animación
        // REPEATING cuya condición se cumpla, y la condición por defecto es `true`: dejarlo sin
        // condición hacía que se encendiera solo y, al tener mejor prioridad que idle, la ardilla
        // se quedaba sentada para siempre aunque no estuviera domesticada.
        //
        // Misma prioridad las tres, para que el bucle no auto-arranque encima de su propia entrada
        // (el guard de MobAnimator es `current.getPriority() <= base.getPriority()`).
        StandardAnimation sitEnter = new StandardAnimation("ordered_sit_enter",
                new AnimSource(() -> SquirrelAnims.SIT_STARTING), Loop.PLAY_ONCE, 0, 5, 0.5F);
        StandardAnimation sitLoop = new StandardAnimation("ordered_sit_loop",
                new AnimSource(() -> SquirrelAnims.SIT_IDLE), Loop.REPEATING, 0, 5, 4.0F);
        StandardAnimation sitExit = new StandardAnimation("ordered_sit_exit",
                new AnimSource(() -> SquirrelAnims.SIT_ENDING), Loop.PLAY_ONCE, 0, 5, 0.5F);

        sitEnter.blendInMs(150).blendOutMs(120).setNextAnimation("ordered_sit_loop");
        sitLoop.blendInMs(120).blendOutMs(150);
        sitLoop.setPlayCondition(a -> this.isOrderedToSit());
        // locksMovement: AnimationLockGoal congela a la ardilla mientras dura la salida, que es lo
        // que impedía que se deslizara con la pose de levantarse a medias.
        sitExit.blendInMs(120).blendOutMs(200).locksMovement();

        this.animator().register(idle, walk, run, sitEnter, sitLoop, sitExit);

        // --- Actividades (prioridad 6) ---
        registerChain("drink", () -> SquirrelAnims.DRINK_STARTING, 0.25F,
                () -> SquirrelAnims.DRINK_IDLE, 1.0F,
                () -> SquirrelAnims.DRINK_ENDING, 0.25F, P_ACTIVITY)
                .setPlayCondition(a -> this.IsDrinking());

        registerChain("clean", () -> SquirrelAnims.GROOMING_STARTING, 0.5F,
                () -> SquirrelAnims.GROOMING_IDLE, 0.5F,
                () -> SquirrelAnims.GROOMING_ENDING, 0.5F, P_ACTIVITY)
                .setPlayCondition(a -> this.isCleaning());

        registerChain("eat", () -> SquirrelAnims.EATING_STARTING, 0.25F,
                () -> SquirrelAnims.EATING_IDLE, 0.5F,
                () -> SquirrelAnims.EATING_ENDING, 0.25F, P_ACTIVITY)
                .setPlayCondition(a -> this.isEatingPlant());

        // --- Variantes de idle (prioridad 7) ---
        // Las de un solo clip no tienen entrada ni salida: al terminar, el bucle se para por su
        // condición y idle entra con su propio crossfade.
        // El export de Blockbench traía cruzados estos dos: lo que llamaba "Lay" es en realidad
        // erguirse (STAND_UP_*, cadena de 3 fases) y lo que llamaba "Stand up" es estirarse
        // (stretch, clip suelto). Ya está corregido aquí y en tools/swap_squirrel_anims.py, que es
        // quien traduce los nombres crudos del export.
        registerSingle("stretch", () -> SquirrelAnims.stretch, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.STRETCH);
        registerSingle("sniff_left", () -> SquirrelAnims.SniffLeft, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SNIFF_LEFT);
        registerSingle("sniff_right", () -> SquirrelAnims.SniffRight, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SNIFF_RIGHT);

        registerChain("idle_sit", () -> SquirrelAnims.SIT_STARTING, 0.5F,
                () -> SquirrelAnims.SIT_IDLE, 4.0F,
                () -> SquirrelAnims.SIT_ENDING, 0.5F, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SIT);

        registerChain("idle_stand_up", () -> SquirrelAnims.STAND_UP_STARTING, 1.0F,
                () -> SquirrelAnims.STAND_UP_IDLE, 1.0F,
                () -> SquirrelAnims.STAND_UP_ENDING, 1.0F, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.STAND_UP);

        // --- Estados continuos: no los dirige ningún goal, sólo su condición ---
        // Prioridades 0-3, o sea por encima de todo lo demás: si la ardilla planea, trepa, nada o
        // duerme, eso manda sobre cualquier actividad o locomoción.
        StandardAnimation glide = new StandardAnimation("glide",
                new AnimSource(() -> SquirrelAnims.GLIDING), Loop.REPEATING, 0, 0, 0.1295F);
        StandardAnimation climbUp = new StandardAnimation("climb_up",
                new AnimSource(() -> SquirrelAnims.CLIMBING_UP), Loop.REPEATING, 0, 1, 1.0F);
        StandardAnimation climbDown = new StandardAnimation("climb_down",
                new AnimSource(() -> SquirrelAnims.CLIMBING_DOWN), Loop.REPEATING, 0, 1, 1.0F);
        StandardAnimation climbHang = new StandardAnimation("climb_hang",
                new AnimSource(() -> SquirrelAnims.CLIMB_IDLE), Loop.REPEATING, 0, 1, 4.0F);
        StandardAnimation swim = new StandardAnimation("swim",
                new AnimSource(() -> SquirrelAnims.Swim), Loop.REPEATING, 0, 2, 2.0F);
        StandardAnimation sleepEnter = new StandardAnimation("sleep_enter",
                new AnimSource(() -> SquirrelAnims.preparing_sleep), Loop.REPEATING, 0, 3, 2.0F);
        StandardAnimation sleepLoop = new StandardAnimation("sleep_loop",
                new AnimSource(() -> SquirrelAnims.Sleep), Loop.REPEATING, 0, 3, 4.0F);
        StandardAnimation sleepExit = new StandardAnimation("sleep_exit",
                new AnimSource(() -> SquirrelAnims.awakening), Loop.REPEATING, 0, 3, 2.0F);

        // Fundidos cortos en trepada y planeo: renderToBuffer gira el modelo -90° de golpe al entrar,
        // así que un crossfade largo mezclaría poses con orientaciones distintas.
        glide.blendInMs(100).blendOutMs(100);
        climbUp.blendInMs(100).blendOutMs(100);
        climbDown.blendInMs(100).blendOutMs(100);
        climbHang.blendInMs(150).blendOutMs(150);
        // El modelo variaba la velocidad de nado con limbSwingAmount; la librería sólo admite un
        // multiplicador fijo, así que se toma un valor medio. Si se nota lento o rápido, es aquí.
        swim.blendInMs(200).blendOutMs(200).playbackSpeed(1.5F);
        sleepEnter.blendInMs(200).blendOutMs(200);
        sleepLoop.blendInMs(300).blendOutMs(300);
        sleepExit.blendInMs(200).blendOutMs(200);

        glide.setPlayCondition(a -> this.isGliding() && this.isAlive());
        climbUp.setPlayCondition(a -> this.isClimbingUp());
        climbDown.setPlayCondition(a -> this.isClimbingDown());
        climbHang.setPlayCondition(a -> this.isHanging());
        swim.setPlayCondition(a -> this.isInWaterOrBubble() && this.isAlive());
        sleepEnter.setPlayCondition(a -> this.getSleepState() == SleepState.PREPARING);
        sleepLoop.setPlayCondition(a -> this.getSleepState() == SleepState.SLEEPING);
        sleepExit.setPlayCondition(a -> this.getSleepState() == SleepState.AWAKENING);

        this.animator().register(glide, climbUp, climbDown, climbHang, swim,
                sleepEnter, sleepLoop, sleepExit);

        this.registerBabyAnimations();
    }

    /**
     * Las del recién nacido, en la {@link #LAYER_BABY capa 1}. {@code SquirrelBabyModel} tiene su
     * propia jerarquía de huesos, así que no pueden convivir con las del adulto en la capa 0; cada
     * modelo lee sólo su capa. Las prioridades son independientes por capa, así que se reutiliza la
     * misma escala.
     */
    private void registerBabyAnimations() {
        StandardAnimation babyIdle = registerSingle("baby_idle",
                () -> SquirrelBabyAnims.idle, 4.0F, Loop.REPEATING, P_IDLE, LAYER_BABY);
        StandardAnimation babyCrawl = registerSingle("baby_crawl",
                () -> SquirrelBabyAnims.move, 4.0F, Loop.REPEATING, P_WALK, LAYER_BABY);

        babyIdle.setPlayCondition(a -> this.isNewborn() && !this.isMoving());
        babyCrawl.setPlayCondition(a -> this.isNewborn() && this.isMoving());

        // Dormir no es una cadena dirigida por un goal, sino tres estados sincronizados: se registran
        // como tres REPEATING con condición sobre SleepState, igual que en el adulto. Una cadena no
        // valdría, porque su entrada es PLAY_ONCE y nadie la lanzaría.
        registerSingle("baby_sleep_enter", () -> SquirrelBabyAnims.preparing_sleep,
                1.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.PREPARING);
        registerSingle("baby_sleep_loop", () -> SquirrelBabyAnims.sleep,
                4.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.SLEEPING);
        registerSingle("baby_sleep_exit", () -> SquirrelBabyAnims.awakening,
                1.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.AWAKENING);

        // El llanto sí es una cadena con dueño: la dirige CritterCryChainGoal. La condición del bucle
        // está además porque el flag lo apaga también FindCryingBabyGoal cuando la madre lo atiende,
        // y así se para lo apague quien lo apague.
        registerChain("cry",
                () -> SquirrelBabyAnims.preparing_cry, 0.5F,
                () -> SquirrelBabyAnims.cry, 1.0F,
                () -> SquirrelBabyAnims.stopping_cry, 0.5F, P_ACTIVITY, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.isCrying());
    }


    /**
     * Reimplementado sobre el animator: la versión de {@link DiverseCritter} lee contadores que sólo
     * existen en el cliente, así que en servidor devolvía siempre false. Ahora es correcta en ambos
     * lados y cubre también la fase de salida.
     */
    @Override
    public boolean isOrderedSitPlaying() {
        return this.animator().isPlaying("ordered_sit_enter")
                || this.animator().isPlaying("ordered_sit_loop")
                || this.animator().isPlaying("ordered_sit_exit");
    }

    /**
     * Todo lo que aparta a la ardilla de la locomoción normal de suelo. Las cadenas de actividad y
     * las variantes de idle todavía las dibuja el camino viejo desde el modelo, así que aquí se
     * excluyen a mano; según vayan migrando a la capa 0 podrán quitarse de esta lista y dejar que
     * las resuelva la prioridad.
     */
    private boolean groundLocomotionAllowed() {
        return this.isAlive()
                // Los recién nacidos se dibujan con SquirrelBabyModel, que tiene su propio juego de
                // clips (SquirrelBabyAnims) sobre una jerarquía de huesos distinta.
                && !this.isNewborn()
                // Sólo se excluye lo que NO vive en la capa 0. Las actividades (sentarse, beber,
                // acicalarse, comer, variantes de idle) ya no hace falta listarlas: tienen mejor
                // prioridad que idle, así que la capa las resuelve sola y el relevo es sin huecos.
                && this.getSleepState() == SleepState.AWAKE
                && !this.isClimbing()
                && !this.isGliding()
                && !this.isInWaterOrBubble();
    }

    @Override
    public void aiStep() {
        ItemStack stack = this.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!stack.isEmpty() && stack.is(DoCTags.Items.SQUIRREL_FOOD) && !this.isNewborn()) {
            this.setHunger(this.maxHunger());

            var foodProps = stack.isEdible() ? stack.getFoodProperties(this) : null;
            if (foodProps != null) {
                this.heal((float) foodProps.getNutrition());
            } else {
                this.heal(1.0F);
            }

            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.AMBIENT);

            stack.shrink(1);
            if (stack.isEmpty()) {
                this.setItemInHand(InteractionHand.MAIN_HAND, Items.AIR.getDefaultInstance());
            }
        }

        if (isHungry() && !this.isNewborn() && (double)this.random.nextFloat() <= 0.2) triggerFoodSearch();

        super.aiStep();

        if (!this.level().isClientSide && this.climbRewardCooldown > 0) {
            this.climbRewardCooldown--;
        }

        if (!this.level().isClientSide) {
            if (this.isClimbing()) {
                this.climbRewardArmed = true;
            } else if (this.climbRewardArmed && this.onGround() && this.climbRewardCooldown <= 0) {
                this.setEnrichment(this.maxEnrichment());
                this.climbRewardArmed = false;
                this.climbRewardCooldown = 20;
            }
        }
    }

    @Override
    public void customServerAiStep() {
        if (this.getMoveControl().hasWanted()) {
            double d0 = this.getMoveControl().getSpeedModifier();
            this.setPose(Pose.STANDING);
            this.setSprinting(d0 >= 1.25D);
        } else {
            this.setPose(Pose.STANDING);
            this.setSprinting(false);
        }
    }

    // --- MOVEMENT & PHYSICS ---

    @Override
    public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return this.cachedHasClimbable ? 16 : super.getMaxFallDistance();
    }

    @Override
    public void jumpInFluid(FluidType type) {
        self().setDeltaMovement(self().getDeltaMovement().add(0.0D, 0.04F * self().getAttributeValue(ForgeMod.SWIM_SPEED.get())/3, 0.0D));
    }

    @Override
    public void sinkInFluid(FluidType type) {
        self().setDeltaMovement(self().getDeltaMovement().add(0.0D, -0.04F * self().getAttributeValue(ForgeMod.SWIM_SPEED.get())/6, 0.0D));
    }

    /**
     * Lo único que queda del camino viejo: el idle que dibuja {@code SquirrelBabyModel}. Todo lo del
     * modelo adulto —locomoción, actividades, variantes de reposo, trepada, planeo, natación y
     * dormir— vive ya en la capa 0 del {@code MobAnimator}, y con ello desaparecieron ~120 líneas de
     * booleanos mutuamente excluyentes y los contadores {@code clientXxxTick} que duplicaban a mano
     * las duraciones de los clips.
     */

    @Override
    public InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);

        boolean isItemTamingFood = itemstack.is(DoCTags.Items.SQUIRREL_FOOD);
        boolean isNurserBottle = itemstack.is(DOCItems.FILLED_NURSER_BOTTLE.get());

        boolean canTameWithThis = false;
        if (!this.isTame()) {
            if (this.isBaby() && !this.isJuvenile()) {
                canTameWithThis = isNurserBottle;
            } else {
                canTameWithThis = isItemTamingFood;
            }
        }

        if (canTameWithThis) {
            if (this.level().isClientSide()) {
                return InteractionResult.CONSUME;
            } else {
                if (!pPlayer.getAbilities().instabuild) {
                    if (isNurserBottle) {
                        itemstack.hurtAndBreak(1, pPlayer, (p) -> p.broadcastBreakEvent(pHand));
                    } else {
                        itemstack.shrink(1);
                    }
                }
                // Alimentar es alimentar, aunque sea una toma de domesticacion. Sin esto, el biberon
                // gastaba durabilidad y no tocaba el hambre: la barra solo se rellenaba una vez el
                // bicho ya era tuyo, porque ese codigo vive en DiverseCritter.mobInteract y esta rama
                // devuelve antes de llegar. Efecto practico: dar el biberon a un huerfano salvaje NO
                // lo salvaba de morir de hambre, que es justo para lo que existe.
                //
                // La domesticacion sigue costando las 3-5 tomas de siempre; lo que cambia es que cada
                // una llena la barra.
                if (isNurserBottle) {
                    this.setHunger(this.maxHunger());
                    this.setThirst(this.maxThirst());
                }

                if (this.tamingFeedsLeft <= 0) this.tamingFeedsLeft = 3 + this.random.nextInt(3);
                this.tamingFeedsLeft--;
                if (this.tamingFeedsLeft <= 0 && !ForgeEventFactory.onAnimalTame(this, pPlayer)) {
                    this.tame(pPlayer);
                    this.navigation.recomputePath();
                    this.setTarget(null);
                    this.level().broadcastEntityEvent(this, (byte)7);
                    this.setWandering(true);
                    this.setOrderedToSit(false);
                    this.setInSittingPose(true);
                } else {
                    this.level().broadcastEntityEvent(this, (byte)6);
                }
                return InteractionResult.SUCCESS;
            }
        }

        if (isItemTamingFood && !this.isBaby()) {
            if (this.getHunger() < this.maxHunger() || this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide()) {
                    this.setHunger(this.maxHunger());

                    var foodProps = itemstack.isEdible() ? itemstack.getFoodProperties(this) : null;
                    if (foodProps != null) this.heal((float) foodProps.getNutrition());

                    if (!pPlayer.getAbilities().instabuild) itemstack.shrink(1);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                    this.gameEvent(GameEvent.EAT, this);
                }
                return InteractionResult.SUCCESS;
            }
        }

        return super.mobInteract(pPlayer, pHand);
    }

    /**
     * Ya no hace falta: {@link CritterEatItemChainGoal} escanea en cada evaluación de {@code canUse()}
     * y su fase APPROACH es la dueña de la navegación.
     *
     * <p>Lo que hacía antes —{@code navigation.stop()} + {@code moveTo()} desde fuera de todo goal,
     * con un 20% de probabilidad por tick, o sea unas cinco veces por segundo mientras hubiera
     * comida en el suelo— destrozaba la ruta continuamente. Con las condiciones de idle/walk
     * mirando el estado de navegación, eso se veía como parpadeo de animaciones.
     */
    private void triggerFoodSearch() {
        // intencionadamente vacío
    }

    @Override
    public boolean canPickUpLoot() {
        return !this.isNewborn();
    }

    @Override
    public boolean wantsToPickUp(ItemStack pStack) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return false;
    }

    // --- HELPERS & CONFIG ---
    @Override public int maxHunger() { return 4000; }
    @Override public int maxThirst() { return 4000; }
    @Override public int maxEnrichment() { return 4000; }
    @Override public int maxHygiene() { return 4000; }
    // Ticks de juego, y exactamente la longitud del clip: SquirrelAnims 2.0 s = 40 ticks en el
    // adulto, SquirrelBabyAnims 1.0 s = 20 en el recién nacido. Pasarse deja al animal congelado
    // en el último frame (la definición no es looping, así que KeyframeBlender clampa); quedarse
    // corto corta el gesto a media pose.
    @Override public int getPreparingSleepDuration() {return this.isNewborn() ? 20 : 40;}
    @Override public int getAwakeningDuration() {return this.isNewborn() ? 20 : 40;}
    @Override protected boolean getDefaultDiurnal() { return false; }
    @Override protected double getHungerLossPerSecond()     { return 3.33; }
    @Override protected double getThirstLossPerSecond()     { return 5.0;  }
    @Override protected double getEnrichmentLossPerSecond() { return 2.5;  }
    @Override protected double getHygieneLossPerSecond()    { return 1.5;  }

    // --- CLIMB ---
    public byte getClimbState() { return this.entityData.get(CLIMB_STATE); }
    public void setClimbState(byte state) {
        byte prev = this.getClimbState();
        this.entityData.set(CLIMB_STATE, state);
        if (prev == CLIMB_DOWN && state == CLIMB_NONE) {
            this.postDescentCooldown = POST_DESCENT_COOLDOWN;
        }
        if (prev != CLIMB_UP && state == CLIMB_UP) {
            this.climbUpStartY = this.blockPosition().getY();
        }
    }

    public boolean isInPostDescentCooldown() {
        return this.postDescentCooldown > 0;
    }
    public boolean isClimbing() { return getClimbState() != CLIMB_NONE; }
    public boolean isClimbingUp() { return getClimbState() == CLIMB_UP; }
    public boolean isClimbingDown() { return getClimbState() == CLIMB_DOWN; }
    public boolean isHanging() { return getClimbState() == CLIMB_HANG; }

    @Override
    public boolean onClimbable() { return isClimbing(); }

    // --- GLIDE ---
    public boolean isGliding() { return this.entityData.get(IS_GLIDING); }

    /** Gliding and eating pose the legs themselves; the rig stands down. */
    @Override
    public boolean isLocomotionSuspended() {
        return super.isLocomotionSuspended() || this.isGliding() || this.isEatingPlant();
    }

    /** Drawn at this size by the renderer; the rig and the mantle measure legs with it. */
    @Override
    public float growthScale() {
        if (this.isNewborn()) {
            return 0.45F;
        }
        return this.isJuvenile() ? 0.65F : 1.0F;
    }

    /** Al apagar el glide se arman AMBOS cooldowns (postGlide + postDescent):
     *  triple protección contra el re-trigger del climb al aterrizar. */
    public void setGliding(boolean gliding) {
        boolean prev = this.isGliding();
        this.entityData.set(IS_GLIDING, gliding);
        if (prev && !gliding) {
            this.postGlideCooldown = POST_GLIDE_COOLDOWN;
            this.postDescentCooldown = POST_DESCENT_COOLDOWN;
        }
    }

    public boolean isInPostGlideCooldown() {
        return this.postGlideCooldown > 0;
    }
    public boolean isInPostCrestCooldown() { return this.postCrestCooldown > 0; }

    /** Altura mínima de aire debajo para que valga la pena planear. */
    public static final int GLIDE_MIN_AIR = 3;

    /**
     * Intenta iniciar el planeo (server-side). Verifica el espacio de aire
     * internamente, así que es seguro llamarlo "a ciegas": si no hay altura
     * suficiente es un no-op y el squirrel simplemente cae (sin daño de caída).
     *
     * Escenario 1: al soltar el HANG (SquirrelClimbGoal.stop()).
     * Escenario 2: en altura sin pared trepable (SquirrelGlideGoal.canUse()).
     *
     * @return true si el glide quedó activo.
     */
    public boolean tryStartGlide() {
        if (this.level().isClientSide) return false;
        if (this.isGliding()) return true;
        if (this.isNewborn()) return false;
        if (this.isInWater() || this.isInWaterOrBubble()) return false;
        if (this.onGround()) return false;
        if (this.distanceToGround(GLIDE_MIN_AIR + 1) < GLIDE_MIN_AIR) return false;
        if (this.hasAdjacentClimbableBlock()) {
            int colHeight = this.adjacentClimbableColumnHeight(GLIDE_MIN_AIR);
            if (colHeight > 0 && colHeight < GLIDE_MIN_AIR) return false;
        }

        float launchYaw;
        net.minecraft.world.level.pathfinder.Path glideNavPath = this.getNavigation().getPath();
        if (glideNavPath != null && !glideNavPath.isDone()) {
            BlockPos glideTarget = glideNavPath.getTarget();
            double dx = glideTarget.getX() + 0.5D - this.getX();
            double dz = glideTarget.getZ() + 0.5D - this.getZ();
            if (dx * dx + dz * dz > 1.0D) {
                launchYaw = (float)(Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            } else {
                launchYaw = this.hasAdjacentClimbableBlock()
                        ? this.getClimbFacingYaw() + 180.0F : this.getYRot();
            }
        } else {
            launchYaw = this.hasAdjacentClimbableBlock()
                    ? this.getClimbFacingYaw() + 180.0F : this.getYRot();
        }

        this.setClimbState(CLIMB_NONE);
        this.setGliding(true);

        this.setYRot(launchYaw);
        this.yRotO = launchYaw;
        this.setYBodyRot(launchYaw);
        this.yBodyRotO = launchYaw;
        this.setYHeadRot(launchYaw);

        float yawRad = (float) Math.toRadians(launchYaw);
        double fx = -Math.sin(yawRad) * 0.30D;
        double fz =  Math.cos(yawRad) * 0.30D;
        this.setDeltaMovement(fx, -0.05D, fz);
        this.getNavigation().stop();
        return true;
    }

    public int distanceToGround(int maxScan) {
        BlockPos.MutableBlockPos pos = this.blockPosition().mutable();
        for (int i = 0; i < maxScan; i++) {
            pos.move(0, -1, 0);
            if (!this.level().getBlockState(pos).isAir()) {
                return i;
            }
        }
        return maxScan;
    }

    public int adjacentClimbableColumnHeight(int maxScan) {
        int by = Mth.floor(this.getY());
        int bx = Mth.floor(this.getX());
        int bz = Mth.floor(this.getZ());
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            for (int yOff = 0; yOff >= -1; yOff--) {
                BlockPos start = new BlockPos(bx + off[0], by + yOff, bz + off[1]);
                if (!this.level().getBlockState(start).is(DoCTags.Blocks.CIVET_CLIMBABLE)) continue;
                int height = 0;
                BlockPos.MutableBlockPos pos = start.mutable();
                while (height < maxScan && this.level().getBlockState(pos).is(DoCTags.Blocks.CIVET_CLIMBABLE)) {
                    height++;
                    pos.move(0, -1, 0);
                }
                return height;
            }
        }
        return 0;
    }

    public int adjacentClimbableBlocksAbove(int maxScan) {
        int by = Mth.floor(this.getY());
        int bx = Mth.floor(this.getX());
        int bz = Mth.floor(this.getZ());
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            for (int yOff = 0; yOff >= -1; yOff--) {
                BlockPos check = new BlockPos(bx + off[0], by + yOff, bz + off[1]);
                if (!this.level().getBlockState(check).is(DoCTags.Blocks.CIVET_CLIMBABLE)) continue;
                // Adjacent column confirmed — count upward from by+1.
                int count = 0;
                BlockPos.MutableBlockPos pos = new BlockPos(bx + off[0], by + 1, bz + off[1]).mutable();
                while (count < maxScan && this.level().getBlockState(pos).is(DoCTags.Blocks.CIVET_CLIMBABLE)) {
                    count++;
                    pos.move(0, 1, 0);
                }
                return count;
            }
        }
        return 0;
    }

    public boolean isEatingPlant() { return this.entityData.get(IS_EATING_PLANT); }
    public void setEatingPlant(boolean eating) { this.entityData.set(IS_EATING_PLANT, eating); }

    public ItemStack getEatingItem() { return this.entityData.get(EATING_ITEM); }
    public void setEatingItem(ItemStack stack) { this.entityData.set(EATING_ITEM, stack.copy()); }

    public boolean hasAdjacentClimbableBlock() {
        return hasAdjacentClimbableBlockInternal();
    }

    private boolean hasAdjacentClimbableBlockInternal() {
        AABB box = this.getBoundingBox();
        double probeDistance = 0.3D;
        int footY = Mth.floor(box.minY);
        int eyeY  = Mth.floor(box.minY + this.getBbHeight() * 0.5);

        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double halfW = (box.maxX - box.minX) * 0.5;
        double halfD = (box.maxZ - box.minZ) * 0.5;

        int[][] offsets = {
            { Mth.floor(cx + halfW + probeDistance), Mth.floor(cz) },   // +X
            { Mth.floor(cx - halfW - probeDistance), Mth.floor(cz) },   // -X
            { Mth.floor(cx), Mth.floor(cz + halfD + probeDistance) },   // +Z
            { Mth.floor(cx), Mth.floor(cz - halfD - probeDistance) }    // -Z
        };

        for (int[] off : offsets) {
            for (int dy : new int[]{0, -1}) {
                BlockPos posFoot = new BlockPos(off[0], footY + dy, off[1]);
                BlockPos posEye  = new BlockPos(off[0], eyeY  + dy, off[1]);
                if (this.level().getBlockState(posFoot).is(DoCTags.Blocks.CIVET_CLIMBABLE)
                 || this.level().getBlockState(posEye).is(DoCTags.Blocks.CIVET_CLIMBABLE)) {
                    return true;
                }
            }
        }
        return false;
    }

    public float getClimbFacingYaw() { return this.entityData.get(CLIMB_FACING_YAW); }

    private void updateClimbFacingYaw() {
        AABB box = this.getBoundingBox();
        double probeDistance = 0.3D;
        int footY = Mth.floor(box.minY);

        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double halfW = (box.maxX - box.minX) * 0.5;
        double halfD = (box.maxZ - box.minZ) * 0.5;

        double[][] probes = {
            { cx + halfW + probeDistance, cz,                           -90.0 },
            { cx - halfW - probeDistance, cz,                            90.0 },
            { cx,                         cz + halfD + probeDistance,     0.0 },
            { cx,                         cz - halfD - probeDistance,   180.0 }
        };

        for (double[] probe : probes) {
            BlockPos pos = new BlockPos(Mth.floor(probe[0]), footY, Mth.floor(probe[1]));
            if (this.level().getBlockState(pos).is(DoCTags.Blocks.CIVET_CLIMBABLE)) {
                this.entityData.set(CLIMB_FACING_YAW, (float) probe[2]);
                return;
            }
        }
    }

    private void updateClimbState() {
        if (this.postDescentCooldown > 0) this.postDescentCooldown--;
        if (this.postGlideCooldown > 0) this.postGlideCooldown--;
        if (this.postCrestCooldown > 0) this.postCrestCooldown--;

        if (this.isGliding()) {
            if (this.isClimbing()) this.entityData.set(CLIMB_STATE, CLIMB_NONE);
            return;
        }

        this.cachedHasClimbable = this.hasAdjacentClimbableBlockInternal();
        boolean hasClimbable = this.cachedHasClimbable;
        byte currentState = this.getClimbState();

        if (currentState == CLIMB_HANG) {
            if (!hasClimbable) {
                this.entityData.set(CLIMB_STATE, CLIMB_NONE);
            } else {
                this.updateClimbFacingYaw();
                this.lockClimbRotation();
            }
            return;
        }

        if (currentState == CLIMB_UP) {
            if (this.verticalCollision && !this.onGround()) {
                this.entityData.set(CLIMB_STATE, CLIMB_NONE);
                this.navigation.recomputePath();
                return;
            }
            if (!hasClimbable && !this.onGround()) {
                // CAMBIO vs Civet: sin impulso de salto. Solo se suelta la pared;
                // si hay altura, SquirrelGlideGoal toma el control (escenario 2).
                this.entityData.set(CLIMB_STATE, CLIMB_NONE);
                // Only arm crest cooldown after a real climb (≥ GLIDE_MIN_AIR blocks).
                // Brief wall touches during descent have climbUpStartY ≈ current Y
                // (barely any height gained), so they must not block the next glide.
                if (this.blockPosition().getY() - this.climbUpStartY >= GLIDE_MIN_AIR) {
                    this.postCrestCooldown = POST_CREST_COOLDOWN;
                }
            } else if (this.onGround() && !hasClimbable) {
                this.entityData.set(CLIMB_STATE, CLIMB_NONE);
            } else {
                this.updateClimbFacingYaw();
                this.lockClimbRotation();
            }
            return;
        }

        if (currentState == CLIMB_DOWN) {
            if (this.onGround()) {
                this.setClimbState(CLIMB_NONE);
                return;
            }
            this.updateClimbFacingYaw();
            this.lockClimbRotation();
            return;
        }

        if (this.postDescentCooldown > 0 || this.postGlideCooldown > 0) return;
        if (this.isMantling()) return;
        if (!this.horizontalCollision || !hasClimbable) return;
        if (this.isEatingPlant() || this.isNewborn() || this.onGround()) return;
        if (this.isInWater() || this.isInWaterOrBubble()) return;

        boolean pathGoesUp = false;
        net.minecraft.world.level.pathfinder.Path path = this.navigation.getPath();
        if (path != null && !path.isDone()) {
            int idx = path.getNextNodeIndex();
            int curY = Mth.floor(this.getY());
            int limit = Math.min(idx + 3, path.getNodeCount());
            for (int i = idx; i < limit; i++) {
                if (path.getNode(i).y > curY) { pathGoesUp = true; break; }
            }
        }
        if (!pathGoesUp) return;

        this.setClimbState(CLIMB_UP);
        this.updateClimbFacingYaw();
        this.lockClimbRotation();
    }

    private void lockClimbRotation() {
        float wallYaw = this.getClimbFacingYaw();
        this.setYRot(wallYaw);
        this.yRotO = wallYaw;
        this.setYBodyRot(wallYaw);
        this.yBodyRotO = wallYaw;
        this.setYHeadRot(wallYaw);
    }

    public static boolean checkSquirrelSpawnRules(EntityType<SquirrelEntity> t, LevelAccessor lvl, MobSpawnType type, BlockPos pos, RandomSource rnd) {
        return SpawnRuleEngine.evaluate(DOCSpecies.SQUIRREL, lvl, type, pos, rnd).allowed();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
        SquirrelEntity squirrel = DOCEntities.SQUIRREL.get().create(pLevel);
        squirrel.setIsMale(this.random.nextBoolean());
        return squirrel;
    }

    @Override
    public CritterDietConfig getDietConfig() {
        return new CritterDietConfig(false, true, true, 0, 200, 200, 300);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TamingFeedsLeft", this.tamingFeedsLeft);
        tag.putByte("Morph", this.getMorph());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.tamingFeedsLeft = tag.getInt("TamingFeedsLeft");
        if (tag.contains("Morph")) this.setMorph(tag.getByte("Morph"));
    }

    public byte getMorph() { return this.entityData.get(MORPH); }
    public void setMorph(byte morph) { this.entityData.set(MORPH, morph); }

    @Override
    public boolean isDoingSpecialActivity() {
        // isClimbing() añadido en la fase 3: sin él, CritterCleanGoal y CritterMotherCleanBabyGoal
        // (prioridad 8 y 9) le robaban MOVE a SquirrelClimbGoal (16) y el acicalado arrancaba
        // colgando de un tronco; el stop() del climb goal llamaba a tryStartGlide() y la ardilla
        // planeaba acicalándose. Los otros cuatro goals que consultan este método ya comprobaban
        // isClimbing() por su cuenta, así que ampliarlo sólo los vuelve redundantes.
        return isGliding() || isEatingPlant() || isClimbing();
    }

    @Override
    public void onStartEatingItem(net.minecraft.world.item.ItemStack stack) {
        setEatingItem(stack);
        setEatingPlant(true);
    }

    @Override
    public void onStopEatingItem() {
        setEatingPlant(false);
        setEatingItem(net.minecraft.world.item.ItemStack.EMPTY);
    }


    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.5F;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return SoundEvents.CAT_PURR;
        }
        return SoundEvents.FOX_AMBIENT;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.FOX_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    @Override
    public SoundEvent getEatingSound(ItemStack pStack) {
        return SoundEvents.RABBIT_AMBIENT;
    }
}
