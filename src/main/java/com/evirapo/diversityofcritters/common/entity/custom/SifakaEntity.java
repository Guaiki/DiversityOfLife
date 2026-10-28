package com.evirapo.diversityofcritters.common.entity.custom;

import com.evirapo.diversityofcritters.client.animations.SifakaAnims;
import com.evirapo.diversityofcritters.client.animations.SifakaBabyAnims;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.ai.*;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.util.CritterDietConfig;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.SpawnRuleEngine;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Rabbit;
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

public class SifakaEntity extends DiverseCritter {

    // Sifaka de Verreaux: bosques secos y calurosos de Madagascar
    @Override
    public com.evirapo.diversityofcritters.common.temperature.TemperatureTier comfortTierMin() {
        return com.evirapo.diversityofcritters.common.temperature.TemperatureTier.MODERATE;
    }
    @Override
    public com.evirapo.diversityofcritters.common.temperature.TemperatureTier comfortTierMax() {
        return com.evirapo.diversityofcritters.common.temperature.TemperatureTier.HOT;
    }

    // Bosques secos y espinosos del oeste de Madagascar
    @Override
    public com.evirapo.diversityofcritters.common.humidity.HumidityTier comfortHumidityMin() {
        return com.evirapo.diversityofcritters.common.humidity.HumidityTier.ARID;
    }
    @Override
    public com.evirapo.diversityofcritters.common.humidity.HumidityTier comfortHumidityMax() {
        return com.evirapo.diversityofcritters.common.humidity.HumidityTier.MODERATE;
    }

    private static final EntityDataAccessor<Byte>    CLIMB_STATE   = SynchedEntityData.defineId(SifakaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float>   CLIMB_FACING_YAW = SynchedEntityData.defineId(SifakaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean>   IS_EATING_PLANT = SynchedEntityData.defineId(SifakaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> EATING_ITEM     = SynchedEntityData.defineId(SifakaEntity.class, EntityDataSerializers.ITEM_STACK);

    public static final byte CLIMB_NONE = 0;
    public static final byte CLIMB_UP   = 1;
    public static final byte CLIMB_DOWN = 2;
    public static final byte CLIMB_HANG = 3;

    private int tamingFeedsLeft = 0;
    private boolean climbRewardArmed = false;
    private int climbRewardCooldown = 0;

    private int walkHopTick = 0;
    private static final int   HOP_INTERVAL      = 10;
    private static final float HOP_POWER         = 0.38F;
    private static final float HOP_HORIZ_BOOST   = 1.6F;

    private int postDescentCooldown = 0;
    private static final int POST_DESCENT_COOLDOWN = 20;

    /**
     * Coronando un tronco: los pies ya pasaron la copa y el sifaka camina sobre ella a esa altura.
     * Null si no. Solo en el servidor. El sifaka no tiene mantle: así pasa encima sin el salto de
     * antes, que lo lanzaba por encima de un tronco de un bloque de ancho.
     */
    @Nullable
    private Direction crestDirection;
    /** Coordenada de la cara del tronco a lo largo de {@link #crestDirection}. */
    private double crestFace;
    /** Lo más que tarda en pasar encima antes de soltar la pared de todas formas, en ticks. */
    private static final int CREST_MAX_TICKS = 20;
    private int crestTicks;
    private boolean cachedHasClimbable = false;

    public SifakaEntity(EntityType<? extends TamableAnimal> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.setMaxUpStep(1);
        this.moveControl = new SifakaMoveControl(this);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CLIMB_STATE, CLIMB_NONE);
        this.entityData.define(CLIMB_FACING_YAW, 0.0F);
        this.entityData.define(IS_EATING_PLANT, false);
        this.entityData.define(EATING_ITEM, ItemStack.EMPTY);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    protected PathNavigation createNavigation(Level pLevel) {
        return new CritterNavigation(this, pLevel);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(0, new CustomFloatGoal(this));
        this.goalSelector.addGoal(1, new SleepBehaviorGoal(this));

        this.goalSelector.addGoal(2, new CritterCryChainGoal(this));
        this.goalSelector.addGoal(2, new FindCryingBabyGoal(this, 1.4D, 24.0D));
        this.goalSelector.addGoal(3, new CritterOrderedSitGoal(this));
        this.goalSelector.addGoal(4, new NaturalMateGoal(this, 1.0D));

        // Solo domesticados: en un salvaje pelearia con la alerta por el flag MOVE, tirando del bicho
        // HACIA la amenaza de la que el otro sistema lo aparta, y dos goals con intenciones opuestas
        // dan tembleque, no comportamiento. Gateado por isTame() no llegan a coexistir nunca: el
        // sistema de wariness se apaga entero al domesticar.
        //
        // Sigue valiendo para llamar con comida a una mascota en modo wandering, donde FollowOwnerGoal
        // no corre. Y deja el sigilo como canal para domesticar: agachado se congela el temporizador
        // de alerta y el critter se queda vigilandote mientras te acercas.
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.15D, Ingredient.of(DoCTags.Items.SIFAKA_FOOD), false) {
            @Override public boolean canUse() { return isTame() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isTame() && super.canContinueToUse(); }
        });

        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.2D, 8.0F, 2.0F, false) {
            @Override public boolean canUse() { return isFollowing() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isFollowing() && super.canContinueToUse(); }
        });

        this.goalSelector.addGoal(8, new CritterCleanChainGoal(this));
        this.goalSelector.addGoal(9, new CritterMotherCleanBabyGoal<>(this, SifakaEntity.class, 1.2D, 16.0D));
        this.goalSelector.addGoal(10, new CritterEatItemChainGoal(this, DoCTags.Items.SIFAKA_FOOD));

        this.goalSelector.addGoal(11, new FindFoodBowlGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(12, new FindWaterBowlGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(13, new CritterDrinkChainGoal(this));
        this.goalSelector.addGoal(14, new SifakaEatLeavesChainGoal(this, 1.1D, 12));
        this.goalSelector.addGoal(16, new SifakaClimbGoal(this, 1.1D, 16));

        this.goalSelector.addGoal(17, new CritterIdleVariantGoal(this));
        this.goalSelector.addGoal(18, new CritterFollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(19, new CritterExploreHeightGoal(this));
        this.goalSelector.addGoal(19, new WaterAvoidingRandomStrollGoal(this, 1.0D) {
            @Override public boolean canUse() {
                boolean canWander = !isTame() || isWandering();
                return canWander && super.canUse();
            }
        });
        this.goalSelector.addGoal(20, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(21, new RandomLookAroundGoal(this));

        // Gestos sueltos con el mismo sorteo que la civeta y la ardilla; sentarse y tumbarse los
        // lleva CritterIdleVariantGoal.
        this.goalSelector.addGoal(22, new CritterIdleGestureGoal(this, 120, 120)
                .add(IdleVariant.STRETCH, "stretch", 20)
                .add(IdleVariant.SNIFF_LEFT, "sniff_left", 25)
                .add(IdleVariant.SNIFF_RIGHT, "sniff_right", 25)
                .condition(c -> !this.isClimbing()));
    }

    // --- ANIMACIONES ---

    @Override
    public void registerAnimations() {
        StandardAnimation idle = new StandardAnimation("idle",
                new AnimSource(() -> SifakaAnims.Idle), Loop.REPEATING, LAYER_ADULT, 9, 2.0F);
        // El modelo viejo muestreaba la marcha a media velocidad (ageInTicks * 25 ms): mismo ritmo.
        StandardAnimation walk = new StandardAnimation("walk",
                new AnimSource(() -> SifakaAnims.Walk), Loop.REPEATING, LAYER_ADULT, 8, 2.0F);
        StandardAnimation run = new StandardAnimation("run",
                new AnimSource(() -> SifakaAnims.Run), Loop.REPEATING, LAYER_ADULT, 7, 2.0F);

        idle.blendInMs(250).blendOutMs(150);
        walk.blendInMs(150).blendOutMs(150).playbackSpeed(0.5F);
        run.blendInMs(150).blendOutMs(150).playbackSpeed(0.5F);

        idle.setPlayCondition(a -> groundLocomotionAllowed() && !this.isMoving());
        walk.setPlayCondition(a -> groundLocomotionAllowed() && this.isMoving() && !this.isSprinting());
        run.setPlayCondition(a -> groundLocomotionAllowed() && this.isMoving() && this.isSprinting());

        StandardAnimation sitEnter = new StandardAnimation("ordered_sit_enter",
                new AnimSource(() -> SifakaAnims.SIT_STARTING), Loop.PLAY_ONCE, LAYER_ADULT, P_ORDERED_SIT, 0.5F);
        StandardAnimation sitLoop = new StandardAnimation("ordered_sit_loop",
                new AnimSource(() -> SifakaAnims.SIT_IDLE), Loop.REPEATING, LAYER_ADULT, P_ORDERED_SIT, 5.0F);
        StandardAnimation sitExit = new StandardAnimation("ordered_sit_exit",
                new AnimSource(() -> SifakaAnims.SIT_ENDING), Loop.PLAY_ONCE, LAYER_ADULT, P_ORDERED_SIT, 0.5F);

        sitEnter.blendInMs(150).blendOutMs(120).setNextAnimation("ordered_sit_loop");
        sitLoop.blendInMs(120).blendOutMs(150);
        sitLoop.setPlayCondition(a -> this.isOrderedToSit());
        sitExit.blendInMs(120).blendOutMs(200).locksMovement();

        this.animator().register(idle, walk, run, sitEnter, sitLoop, sitExit);

        registerChain("drink", () -> SifakaAnims.DRINK_STARTING, 0.25F,
                () -> SifakaAnims.DRINK_IDLE, 1.0F,
                () -> SifakaAnims.DRINK_ENDING, 0.25F, P_ACTIVITY)
                .setPlayCondition(a -> this.IsDrinking());

        registerChain("clean", () -> SifakaAnims.GROOMING_STARTING, 0.5F,
                () -> SifakaAnims.GROOMING_IDLE, 0.5F,
                () -> SifakaAnims.GROOMING_ENDING, 0.5F, P_ACTIVITY)
                .setPlayCondition(a -> this.isCleaning());

        registerChain("eat", () -> SifakaAnims.EATING_STARTING, 0.25F,
                () -> SifakaAnims.EATING_IDLE, 0.5F,
                () -> SifakaAnims.EATING_ENDING, 0.25F, P_ACTIVITY)
                .setPlayCondition(a -> this.isEatingPlant());

        // Same mapping the old client state machine used: STRETCH played stand_up, and the two sniffs
        // played SniffRight / SniffRight2.
        registerSingle("stretch", () -> SifakaAnims.stand_up, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.STRETCH);
        registerSingle("sniff_left", () -> SifakaAnims.SniffRight, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SNIFF_LEFT);
        registerSingle("sniff_right", () -> SifakaAnims.SniffRight2, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SNIFF_RIGHT);

        registerChain("idle_sit", () -> SifakaAnims.SIT_STARTING, 0.5F,
                () -> SifakaAnims.SIT_IDLE, 5.0F,
                () -> SifakaAnims.SIT_ENDING, 0.5F, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SIT);

        registerChain("idle_stand_up", () -> SifakaAnims.LAY_STARTING, 1.0F,
                () -> SifakaAnims.LAY_IDLE, 1.0F,
                () -> SifakaAnims.LAY_ENDING, 1.0F, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.STAND_UP);

        StandardAnimation climbUp = new StandardAnimation("climb_up",
                new AnimSource(() -> SifakaAnims.CLIMBING_UP), Loop.REPEATING, LAYER_ADULT, P_CLIMB, 1.0F);
        StandardAnimation climbDown = new StandardAnimation("climb_down",
                new AnimSource(() -> SifakaAnims.CLIMBING_DOWN), Loop.REPEATING, LAYER_ADULT, P_CLIMB, 1.0F);
        StandardAnimation climbHang = new StandardAnimation("climb_hang",
                new AnimSource(() -> SifakaAnims.CLIMB_IDLE), Loop.REPEATING, LAYER_ADULT, P_CLIMB, 4.0F);
        StandardAnimation swim = new StandardAnimation("swim",
                new AnimSource(() -> SifakaAnims.Swim), Loop.REPEATING, LAYER_ADULT, P_SWIM, 2.0F);
        StandardAnimation attack = new StandardAnimation("attack",
                new AnimSource(() -> SifakaAnims.Attack), Loop.PLAY_ONCE, LAYER_ADULT, P_ATTACK, 0.5F);
        StandardAnimation sleepEnter = new StandardAnimation("sleep_enter",
                new AnimSource(() -> SifakaAnims.preparing_sleep), Loop.REPEATING, LAYER_ADULT, P_SLEEP, 2.0F);
        StandardAnimation sleepLoop = new StandardAnimation("sleep_loop",
                new AnimSource(() -> SifakaAnims.Sleep), Loop.REPEATING, LAYER_ADULT, P_SLEEP, 4.0F);
        StandardAnimation sleepExit = new StandardAnimation("sleep_exit",
                new AnimSource(() -> SifakaAnims.awakening), Loop.REPEATING, LAYER_ADULT, P_SLEEP, 2.0F);

        climbUp.blendInMs(100).blendOutMs(100);
        climbDown.blendInMs(100).blendOutMs(100);
        climbHang.blendInMs(150).blendOutMs(150);
        // The old model sped swimming up with limbSwingAmount; the library takes a fixed multiplier.
        swim.blendInMs(200).blendOutMs(200).playbackSpeed(1.5F);
        attack.blendInMs(80).blendOutMs(120);
        sleepEnter.blendInMs(200).blendOutMs(200);
        sleepLoop.blendInMs(300).blendOutMs(300);
        sleepExit.blendInMs(200).blendOutMs(200);

        climbUp.setPlayCondition(a -> this.isClimbingUp());
        climbDown.setPlayCondition(a -> this.isClimbingDown());
        climbHang.setPlayCondition(a -> this.isHanging());
        swim.setPlayCondition(a -> this.isInWaterOrBubble() && this.isAlive());
        sleepEnter.setPlayCondition(a -> this.getSleepState() == SleepState.PREPARING);
        sleepLoop.setPlayCondition(a -> this.getSleepState() == SleepState.SLEEPING);
        sleepExit.setPlayCondition(a -> this.getSleepState() == SleepState.AWAKENING);

        this.animator().register(climbUp, climbDown, climbHang, swim, attack,
                sleepEnter, sleepLoop, sleepExit);

        this.registerBabyAnimations();
    }

    private void registerBabyAnimations() {
        StandardAnimation babyIdle = registerSingle("baby_idle",
                () -> SifakaBabyAnims.idle, 4.0F, Loop.REPEATING, P_IDLE, LAYER_BABY);
        StandardAnimation babyCrawl = registerSingle("baby_crawl",
                () -> SifakaBabyAnims.move, 4.0F, Loop.REPEATING, P_WALK, LAYER_BABY);

        babyIdle.setPlayCondition(a -> this.isNewborn() && !this.isMoving());
        babyCrawl.setPlayCondition(a -> this.isNewborn() && this.isMoving());

        registerSingle("baby_sleep_enter", () -> SifakaBabyAnims.preparing_sleep,
                1.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.PREPARING);
        registerSingle("baby_sleep_loop", () -> SifakaBabyAnims.sleep,
                4.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.SLEEPING);
        registerSingle("baby_sleep_exit", () -> SifakaBabyAnims.awakening,
                1.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.AWAKENING);

        registerChain("cry",
                () -> SifakaBabyAnims.preparing_cry, 0.5F,
                () -> SifakaBabyAnims.cry, 1.0F,
                () -> SifakaBabyAnims.stopping_cry, 0.5F, P_ACTIVITY, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.isCrying());
    }

    private boolean groundLocomotionAllowed() {
        return this.isAlive()
                && !this.isNewborn()
                && this.getSleepState() == SleepState.AWAKE
                && !this.isClimbing()
                && !this.isInWaterOrBubble();
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            this.updateClimbState();

            boolean isNewborn = this.isNewborn();
            double desiredSpeed = isNewborn ? 0.095D : 0.2D;
            float desiredStep   = isNewborn ? 0.5F : 1.0F;

            if (this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() != desiredSpeed) {
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(desiredSpeed);
            }
            if (this.maxUpStep() != desiredStep) {
                this.setMaxUpStep(desiredStep);
            }

            if (this.isNewborn() && this.getHunger() < 3000) {
                if (!this.isCrying()) {
                    this.setCrying(true);
                    this.playSound(SoundEvents.FOX_SCREECH, 0.5F, 2.0F);
                }
                if (this.tickCount % 80 == 0) {
                    this.playSound(SoundEvents.FOX_SCREECH, 0.5F, 2.0F);
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
                if (this.getRandom().nextInt(3) == 0) {
                    for (int j = 0; j < 4; ++j) {
                        double d2 = this.random.nextDouble() / 2;
                        double d3 = this.random.nextDouble() / 2;
                        Vec3 v = this.getDeltaMovement();
                        this.level().addParticle(ParticleTypes.SPLASH, washingPos.getX() + d2, washingPos.getY() + 0.8F, washingPos.getZ() + d3, v.x, v.y, v.z);
                    }
                }
            } else {
                setIsDrinking(false);
            }
        }
    }

    @Override
    public void aiStep() {
        ItemStack stack = this.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!stack.isEmpty() && stack.is(DoCTags.Items.SIFAKA_FOOD) && !this.isNewborn()) {
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

        super.aiStep();

        if (!this.level().isClientSide) {
            this.doWalkHop();
        }

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

    private void doWalkHop() {
        if (this.isNewborn() || !this.onGround() || this.isClimbing() || this.isInWaterOrBubble()) {
            return;
        }
        if (this.level().getBlockState(this.blockPosition().below()).is(DoCTags.Blocks.CIVET_CLIMBABLE)
                || this.hasAdjacentClimbableBlock()) {
            this.walkHopTick = 3;
            return;
        }
        // isVariantActive se rellenaba solo en el cliente: en servidor, que es donde se salta, valía
        // siempre false. isPerformingActivity() cubre beber, acicalarse, sentarse, variantes de
        // reposo, comer y cualquier clip que clave el movimiento.
        boolean busy = this.getSleepState() != DiverseCritter.SleepState.AWAKE
                || this.isOrderedSitPlaying()
                || this.isPerformingActivity();
        if (busy) {
            this.walkHopTick = 3;
            return;
        }
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        boolean moving = (dx * dx + dz * dz) > 0.0004;
        if (!moving) {
            this.walkHopTick = 0;
            return;
        }
        if (this.walkHopTick > 0) {
            this.walkHopTick--;
        } else {
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x * HOP_HORIZ_BOOST, HOP_POWER, motion.z * HOP_HORIZ_BOOST);
            this.hasImpulse = true;
            this.walkHopTick = HOP_INTERVAL;
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
        self().setDeltaMovement(self().getDeltaMovement().add(0.0D, 0.04F * self().getAttributeValue(ForgeMod.SWIM_SPEED.get()) / 3, 0.0D));
    }

    @Override
    public void sinkInFluid(FluidType type) {
        self().setDeltaMovement(self().getDeltaMovement().add(0.0D, -0.04F * self().getAttributeValue(ForgeMod.SWIM_SPEED.get()) / 6, 0.0D));
    }

    @Override
    public InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);

        boolean isItemTamingMeat = itemstack.is(DoCTags.Items.SIFAKA_FOOD);
        boolean isNurserBottle   = itemstack.is(DOCItems.FILLED_NURSER_BOTTLE.get());

        boolean canTameWithThis = false;
        if (!this.isTame()) {
            if (this.isBaby() && !this.isJuvenile()) {
                canTameWithThis = isNurserBottle;
            } else {
                canTameWithThis = isItemTamingMeat;
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

        boolean isMeatFood = itemstack.is(DoCTags.Items.SIFAKA_FOOD);
        if (isMeatFood && !this.isBaby()) {
            if (this.getHunger() < this.maxHunger() || this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide()) {
                    this.setHunger(this.maxHunger());
                    var foodProps = itemstack.isEdible() ? itemstack.getFoodProperties(this) : null;
                    if (foodProps != null) this.heal((float) foodProps.getNutrition());
                    if (!pPlayer.getAbilities().instabuild) itemstack.shrink(1);
                    this.level().broadcastEntityEvent(this, (byte)7);
                    this.gameEvent(GameEvent.EAT, this);
                }
                return InteractionResult.SUCCESS;
            }
        }

        return super.mobInteract(pPlayer, pHand);
    }

    @Override
    public boolean canPickUpLoot() { return !this.isNewborn(); }

    @Override
    public boolean wantsToPickUp(ItemStack pStack) { return false; }

    @Override
    public boolean canAttack(LivingEntity pTarget) {
        return super.canAttack(pTarget) && ((this.getLastHurtByMob() != null && this.getLastHurtByMob() == pTarget) || this.isHungry());
    }

    @Override
    public boolean isFood(ItemStack pStack) { return false; }

    @Override public int maxHunger()      { return 4000; }
    @Override public int maxThirst()      { return 4000; }
    @Override public int maxEnrichment()  { return 4000; }
    @Override public int maxHygiene()     { return 4000; }
    // Ticks de juego, y exactamente la longitud del clip: 2.0 s = 40 en el adulto, 1.0 s = 20 en
    // el recién nacido. Ver la nota en SleepBehaviorGoal#phaseUntilTick.
    @Override public int getPreparingSleepDuration() { return this.isNewborn() ? 20 : 40; }
    @Override public int getAwakeningDuration()      { return this.isNewborn() ? 20 : 40; }
    @Override protected boolean getDefaultDiurnal()  { return true; }
    @Override protected double getHungerLossPerSecond()     { return 3.33; }
    @Override protected double getThirstLossPerSecond()     { return 5.0;  }
    @Override protected double getEnrichmentLossPerSecond() { return 2.5;  }
    @Override protected double getHygieneLossPerSecond()    { return 1.5;  }

    public byte getClimbState() { return this.entityData.get(CLIMB_STATE); }
    public void setClimbState(byte state) {
        byte prev = this.getClimbState();
        this.entityData.set(CLIMB_STATE, state);
        if (prev == CLIMB_DOWN && state == CLIMB_NONE) {
            this.postDescentCooldown = POST_DESCENT_COOLDOWN;
        }
    }

    public boolean isInPostDescentCooldown() { return this.postDescentCooldown > 0; }
    @Override public boolean canClimb()  { return true; }
    public boolean isClimbing()     { return getClimbState() != CLIMB_NONE; }
    public boolean isClimbingUp()   { return getClimbState() == CLIMB_UP; }
    public boolean isClimbingDown() { return getClimbState() == CLIMB_DOWN; }
    public boolean isHanging()      { return getClimbState() == CLIMB_HANG; }

    @Override
    public boolean onClimbable() { return isClimbing() && this.cachedHasClimbable; }

    public boolean hasAdjacentClimbableBlock() {
        return hasAdjacentClimbableBlockInternal();
    }

    private boolean hasAdjacentClimbableBlockInternal() {
        AABB box = this.getBoundingBox();
        double probeDistance = 0.3D;
        int footY = Mth.floor(box.minY);
        int eyeY  = Mth.floor(box.minY + this.getBbHeight() * 0.5);

        double cx   = (box.minX + box.maxX) * 0.5;
        double cz   = (box.minZ + box.maxZ) * 0.5;
        double halfW = (box.maxX - box.minX) * 0.5;
        double halfD = (box.maxZ - box.minZ) * 0.5;

        int[][] offsets = {
                { Mth.floor(cx + halfW + probeDistance), Mth.floor(cz) },
                { Mth.floor(cx - halfW - probeDistance), Mth.floor(cz) },
                { Mth.floor(cx), Mth.floor(cz + halfD + probeDistance) },
                { Mth.floor(cx), Mth.floor(cz - halfD - probeDistance) }
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

        double cx   = (box.minX + box.maxX) * 0.5;
        double cz   = (box.minZ + box.maxZ) * 0.5;
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
        if (this.isNewborn()) {
            if (this.getClimbState() != CLIMB_NONE) {
                this.entityData.set(CLIMB_STATE, CLIMB_NONE);
            }
            this.cachedHasClimbable = false;
            return;
        }

        if (this.postDescentCooldown > 0) this.postDescentCooldown--;
        if (this.getClimbState() != CLIMB_UP) {
            // Otro sistema cambió el estado (colgarse, por ejemplo): la coronación quedó a medias.
            this.crestDirection = null;
        }

        this.cachedHasClimbable = this.hasAdjacentClimbableBlockInternal();
        boolean hasClimbable  = this.cachedHasClimbable;
        byte currentState     = this.getClimbState();

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
            if (this.crestDirection != null) {
                this.updateCrest();
                return;
            }
            if (!this.onGround() && this.startCrest()) {
                return;
            }
            if (!hasClimbable && !this.onGround()) {
                // Pasada la copa sin sitio encima: suelta la pared, sin salto.
                this.entityData.set(CLIMB_STATE, CLIMB_NONE);
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

        if (this.postDescentCooldown > 0) return;
        if (!this.horizontalCollision || !hasClimbable) return;
        if (this.isNewborn() || this.onGround()) return;
        if (this.isInWater() || this.isInWaterOrBubble()) return;

        boolean pathGoesUp = false;
        net.minecraft.world.level.pathfinder.Path path = this.navigation.getPath();
        if (path != null && !path.isDone()) {
            int idx   = path.getNextNodeIndex();
            int curY  = Mth.floor(this.getY());
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

    /** Lo que avanza por tick al pasar sobre la copa, en bloques: los pies van ya por encima. */
    public static final double CREST_SPEED = 0.15D;

    public boolean isCresting() {
        return this.crestDirection != null;
    }

    @Nullable
    public Direction getCrestDirection() {
        return this.crestDirection;
    }

    /**
     * Si los pies acaban de pasar la copa del tronco que trepa, y encima hay sitio para el cuerpo,
     * empieza a pasar sobre ella.
     */
    private boolean startCrest() {
        final Direction toward = Direction.fromYRot(this.getClimbFacingYaw());
        final double halfWidth = this.getBbWidth() * 0.5D;
        final AABB box = this.getBoundingBox();
        final double cx = (box.minX + box.maxX) * 0.5D;
        final double cz = (box.minZ + box.maxZ) * 0.5D;
        // El bloque de la cara, justo delante del hitbox.
        final int wallX = Mth.floor(cx + toward.getStepX() * (halfWidth + 0.1D));
        final int wallZ = Mth.floor(cz + toward.getStepZ() * (halfWidth + 0.1D));
        final int feetY = Mth.floor(box.minY + 1.0e-3D);
        final BlockPos top = new BlockPos(wallX, feetY - 1, wallZ);
        if (!this.level().getBlockState(top).is(DoCTags.Blocks.CIVET_CLIMBABLE)
                || top.getY() + 1.0D > box.minY + 1.0e-3D) {
            return false;
        }
        final int headY = Mth.floor(box.minY + this.getBbHeight());
        for (int y = feetY; y <= headY; y++) {
            final BlockPos above = new BlockPos(wallX, y, wallZ);
            if (!this.level().getBlockState(above).getCollisionShape(this.level(), above).isEmpty()) {
                return false;
            }
        }
        this.crestDirection = toward;
        this.crestFace = toward.getStepX() != 0
                ? (toward.getStepX() > 0 ? wallX : wallX + 1.0D)
                : (toward.getStepZ() > 0 ? wallZ : wallZ + 1.0D);
        this.crestTicks = 0;
        return true;
    }

    /** Sigue pasando sobre la copa; con el cuerpo entero encima, deja de trepar. */
    private void updateCrest() {
        final Direction toward = this.crestDirection;
        final double along = toward.getStepX() != 0 ? this.getX() : this.getZ();
        final double past = (toward.getStepX() + toward.getStepZ()) * (along - this.crestFace);
        if (past >= this.getBbWidth() * 0.5D || ++this.crestTicks > CREST_MAX_TICKS) {
            this.crestDirection = null;
            this.entityData.set(CLIMB_STATE, CLIMB_NONE);
            // La ruta subía por nodos pegados a la cara; volver a ellos lo tiraría por el borde.
            this.navigation.recomputePath();
            return;
        }
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

    public boolean isEatingPlant() { return this.entityData.get(IS_EATING_PLANT); }
    public void setEatingPlant(boolean v) { this.entityData.set(IS_EATING_PLANT, v); }
    public ItemStack getEatingItem() { return this.entityData.get(EATING_ITEM); }
    public void setEatingItem(ItemStack stack) { this.entityData.set(EATING_ITEM, stack.copy()); }

    @Override
    public void onStartEatingItem(ItemStack stack) {
        setEatingItem(stack);
        setEatingPlant(true);
    }

    @Override
    public void onStopEatingItem() {
        setEatingPlant(false);
        setEatingItem(ItemStack.EMPTY);
    }

    @Override
    public boolean isDoingSpecialActivity() {
        return isEatingPlant();
    }

    public static boolean checkSifakaSpawnRules(EntityType<SifakaEntity> t, LevelAccessor lvl, MobSpawnType type, BlockPos pos, RandomSource rnd) {
        return SpawnRuleEngine.evaluate(DOCSpecies.SIFAKA, lvl, type, pos, rnd).allowed();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
        SifakaEntity sifaka = DOCEntities.SIFAKA.get().create(pLevel);
        sifaka.setIsMale(this.random.nextBoolean());
        return sifaka;
    }

    @Override
    public CritterDietConfig getDietConfig() {
        return new CritterDietConfig(false, true, true, 0, 200, 200, 300);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TamingFeedsLeft", this.tamingFeedsLeft);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.tamingFeedsLeft = tag.getInt("TamingFeedsLeft");
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (this.isSleeping()) return SoundEvents.CAT_PURR;
        if (this.isTame())     return SoundEvents.CAT_AMBIENT;
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
        return SoundEvents.CAT_EAT;
    }

    @Override
    public boolean doHurtTarget(Entity pEntity) {
        boolean flag = super.doHurtTarget(pEntity);
        if (flag) {
            this.animator().play(this.animator().getByName("attack"));
            this.playSound(SoundEvents.FOX_BITE, 1.0F, 1.0F);
            if (!this.level().isClientSide && isPrey(pEntity) && pEntity instanceof LivingEntity living && living.isDeadOrDying()) {
                this.setEnrichment(this.maxEnrichment());
            }
        }
        return flag;
    }

    private boolean isPrey(Entity entity) {
        return entity instanceof Rabbit || entity instanceof Chicken;
    }
}