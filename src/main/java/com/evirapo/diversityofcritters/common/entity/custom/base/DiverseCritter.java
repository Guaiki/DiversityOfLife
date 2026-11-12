package com.evirapo.diversityofcritters.common.entity.custom.base;

import com.evirapo.diversityofcritters.client.menu.DOCStatsMenu;
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.entity.ai.SeekComfortGoal;
import com.evirapo.diversityofcritters.common.damage.DOCDamageTypes;
import com.evirapo.diversityofcritters.common.gamerule.DOCGameRules;
import com.evirapo.diversityofcritters.common.entity.util.CritterDietConfig;
import com.evirapo.diversityofcritters.common.entity.util.Sedation;
import com.evirapo.diversityofcritters.common.entity.util.ThreatSensor;
import com.evirapo.diversityofcritters.common.humidity.HumiditySystem;
import com.evirapo.diversityofcritters.common.humidity.HumidityTier;
import com.evirapo.diversityofcritters.common.temperature.TemperatureSystem;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import com.evirapo.diversityofcritters.common.entity.ai.mantle.MantleController;
import com.evirapo.diversityofcritters.common.locomotion.FootSupport;
import com.evirapo.diversityofcritters.common.locomotion.ILocomotionSubject;
import com.evirapo.diversityofcritters.common.locomotion.MantleState;
import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import com.evirapo.diversityofcritters.common.spawn.PopulationMath;
import com.evirapo.diversityofcritters.common.spawn.WildDeathRules;
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
import com.evirapo.diversityofcritters.network.DOCNetworkHandler;
import com.evirapo.diversityofcritters.network.OpenStatsScreenPacket;
import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.deluxelib.entity.MovementHysteresis;
import net.darkblade.deluxelib.entity.ai.goal.AnimationLockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;
import java.util.function.Supplier;

public abstract class DiverseCritter extends TamableAnimal
        implements ContainerListener, IAnimatedAttacker, Animatable<DiverseCritter>, ILocomotionSubject {

    public enum SleepState {
        AWAKE(0), PREPARING(1), SLEEPING(2), AWAKENING(3);
        private final int id;
        SleepState(int id) { this.id = id; }
        public int getId() { return id; }
        public static SleepState byId(int id) {
            return Arrays.stream(values()).filter(s -> s.id == id).findFirst().orElse(AWAKE);
        }
    }

    /**
     * Florituras de reposo. {@code STRETCH} es un clip suelto (estirarse) y {@code STAND_UP} es una
     * cadena de tres fases (erguirse sobre las patas traseras, aguantar, bajar).
     *
     * <p>Los nombres estuvieron cruzados hasta 2026-08-10: el export de Blockbench llamaba "Lay" a
     * lo que en realidad es erguirse, y "Stand up" a lo que es estirarse. Los bytes de
     * {@code toIdleByte} se mantuvieron al corregirlo, así que los mundos guardados siguen válidos.
     */
    public enum IdleVariant { NONE, STRETCH, SNIFF_LEFT, SNIFF_RIGHT, SIT, STAND_UP }

    /** Sincronizado porque las playCondition de idle/walk/run se evalúan en servidor pero el cliente
     *  necesita el mismo valor: deltaMovement no se sincroniza para mobs. Ver {@link #isMoving()}. */
    private static final EntityDataAccessor<Boolean> IS_MOVING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_MALE = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IS_JUVENILE = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_PREGNANT = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CAN_BREED = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> HUNGER = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> THIRST = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ENRICHMENT = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HYGIENE = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DRINKING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DRINK_POS = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> CLEANING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> SLEEP_STATE_ID = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> DIURNAL = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);

    protected static final EntityDataAccessor<Boolean> WANDERING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CRYING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> IDLE_VARIANT = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IS_ALERT = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FLEEING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TEMP_TIER = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TEMP_COMFORT = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HUMIDITY_TIER = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HUMIDITY_COMFORT = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> RULES_OFF = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BYTE);

    // Mantle in progress (see MantleController). Empty lip = not mantling. The phase is not synced:
    // both sides derive it from the body's height.
    private static final EntityDataAccessor<Optional<BlockPos>> MANTLE_LIP = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Byte> MANTLE_FACING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> MANTLE_START = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> MANTLE_START_Y = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MANTLE_LIP_Y = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.FLOAT);
    /** The mantle's pace, 1 normally; lower for slow motion (/civet_debug mantle_speed). */
    private static final EntityDataAccessor<Float> MANTLE_TIME_SCALE = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.FLOAT);
    /** The mantle crests a trunk the critter was climbing (MantleState#fromClimb). */
    private static final EntityDataAccessor<Boolean> MANTLE_FROM_CLIMB = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);

    /** Species that can climb a ledge set this in their constructor. Server side only acts on it. */
    @Nullable
    protected MantleController mantle;

    /**
     * Species whose feet are checked at edges set this in their constructor: with two feet over a
     * drop, the body slides off instead of hanging on by the corner of its collision box.
     */
    @Nullable
    protected FootSupport feet;

    /**
     * How high a critter walks straight up, as vanilla mobs do: stairs, and anything low. A full
     * block is a mantle, or a jump when none can start; stepping it would lift the body a whole
     * block in one tick, which is what being pushed against one used to do. A slab is under this
     * but is a ledge too: {@link #getStepHeight} keeps the body from stepping it.
     */
    public static final float WALK_STEP = 0.6F;
    /** How far under a ledge's height the step is held while the body runs into it. */
    private static final double UNDER_LEDGE = 1.0 / 64.0;

    /**
     * Vanilla steps a body up anything within its step height the moment it walks into it. A ledge
     * the critter mantles (a slab) is not stepped: walked or pushed into, the body stops at it,
     * and a path climbs it with a mantle, or a jump when none can start. Stairs are stepped as
     * usual. Newborns, which never mantle, step as usual too.
     *
     * <p>Asked on every move; the ledge is only probed when the move runs into something.
     */
    @Override
    public float getStepHeight() {
        final float step = super.getStepHeight();
        if (this.mantle == null || this.isNewborn() || step < this.mantle.profile().minMantleHeight
                || !this.onGround()) {
            return step;
        }
        final Vec3 motion = this.getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z < 1.0e-8
                || this.level().noCollision(this, this.getBoundingBox().move(motion.x, 0.0D, motion.z))) {
            return step;
        }
        final double ledge = this.mantle.ledgeInTheWay(motion.x, motion.z);
        return ledge <= step ? (float) Math.max(ledge - UNDER_LEDGE, 0.0) : step;
    }

    int prevHunger;
    int prevThirst;

    public int idleVariantCooldown = 0;
    public boolean isBeingCleaned = false;

    private double hungerLossAccum = 0.0;
    private double thirstLossAccum = 0.0;
    private double enrichmentLossAccum = 0.0;
    private double hygieneLossAccum = 0.0;
    public int attackAnimationTimeout = 0;

    public static final int TICKS_PER_DAY = 24000;
    public static final int TOTAL_GROWTH_TIME = 8 * TICKS_PER_DAY;
    public static final int JUVENILE_AGE_THRESHOLD = -4 * TICKS_PER_DAY;

    public int pregnancyTimer = 0;
    public int breedCooldown = 0;
    public static final int PREGNANCY_TIME_TICKS = 48000;

    /**
     * Cada cuantos ticks se regenera medio corazon. Un corazon son 2 HP, asi que medio corazon es
     * {@code heal(1.0F)} y no {@code 0.5F}.
     */
    private static final int HEAL_INTERVAL_TICKS = 1200;

    /**
     * A partir de que distancia una cuidadora deja de contar como presente.
     *
     * <p><b>Este mismo radio se usa en los dos lados</b> —la cria buscando a su cuidadora y la
     * cuidadora comprobando si ya tiene cria— y tiene que seguir siendo asi. Si se separan aparece una
     * banda de distancias en la que la madre se cree libre mientras la cria la sigue considerando
     * suya, y acaba con dos crias. No da error de compilacion: desde fuera solo se ve una hembra con
     * dos bebes y ninguna explicacion.
     */
    public static final double CAREGIVER_RANGE = 32.0D;

    /** Cuanto aguanta una cria sin ver a su cuidadora antes de declararse huerfana. 1200 = 60 s. */
    public static final int ORPHAN_TIMEOUT_TICKS = 1200;

    /** Cada cuantos ticks la cria comprueba si su cuidadora sigue cerca. */
    private static final int CAREGIVER_CHECK_INTERVAL = 20;

    /**
     * Quien cuida de esta cria. Solo significativo en recien nacidos; en un adulto es siempre null.
     *
     * <p>Solo servidor y persistido en NBT. No se sincroniza al cliente porque nada se dibuja a partir
     * de el.
     *
     * <p>El vinculo vive SOLO aqui, y no tambien en la cuidadora, a proposito: dos copias del mismo
     * hecho se desincronizan en cuanto una de las dos entidades se descarga, muere o se serializa a
     * medias.
     */
    private @Nullable UUID caregiverUUID;

    /** Tick en que se vio por ultima vez a la cuidadora. Ver {@link #readAdditionalSaveData}. */
    private int lastCaregiverSeenTick;

    // --- ANIMACIONES (DeluxeLib) ---
    private final MobAnimator<DiverseCritter> animator = new MobAnimator<>(this);
    /** Suaviza el "se está moviendo" para que el umbral crudo no parpadee entre idle y walk. */
    private final MovementHysteresis movingHysteresis = new MovementHysteresis();

    /**
     * Deteccion de amenazas y maquina de alerta/huida. Vive aqui y no en un goal porque el sueno
     * necesita consultarla mientras es EL quien retiene al bicho; ver el javadoc de la clase.
     */
    private final ThreatSensor threatSensor = new ThreatSensor(this);

    public ThreatSensor sensor() { return this.threatSensor; }

    /** Tiempos del tranquilizante. Ver {@link Sedation}; quien duerme al bicho es SleepBehaviorGoal. */
    private final Sedation sedation = new Sedation();

    /** Dormido por un tranquilizante, pase lo que pase con su ciclo natural. Servidor. */
    public boolean isSedated() { return this.sedation.isSedated(); }

    /** Servidor. Lo llaman el dardo y la píldora, vía {@code TranquilizerHelper}. */
    public Sedation.DoseResult tryTranquilize() { return this.sedation.dose(); }

    public int tranquilizerCooldownTicks() { return this.sedation.cooldownTicks(); }

    @Override
    public @javax.annotation.Nonnull MobAnimator<DiverseCritter> animator() {
        return this.animator;
    }

    /**
     * La llama DeluxeLib en EntityJoinLevelEvent, en ambos lados. Vacío por defecto: cada especie
     * registra las suyas. Las que no lo hagan simplemente no tienen animator con contenido.
     */
    @Override
    public void registerAnimations() {
    }

    // Prioridades de la capa 0. Número más bajo gana: si la especie planea, trepa, nada o duerme,
    // eso manda sobre cualquier actividad, y las actividades mandan sobre la locomoción.
    protected static final int P_GLIDE = 0;
    protected static final int P_CLIMB = 1;
    protected static final int P_SWIM = 2;
    protected static final int P_SLEEP = 3;
    protected static final int P_ATTACK = 4;
    /**
     * Misma que {@link #P_ATTACK} a proposito. No chocan: {@code CritterAlertGoal} esta en prioridad
     * de goal 2 y desaloja al goal de ataque (7), asi que dejan de producirse impactos y con ellos
     * los clips de ataque.
     */
    protected static final int P_ALERT = 4;
    protected static final int P_ORDERED_SIT = 5;
    protected static final int P_ACTIVITY = 6;
    protected static final int P_IDLE_VARIANT = 7;
    protected static final int P_WALK = 8;
    protected static final int P_IDLE = 9;

    /**
     * Registra una cadena {@code <nombre>_enter/_loop/_exit} en la capa 0, las tres con la misma
     * prioridad. Devuelve el bucle porque **siempre** hay que ponerle una {@code playCondition}: sin
     * ella {@code MobAnimator} lo auto-arrancaría solo, y al tener mejor prioridad que idle se
     * quedaría pegado para siempre.
     *
     * <p>Que las tres compartan prioridad es lo que impide que el bucle pise a su propia entrada:
     * el guard del auto-arranque es {@code current.getPriority() <= base.getPriority()}.
     *
     * <p>La salida lleva {@code locksMovement()}, así que {@code AnimationLockGoal} clava al critter
     * mientras suena y no se desliza con la pose de settle a medias.
     */
    protected final StandardAnimation registerChain(String name,
                                                    Supplier<Object> enter, float enterSecs,
                                                    Supplier<Object> loop, float loopSecs,
                                                    Supplier<Object> exit, float exitSecs, int priority) {
        return this.registerChain(name, enter, enterSecs, loop, loopSecs, exit, exitSecs, priority, LAYER_ADULT);
    }

    /**
     * Capa 0: el modelo adulto. Capa 1: el de bebé, que tiene una jerarquía de huesos completamente
     * distinta —la raíz de {@code SquirrelBabyModel} se llama {@code Bandedpalmcivetkit}— así que sus
     * clips no pueden convivir con los del adulto: cada modelo lee sólo su capa y no hay
     * contaminación cruzada ni que repetir un gate {@code isNewborn()} en veinte condiciones.
     */
    protected static final int LAYER_ADULT = 0;
    protected static final int LAYER_BABY = 1;

    /** @see #registerChain(String, Supplier, float, Supplier, float, Supplier, float, int) */
    protected final StandardAnimation registerChain(String name,
                                                    Supplier<Object> enter, float enterSecs,
                                                    Supplier<Object> loop, float loopSecs,
                                                    Supplier<Object> exit, float exitSecs,
                                                    int priority, int layer) {
        StandardAnimation e = new StandardAnimation(name + "_enter",
                new AnimSource(enter), Loop.PLAY_ONCE, layer, priority, enterSecs);
        StandardAnimation l = new StandardAnimation(name + "_loop",
                new AnimSource(loop), Loop.REPEATING, layer, priority, loopSecs);
        StandardAnimation x = new StandardAnimation(name + "_exit",
                new AnimSource(exit), Loop.PLAY_ONCE, layer, priority, exitSecs);

        e.blendInMs(150).blendOutMs(120).setNextAnimation(name + "_loop");
        l.blendInMs(120).blendOutMs(150);
        x.blendInMs(120).blendOutMs(200).locksMovement();

        this.animator().register(e, l, x);
        return l;
    }

    /**
     * Gesto o estado de un solo clip: se registra como {@code <nombre>_loop}.
     *
     * <p>{@code PLAY_ONCE} cuando es un gesto que debe sonar una vez y ceder (lo usa
     * {@code CritterIdleGestureGoal}, que cierra el goal cuando el animator dice que terminó), y
     * {@code REPEATING} cuando es un estado continuo como nadar o trepar.
     */
    protected final StandardAnimation registerSingle(String name, Supplier<Object> clip,
                                                     float seconds, Loop loop, int priority) {
        return this.registerSingle(name, clip, seconds, loop, priority, LAYER_ADULT);
    }

    /** @see #registerSingle(String, Supplier, float, Loop, int) */
    protected final StandardAnimation registerSingle(String name, Supplier<Object> clip,
                                                     float seconds, Loop loop, int priority, int layer) {
        StandardAnimation a = new StandardAnimation(name + "_loop",
                new AnimSource(clip), loop, layer, priority, seconds);
        a.blendInMs(150).blendOutMs(200);
        this.animator().register(a);
        return a;
    }

    /**
     * Congela al critter mientras suene un clip marcado con {@code locksMovement()} — los de salida
     * de las cadenas de actividad. Va a prioridad 0 para ganarle MOVE/LOOK a la locomoción, y no
     * reclama JUMP, así que {@code CustomFloatGoal} sigue flotando en el agua.
     *
     * <p>Las subclases llaman a {@code super.registerGoals()} antes de registrar los suyos, así que
     * lo heredan las tres especies sin tocarlas.
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new AnimationLockGoal(this));
        // Por encima de comer (10+) y de seguir al dueño (6), por debajo de huir y del sueño. En
        // simple estrés el propio goal cede ante el hambre y la sed.
        this.goalSelector.addGoal(5, new SeekComfortGoal(this, 1.1D));
    }

    /** Con histéresis y sincronizado — ver {@link #IS_MOVING}. Lo usan las playCondition de
     *  idle/walk/run. */
    public boolean isMoving() {
        return this.entityData.get(IS_MOVING);
    }

    /**
     * True mientras el critter está metido en una actividad con animación propia: beber, acicalarse,
     * sentarse por orden, una variante de reposo, o cualquier clip que esté clavando el movimiento.
     *
     * <p>Sirve para que un gesto cosmético no se apile encima de una acción en curso. No se puede
     * resolver sólo con {@code isMovementLocked()}: un gesto es precisamente algo que NO clava el
     * movimiento, así que las actividades que están en su fase de bucle no aparecerían ahí.
     */
    public boolean isPerformingActivity() {
        return this.IsDrinking()
                || this.isCleaning()
                || this.isOrderedToSit()
                || this.isIdleLocked()
                || this.isDoingSpecialActivity()
                || this.animator().isMovementLocked();
    }

    protected DiverseCritter(EntityType<? extends TamableAnimal> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.createInventory();
        this.lookControl = new CritterLookControl();
        this.moveControl = new DiverseCritter.CritterMoveControl();
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
    }

    public int maxHunger(){ return 100; }
    public int maxThirst(){ return 100; }
    public int maxEnrichment(){ return 100; }
    public int maxHygiene() { return 100; }

    protected double getHungerLossPerSecond() { return 10.0; }
    protected double getThirstLossPerSecond() { return 20.0; }
    protected double getEnrichmentLossPerSecond() { return 10.0; }
    protected double getHygieneLossPerSecond() { return 5.0; }

    public int getPreparingSleepDuration() { return 40; }
    public int getAwakeningDuration() { return 40; }
    protected boolean getDefaultDiurnal() { return true; }

    /** Any of the three ordered-sit clips is playing. Read from the animator, so it holds on both sides. */
    public boolean isOrderedSitPlaying() {
        return this.animator().isPlaying("ordered_sit_enter")
                || this.animator().isPlaying("ordered_sit_loop")
                || this.animator().isPlaying("ordered_sit_exit");
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_MOVING, false);
        this.entityData.define(IS_MALE, true);
        this.entityData.define(HUNGER, maxHunger());
        this.entityData.define(THIRST, maxThirst());
        this.entityData.define(ENRICHMENT, maxEnrichment());
        this.entityData.define(HYGIENE, maxHygiene());
        this.entityData.define(DRINKING, false);
        this.entityData.define(CLEANING, false);
        this.entityData.define(ATTACKING, false);
        this.entityData.define(DRINK_POS, Optional.empty());
        this.entityData.define(DATA_FLAGS_ID, (byte)0);
        this.entityData.define(SLEEP_STATE_ID, SleepState.AWAKE.getId());
        this.entityData.define(DIURNAL, getDefaultDiurnal());
        this.entityData.define(WANDERING, false);
        this.entityData.define(IS_JUVENILE, false);
        this.entityData.define(IS_CRYING, false);
        this.entityData.define(IS_PREGNANT, false);
        this.entityData.define(CAN_BREED, true);
        this.entityData.define(IDLE_VARIANT, (byte) 0);
        this.entityData.define(IS_ALERT, false);
        this.entityData.define(IS_FLEEING, false);
        this.entityData.define(TEMP_TIER, TemperatureTier.MODERATE.ordinal());
        this.entityData.define(TEMP_COMFORT, 100);
        this.entityData.define(HUMIDITY_TIER, HumidityTier.MODERATE.ordinal());
        this.entityData.define(HUMIDITY_COMFORT, 100);
        this.entityData.define(RULES_OFF, (byte) 0);
        this.entityData.define(MANTLE_LIP, Optional.empty());
        this.entityData.define(MANTLE_FACING, (byte) Direction.SOUTH.get2DDataValue());
        this.entityData.define(MANTLE_START, 0);
        this.entityData.define(MANTLE_START_Y, 0.0F);
        this.entityData.define(MANTLE_LIP_Y, 0.0F);
        this.entityData.define(MANTLE_TIME_SCALE, 1.0F);
        this.entityData.define(MANTLE_FROM_CLIMB, false);
    }

    // --- MANTLE ---

    /**
     * How big this critter is drawn next to an adult, 1 for adults. The hitbox does not change, so
     * this is what tells the server a young critter's legs are shorter.
     */
    public float growthScale() {
        return 1.0F;
    }

    /** The ledge climb this species can do, or null. */
    @Nullable
    public MantleController mantle() {
        return this.mantle;
    }

    public boolean isMantling() {
        return this.entityData.get(MANTLE_LIP).isPresent();
    }

    /** Publishes a mantle to the clients. Only {@link MantleController} calls this. */
    public void setMantleData(BlockPos lip, Direction facing, int startTick, float startY, float lipY, float timeScale,
                              boolean fromClimb) {
        this.entityData.set(MANTLE_FACING, (byte) facing.get2DDataValue());
        this.entityData.set(MANTLE_START, startTick);
        this.entityData.set(MANTLE_START_Y, startY);
        this.entityData.set(MANTLE_LIP_Y, lipY);
        this.entityData.set(MANTLE_TIME_SCALE, timeScale);
        this.entityData.set(MANTLE_FROM_CLIMB, fromClimb);
        // Last: clients treat the lip as the switch, so everything else is in place when it flips.
        this.entityData.set(MANTLE_LIP, Optional.of(lip));
    }

    public void clearMantleData() {
        this.entityData.set(MANTLE_LIP, Optional.empty());
    }

    @Override
    public boolean readMantle(MantleState out) {
        final Optional<BlockPos> lip = this.entityData.get(MANTLE_LIP);
        if (lip.isEmpty()) {
            return false;
        }
        // The start tick travels as the low 32 bits of the game time; rebuild it wrap-safe.
        final long now = this.level().getGameTime();
        final long startTick = now - ((int) now - this.entityData.get(MANTLE_START));
        final BlockPos pos = lip.get();
        out.set(pos.getX(), pos.getY(), pos.getZ(), this.entityData.get(MANTLE_LIP_Y),
                Direction.from2DDataValue(this.entityData.get(MANTLE_FACING)),
                this.entityData.get(MANTLE_START_Y), startTick);
        out.timeScale = this.entityData.get(MANTLE_TIME_SCALE);
        out.fromClimb = this.entityData.get(MANTLE_FROM_CLIMB);
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        // A mantle is never saved: a critter loaded mid-climb must not come back floating.
        if (this.mantle != null && this.mantle.holdsGravity()) {
            pCompound.remove("NoGravity");
        }
        pCompound.putBoolean("IsMale", this.getIsMale());
        pCompound.putInt("Hunger", this.getHunger());
        pCompound.putInt("Thirst", this.getThirst());
        pCompound.putInt("Enrichment", this.getEnrichment());
        pCompound.putInt("Hygiene", this.getHygiene());
        pCompound.putBoolean("Cleaning", this.isCleaning());
        if (this.caregiverUUID != null) {
            pCompound.putUUID("Caregiver", this.caregiverUUID);
        }
        pCompound.putInt("SleepState", this.getSleepState().getId());
        pCompound.putBoolean("Diurnal", this.isDiurnal());
        pCompound.putBoolean("Wandering", this.isWandering());
        pCompound.putInt("DiverseAge", this.getAge());
        pCompound.putBoolean("IsPregnant", this.isPregnant());
        pCompound.putBoolean("CanBreed", this.canBreed());
        pCompound.putInt("PregnancyTimer", this.pregnancyTimer);
        pCompound.putInt("BreedCooldown", this.breedCooldown);
        pCompound.putInt("TempComfort", this.getTemperatureComfort());
        pCompound.putInt("HumidityComfort", this.getHumidityComfort());
        pCompound.putBoolean("DOCWasReleased", this.wasReleased);
        pCompound.putBoolean("DOCInWild", this.inWild);
        if (this.sireOwner != null) {
            pCompound.putUUID("DOCSireOwner", this.sireOwner);
        }
        pCompound.putInt("TranqDrowsy", this.sedation.drowsyTicks());
        pCompound.putInt("TranqSedated", this.sedation.sedatedTicks());
        pCompound.putInt("TranqCooldown", this.sedation.cooldownTicks());
    }

    private Integer loadedNbtAge = null;

    /**
     * Whether this animal has ever been returned to the wild. It marks the animal as wild fauna for
     * despawn purposes, see {@link #removeWhenFarAway(double)}. Counting it towards the population
     * is {@link #inWild}'s job.
     */
    private boolean wasReleased = false;

    public boolean wasReleased() {
        return this.wasReleased;
    }

    public void setWasReleased(boolean wasReleased) {
        this.wasReleased = wasReleased;
    }

    /**
     * Whether this animal is counted in its species' wild population.
     *
     * Not the same as {@code !isTame()}: an animal from a spawn egg or {@code /summon} is not tame
     * but was never counted either, and if its death subtracted, admin tooling would corrupt the
     * population. Server only, never synced: nothing is drawn from it.
     */
    private boolean inWild = false;

    public boolean isInWild() {
        return this.inWild;
    }

    public void setInWild(boolean inWild) {
        this.inWild = inWild;
    }

    /**
     * Owner of the father, recorded at conception, absent if he was wild. With the mother's owner it
     * decides whether the baby is born tame. Cleared at birth.
     */
    @Nullable
    private UUID sireOwner;

    public void setSireOwner(@Nullable UUID sireOwner) {
        this.sireOwner = sireOwner;
    }

    /** Breeding cooldown for this animal: wild ones follow wildBreedingRate, tame ones never do. */
    public int wildScaledCooldown(int ticks) {
        if (this.isTame()) return ticks;
        return PopulationMath.scaledCooldown(ticks, DOCServerConfig.population().wildBreedingRate());
    }

    /**
     * Gives released animals the despawn behaviour of wild fauna.
     *
     * Animal.removeWhenFarAway() returns false, so vanilla passive mobs (and these critters by
     * inheritance) never despawn. Without this override every released animal would stay loaded in
     * the world forever, and mass releasing would end up dragging the save down.
     *
     * The override is deliberately narrow: it only affects released, ownerless animals. Naturally
     * spawned and tamed ones keep vanilla behaviour.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        if (!DOCServerConfig.releasedCanDespawn()) return false;
        return this.wasReleased && !this.isTame();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.wasReleased = pCompound.getBoolean("DOCWasReleased");
        // Without the flag there are two very different cases. A full save from before the flag
        // existed (it always carries DiverseAge): wild ones are counted, tame ones are not, and
        // super.readAdditionalSaveData() has already restored the tame state. Or a partial tag, which
        // is how /summon and other mods create entities: that animal never lived in the wild, so it
        // keeps the default and is not counted.
        if (pCompound.contains("DOCInWild")) {
            this.inWild = pCompound.getBoolean("DOCInWild");
        } else if (pCompound.contains("DiverseAge")) {
            this.inWild = !this.isTame();
        }
        this.sireOwner = pCompound.hasUUID("DOCSireOwner") ? pCompound.getUUID("DOCSireOwner") : null;
        if (pCompound.contains("DiverseAge")) {
            this.loadedNbtAge = pCompound.getInt("DiverseAge");
            if (this.loadedNbtAge != null) {
                this.setAge(this.loadedNbtAge);
            }
        }
        this.updateJuvenileState(this.getAge());
        if (pCompound.contains("IsMale")) this.setIsMale(pCompound.getBoolean("IsMale"));
        if (pCompound.contains("Hunger")) this.setHunger(pCompound.getInt("Hunger"));
        if (pCompound.contains("Thirst")) this.setThirst(pCompound.getInt("Thirst"));
        if (pCompound.contains("Enrichment")) this.setEnrichment(pCompound.getInt("Enrichment"));
        if (pCompound.contains("Hygiene")) {
            this.setHygiene(pCompound.getInt("Hygiene"));
        }
        this.setCleaning(pCompound.getBoolean("Cleaning"));
        if (pCompound.contains("SleepState")) {
            this.setSleepState(SleepState.byId(pCompound.getInt("SleepState")));
        }
        if (pCompound.contains("Diurnal")) {
            this.setDiurnal(pCompound.getBoolean("Diurnal"));
        }
        this.setWandering(pCompound.getBoolean("Wandering"));

        if (pCompound.contains("IsPregnant")) this.setPregnant(pCompound.getBoolean("IsPregnant"));
        if (pCompound.contains("CanBreed")) this.setCanBreed(pCompound.getBoolean("CanBreed"));
        if (pCompound.contains("PregnancyTimer")) this.pregnancyTimer = pCompound.getInt("PregnancyTimer");
        if (pCompound.contains("BreedCooldown")) this.breedCooldown = pCompound.getInt("BreedCooldown");
        if (pCompound.contains("TempComfort")) this.setTemperatureComfort(pCompound.getInt("TempComfort"));
        if (pCompound.contains("HumidityComfort")) this.setHumidityComfort(pCompound.getInt("HumidityComfort"));
        this.sedation.restore(pCompound.getInt("TranqDrowsy"), pCompound.getInt("TranqSedated"),
                pCompound.getInt("TranqCooldown"));

        // El contador NO se persiste a proposito. tickCount vuelve a 0 al cargar del disco, asi que
        // guardar el valor viejo daria una resta negativa enorme y la cria no se declararia huerfana
        // jamas. Arrancando a cero vuelve del disco con sus 60 s completos para reencontrar a su
        // madre: recargar un mundo no huerfana a nadie, ni perpetua un vinculo con una madre que ya no
        // existe.
        this.lastCaregiverSeenTick = this.tickCount;
        this.caregiverUUID = pCompound.hasUUID("Caregiver") ? pCompound.getUUID("Caregiver") : null;
    }

    public Boolean getIsMale() { return this.entityData.get(IS_MALE); }
    public void setIsMale(Boolean isMale) { this.entityData.set(IS_MALE, isMale); }

    public boolean isPregnant() { return this.entityData.get(IS_PREGNANT); }
    public void setPregnant(boolean pregnant) { this.entityData.set(IS_PREGNANT, pregnant); }
    public boolean canBreed() { return this.entityData.get(CAN_BREED); }
    public void setCanBreed(boolean canBreed) { this.entityData.set(CAN_BREED, canBreed); }

    public int getHungerPercentage() { return (100 * this.getHunger())/this.maxHunger(); }
    public int getThirstPercentage() { return (100 * this.getThirst())/this.maxThirst(); }
    public int getEnrichmentPercentage() {return (100 * this.getEnrichment()) / this.maxEnrichment();}
    public int getHygienePercentage() {return (100 * this.getHygiene()) / this.maxHygiene();}

    public int getHunger() { return this.entityData.get(HUNGER); }
    public void setHunger(int hunger) { this.entityData.set(HUNGER, Math.max(0, Math.min(maxHunger(), hunger))); }
    public int getThirst() { return this.entityData.get(THIRST); }
    public void setThirst(int thirst) { this.entityData.set(THIRST, Math.max(0, Math.min(maxThirst(), thirst))); }
    public int getEnrichment() {return this.entityData.get(ENRICHMENT);}
    public void setEnrichment(int enrichment) { this.entityData.set(ENRICHMENT, Math.max(0, Math.min(maxEnrichment(), enrichment))); }
    public int getHygiene() { return this.entityData.get(HYGIENE); }
    public void setHygiene(int hygiene) { this.entityData.set(HYGIENE, Math.max(0, Math.min(maxHygiene(), hygiene))); }

    public boolean isCleaning() { return this.entityData.get(CLEANING); }
    public void setCleaning(boolean value) { this.entityData.set(CLEANING, value); }
    public boolean hasLowHygiene() { return this.getHygienePercentage() < 50; }

    public Boolean IsDrinking() { return this.entityData.get(DRINKING); }
    public void setIsDrinking(Boolean isDrinking) { this.entityData.set(DRINKING, isDrinking); }

    public BlockPos getDrinkPos() { return this.entityData.get(DRINK_POS).orElse(null); }
    public void setDrinkPos(BlockPos washingPos) { this.entityData.set(DRINK_POS, Optional.ofNullable(washingPos)); }

    public boolean isWandering() { return this.entityData.get(WANDERING); }
    public void setWandering(boolean value) { this.entityData.set(WANDERING, value); }
    // Un recién nacido no admite órdenes (el palo de entrenamiento lo rechaza), así que tampoco puede
    // estar en modo seguir: va con su madre, no con el dueño.
    public boolean isFollowing() { return !this.isNewborn() && !this.isOrderedToSit() && !this.isWandering(); }

    @Override public boolean isAttacking() { return this.entityData.get(ATTACKING); }
    @Override public void setAttacking(boolean attacking) { this.entityData.set(ATTACKING, attacking); }
    @Override public int attackAnimationTimeout() { return this.attackAnimationTimeout; }
    @Override public void setAttackAnimationTimeout(int timeout) { this.attackAnimationTimeout = timeout; }

    public boolean isJuvenile() {return this.entityData.get(IS_JUVENILE);}
    public boolean isNewborn() {return this.isBaby() && !isJuvenile();}

    public boolean isCrying() { return this.entityData.get(IS_CRYING); }
    public void setCrying(boolean val) { this.entityData.set(IS_CRYING, val); }

    public SleepState getSleepState() { return SleepState.byId(this.entityData.get(SLEEP_STATE_ID)); }
    public void setSleepState(SleepState state) { this.entityData.set(SLEEP_STATE_ID, state.getId()); }

    public boolean isSleeping() { return getSleepState() == SleepState.SLEEPING; }
    public boolean isPreparingSleep() { return getSleepState() == SleepState.PREPARING; }
    public boolean isAwakeing() { return getSleepState() == SleepState.AWAKENING; }

    public boolean isDiurnal() { return this.entityData.get(DIURNAL); }
    public void setDiurnal(boolean value) { this.entityData.set(DIURNAL, value); }

    /**
     * Cambia el cronotipo por acción del jugador (botones sol/luna de la ficha).
     *
     * <p>Aparte de mover el flag deja marcado que el ciclo de sueño debe re-evaluarse en el acto.
     * Sin esto el cambio se nota pero tarda: dormirse tiene un retardo repartido de hasta medio
     * minuto, y entre siesta y siesta hay una espera de 30 a 90 s, así que pulsar el botón parecía
     * no hacer nada. Sólo lo pone esta vía, no {@link #setDiurnal}, para que restaurar el flag
     * desde NBT al cargar el mundo no dispare una siesta inmediata en todo lo que se cargue.
     *
     * @see com.evirapo.diversityofcritters.common.entity.ai.SleepBehaviorGoal#canUse()
     */
    public void setDiurnalByPlayer(boolean value) {
        this.setDiurnal(value);
        this.scheduleChanged = true;
    }

    /** Servidor. Lo consume {@code SleepBehaviorGoal}; devolverlo lo desarma. */
    public boolean consumeScheduleChanged() {
        boolean changed = this.scheduleChanged;
        this.scheduleChanged = false;
        return changed;
    }

    private boolean scheduleChanged;

    public void clearStates() { this.setSleepState(SleepState.AWAKE); this.setCleaning(false); }

    private static byte toIdleByte(IdleVariant v) {
        return switch (v) {
            case NONE -> 0; case STRETCH -> 1; case SNIFF_LEFT -> 2;
            case SNIFF_RIGHT -> 3; case SIT -> 4; case STAND_UP -> 5;
        };
    }
    private static IdleVariant idleFromByte(byte b) {
        return switch (b) {
            case 1 -> IdleVariant.STRETCH; case 2 -> IdleVariant.SNIFF_LEFT;
            case 3 -> IdleVariant.SNIFF_RIGHT; case 4 -> IdleVariant.SIT; case 5 -> IdleVariant.STAND_UP;
            default -> IdleVariant.NONE;
        };
    }
    public void setIdleVariant(IdleVariant v) { this.entityData.set(IDLE_VARIANT, toIdleByte(v)); }
    public IdleVariant getIdleVariant() { return idleFromByte(this.entityData.get(IDLE_VARIANT)); }
    public boolean isIdleLocked() { return getIdleVariant() != IdleVariant.NONE; }

    public boolean isDoingSpecialActivity() { return false; }

    // --- TEMPERATURE ---
    // --- GAMERULES ---
    // Gamerules never reach the client, but the stat screens work out comfort zones there. So the
    // server copies the two rules into this synced byte, and both sides read the byte.
    private static final byte NEEDS_OFF = 1;
    private static final byte CLIMATE_OFF = 2;

    /** False while {@code doCritterNeeds} is off. Safe on both sides. */
    public boolean needsEnabled() { return (this.entityData.get(RULES_OFF) & NEEDS_OFF) == 0; }

    /** False while {@code doCritterClimate} is off. Safe on both sides. */
    public boolean climateEnabled() { return (this.entityData.get(RULES_OFF) & CLIMATE_OFF) == 0; }

    private void syncGameRules() {
        GameRules rules = this.level().getGameRules();
        byte off = 0;
        if (!rules.getBoolean(DOCGameRules.CRITTER_NEEDS)) off |= NEEDS_OFF;
        if (!rules.getBoolean(DOCGameRules.CRITTER_CLIMATE)) off |= CLIMATE_OFF;
        if (off != this.entityData.get(RULES_OFF)) this.entityData.set(RULES_OFF, off);
    }

    private static final int TEMP_CHECK_INTERVAL = 40;
    private static final int TEMP_DANGER_DAMAGE_INTERVAL = 600; // 30s
    private static final float TEMP_DANGER_DAMAGE = 2.0F;       // 1 corazón
    private static final int TEMP_COMFORT_GAIN_PER_SECOND = 4;  // lleno desde 0 en 25s
    private static final int TEMP_STRESS_LOSS_PER_SECOND = 2;   // vacío desde 100 en 50s
    private static final int TEMP_DANGER_LOSS_PER_SECOND = 4;   // vacío desde 100 en 25s

    /** Rango de confort por especie; sobrescribir en cada entidad. */
    public TemperatureTier comfortTierMin() { return TemperatureTier.COOL; }
    public TemperatureTier comfortTierMax() { return TemperatureTier.WARM; }

    public TemperatureTier getTemperatureTier() { return TemperatureTier.byId(this.entityData.get(TEMP_TIER)); }

    /** 0 = Confort, 1 = Estrés, 2+ = Peligro. Siempre confort con doCritterClimate apagado. */
    public int getTemperatureZone() {
        if (!this.climateEnabled()) return 0;
        return getTemperatureTier().distanceOutside(comfortTierMin(), comfortTierMax());
    }

    public boolean isTemperatureComfortable() { return getTemperatureZone() == 0; }

    /** Barra de temperatura (0-100): sube cuando el animal está en su rango, baja fuera de él. */
    public int getTemperatureComfort() { return this.entityData.get(TEMP_COMFORT); }
    public void setTemperatureComfort(int value) {
        this.entityData.set(TEMP_COMFORT, Math.max(0, Math.min(100, value)));
    }

    private double getTemperatureLossMultiplier() {
        int zone = getTemperatureZone();
        if (zone >= 2) return 2.0;
        if (zone == 1) return 1.5;
        return 1.0;
    }

    private void tickTemperature() {
        if (!this.climateEnabled()) {
            // doCritterClimate off: the bar shows full, and the tier is not worth computing.
            setTemperatureComfort(100);
            return;
        }

        if (this.tickCount % TEMP_CHECK_INTERVAL == 0) {
            TemperatureTier tier = TemperatureSystem.getEffectiveTier(this.level(), this.blockPosition());
            this.entityData.set(TEMP_TIER, tier.ordinal());
        }

        if (this.tickCount % 20 == 0) {
            int zone = getTemperatureZone();
            int delta = zone == 0 ? TEMP_COMFORT_GAIN_PER_SECOND
                    : (zone == 1 ? -TEMP_STRESS_LOSS_PER_SECOND : -TEMP_DANGER_LOSS_PER_SECOND);
            setTemperatureComfort(getTemperatureComfort() + delta);
        }

        if (getTemperatureZone() >= 2 && this.tickCount % TEMP_DANGER_DAMAGE_INTERVAL == 0) {
            boolean tooCold = getTemperatureTier().ordinal() < comfortTierMin().ordinal();
            DamageSource source = DOCDamageTypes.source(this.level(),
                    tooCold ? DOCDamageTypes.HYPOTHERMIA : DOCDamageTypes.HEATSTROKE);
            this.hurt(source, TEMP_DANGER_DAMAGE);
        }
    }

    // --- HUMIDITY ---
    private static final int HUMIDITY_CHECK_INTERVAL = 40;
    private static final int HUMIDITY_DANGER_DAMAGE_INTERVAL = 600; // 30s
    private static final float HUMIDITY_DANGER_DAMAGE = 2.0F;       // 1 corazón
    private static final int HUMIDITY_COMFORT_GAIN_PER_SECOND = 4;  // lleno desde 0 en 25s
    private static final int HUMIDITY_STRESS_LOSS_PER_SECOND = 2;   // vacío desde 100 en 50s
    private static final int HUMIDITY_DANGER_LOSS_PER_SECOND = 4;   // vacío desde 100 en 25s

    /** Rango de confort por especie; sobrescribir en cada entidad. */
    public HumidityTier comfortHumidityMin() { return HumidityTier.MODERATE; }
    public HumidityTier comfortHumidityMax() { return HumidityTier.HUMID; }

    public HumidityTier getHumidityTier() { return HumidityTier.byId(this.entityData.get(HUMIDITY_TIER)); }

    /** 0 = Confort, 1 = Estrés, 2+ = Peligro. Siempre confort con doCritterClimate apagado. */
    public int getHumidityZone() {
        if (!this.climateEnabled()) return 0;
        return getHumidityTier().distanceOutside(comfortHumidityMin(), comfortHumidityMax());
    }

    public boolean isHumidityComfortable() { return getHumidityZone() == 0; }

    /** Barra de humedad (0-100): sube cuando el animal está en su rango, baja fuera de él. */
    public int getHumidityComfort() { return this.entityData.get(HUMIDITY_COMFORT); }
    public void setHumidityComfort(int value) {
        this.entityData.set(HUMIDITY_COMFORT, Math.max(0, Math.min(100, value)));
    }

    private double getHumidityLossMultiplier() {
        int zone = getHumidityZone();
        if (zone >= 2) return 2.0;
        if (zone == 1) return 1.5;
        return 1.0;
    }

    private void tickHumidity() {
        if (!this.climateEnabled()) {
            setHumidityComfort(100);
            return;
        }

        if (this.tickCount % HUMIDITY_CHECK_INTERVAL == 0) {
            HumidityTier tier = HumiditySystem.getEffectiveTier(this.level(), this.blockPosition());
            this.entityData.set(HUMIDITY_TIER, tier.ordinal());
        }

        if (this.tickCount % 20 == 0) {
            int zone = getHumidityZone();
            int delta = zone == 0 ? HUMIDITY_COMFORT_GAIN_PER_SECOND
                    : (zone == 1 ? -HUMIDITY_STRESS_LOSS_PER_SECOND : -HUMIDITY_DANGER_LOSS_PER_SECOND);
            setHumidityComfort(getHumidityComfort() + delta);
        }

        if (getHumidityZone() >= 2 && this.tickCount % HUMIDITY_DANGER_DAMAGE_INTERVAL == 0) {
            this.hurt(DOCDamageTypes.source(this.level(), DOCDamageTypes.HUMIDITY), HUMIDITY_DANGER_DAMAGE);
        }
    }

    /**
     * Lo incómodo que sería para esta especie estar en {@code pos}: 0 = temperatura y humedad en
     * rango; cada eje suma 1 en estrés y 4 en peligro, así que el peligro pesa más que dos estrés
     * juntos. Mira el clima efectivo (bioma, estación, aire acondicionado y humidificador) en ese
     * momento, no el tier sincronizado del animal. Siempre 0 con {@code doCritterClimate} apagado.
     */
    public int climateScoreAt(BlockPos pos) {
        if (!this.climateEnabled()) return 0;
        int t = TemperatureSystem.getEffectiveTier(this.level(), pos).distanceOutside(comfortTierMin(), comfortTierMax());
        int h = HumiditySystem.getEffectiveTier(this.level(), pos).distanceOutside(comfortHumidityMin(), comfortHumidityMax());
        return climateWeight(t) + climateWeight(h);
    }

    /** Puntuación de {@link #climateScoreAt} a partir de la cual hay peligro en algún eje. */
    public static final int CLIMATE_DANGER_SCORE = 4;

    private static int climateWeight(int distance) {
        if (distance <= 0) return 0;
        return distance == 1 ? 1 : CLIMATE_DANGER_SCORE;
    }

    public boolean isClimbing() { return false; }

    /**
     * Si la especie trepa por los bloques {@code civet_climbable}. Lo lee el pathfinder: sin esto una
     * especie que no escala trazaría rutas por encima de una pared de troncos y se quedaría pegada a
     * ella, creyendo alcanzable lo que hay detrás.
     *
     * <p>Tiene que devolver una constante: se consulta desde {@code createNavigation()}, que corre en
     * el constructor de {@code Mob}, antes de que se inicialicen los campos de la subclase.
     */
    public boolean canClimb() { return false; }

    /**
     * Si es un animal grande (como el okapi): al comer recupera menos sed que uno pequeño. Constante
     * por especie.
     */
    public boolean isLargeAnimal() { return false; }

    /**
     * Categorías de presa que caza esta especie (etiquetas {@code diversityofcritters:category/*}).
     * Vacía = no caza.
     */
    public List<TagKey<EntityType<?>>> preyCategories() { return List.of(); }

    /** Hasta dónde ve una presa, en bloques. */
    public double huntRange() { return 32.0D; }

    /**
     * Si {@code target} es presa de {@code hunter}: su tipo está en alguna categoría que el cazador
     * caza, no es de su especie, está vivo y no es una mascota (de nadie).
     */
    public static boolean isPreyFor(DiverseCritter hunter, LivingEntity target) {
        if (target == hunter || !target.isAlive()) return false;
        if (target.getType() == hunter.getType()) return false;
        if (target instanceof TamableAnimal tamable && tamable.isTame()) return false;
        return hunter.huntsCategoryOf(target.getType());
    }

    /** Si esta especie caza alguna categoría de presa a la que pertenece {@code type}. */
    public boolean huntsCategoryOf(EntityType<?> type) {
        for (TagKey<EntityType<?>> category : this.preyCategories()) {
            if (type.is(category)) return true;
        }
        return false;
    }

    /**
     * Si ahora caza: con hambre, adulto, despierto y sin sedar; una mascota solo en modo wandering.
     */
    public boolean canHunt() {
        if (!this.isHungry() || this.isBaby() || this.isSedated()) return false;
        if (this.getSleepState() != SleepState.AWAKE) return false;
        return !this.isTame() || (this.isWandering() && !this.isOrderedToSit());
    }

    /**
     * Una comida completa: llena el hambre y quita parte de la sed, más en un animal pequeño
     * ({@code smallThirstFromFood}) que en uno grande ({@code largeThirstFromFood}).
     */
    public void onAteFood() {
        this.setHunger(this.maxHunger());
        var eco = DOCServerConfig.ecosystem();
        int pct = this.isLargeAnimal() ? eco.largeThirstFromFood() : eco.smallThirstFromFood();
        this.setThirst(this.getThirst() + this.maxThirst() * pct / 100);
    }

    /**
     * Poses where the legs are placed by the animation for reasons other than the ground under
     * them: resting, grooming, drinking, rearing, climbing, riding, swimming, dying. Species add
     * their own busy states on top.
     */
    @Override
    public boolean isLocomotionSuspended() {
        final IdleVariant idle = this.getIdleVariant();
        return this.getSleepState() != SleepState.AWAKE
                || this.isOrderedToSit() || this.isOrderedSitPlaying()
                || idle == IdleVariant.SIT || idle == IdleVariant.STAND_UP || idle == IdleVariant.STRETCH
                || this.isCleaning() || this.IsDrinking()
                || this.isClimbing()
                || this.isInWaterOrBubble()
                || this.isPassenger()
                || this.isDeadOrDying();
    }

    public void onStartEatingItem(ItemStack stack) {}
    public void onStopEatingItem() {}

    private Integer fixAgeOnFirstTick = null;

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.fixAgeOnFirstTick != null) {
            this.setAge(this.fixAgeOnFirstTick);
            this.updateJuvenileState(this.fixAgeOnFirstTick);
            this.fixAgeOnFirstTick = null;
        }
        super.tick();

        if (!this.level().isClientSide()) {

            if (this.breedCooldown > 0) {
                this.breedCooldown--;
            }

            if (this.isPregnant()) {
                this.pregnancyTimer--;
                if (this.pregnancyTimer <= 0) {
                    this.giveBirth();
                }
            }

            // Once a second is plenty: a gamerule change taking up to a second to land is not noticeable.
            if (this.tickCount % 20 == 0) this.syncGameRules();

            // Aquí y no en el goal de sueño: tick() corre todos los ticks y los goals en alternos.
            this.sedation.tick();
            this.tickTemperature();
            this.tickHumidity();
            double tempMultiplier = Math.max(getTemperatureLossMultiplier(), getHumidityLossMultiplier());

            if (!this.needsEnabled()) {
                // doCritterNeeds off: kept full rather than frozen, so nothing downstream ever sees a
                // hungry or thirsty animal (it would hunt for food forever and never fall asleep).
                if (this.getHunger() < this.maxHunger()) this.setHunger(this.maxHunger());
                if (this.getThirst() < this.maxThirst()) this.setThirst(this.maxThirst());
            } else {
                if (this.getHunger() > 0) {
                    double lossPerTick = getHungerLossPerSecond() / 60.0 * tempMultiplier;
                    hungerLossAccum += lossPerTick;
                    if (hungerLossAccum >= 1.0) {
                        int loss = (int) hungerLossAccum; hungerLossAccum -= loss;
                        this.prevHunger = this.getHunger(); this.setHunger(prevHunger - loss);
                    }
                }
                if (this.getThirst() > 0) {
                    double lossPerTick = getThirstLossPerSecond() / 60.0 * tempMultiplier;
                    thirstLossAccum += lossPerTick;
                    if (thirstLossAccum >= 1.0) {
                        int loss = (int) thirstLossAccum; thirstLossAccum -= loss;
                        this.prevThirst = this.getThirst(); this.setThirst(prevThirst - loss);
                    }
                }
            }
            if (this.getEnrichment() > 0 && !this.isNewborn()) {
                double lossPerTick = getEnrichmentLossPerSecond() / 60.0;
                enrichmentLossAccum += lossPerTick;
                if (enrichmentLossAccum >= 1.0) {
                    int loss = (int) enrichmentLossAccum; enrichmentLossAccum -= loss;
                    this.setEnrichment(this.getEnrichment() - loss);
                }
            }
            if (this.getHygiene() > 0) {
                double lossPerTick = getHygieneLossPerSecond() / 60.0;
                hygieneLossAccum += lossPerTick;
                if (hygieneLossAccum >= 1.0) {
                    int loss = (int) hygieneLossAccum; hygieneLossAccum -= loss;
                    this.setHygiene(this.getHygiene() - loss);
                }
            }

            // Hunger and thirst kill through different causes because only starvation counts against
            // the wild population. With both empty, hunger wins: it is the one that counts.
            // The needs check goes first so the roll only consumes randomness when it matters.
            if ((this.getHunger() <= 0 || this.getThirst() <= 0) && random.nextInt(10) > 8) {
                if (this.getHunger() <= 0) {
                    this.starve();
                } else if (this.getThirst() <= 0) {
                    this.dehydrate();
                }
            }

            this.tickPassiveHealing();
            this.tickCaregiverPresence();

            if (this.idleVariantCooldown > 0) this.idleVariantCooldown--;
        }

        if (this.isOrderedToSit()) {
            if (!this.level().isClientSide) this.getNavigation().stop();
            Vec3 currentDelta = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, currentDelta.y, 0.0D);
        }

        if (!this.level().isClientSide()) {
            // Last: sitting zeroes the horizontal movement above, and must not hold the body on an edge.
            this.slideOffEdge();
        }
    }

    /** Something at least this far ahead of the heading (cosine) is met head on. */
    private static final double HEAD_ON = 0.5;
    /** Sideways push per tick while meeting something head on, blocks per tick. */
    private static final double SIDESTEP = 0.04;

    /**
     * Walking into something ahead, step to the right. Paths run through the middle of blocks, so
     * two critters meeting on one line push each other straight back and neither gets by (they
     * would walk on the spot until one gives up). Each keeping right, they pass.
     */
    @Override
    public void push(Entity other) {
        super.push(other);
        if (this.level().isClientSide() || this.zza <= 0.0F || this.isMantling() || this.isClimbing()) {
            return;
        }
        final double dx = other.getX() - this.getX();
        final double dz = other.getZ() - this.getZ();
        final double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0e-3) {
            return;
        }
        final double yaw = Math.toRadians(this.getYRot());
        if ((-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / distance < HEAD_ON) {
            return;
        }
        // Right is minus left, and left is (cos, sin) of the yaw.
        this.push(-Math.cos(yaw) * SIDESTEP, 0.0D, -Math.sin(yaw) * SIDESTEP);
    }

    /**
     * Standing on an edge with two feet or more over the drop: slide off toward it, at least at
     * {@link FootSupport#SLIDE_SPEED}, until nothing holds the body and it falls. Not while the
     * mantle or a climb places the body, nor while carried, swimming or floating.
     */
    private void slideOffEdge() {
        if (this.feet == null || !this.onGround() || this.isMantling() || this.isClimbing()
                || this.isPassenger() || this.isInWaterOrBubble() || this.isNoGravity()) {
            return;
        }
        if (!this.feet.check(this.level(), this.getX(), this.getY(), this.getZ(), this.yBodyRot,
                this.growthScale(), this.getBbWidth() * 0.5F)) {
            return;
        }
        if (this.zza > 0.0F) {
            // Walking onto what holds it: vanilla lifts the box onto a step as soon as its front edge
            // is over it, with the feet still on the ground below. That is arriving, not falling off.
            final double yaw = Math.toRadians(this.getYRot());
            if (-Math.sin(yaw) * this.feet.slideX + Math.cos(yaw) * this.feet.slideZ < 0.0D) {
                return;
            }
        }
        final Vec3 motion = this.getDeltaMovement();
        final double along = motion.x * this.feet.slideX + motion.z * this.feet.slideZ;
        if (along < FootSupport.SLIDE_SPEED) {
            final double push = FootSupport.SLIDE_SPEED - along;
            this.setDeltaMovement(motion.x + this.feet.slideX * push, motion.y, motion.z + this.feet.slideZ * push);
        }
    }

    protected void giveBirth() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        AgeableMob baby = this.getBreedOffspring(serverLevel, this);

        if (baby != null) {
            baby.setBaby(true);
            baby.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);

            // giveBirth() corre sobre la madre, asi que this es ella. Una cria invocada con /summon o
            // con huevo no pasa por aqui: nace sin cuidadora, huerfana y adoptable, que es el
            // comportamiento correcto por defecto.
            if (baby instanceof DiverseCritter cria) {
                cria.setCaregiver(this.getUUID());

                // Con algun progenitor domesticado la cria nace domesticada: nunca vivio en libertad,
                // asi que no puede contar ni restar de la poblacion salvaje. El dueño es el de la
                // madre o, si ella es salvaje, el del padre.
                UUID owner = this.isTame() ? this.getOwnerUUID() : this.sireOwner;
                if (owner != null) {
                    cria.setTame(true);
                    cria.setOwnerUUID(owner);
                    cria.setInWild(false);
                } else {
                    cria.setInWild(true);
                }
            }

            serverLevel.addFreshEntityWithPassengers(baby);

            if (baby instanceof DiverseCritter cria && cria.isInWild()) {
                WildPopulation.record(cria, PopulationEvent.WILD_BIRTH);
            }

            serverLevel.broadcastEntityEvent(this, (byte) 18);
        }

        this.sireOwner = null;
        this.setPregnant(false);
        this.pregnancyTimer = 0;
        this.breedCooldown = this.wildScaledCooldown(96000 + this.random.nextInt(24000));
    }

    protected void starve() { this.hurt(DOCDamageTypes.source(this.level(), DOCDamageTypes.STARVATION), 1); }
    protected void dehydrate() { this.hurt(DOCDamageTypes.source(this.level(), DOCDamageTypes.DEHYDRATION), 1); }

    public class CritterLookControl extends LookControl {
        public CritterLookControl() { super(DiverseCritter.this); }
        @Override public void tick() { if (DiverseCritter.this.getSleepState() == SleepState.AWAKE) super.tick(); }
        @Override protected boolean resetXRotOnTick() { return !DiverseCritter.this.isCrouching(); }
    }
    public class CritterMoveControl extends MoveControl {
        public CritterMoveControl() { super(DiverseCritter.this); }
        public void tick() { if (DiverseCritter.this.canMove()) super.tick(); }
    }

    private boolean inventoryOpen;
    private DiverseInventory inventory;
    private void createInventory() {
        SimpleContainer simplecontainer = this.getInventory();
        this.inventory = new DiverseInventory(this);
        if (simplecontainer != null) simplecontainer.removeListener(this);
        this.getInventory().addListener(this);
    }
    public void openGUI(Player player) {
        if (!this.level().isClientSide()) {
            ServerPlayer sp = (ServerPlayer) player;
            if (sp.containerMenu != sp.inventoryMenu) sp.closeContainer();
            sp.nextContainerCounter();
            DOCNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new OpenStatsScreenPacket(sp.containerCounter, this.getId()));
            sp.containerMenu = new DOCStatsMenu(sp.containerCounter, this.getInventory(), sp.getInventory());
            sp.initMenu(sp.containerMenu);
            this.inventoryOpen = true;
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(sp, sp.containerMenu));
        }
    }
    @Nullable public SimpleContainer getInventory() { return this.inventory; }
    public void closeInventory() { this.inventoryOpen = false; }
    @Override public void containerChanged(Container pContainer) {}

    public boolean isThirsty() { return getThirst() <= (int)(maxThirst() * 0.5f); }
    public boolean isHungry() { return getHunger() <= (int)(maxHunger() * 0.5f); }

    /**
     * Lo que no deja dormir y despierta al que duerme: hambre, sed o peligro de temperatura o
     * humedad. Con las necesidades o el clima apagados por gamerule esas partes nunca son ciertas.
     */
    public boolean hasUrgentNeed() {
        return isHungry() || isThirsty() || getTemperatureZone() >= 2 || getHumidityZone() >= 2;
    }
    public boolean isEnrichmentNeeded() { return getEnrichment() <= (int)(maxEnrichment() * 0.5f); }

    public boolean canMove() {
        return getSleepState() == SleepState.AWAKE
                && !this.isOrderedToSit() && !this.isCleaning();
    }

    protected void messageState(String state, Player player) {
        player.displayClientMessage(this.getName().copy().append(Component.literal(" is now " + state)), true);
    }

    @Override
    public InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);

        if (itemstack.is(DOCItems.ZOO_BOOK.get())) {
            this.openGUI(pPlayer);
            return InteractionResult.SUCCESS;
        }

        if (this.isBaby() && itemstack.is(DOCItems.FILLED_NURSER_BOTTLE.get())) {
            if (!this.level().isClientSide) {
                if (!this.isTame()) {
                    this.tame(pPlayer);
                }

                this.setHunger(this.maxHunger());
                this.setThirst(this.maxThirst());

                this.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-this.getAge()), true);

                this.level().broadcastEntityEvent(this, (byte) 7);

                itemstack.hurtAndBreak(1, pPlayer, (player) -> {
                    player.broadcastBreakEvent(pHand);
                });

                if (itemstack.isEmpty()) {
                    pPlayer.setItemInHand(pHand, new ItemStack(DOCItems.EMPTY_NURSER_BOTTLE.get()));
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isOwnedBy(pPlayer) && itemstack.is(DOCItems.TRAINING_STICK.get())) {
            if (this.isNewborn()) {
                if (level().isClientSide) pPlayer.displayClientMessage(Component.literal("Too young to follow orders!"), true);
                return InteractionResult.PASS;
            }

            if (level().isClientSide) return InteractionResult.CONSUME;

            if (!this.isOrderedToSit() && !this.isWandering()) {
                this.setWandering(true);
                this.setOrderedToSit(false);
                this.setInSittingPose(false);
                this.messageState("wandering", pPlayer);
            }
            else {
                boolean willSit = !this.isOrderedToSit();

                if (willSit) {
                    this.setCleaning(false);
                    this.setAttacking(false);
                    this.getNavigation().stop();
                    this.setTarget(null);

                    setModeSit(true);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 0.6f, 1.0f);
                } else {
                    this.setWandering(false);
                    this.setOrderedToSit(false);
                    this.setInSittingPose(false);
                }
                this.messageState(willSit ? "sit" : "following", pPlayer);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (this.isBaby() && this.isFood(itemstack)) {
            return InteractionResult.PASS;
        }

        return super.mobInteract(pPlayer, pHand);
    }

    protected void setModeSit(boolean sit) {
        this.setWandering(false);
        this.setOrderedToSit(sit);
        this.setInSittingPose(sit);
        if (sit) {
            if (!level().isClientSide) {
                this.getNavigation().stop();
                this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
            }
            this.setSprinting(false);
            this.setJumping(false);
            Vec3 dm = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, dm.y, 0.0D);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {

        SpawnGroupData res = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        if (reason != MobSpawnType.EVENT) {
            if (dataTag == null || !dataTag.contains("IsMale")) {
                this.setIsMale(this.random.nextBoolean());
            }
        }

        // Natural spawns are wild animals, but they do not add to the population: they are the
        // baseline the biome modifier already accounts for. Counting them would feed back on itself.
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            this.inWild = true;
        }


        if (reason == MobSpawnType.EVENT && this.loadedNbtAge != null) {

            if (this.getAge() > this.loadedNbtAge) {
                this.setAge(this.loadedNbtAge);
                this.updateJuvenileState(this.loadedNbtAge);
            }
        }

        return res;
    }

    /**
     * Taming takes the animal out of the wild population.
     *
     * Hooked here and not in setTame(): TamableAnimal calls setTame(true) when it loads a tame
     * animal from disk, which would subtract on every chunk load. tame(Player) is what every taming
     * path in the mod goes through.
     */
    @Override
    public void tame(Player pPlayer) {
        if (!this.level().isClientSide && this.inWild) {
            this.inWild = false;
            WildPopulation.record(this, PopulationEvent.TAME);
        }
        super.tame(pPlayer);
    }

    @Override
    public void aiStep() {
        if (getSleepState() != SleepState.AWAKE) setTarget(null);
        super.aiStep();
        if (!this.level().isClientSide) {
            // Como GuardingMeleeEntity (DeluxeLib). El orden importa: se mide DESPUES de
            // super.aiStep(), que es donde corre el move control y travel() aplica la velocidad de
            // este tick. Medirlo antes lee el estado previo al movimiento.
            //
            // Se añade "tiene camino pendiente" a la ENTRADA de la histéresis en vez de consultarlo
            // desde las playCondition. Athenian lo hace en la condición porque nada le recrea la
            // ruta desde fuera; aquí sí: triggerFoodSearch() llama a stop()+moveTo() hasta cinco
            // veces por segundo mientras haya comida en el suelo, así que isDone() parpadea y las
            // animaciones parpadeaban con él. Pasando por la histéresis, esos seis ticks de
            // retención absorben el vaivén y el flag sale estable.
            boolean movingNow = this.getDeltaMovement().horizontalDistanceSqr() > 2.5E-7
                    || !this.getNavigation().isDone();
            boolean moving = this.movingHysteresis.tick(movingNow);
            if (moving != this.entityData.get(IS_MOVING)) {
                this.entityData.set(IS_MOVING, moving);
            }

            // Despues de la histeresis a proposito: el sensor no la usa hoy, pero cualquier condicion
            // que anadamos a isThreat() o al estado vera el flag de movimiento ya asentado.
            this.threatSensor.tick();
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (getSleepState() != SleepState.AWAKE || this.isOrderedToSit() || this.IsDrinking() || this.isCleaning()) {
            if (!level().isClientSide) {
                this.getNavigation().stop();
                this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
            }
            Vec3 dm = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, dm.y, 0.0D);
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(travelVector);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (!this.level().isClientSide && result) {
            if (this.isCleaning()) this.setCleaning(false);
        }
        return result;
    }

    // --- DIET ---
    public abstract CritterDietConfig getDietConfig();

    @Override
    public boolean canMate(Animal otherAnimal) {
        if (otherAnimal == this || !this.canBreed()) return false;
        if (!(otherAnimal instanceof DiverseCritter otherCritter)) return false;
        if (!otherCritter.canBreed()) return false;

        if (this.getIsMale() == otherCritter.getIsMale()) return false;
        if (this.isPregnant() || otherCritter.isPregnant()) return false;
        if (this.isOrderedToSit() || otherCritter.isOrderedToSit()) return false;
        if (this.isBaby() || otherCritter.isBaby()) return false;
        if (!this.isTemperatureComfortable() || !otherCritter.isTemperatureComfortable()) return false;
        if (!this.isHumidityComfortable() || !otherCritter.isHumidityComfortable()) return false;

        return this.breedCooldown <= 0 && otherCritter.breedCooldown <= 0;
    }

    @Override
    public void setBaby(boolean pBaby) {
        this.setAge(pBaby ? -TOTAL_GROWTH_TIME : 0);
    }

    @Override
    public void setAge(int pAge) {
        if (pAge == -24000) {
            int currentAge = this.getAge();
            if (currentAge < -40000) {
                return;
            }
        }

        super.setAge(pAge);
        this.updateJuvenileState(pAge);
    }

    private void updateJuvenileState(int age) {
        boolean isJuvenile = age < 0 && age >= JUVENILE_AGE_THRESHOLD;
        if (this.entityData.get(IS_JUVENILE) != isJuvenile) {
            this.entityData.set(IS_JUVENILE, isJuvenile);
        }
    }

    @Override
    protected float getJumpPower() {
        return this.isNewborn() ? 0.0F : super.getJumpPower();
    }

    @Override
    public boolean canPickUpLoot() {
        return !this.isNewborn();
    }

    @Override
    public void ageUp(int pAmount, boolean pForced) {
        if (pForced) {
            super.ageUp(pAmount, true);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  WARINESS & FLIGHT
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Los escribe {@code ThreatSensor} en servidor; el cliente los lee para las playCondition, y el
     * comando {@code /doc_wariness} para introspeccion.
     *
     * <p><b>{@code IS_FLEEING} no dibuja nada.</b> El galope sale de {@link #isMoving()} y
     * {@code isSprinting()}, que ya viajan sincronizados. Existe para que otro goal pueda preguntar
     * "esta huyendo?" sin hablar con el sensor, que es solo de servidor.
     */
    public boolean isAlert() { return this.entityData.get(IS_ALERT); }
    public void setAlert(boolean value) { this.entityData.set(IS_ALERT, value); }

    public boolean isFleeing() { return this.entityData.get(IS_FLEEING); }
    public void setFleeing(boolean value) { this.entityData.set(IS_FLEEING, value); }

    /**
     * Opt-in por especie del sistema de wariness.
     *
     * <p><b>Falso por defecto a proposito.</b> La ardilla y la sifaka no tienen clips de alerta, y
     * encenderlas las dejaria congeladas cuatro segundos con la pose de idle puesta, que se lee como
     * un bug y no como miedo. Cuando lleguen sus clips se suman sobrescribiendo esto y registrando
     * {@code alert_enter} / {@code alert_loop}.
     */
    public boolean isWary() { return false; }

    /**
     * Que cuenta como amenaza. Por defecto cualquier jugador en supervivencia o aventura.
     *
     * <p><b>Creativo y espectador quedan fuera.</b> En creativo estas construyendo el recinto, y que
     * los animales salgan corriendo cada vez que te acercas hace el modo inservible para eso. El
     * precio es que el sistema de wariness no se puede probar volando: hay que estar en
     * supervivencia.
     *
     * <p><b>Deuda declarada:</b> anadir depredadores exige tocar dos sitios — sobrescribir esto y
     * ampliar la consulta de {@code ThreatSensor.scan()}, que hoy pide solo {@code Player.class}
     * porque es lo barato y va indexado por chunk. Hoy ningun mob del juego caza a estos critters,
     * asi que esa rama seria codigo sin nada que la dispare.
     */
    public boolean isThreat(LivingEntity other) {
        return other instanceof Player player && !player.isSpectator() && !player.isCreative();
    }

    /** Zona roja: huida inmediata. */
    public double innerThreatRadius() { return 4.0D; }

    /** Zona amarilla: alerta y arranque del temporizador. */
    public double outerThreatRadius() { return 10.0D; }

    /** Cuanto aguanta alertado antes de salir corriendo. 80 ticks = 4 s. */
    public int alertDwellTicks() { return 80; }

    /** Multiplicador de velocidad al huir. */
    public double fleeSpeed() { return 1.6D; }

    /**
     * Distancia a la que deja de huir. <b>Invariante: mayor que {@link #outerThreatRadius()}.</b>
     *
     * <p>Es el anti-loop: al terminar la huida la amenaza ya esta fuera de la zona amarilla, asi que
     * no puede re-alertarse en el acto. {@code ThreatSensor} loguea y clampa si se configura mal —
     * no lo comprueba con {@code assert}, que Java desactiva en runtime.
     */
    public double safeDistance() { return 16.0D; }

    /** Techo de la huida, para el acorralado que nunca alcanzaria safeDistance. 200 ticks = 10 s. */
    public int maxFleeTicks() { return 200; }

    /** Espera sin amenazas antes de volver a dormirse. 6000 ticks = 5 min. */
    public int reSleepCooldownTicks() { return 6000; }

    // ─────────────────────────────────────────────────────────────────────────
    //  CURADO PASIVO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Medio corazon por minuto, solo si esta bien alimentado.
     *
     * <p>Los umbrales son relativos porque cada especie tiene su escala: la civeta tiene 4000 de
     * hambre maxima y las demas otra cosa.
     *
     * <p>El intervalo se escalona con {@code getId()} para que veinte critters no se curen todos en el
     * mismo tick.
     *
     * <p>No trata distinto a los recien nacidos, no se apaga durmiendo y no consulta el sistema de
     * wariness. Cada condicion extra seria un sitio mas donde el curado se queda mudo sin que se
     * entienda por que; el vinculo con la comida y el agua ya cumple el objetivo de diseno.
     */
    private void tickPassiveHealing() {
        if ((this.tickCount + this.getId()) % HEAL_INTERVAL_TICKS != 0) return;
        if (this.getHealth() >= this.getMaxHealth()) return;
        if (this.getHunger() <= this.maxHunger() / 2) return;
        if (this.getThirst() <= this.maxThirst() / 2) return;

        this.heal(1.0F);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VINCULO MADRE-CRIA
    // ─────────────────────────────────────────────────────────────────────────

    public @Nullable UUID getCaregiver() { return this.caregiverUUID; }

    /** Vincula esta cria a una cuidadora y reinicia el contador de ausencia. */
    public void setCaregiver(@Nullable UUID uuid) {
        this.caregiverUUID = uuid;
        this.lastCaregiverSeenTick = this.tickCount;
    }

    public void clearCaregiver() {
        this.caregiverUUID = null;
    }

    /**
     * Una cria sin cuidadora, y por tanto adoptable. Un adulto nunca lo es: el vinculo solo significa
     * algo mientras es recien nacido, y devolver true para todos los adultos haria que el goal los
     * tratara como candidatos a adopcion.
     */
    public boolean isOrphan() {
        return this.isNewborn() && this.caregiverUUID == null;
    }

    public int ticksSinceCaregiverSeen() {
        return this.tickCount - this.lastCaregiverSeenTick;
    }

    /**
     * Mantiene vivo el vinculo mientras la cuidadora este cerca, y lo corta cuando lleva demasiado
     * tiempo sin aparecer.
     *
     * <p>Usa {@code ServerLevel.getEntity(UUID)}, que es una busqueda en mapa y no un escaneo por
     * area. Si la madre esta en un chunk descargado devuelve null y cuenta como ausencia — correcto,
     * porque desde el punto de vista de la cria lo es.
     *
     * <p>Esta es la regla que cubre los dos casos del encargo con un solo mecanismo: para la cria,
     * "mi madre murio" y "se la llevaron" son el mismo hecho.
     */
    private void tickCaregiverPresence() {
        if (this.caregiverUUID == null) return;
        if (!this.isNewborn()) {
            // Ha crecido: el vinculo deja de significar nada y se suelta para no arrastrarlo en NBT ni
            // ensuciar el comando de depuracion.
            this.clearCaregiver();
            return;
        }
        if (this.tickCount % CAREGIVER_CHECK_INTERVAL != 0) return;

        Entity caregiver = ((ServerLevel) this.level()).getEntity(this.caregiverUUID);
        if (caregiver instanceof DiverseCritter mother
                && mother.isAlive()
                && this.distanceTo(mother) <= CAREGIVER_RANGE) {
            this.lastCaregiverSeenTick = this.tickCount;
        }

        if (this.ticksSinceCaregiverSeen() > ORPHAN_TIMEOUT_TICKS) {
            this.clearCaregiver();
        }
    }

    /**
     * Al morir, corta el vinculo con su cria para que quede adoptable <b>en el acto</b> y no dentro de
     * 60 s.
     *
     * <p>Hace falta este camino porque al morir la entidad desaparece del mundo, y desde la cria eso
     * es indistinguible de "chunk descargado": el temporizador no puede saber que la madre no va a
     * volver. Se resuelve desde el otro lado, que si lo sabe.
     *
     * <p>El temporizador sigue siendo la red para todo lo demas: que se la lleven, que muera lejos de
     * la cria, o que cambie de dimension.
     */
    @Override
    public void die(DamageSource pDamageSource) {
        if (!this.level().isClientSide) {
            // A tame animal was already subtracted when it was tamed; counting its death would charge
            // the player twice. The dead/removed check guards against die() running twice.
            if (this.inWild && !this.isTame() && !this.dead && !this.isRemoved()) {
                PopulationEvent event = WildDeathRules.classify(pDamageSource, this.lastHurtByPlayer);
                if (event != null) WildPopulation.record(this, event);
            }

            for (DiverseCritter ward : this.level().getEntitiesOfClass(
                    DiverseCritter.class,
                    this.getBoundingBox().inflate(CAREGIVER_RANGE),
                    other -> other.isNewborn() && this.getUUID().equals(other.getCaregiver()))) {
                ward.clearCaregiver();
            }
        }
        super.die(pDamageSource);
    }
}