package com.evirapo.diversityofcritters.common.entity.custom;

import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.ai.*;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.IDiggingCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.IScratchingCritter;
import com.evirapo.diversityofcritters.common.entity.util.CritterClaims;
import com.evirapo.diversityofcritters.common.entity.util.CritterDietConfig;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import com.evirapo.diversityofcritters.client.animations.CivetAnims;
import com.evirapo.diversityofcritters.client.animations.CivetBabyAnims;
import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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

public class CivetEntity extends DiverseCritter
        implements IDiggingCritter, IScratchingCritter {

    // Civeta de palma bandeada: tropical del sudeste asiático
    @Override
    public com.evirapo.diversityofcritters.common.temperature.TemperatureTier comfortTierMin() {
        return com.evirapo.diversityofcritters.common.temperature.TemperatureTier.WARM;
    }
    @Override
    public com.evirapo.diversityofcritters.common.temperature.TemperatureTier comfortTierMax() {
        return com.evirapo.diversityofcritters.common.temperature.TemperatureTier.HOT;
    }

    // Selva tropical húmeda del sudeste asiático
    @Override
    public com.evirapo.diversityofcritters.common.humidity.HumidityTier comfortHumidityMin() {
        return com.evirapo.diversityofcritters.common.humidity.HumidityTier.HUMID;
    }
    @Override
    public com.evirapo.diversityofcritters.common.humidity.HumidityTier comfortHumidityMax() {
        return com.evirapo.diversityofcritters.common.humidity.HumidityTier.WET;
    }

    // Sin AnimationState: TODAS las animaciones de la civeta, adulta y bebe, viven en el
    // MobAnimator que hereda de DiverseCritter -- el adulto en la capa 0 y el bebe en la 1.
    // Las maquinas de estado solo-cliente que habia aqui (setupAnimationStatesClient con sus
    // doce contadores client*Tick, y handleCryingAnimationClient) daban la fase equivocada a
    // quien entrara en rango a mitad de una secuencia; ahora el servidor manda y se sincroniza.

    private static final EntityDataAccessor<Byte> CLIMB_STATE = SynchedEntityData.defineId(CivetEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> CLIMB_FACING_YAW = SynchedEntityData.defineId(CivetEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_DIGGING = SynchedEntityData.defineId(CivetEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_SCRATCHING = SynchedEntityData.defineId(CivetEntity.class, EntityDataSerializers.BOOLEAN);

    public static final byte CLIMB_NONE = 0;
    public static final byte CLIMB_UP   = 1;
    public static final byte CLIMB_DOWN = 2;
    public static final byte CLIMB_HANG = 3;

    // --- VARIABLES ---
    private LookForFoodItems forFoodGoal;
    private int tamingFeedsLeft = 0;
    private boolean climbRewardArmed = false;
    private int climbRewardCooldown = 0;

    private int postDescentCooldown = 0;
    private static final int POST_DESCENT_COOLDOWN = 20;
    private boolean cachedHasClimbable = false;

    public CivetEntity(EntityType<? extends TamableAnimal> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.setMaxUpStep(1);
        this.moveControl = new CivetMoveControl(this);
    }

    // --- SETUP ---
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CLIMB_STATE, CLIMB_NONE);
        this.entityData.define(CLIMB_FACING_YAW, 0.0F);
        this.entityData.define(IS_DIGGING, false);
        this.entityData.define(IS_SCRATCHING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    protected PathNavigation createNavigation(Level pLevel) {
        return new CritterNavigation(this, pLevel);
    }

    // --- GOALS ---
    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(0, new CustomFloatGoal(this));
        this.goalSelector.addGoal(1, new SleepBehaviorGoal(this));

        // Sustituye a BabyCryGoal: aquella dejaba las tres fases del llanto a contadores que solo
        // existian en el cliente, y no retenia durante el clip de parada.
        this.goalSelector.addGoal(2, new CritterCryChainGoal(this));
        this.goalSelector.addGoal(2, new FindCryingBabyGoal(this, 1.4D, 24.0D));
        // Sustituye a SitWhenOrderedToGoal: mismo comportamiento, pero ademas es dueño de la cadena
        // ordered_sit_* y su clip de salida ya no se corta al levantarse.
        this.goalSelector.addGoal(3, new CritterOrderedSitGoal(this));
        this.goalSelector.addGoal(4, new NaturalMateGoal(this, 1.0D));

        this.goalSelector.addGoal(5, new TemptGoal(this, 1.15D, Ingredient.of(Items.BEEF), false));

        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.2D, 8.0F, 2.0F, false) {
            @Override public boolean canUse() { return isFollowing() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isFollowing() && super.canContinueToUse(); }
        });

        // Sustituye a AnimatedAttackGoal. Aquel encendia el flag sincronizado ATTACKING y la
        // animacion se colgaba de el con una playCondition, asi que el clip empezaba cuando el flag
        // llegaba al cliente y no cuando caia el golpe. Este dispara el clip en el instante del
        // impacto, que es lo que hace que el swing se lea.
        //
        // Ojo: AnimatableMeleeAttackGoal NO reparte dano por su cuenta -- hace swing() y delega en
        // onAttack. El doHurtTarget va aqui, igual que lo hacia performAttack().
        this.goalSelector.addGoal(7, new AnimatableMeleeAttackGoal(this, 1.25D, true)
                .onAttack((target, anim) -> {
                    this.doHurtTarget(target);
                    this.animator().play(this.animator().getByName("attack"));
                }));

        this.goalSelector.addGoal(8, new CritterCleanChainGoal(this));
        this.goalSelector.addGoal(9, new CritterMotherCleanBabyGoal<>(this, CivetEntity.class, 1.2D, 16.0D));
        this.forFoodGoal = new LookForFoodItems(this, DoCTags.Items.MEATS);
        this.goalSelector.addGoal(10, this.forFoodGoal);

        this.goalSelector.addGoal(11, new FindFoodBowlGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(12, new FindWaterBowlGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(13, new CritterDrinkChainGoal(this));
        this.goalSelector.addGoal(14, new FindDigBoxGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(15, new CritterScratchLogChainGoal(this, 1.1D, 16));
        this.goalSelector.addGoal(16, new CritterDigDirtChainGoal(this));
        this.goalSelector.addGoal(16, new CivetClimbGoal(this, 1.1D, 16));

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

        // Sin flags: la prioridad da igual, va el ultimo por legibilidad. Los pesos son los del
        // sorteo original (20 / 25 / 25); sentarse y tumbarse los lleva CritterIdleVariantGoal.
        this.goalSelector.addGoal(22, new CritterIdleGestureGoal(this, 120, 120)
                .add(IdleVariant.STRETCH, "stretch", 20)
                .add(IdleVariant.SNIFF_LEFT, "sniff_left", 25)
                .add(IdleVariant.SNIFF_RIGHT, "sniff_right", 25)
                .condition(c -> !this.isClimbing()));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Rabbit.class, false, (living) -> this.isHungry()));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Chicken.class, false, (living) -> this.isHungry()));
    }

    // --- ANIMACIONES ---

    /**
     * Locomocion de suelo al estilo Athenian/Arpy: tres animaciones REPEATING en la capa 0 con
     * playCondition mutuamente excluyentes. El crossfade lo hace el BlendState de DeluxeLib, no
     * pesos calculados a mano en el modelo, y el clip corre a su propio ritmo en vez de ir atado a
     * limbSwing (adios a {@code sprintProgress} y a {@code animateWalkWithWeight}).
     *
     * <p>Las duraciones son las longitudes reales de los clips en {@link CivetAnims}; si se vuelven
     * a exportar desde Blockbench con otra longitud hay que actualizarlas aqui.
     */
    @Override
    public void registerAnimations() {
        StandardAnimation idle = new StandardAnimation("idle",
                new AnimSource(() -> CivetAnims.IDLE), Loop.REPEATING, 0, 9, 2.0F);
        StandardAnimation walk = new StandardAnimation("walk",
                new AnimSource(() -> CivetAnims.WALK), Loop.REPEATING, 0, 8, 0.9662F);
        StandardAnimation run = new StandardAnimation("run",
                new AnimSource(() -> CivetAnims.RUN), Loop.REPEATING, 0, 7, 0.6667F);

        idle.blendInMs(250).blendOutMs(150);
        walk.blendInMs(150).blendOutMs(150);
        run.blendInMs(150).blendOutMs(150);

        // Un unico signo de movimiento: isMoving(), que combina velocidad y camino pendiente pasados
        // por la histeresis de DiverseCritter.aiStep. Leer la navegacion en crudo aqui hacia
        // parpadear idle/walk cada vez que algo recreaba la ruta desde fuera de un goal.
        idle.setPlayCondition(a -> groundLocomotionAllowed() && !this.isMoving());
        walk.setPlayCondition(a -> groundLocomotionAllowed() && this.isMoving() && !this.isSprinting());
        run.setPlayCondition(a -> groundLocomotionAllowed() && this.isMoving() && this.isSprinting());

        // --- Sentarse por orden: cadena de 3 fases dirigida por CritterOrderedSitGoal ---
        // Entrada y salida son PLAY_ONCE y solo arrancan con play() desde el goal; eso es lo que
        // permite que la salida suene entera cuando isOrderedToSit() ya es false. El bucle SI lleva
        // playCondition: sin ella MobAnimator.tick() lo auto-arrancaria y, al ganarle a idle, la
        // civeta se quedaria sentada para siempre aunque no estuviera domesticada.
        //
        // Misma prioridad las tres, para que el bucle no auto-arranque encima de su propia entrada
        // (el guard de MobAnimator es `current.getPriority() <= base.getPriority()`).
        StandardAnimation sitEnter = new StandardAnimation("ordered_sit_enter",
                new AnimSource(() -> CivetAnims.SIT_STARTING), Loop.PLAY_ONCE, 0, P_ORDERED_SIT, 0.5F);
        StandardAnimation sitLoop = new StandardAnimation("ordered_sit_loop",
                new AnimSource(() -> CivetAnims.SIT_IDLE), Loop.REPEATING, 0, P_ORDERED_SIT, 4.0F);
        StandardAnimation sitExit = new StandardAnimation("ordered_sit_exit",
                new AnimSource(() -> CivetAnims.SIT_ENDING), Loop.PLAY_ONCE, 0, P_ORDERED_SIT, 1.0F);

        sitEnter.blendInMs(150).blendOutMs(120).setNextAnimation("ordered_sit_loop");
        sitLoop.blendInMs(120).blendOutMs(150);
        sitLoop.setPlayCondition(a -> this.isOrderedToSit());
        // locksMovement: AnimationLockGoal congela a la civeta mientras dura la salida, que es lo que
        // impedia que se deslizara con la pose de levantarse a medias.
        sitExit.blendInMs(120).blendOutMs(200).locksMovement();

        this.animator().register(idle, walk, run, sitEnter, sitLoop, sitExit);

        // --- Actividades (prioridad 6) ---
        registerChain("drink", () -> CivetAnims.DRINK_STARTING, 0.25F,
                () -> CivetAnims.DRINK_IDLE, 1.0F,
                () -> CivetAnims.DRINK_ENDING, 0.25F, P_ACTIVITY)
                .setPlayCondition(a -> this.IsDrinking());

        registerChain("clean", () -> CivetAnims.GROOMING_STARTING, 0.5F,
                () -> CivetAnims.GROOMING_IDLE, 0.5F,
                () -> CivetAnims.GROOMING_ENDING, 0.5F, P_ACTIVITY)
                .setPlayCondition(a -> this.isCleaning());

        // Cavar y aranar son lo propio de la civeta. Los dirigen CritterDigDirtChainGoal y
        // CritterScratchLogChainGoal, que llegan al flag por IDiggingCritter / IScratchingCritter en
        // vez de por un instanceof, asi que cualquier especie que implemente esas interfaces los
        // hereda tal cual. dig_* lo comparte ademas FindDigBoxGoal (la caja de arena).
        registerChain("dig", () -> CivetAnims.DIGGING_STARTING, 0.5F,
                () -> CivetAnims.DIGGING_IDLE, 0.5F,
                () -> CivetAnims.DIGGING_ENDING, 0.5F, P_ACTIVITY)
                .setPlayCondition(a -> this.isDigging());

        registerChain("scratch", () -> CivetAnims.SCRATCHING_STARTING, 0.5F,
                () -> CivetAnims.SCRATCHING_IDLE, 0.5F,
                () -> CivetAnims.SCRATCHING_ENDING, 0.5F, P_ACTIVITY)
                .setPlayCondition(a -> this.isScratching());

        // --- Variantes de reposo (prioridad 7) ---
        // Las de un solo clip no tienen entrada ni salida: al terminar, el bucle se para por su
        // condicion e idle entra con su propio crossfade.
        //
        // El export de Blockbench trae cruzados dos nombres, igual que el de la ardilla: lo que
        // llama "Stand up" es en realidad estirarse (clip suelto, variante STRETCH) y la cadena
        // "Lay" es tumbarse, que el mod dispara con la variante llamada STAND_UP. La traduccion
        // esta en tools/swap_civet_anims.py.
        registerSingle("stretch", () -> CivetAnims.STAND_UP, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.STRETCH);
        registerSingle("sniff_left", () -> CivetAnims.SNIFF_LEFT, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SNIFF_LEFT);
        registerSingle("sniff_right", () -> CivetAnims.SNIFF_RIGHT, 2.0F, Loop.PLAY_ONCE, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SNIFF_RIGHT);

        registerChain("idle_sit", () -> CivetAnims.SIT_STARTING, 0.5F,
                () -> CivetAnims.SIT_IDLE, 4.0F,
                () -> CivetAnims.SIT_ENDING, 1.0F, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.SIT);

        // CritterIdleVariantGoal.chooseChain() nombra esta cadena "idle_stand_up_*" aunque los clips
        // sean los de tumbarse: el nombre viene del IdleVariant, no del clip.
        registerChain("idle_stand_up", () -> CivetAnims.LAY_STARTING, 1.0F,
                () -> CivetAnims.LAY_IDLE, 4.0F,
                () -> CivetAnims.LAY_ENDING, 1.0F, P_IDLE_VARIANT)
                .setPlayCondition(a -> this.getIdleVariant() == IdleVariant.STAND_UP);

        // --- Estados continuos: no los dirige ningun goal, solo su condicion ---
        // Prioridades 1-4, o sea por encima de las actividades: si la civeta trepa, nada, duerme o
        // esta atacando, eso manda sobre cualquier actividad o locomocion.
        StandardAnimation climbUp = new StandardAnimation("climb_up",
                new AnimSource(() -> CivetAnims.CLIMBING_UP), Loop.REPEATING, 0, P_CLIMB, 1.0F);
        StandardAnimation climbDown = new StandardAnimation("climb_down",
                new AnimSource(() -> CivetAnims.CLIMBING_DOWN), Loop.REPEATING, 0, P_CLIMB, 1.0F);
        StandardAnimation climbHang = new StandardAnimation("climb_hang",
                new AnimSource(() -> CivetAnims.CLIMB_IDLE), Loop.REPEATING, 0, P_CLIMB, 4.0F);
        StandardAnimation swim = new StandardAnimation("swim",
                new AnimSource(() -> CivetAnims.SWIM), Loop.REPEATING, 0, P_SWIM, 2.0F);
        // PLAY_ONCE y sin playCondition: un golpe no es un estado. Lo lanza AnimatableMeleeAttackGoal
        // con play() en el frame del impacto; una PLAY_ONCE nunca auto-arranca, asi que no hace falta
        // condicion que la contenga.
        StandardAnimation attack = new StandardAnimation("attack",
                new AnimSource(() -> CivetAnims.ATTACK), Loop.PLAY_ONCE, 0, P_ATTACK, 0.5F);
        StandardAnimation sleepEnter = new StandardAnimation("sleep_enter",
                new AnimSource(() -> CivetAnims.PREPARING_SLEEP), Loop.REPEATING, 0, P_SLEEP, 2.0F);
        StandardAnimation sleepLoop = new StandardAnimation("sleep_loop",
                new AnimSource(() -> CivetAnims.SLEEP), Loop.REPEATING, 0, P_SLEEP, 4.0F);
        StandardAnimation sleepExit = new StandardAnimation("sleep_exit",
                new AnimSource(() -> CivetAnims.AWAKENING), Loop.REPEATING, 0, P_SLEEP, 2.0F);

        // Fundidos cortos en trepada: renderToBuffer gira el modelo -90 grados de golpe al entrar,
        // asi que un crossfade largo mezclaria poses con orientaciones distintas.
        climbUp.blendInMs(100).blendOutMs(100);
        climbDown.blendInMs(100).blendOutMs(100);
        // climb_hang NO lleva locksMovement(), aunque el spec §5 lo pida: contradice la regla del
        // §4.1 —"no se marcan los clips de actividades con dueño"— y aqui hay dueño,
        // CivetClimbGoal, que retiene MOVE y hace setDeltaMovement(0,0,0) cada tick.
        //
        // Marcarlo montaba este bucle: AnimationLockGoal es prioridad 0, asi que en la misma pasada
        // del GoalSelector le robaba MOVE/LOOK al climb goal (prioridad 16) y lo desalojaba. Al
        // desalojarlo corria su stop(), que ademas de soltar el CLIMB_HANG regala
        // setEnrichment(maxEnrichment()). Resultado: la civeta colgaba UN tick, cobraba el
        // enriquecimiento entero, y a partir de ahi isEnrichmentNeeded() era false y ni cavaba ni
        // se aranaba. La ardilla nunca lo llevo; esto la iguala.
        climbHang.blendInMs(150).blendOutMs(150);
        // El modelo variaba la velocidad de nado con limbSwingAmount; la libreria solo admite un
        // multiplicador fijo, asi que se toma un valor medio. Si se nota lento o rapido, es aqui.
        swim.blendInMs(200).blendOutMs(200).playbackSpeed(1.5F);
        // Fundidos cortos: es un golpe, no una transicion.
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

    /**
     * Las del recien nacido, en la {@link #LAYER_BABY capa 1}. {@code CivetBabyModel} tiene su propia
     * jerarquia de huesos (la raiz se llama {@code Bandedpalmcivetkit}), asi que sus clips no pueden
     * convivir con los del adulto en la capa 0; cada modelo lee solo su capa. Las prioridades son
     * independientes por capa, asi que se reutiliza la misma escala.
     */
    private void registerBabyAnimations() {
        StandardAnimation babyIdle = registerSingle("baby_idle",
                () -> CivetBabyAnims.idle, 4.0F, Loop.REPEATING, P_IDLE, LAYER_BABY);
        StandardAnimation babyCrawl = registerSingle("baby_crawl",
                () -> CivetBabyAnims.move, 4.0F, Loop.REPEATING, P_WALK, LAYER_BABY);

        babyIdle.setPlayCondition(a -> this.isNewborn() && !this.isMoving());
        babyCrawl.setPlayCondition(a -> this.isNewborn() && this.isMoving());

        // Dormir no es una cadena dirigida por un goal, sino tres estados sincronizados: tres
        // REPEATING con condicion sobre SleepState, igual que en el adulto. Una cadena no valdria,
        // porque su entrada es PLAY_ONCE y nadie la lanzaria.
        registerSingle("baby_sleep_enter", () -> CivetBabyAnims.preparing_sleep,
                1.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.PREPARING);
        registerSingle("baby_sleep_loop", () -> CivetBabyAnims.sleep,
                4.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.SLEEPING);
        registerSingle("baby_sleep_exit", () -> CivetBabyAnims.awakening,
                1.0F, Loop.REPEATING, P_SLEEP, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.getSleepState() == SleepState.AWAKENING);

        // El llanto si es una cadena con dueno: la dirige CritterCryChainGoal. La condicion del bucle
        // esta ademas porque el flag lo apaga tambien FindCryingBabyGoal cuando la madre lo atiende,
        // y asi se para lo apague quien lo apague.
        registerChain("cry",
                () -> CivetBabyAnims.preparing_cry, 0.5F,
                () -> CivetBabyAnims.cry, 4.0F,
                () -> CivetBabyAnims.stopping_cry, 0.5F, P_ACTIVITY, LAYER_BABY)
                .setPlayCondition(a -> this.isNewborn() && this.isCrying());
    }

    /**
     * Reimplementado sobre el animator: la version de {@link DiverseCritter} lee contadores que solo
     * existian en el cliente, asi que en servidor devolvia siempre false. Ahora es correcta en ambos
     * lados y cubre tambien la fase de salida.
     */
    @Override
    public boolean isOrderedSitPlaying() {
        return this.animator().isPlaying("ordered_sit_enter")
                || this.animator().isPlaying("ordered_sit_loop")
                || this.animator().isPlaying("ordered_sit_exit");
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

    @Override
    public void aiStep() {
        ItemStack stack = this.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!stack.isEmpty() && stack.is(DoCTags.Items.MEATS) && !this.isNewborn()) {
            this.setHunger(this.maxHunger());

            var foodProps = stack.isEdible() ? stack.getFoodProperties(this) : null;
            if (foodProps != null) {
                this.heal((float) foodProps.getNutrition());
            } else {
                this.heal(1.0F);
            }
            // ---------------------------------------------

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

    @Override
    public InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);

        boolean isItemTamingMeat = itemstack.is(DoCTags.Items.MEATS) || itemstack.is(ItemTags.FISHES);
        boolean isNurserBottle = itemstack.is(DOCItems.FILLED_NURSER_BOTTLE.get());

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

        boolean isMeatFood = itemstack.is(DoCTags.Items.MEATS) || itemstack.is(ItemTags.FISHES);
        if (isMeatFood && !this.isBaby()) {
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

    private void triggerFoodSearch() {
        if (this.isNewborn()) return;
        if (this.forFoodGoal != null) {
            this.forFoodGoal.trigger();
        } else {
            this.navigation.stop();
            Predicate<ItemEntity> predicate = (e) -> e.getItem().is(DoCTags.Items.MEATS);
            List<? extends ItemEntity> list = this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(32.0D, 8.0D, 32.0D), predicate);
            if (!list.isEmpty()) this.navigation.moveTo(list.get(0), 1.1);
        }
    }

    @Override
    public boolean canPickUpLoot() {
        return !this.isNewborn();
    }

    @Override
    public boolean wantsToPickUp(ItemStack pStack) {
        return pStack.is(DoCTags.Items.MEATS);
    }

    @Override
    public boolean canAttack(LivingEntity pTarget) {
        return super.canAttack(pTarget) && ((this.getLastHurtByMob() != null && this.getLastHurtByMob() == pTarget) || this.isHungry());
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
    // Ticks de juego, y exactamente la longitud del clip: 2.0 s = 40 en el adulto, 1.0 s = 20 en
    // el recién nacido. Ver la nota en SleepBehaviorGoal#phaseUntilTick.
    @Override public int getPreparingSleepDuration() {return this.isNewborn() ? 20 : 40;}
    @Override public int getAwakeningDuration() {return this.isNewborn() ? 20 : 40;}
    @Override protected boolean getDefaultDiurnal() { return false; }
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

    public boolean isInPostDescentCooldown() {
        return this.postDescentCooldown > 0;
    }
    public boolean isClimbing() { return getClimbState() != CLIMB_NONE; }
    public boolean isClimbingUp() { return getClimbState() == CLIMB_UP; }
    public boolean isClimbingDown() { return getClimbState() == CLIMB_DOWN; }
    public boolean isHanging() { return getClimbState() == CLIMB_HANG; }

    @Override
    public boolean onClimbable() { return isClimbing(); }

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
                float yawRad = this.getYRot() * ((float) Math.PI / 180F);
                this.setDeltaMovement(-Math.sin(yawRad) * 0.25D, 0.4D, Math.cos(yawRad) * 0.25D);
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
        if (this.isScratching() || this.isNewborn() || this.onGround()) return;
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

    /**
     * Cavar y aranar cuentan como actividad especial por el mismo motivo que trepar en la ardilla:
     * CritterCleanChainGoal (prioridad 8) y CritterMotherCleanBabyGoal (9) le ganan MOVE a los goals
     * de aranar (15) y cavar (16), y sin este gate el acicalado arrancaba encima con la civeta
     * metida en el hoyo.
     */
    @Override
    public boolean isDoingSpecialActivity() {
        return isClimbing() || isDigging() || isScratching();
    }

    /**
     * Reservas de bloque, solo servidor. No se sincronizan ni se guardan: solo las leen busquedas de
     * servidor, y si la civeta se descarga la reserva se va con ella. Ver {@link CritterClaims}.
     */
    private @Nullable BlockPos digTarget;
    private @Nullable BlockPos scratchTarget;

    @Override public @Nullable BlockPos getDigTarget() { return this.digTarget; }
    @Override public void setDigTarget(@Nullable BlockPos pos) { this.digTarget = pos; }

    @Override public @Nullable BlockPos getScratchTarget() { return this.scratchTarget; }
    @Override public void setScratchTarget(@Nullable BlockPos pos) { this.scratchTarget = pos; }

    @Override public boolean isDigging() {return this.entityData.get(IS_DIGGING);}
    @Override public void setDigging(boolean isDigging) {this.entityData.set(IS_DIGGING, isDigging);}

    @Override public boolean isScratching() { return this.entityData.get(IS_SCRATCHING); }
    @Override public void setScratching(boolean isScratching) { this.entityData.set(IS_SCRATCHING, isScratching); }

    public static boolean checkCivetSpawnRules(EntityType<CivetEntity> t, LevelAccessor lvl, MobSpawnType type, BlockPos pos, RandomSource rnd) {
        return true;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
        CivetEntity civet = DOCEntities.CIVET.get().create(pLevel);
        civet.setIsMale(this.random.nextBoolean());
        return civet;
    }

    @Override
    public CritterDietConfig getDietConfig() {
        return new CritterDietConfig(true, true, true, 200, 200, 200, 300);
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

    @Override
    protected SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return SoundEvents.CAT_PURR;
        }
        if (this.isTame()) {
            return SoundEvents.CAT_AMBIENT;
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
        return SoundEvents.CAT_EAT;
    }

    @Override
    public boolean doHurtTarget(Entity pEntity) {
        boolean flag = super.doHurtTarget(pEntity);
        if (flag) {
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

