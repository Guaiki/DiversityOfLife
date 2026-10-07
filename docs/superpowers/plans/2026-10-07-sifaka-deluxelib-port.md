# Port del sifaka a DeluxeLib (módulo 6) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Todas las animaciones del sifaka (adulto y bebé) corren en DeluxeLib, como la civeta y la ardilla.

**Architecture:** `SifakaEntity.registerAnimations()` registra los clips con los helpers de
`DiverseCritter`; los goals pasan a las versiones "chain" que dirigen el animator; los modelos
sustituyen sus `animate(...)` por `AnimBlend.applyBlended` (capa 0 adulto, capa 1 bebé). Después se
limpia de `DiverseCritter` el sistema viejo, del que el sifaka era el último usuario.

**Tech Stack:** Forge 47.4.10 (MC 1.20.1), DeluxeLib 1.0.2, GameTest de Forge. Spec:
`docs/superpowers/specs/2026-10-07-sifaka-deluxelib-port-design.md`.

## Global Constraints

- Rutas de Java relativas a `src/main/java/com/evirapo/diversityofcritters/`.
- Sin animación de caída.
- Mismos clips que hoy; duraciones = `withLength` de cada clip (tabla del spec).
- Nunca `gradlew clean`. No añadir `build.gradle` ni `mods.toml` a los commits.
- Los gametests limpian sus entidades con `helper.killAllEntities()`.

---

### Task 1: Gametest de animaciones registradas

**Files:**
- Create: `gametest/SifakaAnimationGameTests.java`

- [ ] **Step 1: Escribir el test**

```java
package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Every animation the sifaka's goals and play conditions ask for is registered. A name that differs by
 * one letter between the registration and a goal fails silently in game (the pose just never plays),
 * so this pins the full list.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class SifakaAnimationGameTests {

    private static final String ARENA = "mantle_arena";

    private static final List<String> NAMES = List.of(
            "idle", "walk", "run",
            "ordered_sit_enter", "ordered_sit_loop", "ordered_sit_exit",
            "drink_enter", "drink_loop", "drink_exit",
            "clean_enter", "clean_loop", "clean_exit",
            "eat_enter", "eat_loop", "eat_exit",
            "stretch_loop", "sniff_left_loop", "sniff_right_loop",
            "idle_sit_enter", "idle_sit_loop", "idle_sit_exit",
            "idle_stand_up_enter", "idle_stand_up_loop", "idle_stand_up_exit",
            "climb_up", "climb_down", "climb_hang", "swim",
            "sleep_enter", "sleep_loop", "sleep_exit", "attack",
            "baby_idle_loop", "baby_crawl_loop",
            "baby_sleep_enter_loop", "baby_sleep_loop_loop", "baby_sleep_exit_loop",
            "cry_enter", "cry_loop", "cry_exit");

    private SifakaAnimationGameTests() {
    }

    @GameTest(template = ARENA)
    public static void everySifakaAnimationIsRegistered(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        SifakaEntity sifaka = helper.spawn(DOCEntities.SIFAKA.get(), new Vec3(5.5, 3, 5.5));

        for (String name : NAMES) {
            try {
                sifaka.animator().getByName(name);
            } catch (RuntimeException e) {
                helper.killAllEntities();
                helper.fail("the sifaka has no animation '" + name + "'");
            }
        }

        helper.killAllEntities();
        helper.succeed();
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew runGameTestServer`
Expected: `everysifakaanimationisregistered failed! the sifaka has no animation 'idle'`.

(El commit va con la Task 2, que es la que lo hace pasar.)

---

### Task 2: El sifaka en DeluxeLib

**Files:**
- Create: `common/entity/ai/SifakaEatLeavesChainGoal.java`
- Modify: `common/entity/custom/SifakaEntity.java`
- Modify: `client/models/SifakaModel.java`
- Modify: `client/models/SifakaBabyModel.java`

- [ ] **Step 1: `SifakaEatLeavesChainGoal`**

```java
package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Eating leaves. Replaces {@link SifakaEatPlantGoal}, the same way {@link SquirrelEatLeavesChainGoal}
 * replaced the squirrel's: the leaf search stays, the approach and the timing move to the base class,
 * and the animator plays the eat_enter / eat_loop / eat_exit chain.
 */
public class SifakaEatLeavesChainGoal extends PhasedActivityGoal<DiverseCritter> {

    private final SifakaEntity sifaka;
    private final int searchRadius;
    private @Nullable BlockPos leavesPos;

    private long lastScanTime = -999L;
    private static final int SCAN_INTERVAL_TICKS = 20;

    public SifakaEatLeavesChainGoal(SifakaEntity sifaka, double speed, int searchRadius) {
        super(sifaka, "eat_enter", "eat_loop", "eat_exit");
        this.sifaka = sifaka;
        this.searchRadius = searchRadius;
        // EATING_IDLE lasts 0.5 s (10 ticks); 5-6 passes is about the old goal's 60 ticks.
        repeat(5, 6);
        cooldown(200, 200);
        approach(1.0F + sifaka.getBbWidth() / 2.0F, speed);
    }

    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isHungry()) return false;
        if (mob.isOrderedToSit() || mob.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (sifaka.isClimbing() || sifaka.isEatingPlant()) return false;

        long now = mob.level().getGameTime();
        if (now - this.lastScanTime < SCAN_INTERVAL_TICKS) return false;
        this.lastScanTime = now;

        this.leavesPos = findNearestLeaves();
        return this.leavesPos != null
                && mob.getNavigation().createPath(this.leavesPos, 0) != null;
    }

    @Override
    protected boolean shouldContinue() {
        return this.leavesPos != null
                && mob.level().getBlockState(this.leavesPos).is(BlockTags.LEAVES);
    }

    @Override
    protected @Nullable Vec3 approachTarget() {
        return this.leavesPos == null ? null : Vec3.atCenterOf(this.leavesPos);
    }

    @Override
    protected boolean faceTarget() {
        return true;
    }

    @Override
    protected void onActivityStart() {
        if (this.leavesPos != null) {
            sifaka.setEatingItem(new ItemStack(mob.level().getBlockState(this.leavesPos).getBlock()));
        }
        sifaka.setEatingPlant(true);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        sifaka.setEatingPlant(false);
        sifaka.setEatingItem(ItemStack.EMPTY);
        if (!interrupted) {
            mob.setHunger(mob.maxHunger());
        }
        this.leavesPos = null;
    }

    private @Nullable BlockPos findNearestLeaves() {
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        int r = this.searchRadius;
        int vy = Math.min(r, 6);

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -vy, -r), origin.offset(r, vy, r))) {
            if (mob.level().getBlockState(pos).is(BlockTags.LEAVES)) {
                double d = origin.distSqr(pos);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
```

- [ ] **Step 2: `SifakaEntity` — registro de animaciones**

Imports:

```java
import com.evirapo.diversityofcritters.client.animations.SifakaAnims;
import com.evirapo.diversityofcritters.client.animations.SifakaBabyAnims;
import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
```

(La civeta ya importa sus clases de animación de cliente desde la entidad: los `AnimSource` son
`Supplier`, así que solo se resuelven en el cliente.)

Métodos nuevos, tras `registerGoals()`:

```java
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
```

- [ ] **Step 3: `SifakaEntity` — goals**

En `registerGoals()`:

| Sustituir | Por |
|---|---|
| `this.goalSelector.addGoal(2, new BabyCryGoal(this));` | `this.goalSelector.addGoal(2, new CritterCryChainGoal(this));` |
| `this.goalSelector.addGoal(3, new SitWhenOrderedToGoal(this));` | `this.goalSelector.addGoal(3, new CritterOrderedSitGoal(this));` |
| `this.goalSelector.addGoal(8, new CritterCleanGoal(this));` | `this.goalSelector.addGoal(8, new CritterCleanChainGoal(this));` |
| `this.forFoodGoal = new CritterEatItemGoal(this, DoCTags.Items.SIFAKA_FOOD);` + `this.goalSelector.addGoal(10, this.forFoodGoal);` | `this.goalSelector.addGoal(10, new CritterEatItemChainGoal(this, DoCTags.Items.SIFAKA_FOOD));` |
| `this.goalSelector.addGoal(13, new CritterDrinkGoal(this));` | `this.goalSelector.addGoal(13, new CritterDrinkChainGoal(this));` |
| `this.goalSelector.addGoal(14, new SifakaEatPlantGoal(this, 1.1D, 12));` | `this.goalSelector.addGoal(14, new SifakaEatLeavesChainGoal(this, 1.1D, 12));` |
| `this.goalSelector.addGoal(17, new CritterIdleGoal(this));` | `this.goalSelector.addGoal(17, new CritterIdleVariantGoal(this));` |

Y al final de `registerGoals()`, tras `RandomLookAroundGoal`:

```java
        // Gestos sueltos con el mismo sorteo que la civeta y la ardilla; sentarse y tumbarse los
        // lleva CritterIdleVariantGoal.
        this.goalSelector.addGoal(22, new CritterIdleGestureGoal(this, 120, 120)
                .add(IdleVariant.STRETCH, "stretch", 20)
                .add(IdleVariant.SNIFF_LEFT, "sniff_left", 25)
                .add(IdleVariant.SNIFF_RIGHT, "sniff_right", 25)
                .condition(c -> !this.isClimbing()));
```

- [ ] **Step 4: `SifakaEntity` — limpieza**

Borrar:
- los 27 campos `public final AnimationState ...` (de `idleAnimationState` a `swimAnimationState`);
- los campos `forFoodGoal`, `cryTimer`, `stopCryTimer`, `wasCryingClient`, `prevClientVariant`,
  `clientIdleTick`, `prevCleaningClient`, `clientCleanTick`, `clientCleanEndingTick`,
  `prevDrinkingClient`, `clientDrinkTick`, `clientDrinkEndingTick`, `prevEatingPlantClient`,
  `clientEatPlantTick`, `clientEatPlantEndingTick`, `sprintProgress`, `isVariantActive`;
- en el constructor, `this.idleAnimationState.start(0);`;
- en `tick()`, el bloque `if (this.level().isClientSide) { ... }` entero (contadores, sprint,
  `setupAnimationStatesClient()`, `handleCryingAnimationClient()`);
- en `aiStep()`, la línea `if (isHungry() && !this.isNewborn() && ...) triggerFoodSearch();`;
- los métodos `setupAnimationStatesClient()`, `triggerFoodSearch()` y `handleCryingAnimationClient()`.

En `doWalkHop()`, sustituir

```java
        boolean busy = this.getSleepState() != DiverseCritter.SleepState.AWAKE
                || this.isOrderedSitPlaying()
                || this.isVariantActive
                || this.isCleaning()
                || this.IsDrinking()
                || this.isEatingPlant();
```
por
```java
        // isVariantActive se rellenaba solo en el cliente: en servidor, que es donde se salta, valía
        // siempre false. isPerformingActivity() cubre beber, acicalarse, sentarse, variantes de
        // reposo, comer y cualquier clip que clave el movimiento.
        boolean busy = this.getSleepState() != DiverseCritter.SleepState.AWAKE
                || this.isOrderedSitPlaying()
                || this.isPerformingActivity();
```

En `doHurtTarget`, dentro de `if (flag) {`, al principio:

```java
            this.animator().play(this.animator().getByName("attack"));
```

- [ ] **Step 5: `SifakaModel`**

Imports: quitar `SifakaAnims`, `AnimationDefinition`, `KeyframeAnimations`, `AnimationState`, `Vector3f`;
añadir

```java
import net.darkblade.deluxelib.anim.Animation;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.deluxelib.client.anim.AnimBlend;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.component.LookAtAdditive;

import java.util.Set;
```

Sustituir `private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();` por:

```java
	/** The head follows what it looks at only in these; any other pose owns the head. */
	private static final Set<String> HEAD_TRACKING_ANIMS = Set.of("idle", "walk", "run", "ordered_sit_loop");

	private final LookAtAdditive<T, SifakaModel<T>> lookAt =
			new LookAtAdditive<T, SifakaModel<T>>(m -> m.Head, 75.0F, 40.0F)
					.enabledWhile(entity -> {
						Animation current = entity.animator().getCurrent(0);
						return current != MobAnimator.EMPTY
								&& HEAD_TRACKING_ANIMS.contains(current.getName());
					});
```

Sustituir `setupAnim` entero por:

```java
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
```

Borrar `animateWithWeight(...)` y `animateHop(...)`.

- [ ] **Step 6: `SifakaBabyModel`**

Imports: quitar `SifakaBabyAnims`, `AnimationDefinition`, `KeyframeAnimations`, `AnimationState`,
`Vector3f`; añadir `import net.darkblade.deluxelib.client.anim.AnimBlend;`.

Campo nuevo, el primero de la clase:

```java
    /**
     * What {@link #root()} returns: the part wrapping the root bone. KeyframeBlender resolves channels
     * with getAnyDescendantWithName from root(), so returning the root bone itself would silently drop
     * that bone's channels. See CivetModel's meshRoot.
     */
    private final ModelPart meshRoot;
```

En el constructor, primera línea: `this.meshRoot = root;`.

Borrar `private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();`, `animateWithWeight` y
`animateCrawling`. Sustituir `setupAnim` por:

```java
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        // Layer 1: the baby has its own bone hierarchy, so its clips live apart from the adult's.
        AnimBlend.applyBlended(this, entity.animator(), ageInTicks, 200L, 1);
    }
```

y `root()` por:

```java
    @Override
    public ModelPart root() {
        return meshRoot;
    }
```

- [ ] **Step 7: Compilar y gametests**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew runGameTestServer`
Expected: `All 21 required tests passed :)`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/gametest/SifakaAnimationGameTests.java src/main/java/com/evirapo/diversityofcritters/common/entity/ai/SifakaEatLeavesChainGoal.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SifakaEntity.java src/main/java/com/evirapo/diversityofcritters/client/models/SifakaModel.java src/main/java/com/evirapo/diversityofcritters/client/models/SifakaBabyModel.java
git commit -m "Sifaka port: animations, goals and models on DeluxeLib"
```

---

### Task 3: Quitar el sistema viejo de `DiverseCritter`

**Files:**
- Modify: `common/entity/custom/base/DiverseCritter.java`
- Modify: `common/entity/custom/CivetEntity.java`

- [ ] **Step 1: Comprobar que nadie más lo usa**

Run: `grep -rn "preparingSleepState\|sleepState\.\|awakeningState\|orderedSit\(Starting\|Idle\|Ending\)State\|clientOrderedSit\|getOrderedSit\(Starting\|Ending\)Duration" src/main/java --include=*.java | grep -v "base/DiverseCritter.java"`
Expected: sin resultados.

- [ ] **Step 2: Borrar**

En `DiverseCritter`: los seis `AnimationState` (`preparingSleepState`, `sleepState`, `awakeningState`,
`orderedSitStartingState`, `orderedSitIdleState`, `orderedSitEndingState`), los campos
`prevOrderedSitClient`, `clientOrderedSitTick`, `clientOrderedSitEndingTick` y `prevSleepState`, los
métodos `getOrderedSitStartingDuration()`, `getOrderedSitEndingDuration()`,
`handleSleepAnimationsClient()` y `updateSitAnimationClient()`, y en `tick()` el bloque

```java
        if (this.level().isClientSide()) {
            updateSitAnimationClient();
            handleSleepAnimationsClient();
        }
```

- [ ] **Step 3: `isOrderedSitPlaying()` desde el animator**

Sustituir

```java
    public boolean isOrderedSitPlaying() {
        return prevOrderedSitClient || clientOrderedSitEndingTick > 0;
    }
```
por
```java
    /** Any of the three ordered-sit clips is playing. Read from the animator, so it holds on both sides. */
    public boolean isOrderedSitPlaying() {
        return this.animator().isPlaying("ordered_sit_enter")
                || this.animator().isPlaying("ordered_sit_loop")
                || this.animator().isPlaying("ordered_sit_exit");
    }
```

y borrar el override idéntico de `CivetEntity.isOrderedSitPlaying()`.

- [ ] **Step 4: Build y gametests**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew runGameTestServer`
Expected: `All 21 required tests passed :)`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java
git commit -m "Sifaka port: drop the old animation states from DiverseCritter"
```

---

### Task 4: Prueba en juego y guía

- [ ] Checklist en juego para el usuario (cada estado del sifaka adulto y bebé). Con su confirmación,
  sección del módulo 6 en la guía del cliente (https://claude.ai/artifact/3U5ZEiYMzyqd1KVUHafyCK).
