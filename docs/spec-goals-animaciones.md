# Spec v2: goals con fases de animación

Sustituye a la v1. El diagnóstico (§1) se mantiene; el diseño (§2 en adelante) cambia por completo
tras analizar cómo están hechos los goals de Athenian, Arpy y el Minotauro.

Complementa [deluxelib-anim-integration.md](deluxelib-anim-integration.md) (librería y cableado).

---

## 1. Diagnóstico (sin cambios respecto a la v1)

### 1.1 Los goals sueltan sus `Flags` antes de que termine el clip de salida

Un goal que hace `setCleaning(false)` y termina libera `MOVE`/`LOOK`/`JUMP` en ese tick. El clip
`clean_ending` sigue 10 ticks más, durante los cuales `CritterIdleGoal` o
`WaterAvoidingRandomStrollGoal` ya pueden caminar. El critter se desliza con la pose de salida.

| Goal | Ending | ¿Retiene los Flags? |
|---|---|---|
| `SleepBehaviorGoal` | `getAwakeningDuration()` | ✅ `canContinueToUse()` → `timer < wakeDuration` |
| `CritterDrinkGoal` | 5 ticks | ⚠️ sí, con `isFinishing`/`finishingTimer` ad-hoc |
| `CritterIdleGoal` | 10 / 20 | ⚠️ por accidente: el `durationTimer` (110/120) lo incluye |
| `CritterCleanGoal` | 10 ticks | ❌ **no** |
| `SquirrelEatLeavesGoal` | 6 ticks | ❌ **no** |
| `BabyCryGoal` | 10 ticks | ❌ **no** (el ending es puramente cliente) |
| `SitWhenOrderedToGoal` | 20 ticks | ❌ **no** |

### 1.2 Duraciones escritas en tres sitios

sit: goal `110` / entidad `10+90+10` / clips. lay: `120` / `20+80+20`. drink: goal `finishingTimer < 5`
/ entidad `clientDrinkEndingTick > 5` / `withLength(0.25F)`. clean: entidad `10`/`10` pero el goal usa
`CLEAN_DURATION = 80`, que **no incluye el ending** — ya están desincronizados.

### 1.3 Máquinas de estado que solo existen en el cliente

`handleCryingAnimationClient()` y `updateSitAnimationClient()` corren enteras en el cliente. Un
jugador que entra en rango a mitad de la secuencia ve la fase equivocada. Y `isOrderedSitPlaying()`
devuelve **siempre `false` en el servidor**, pese a usarse como gate.

### 1.4 Exclusión mutua a mano

`setupAnimationStatesClient()` calcula `groundLocomotion`, `isVariantPlaying`, `isCleanPlaying`… cada
uno excluyendo a los demás con `&&`. Lo resuelve el reparto de prioridades por capa.

### 1.5 Los flags de actividad tienen varios dueños

`DRINKING`: `CritterDrinkGoal`, `FindWaterBowlGoal`, `FindFoodBowlGoal`,
`CritterMotherCleanBabyGoal` y el `tick()` de la entidad. `IS_EATING_PLANT`: `SquirrelEatLeavesGoal`,
`CritterEatItemGoal`, `SifakaEatPlantGoal`. `IS_DIGGING`: `DigDirtGoal`, `FindDigBoxGoal`.

> **Resuelto** (ver §10-D2): `FindFoodBowlGoal` llama a `setIsDrinking(true)` a propósito — el clip de
> beber *es* la animación de "cabeza abajo en el cuenco", se use para agua o para comida. No es un
> bug. Lo que sí cambia es que bajo el diseño nuevo esa elección queda declarada de forma explícita
> en el goal, en vez de escondida en un `setIsDrinking(true)` dentro de una clase llamada
> `FindFoodBowl`.

---

## 2. Qué hay ya construido en DeluxeLib

Antes de diseñar nada: la librería tiene **tres** patrones distintos para ligar goals y animaciones,
y hay que saber cuál copiar.

### 2.1 `GuardedMeleeAttackGoal` (Athenian) — un goal, fases internas

Un solo `Goal` con `enum Phase { CHASE, GUARD, ENGAGE }`. Las animaciones **no** las dispara el goal:
el goal expone accesores públicos (`isGuarding()`, `isRaisingGuard()`, `getStrafeDirection()`) y las
animaciones se enganchan por `playCondition`:

```java
guard.setPlayCondition(a -> guardGoal.isGuarding() && (guardGoal.isRaisingGuard() || !isMoving()));
guardLeft.setPlayCondition(a -> ... && guardGoal.getStrafeDirection() > 0);
```

Y lo más importante para nosotros — **el animator es la fuente de verdad para saber cuándo acaba una
fase**, con el contador de ticks solo como red de seguridad:

```java
.attackAnimations("attack", "attack_slice")
// ...
if (!this.isAttackAnimationPlaying() || this.postAttackDelay <= 0) {
    this.recoveringFromAttack = false;
    this.enterGuard(target, true);
}
```

El comentario del propio código lo dice: *"the real signal is isAttackAnimationPlaying(), this cap
only kicks in if that never reports false"*. Ese es el mecanismo que resuelve nuestra §1.2 de raíz.

### 2.2 `StaggerGoal` — congelar por estado del animator

```java
setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));   // NO JUMP: FloatGoal sigue flotando en agua
public boolean canUse() { return animatable.animator().isStaggering(); }
public void tick()      { mob.getNavigation().stop(); }
```

Sin timing propio: se suelta en el instante en que el animator dice que terminó. **Esta es la
respuesta al problema del movimiento**, y es mejor que lo que propuse en la v1 (ver §4).

### 2.3 `Cortex` — máquina de estados completa (Minotauro)

`Cortex<E,S>` + `Behavior<E,S>` + `BehaviorContext` (pizarra tipada con datos persistentes y
transitorios) + `CortexGoal` como puente a vanilla. Cada `Behavior` tiene `onEnter` / `tick()` que
devuelve el id del siguiente estado o `null` / `onExit(interrupted)` / `canBeInterrupted` /
`canStart` / `shouldForceExit` / `onDamaged`. Hay reglas globales, transiciones bloqueables y cola
de transiciones con prioridad.

La embestida del Minotauro **ya es una cadena de 4 fases**: `ChargeWindupBehavior` (juega
`charge_start`, fija dirección) → `ChargeRunBehavior` (bucle, tres salidas posibles) →
`ChargeHitBehavior` / `ChargeStunBehavior`. Y `TimedAnimationBehavior(anim, ticks, nextState)` es
literalmente "reproduce un one-shot, espera, transiciona".

**Es decir: la API de goal con fases que propones ya existe.** La pregunta no es si construirla, es
si Cortex sirve tal cual para los critters.

### 2.4 Por qué Cortex **no** sirve tal cual

`CortexGoal.canUse()` devuelve `true` siempre y retiene `MOVE`+`LOOK` de forma permanente. Es una
máquina de estados **exclusiva**: si la adoptamos, los ~20 goals del squirrel tienen que entrar
dentro, incluidos los vanilla (`TemptGoal`, `FollowOwnerGoal`, `FollowParentGoal`,
`WaterAvoidingRandomStrollGoal`, `LookAtPlayerGoal`, `RandomLookAroundGoal`). Eso es reescribir la IA
del mod, no migrar animaciones.

Cortex está pensado para jefes con un ciclo de combate cerrado. Los critters son lo contrario:
muchas actividades pequeñas, independientes, que compiten por prioridad.

---

## 3. Propuesta: `PhasedActivityGoal` en DeluxeLib

Un `Goal` vanilla normal — convive con los demás por prioridad y flags — que hospeda una
mini-máquina de fases. Toma prestados los mecanismos ya probados de §2.1 y §2.3, sin la
exclusividad de Cortex.

### 3.1 Las fases

```
APPROACH (opcional) → ENTER (opcional) → LOOP × N → EXIT (opcional) → fin
```

- **APPROACH**: navega hasta un `Supplier<@Nullable Vec3>` hasta estar dentro de `reach`. Cubre
  drink/eat/dig/scratch/find-bowl. Se omite si no se configura destino.
- **ENTER**: `PLAY_ONCE`. Avanza cuando `!animator.isPlaying(enter)` — animator como fuente de
  verdad, con cap de ticks de seguridad (§2.1).
- **LOOP**: `REPEATING`. Dura **`N × duración del clip`**, con `N` sorteado en `[minRepeats,
  maxRepeats]`. La duración se lee de la animación registrada (`BaseAnimation.getDuration()`), **no
  se escribe en el goal**. Esto es lo que mata §1.2.
- **EXIT**: se dispara como último acto del goal, que entonces termina. El congelado durante el exit
  lo lleva `AnimationLockGoal` (§4), no este goal.

### 3.2 La API — clase base abstracta (decisión tomada, §10-D1)

El ciclo de fases es `final` y no se puede romper desde fuera; la subclase solo rellena hooks.

```java
public abstract class PhasedActivityGoal<E extends Mob & Animatable<E>> extends Goal {

    public enum Phase { APPROACH, ENTER, LOOP, DONE }

    protected final E mob;

    protected PhasedActivityGoal(E mob, @Nullable String enter, String loop, @Nullable String exit);

    // --- ajustes, se llaman desde el constructor de la subclase ---
    protected final void repeat(int min, int max);        // repeticiones del LOOP
    protected final void cooldown(int min, int max);
    protected final void chance(float perTick);
    protected final void approach(float reach, double speed);
    protected final void safetyCap(int ticks);            // red de seguridad, por defecto 200

    // --- hooks que sobrescribe la subclase ---
    protected abstract boolean shouldStart();
    protected boolean        shouldContinue()        { return true; }   // corta el LOOP → EXIT
    protected @Nullable Vec3 approachTarget()        { return null; }   // null = sin fase APPROACH
    protected String[]       chooseChain()           { return defaultChain(); }  // {enter, loop, exit}
    protected void           onActivityStart()       {}                 // p.ej. setCleaning(true)
    protected void           onLoopCycle()           {}                 // una vez por repetición
    protected void           onActivityDone(boolean interrupted) {}     // p.ej. setCleaning(false)

    // --- ciclo de fases, cerrado ---
    @Override public final boolean canUse();
    @Override public final boolean canContinueToUse();
    @Override public final void    start();
    @Override public final void    tick();
    @Override public final void    stop();

    // --- introspección, para las playCondition ---
    public final Phase   phase();
    public final boolean isActive();
}
```

El corazón, que es lo que arregla §1.1 en una línea:

```java
@Override
public final boolean canContinueToUse() {
    return this.phase != Phase.DONE;
}
```

Y el avance de fase, copiando el mecanismo de §2.1 (animator como fuente de verdad, ticks como cap):

```java
case ENTER -> { if (!animator.isPlaying(enterAnim) || phaseTicks > safetyCap) enterLoop(); }
case LOOP  -> { if (--loopTicksLeft <= 0 || !shouldContinue()) fireExitAndFinish(); }
```

`loopTicksLeft` se inicializa como `repeats × animator.getByName(loop).getDuration()` — la duración
sale de la animación registrada, **nunca se escribe en el goal**. Eso es lo que mata §1.2.

Unas 150 líneas. Reemplaza `isFinishing`/`finishingTimer`, `CLEAN_DURATION`, `EAT_DURATION`, el
`switch` de `durationTimer` de `CritterIdleGoal` y los `prepareDuration`/`wakeDuration` de
`SleepBehaviorGoal`.

`chooseChain()` cubre las variantes de idle: `CritterIdleGoal` lo sobrescribe y sortea entre
`stand_up`, `sniff_left`, `sniff_right`, `sit_*` y `lay_*`. Las que son un solo clip se declaran como
`{null, "stand_up", null}` con `repeat(1, 1)`.

### 3.3 Ejemplo: `CritterCleanGoal` (57 líneas → 18)

```java
public class CritterCleanGoal extends PhasedActivityGoal<DiverseCritter> {

    public CritterCleanGoal(DiverseCritter critter) {
        super(critter, "clean_enter", "clean_loop", "clean_exit");
        repeat(2, 4);
        chance(0.005F);
        cooldown(200, 400);
    }

    @Override protected boolean shouldStart() {
        return mob.hasLowHygiene() && !mob.isNewborn()
            && mob.getSleepState() == SleepState.AWAKE
            && !mob.isOrderedToSit() && !mob.isIdleLocked()
            && !mob.isDoingSpecialActivity();   // ahora incluye isClimbing() — ver §5.1
    }

    @Override protected void onActivityStart() { mob.setCleaning(true); }

    @Override protected void onActivityDone(boolean interrupted) {
        mob.setCleaning(false);
        if (!interrupted) mob.setHygiene(mob.maxHygiene());   // igual que hoy: cortarlo no premia
    }
}
```

Desaparecen `cleanTimer`, `CLEAN_DURATION`, `start()`, `stop()`, `tick()`, `canContinueToUse()`, y
gana las tres fases y el gate de trepada que hoy le falta.

### 3.4 Ejemplo: bebida, con aproximación

```java
public class CritterDrinkGoal extends PhasedActivityGoal<DiverseCritter> {

    private @Nullable BlockPos waterPos;

    public CritterDrinkGoal(DiverseCritter critter) {
        super(critter, "drink_enter", "drink_loop", "drink_exit");
        repeat(2, 3);
        chance(0.3F);
        approach(1.1F, 1.2D);
    }

    @Override protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isThirsty()) return false;
        this.waterPos = generateTarget();              // se conserva tal cual del goal actual
        return waterPos != null && getLandPos(waterPos) != null;
    }

    @Override protected @Nullable Vec3 approachTarget() {
        return waterPos == null ? null : Vec3.atCenterOf(waterPos);
    }

    @Override protected boolean shouldContinue() { return !mob.isInWater(); }

    @Override protected void onActivityStart() { mob.setIsDrinking(true); mob.setDrinkPos(waterPos); }

    @Override protected void onLoopCycle() {         // un sorbo por repetición
        mob.setThirst(mob.maxThirst());
        mob.playSound(SoundEvents.GENERIC_SWIM, 0.7F, 0.5F + mob.getRandom().nextFloat());
    }

    @Override protected void onActivityDone(boolean interrupted) {
        mob.setIsDrinking(false);
        mob.setDrinkPos(null);
        this.waterPos = null;
    }
}
```

`onLoopCycle()` sustituye a `if (drinkTimer >= 5 && (drinkTimer - 5) % 20 == 10)`: el número de
sorbos *es* el número de repeticiones, y ya no hay aritmética de módulos que cuadrar con el clip.
`generateTarget()`, `getLandPos()` e `isConnectedToLand()` se conservan sin tocar.

### 3.5 Esto disuelve el problema de los múltiples dueños (§1.5)

Con las fases dentro del goal, **el flag pasa a ser una salida del goal, no una entrada**. Cada dueño
(`CritterDrinkGoal`, `FindWaterBowlGoal`, `FindFoodBowlGoal`, `CritterMotherCleanBabyGoal`) se
convierte en su propia instancia de `PhasedActivityGoal` con su propia cadena, y no pueden solaparse
porque compiten por `MOVE`.

**Consecuencia: `setExitAnimation` ya no hace falta.** Era necesario solo porque en la v1 la cadena
la dirigía el flag. Se cae de la lista de cambios en la librería.

### 3.6 Qué NO va por aquí

Las animaciones **derivadas de estado** siguen con `playCondition` puro, sin goal que las dirija:
`idle`, `walk`, `run`, `swim`, `climb_up/down/hang`, `glide`. Son estado continuo, no actividades con
principio y fin. Es exactamente la división que hace Arpy: `idle_fly`/`fly_sprint`/`dive_attack` van
por `playCondition`, mientras que `take_off`/`landing` los dispara `AbstractFlyingEntity` con
`play()` desde sus hooks de ciclo de vida (`onTakeoffBegin`, `onLandingBegin`, `onLandingComplete`) y
completa la transición con `shouldCompleteTakeoff() → !animator.isPlaying("take_off")` — otra vez el
animator como fuente de verdad.

---

## 4. El movimiento: `AnimationLockGoal` (corrección de la v1)

**Retiro la propuesta de la v1** de meter un chequeo en `customServerAiStep()`. `StaggerGoal` (§2.2)
demuestra que el idioma de la librería para "congela al mob mientras una animación manda" es **un
goal de prioridad 0 gated en el animator**. Es mejor porque libera los flags correctamente (los demás
goals *saben* que no pueden moverse, en vez de moverse y que otro les anule la velocidad) y no pelea
con el `MoveControl`.

```java
public class AnimationLockGoal extends Goal {
    public AnimationLockGoal(Mob mob) {
        // MOVE + LOOK, NO JUMP — igual que StaggerGoal, para que FloatGoal siga flotando en agua.
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse()           { return animator.isMovementLocked(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void    start()            { mob.getNavigation().stop(); }
    @Override public void    tick()             { mob.getNavigation().stop(); }
}
```

Con `locksMovement()` en `BaseAnimation` y `isMovementLocked()` en `MobAnimator`, copia exacta del
par `blockAdditive()` / `isAdditiveBlocked()` que ya existe.

### 4.1 Regla de reparto (importante)

`AnimationLockGoal` va a prioridad **0** y `PhasedActivityGoal` a la suya (8, 13, 17…). Si ambos
reclamaran el mismo clip habría conflicto: el lock robaría `MOVE` al goal de actividad. Por eso:

- **Se marcan con `locksMovement()`**: los clips `*_exit`, `climb_hang`, `sleep_*`, `cry_*`, y
  cualquier clip que no tenga un goal dueño.
- **NO se marcan**: los `*_loop` y `*_enter` de actividades con dueño — ahí ya retiene el
  `PhasedActivityGoal`.

Y por eso `PhasedActivityGoal` **termina al disparar el exit** en vez de retenerlo él: un solo
mecanismo se encarga de los congelados, sin solape. El relevo no deja hueco: en el mismo
`GoalSelector.tick()`, la pasada de limpieza para el goal de actividad y la de actualización arranca
`AnimationLockGoal`, que al ser prioridad 0 se evalúa antes que el goal de paseo.

Bonus: esto cubre gratis el caso en que la actividad es **desalojada** por un goal de más prioridad
(dormir interrumpiendo el acicalado). El exit se reproduce igual y el lock lo sostiene, aunque el
goal dueño ya no exista.

---

## 5. Cambios por goal

| Goal | Prio | Cambio |
|---|---|---|
| `CritterCleanGoal` | 8 | **Se borra.** → `PhasedActivityGoal` (§3.3). Gana el gate `!isClimbing()` que hoy le falta. |
| `CritterDrinkGoal` | 13 | **Se borra** salvo `generateTarget()`/`getLandPos()`, que pasan a ser el `Supplier` del `approach` (§3.4). |
| `SquirrelEatLeavesGoal` | 14 | Conserva `findNearestLeavesBlock()`; el resto → `PhasedActivityGoal` con `approach`. |
| `CritterIdleGoal` | 17 | → `PhasedActivityGoal` con `variant()` ×5. `STAND_UP`/`SNIFF_*` son `enter=null, exit=null, repeat(1)`. Mantiene `idleVariantCooldown`. |
| `BabyCryGoal` | 3 | → `PhasedActivityGoal`. Se borra `handleCryingAnimationClient()`. |
| `SleepBehaviorGoal` | 1 | → `PhasedActivityGoal` con `continueCondition` = la comprobación día/noche. Se borran `prepareDuration`, `wakeDuration` y el `switch`. |
| `SitWhenOrderedToGoal` | 4 | Se deja el goal vanilla intacto. El exit lo cubre `locksMovement()` + `AnimationLockGoal`. |
| `SquirrelGlideGoal` | 2 | Sin cambios de lógica. Solo `glide.setPlayCondition(a -> isGliding()).blockAdditive()`. |
| `SquirrelClimbGoal` | 16 | **Añadir `Flag.LOOK`** (hoy solo reclama `MOVE`, así que los goals de mirada siguen corriendo durante el HANG y con `LookAtAdditive` eso se ve como temblor de cabeza). Marcar `climb_hang` con `locksMovement()`. |
| `FindWaterBowlGoal` / `FindFoodBowlGoal` / `CritterMotherCleanBabyGoal` | 9–12 | → `PhasedActivityGoal` con `approach`. **Los tres declaran la cadena `drink_*`** en las tres especies: el clip de beber es la animación de "cabeza abajo en el cuenco", sirva agua o comida (§10-D2). Se conserva `BowlFeedingHelper` intacto. |
| `DigDirtGoal`, `ScratchLogGoal`, `FindDigBoxGoal` (Civet) | — | Mismo patrón en la fase 4. |
| Resto (`CustomFloatGoal`, `NaturalMateGoal`, `TemptGoal`, `FollowOwnerGoal`, `FollowParentGoal`, `FindCryingBabyGoal`, `CritterExploreHeightGoal`, `LookForFoodItems`, strolls, looks) | — | Sin cambios. |

### 5.1 Solape que hay que arreglar igualmente

`CritterCleanGoal` (prio 8) le gana `MOVE` a `SquirrelClimbGoal` (prio 16) y no comprueba
`isClimbing()` — `isDoingSpecialActivity()` del squirrel solo cubre `isGliding() || isEatingPlant()`.
Secuencia real: el squirrel cuelga de un tronco → arranca el acicalado → el climb goal es desalojado
→ su `stop()` llama a `tryStartGlide()` → planea reproduciendo la animación de acicalado. Mismo
riesgo con `CritterMotherCleanBabyGoal` (9), `CritterEatItemGoal` (10), `FindFoodBowlGoal` (11) y
`FindWaterBowlGoal` (12), todos con mejor prioridad que climb.

---

## 6. Cambios en la entidad

### 6.1 Borrados

`SquirrelEntity`: los 27 `AnimationState`, `setupAnimationStatesClient()` (~120 líneas),
`handleCryingAnimationClient()`, y todos los contadores `clientIdleTick`, `clientCleanTick`,
`clientCleanEndingTick`, `clientDrinkTick`, `clientDrinkEndingTick`, `clientEatPlantTick`,
`clientEatPlantEndingTick`, `prev*Client`, `isVariantActive`, `sprintProgress`, `cryTimer`,
`stopCryTimer`, `wasCryingClient`.

`DiverseCritter`: `updateSitAnimationClient()`, `clientOrderedSitTick`, `clientOrderedSitEndingTick`,
`prevOrderedSitClient`, `getOrderedSitStartingDuration()`, `getOrderedSitEndingDuration()`, los 6
`AnimationState`. `isOrderedSitPlaying()` se reimplementa sobre `animator().isPlaying(...)` y pasa a
ser correcto en servidor.

### 6.2 Añadidos

`DiverseCritter implements Animatable<DiverseCritter>` con el `MobAnimator`, un `registerAnimations()`
que registra las cadenas comunes y llama a un hook `registerSpeciesAnimations()`, y el flag
`IS_MOVING` sincronizado con histéresis:

```java
this.entityData.set(IS_MOVING, this.movingHysteresis.tick(
        this.getDeltaMovement().horizontalDistanceSqr() > 2.5E-7));
```

`MovementHysteresis` ya existe en la librería con este propósito exacto (6 ticks de hold) pero es
**package-private**: hay que hacerla pública. `deltaMovement` no se sincroniza para mobs, y el umbral
crudo parpadea, así que `idle.setPlayCondition(a -> !isMoving())` lo necesita.

### 6.3 Orden de tick

Forge dispara `LivingTickEvent` (donde la librería llama a `animator().tick()`) **antes** de
`aiStep()`, o sea antes del `GoalSelector`. Un flag encendido por un goal en el tick N no se ve en un
`playCondition` hasta N+1. Por eso `PhasedActivityGoal` dispara los clips con `play()` (efecto
inmediato) y no confía en el auto-arranque.

---

## 7. Reparto de prioridades de animación (capa 0)

Número más bajo gana.

| Prio | Animaciones | Mecanismo |
|---|---|---|
| 0 | `glide` | `playCondition` |
| 1 | `climb_up`, `climb_down`, `climb_hang` | `playCondition` |
| 2 | `swim` | `playCondition` |
| 3 | `sleep_*` | `PhasedActivityGoal` |
| 4 | `cry_*` | `PhasedActivityGoal` |
| 5 | `ordered_sit_*` | goal vanilla + `locksMovement()` |
| 6 | `drink_*`, `clean_*`, `eat_*` (Civet: `dig_*`, `scratch_*`) | `PhasedActivityGoal` |
| 7 | `stand_up`, `sniff_*`, `sit_*`, `lay_*` | `PhasedActivityGoal` con `variant()` |
| 9 | `idle` | `playCondition = !isMoving()` |

Las tres fases de una cadena van en la **misma capa y misma prioridad** (el guard de auto-arranque de
`MobAnimator.tick()` es `current.getPriority() <= base.getPriority()`).

---

## 8. Cambios en DeluxeLib

| # | Cambio | Estado |
|---|---|---|
| 1 | `PhasedActivityGoal`, clase base abstracta (§3) | nuevo, ~150 líneas |
| 2 | `BaseAnimation.locksMovement()` + `MobAnimator.isMovementLocked()` | ~12 líneas, gemelo de `blockAdditive()` |
| 3 | `AnimationLockGoal` (§4) | ~25 líneas, `StaggerGoal` con otro predicado |
| 4 | `MovementHysteresis` a `public` | 1 palabra |
| 5 | Aislar el paquete `test` (Athenian/Arpy/Spartan/Minotaur se registran incondicionalmente) | bloqueante |
| 6 | `MobAnimator.DEBUG = false` | bloqueante |
| ~~7~~ | ~~`setExitAnimation`~~ | **ya no hace falta** (§3.5) |

`Rig.Builder.walkBlend(...)` y la decisión sobre `applyWalk` quedan aplazadas: sigue en pie lo de la
v1 — `applyWalk` tiene cero usuarios en la librería, así que en la fase 1 se conserva el overlay
actual (`animateWalkWithWeight` + `sprintProgress`) envuelto en un `RigComponent`, y el swap es una
línea cuando queramos probarlo.

---

## 9. Fases y criterios de aceptación

**Fase 0 — DeluxeLib.** Los 6 cambios de §8. Publicar `1.4.0-1.20.1`.
*Aceptación*: Athenian, Arpy y Minotauro se comportan igual que antes.

**Fase 1 — Cableado.** Repo maven, `fg.deobf`, `mods.toml`, ATs.
*Aceptación*: el mod arranca con `deluxelib` y sin entidades de test en el juego.

**Fase 2 — `DiverseCritter`.** `Animatable`, `MobAnimator`, `IS_MOVING`, `AnimationLockGoal`, cadenas
comunes (`sleep_*`, `ordered_sit_*`, `cry_*`) con `PhasedActivityGoal`.
*Aceptación*: dormir, sentarse por orden y llorar con crossfade y sin deslizamiento durante los exits.

**Fase 3 — `SquirrelEntity`.** Resto de cadenas, `Rig`, borrado de `setupAnimationStatesClient`,
goals de §5. *Aceptación*, en juego:
1. Acicalado → caminar: sin deslizamiento durante los 10 ticks de salida.
2. Beber: N sorbos audibles = N repeticiones configuradas, sin corte duro entre fases.
3. Colgar de un tronco: no arranca el acicalado; sin temblor de cabeza.
4. Trepar → coronar → planear → aterrizar: sin poses residuales.
5. Variante idle SIT completa sin salto entre las 3 fases.
6. Marcha indistinguible de la versión actual.
7. Dormir interrumpiendo un acicalado: el exit del acicalado se reproduce entero y el critter no se
   desliza (verifica el relevo `PhasedActivityGoal` → `AnimationLockGoal`).

**Fase 4 — `SifakaEntity` y `CivetEntity`.** Civet añade `dig_*` y `scratch_*`. Ambos añaden
`attack`, donde entra `AnimatableMeleeAttackGoal` y se retira `IAnimatedAttacker`.

**Fase 5 — Opcional.** A/B de `applyWalk`; `registerDeath` + `CustomMobRenderer`.

---

## 10. Decisiones tomadas

Ninguna queda abierta. Registro de las que se consultaron y de las que se cerraron por evidencia.

### Consultadas

**D1 — Forma de `PhasedActivityGoal`: clase base abstracta.**
Los goals concretos la extienden y rellenan hooks protegidos. Encaja con cómo están escritos los 30
goals del mod, los stack traces llevan nombres reales, y los goals con búsqueda y navegación
(`CritterDrinkGoal`, `SquirrelEatLeavesGoal`, los de cuenco) necesitan métodos de verdad con sus
timers de reintento, no lambdas. El ciclo de fases va `final` para que no se pueda romper desde una
subclase.

**D2 — El clip de beber se usa también para comer del cuenco: es deliberado.**
No hay hueco que rellenar ni animación que crear. `FindFoodBowlGoal`, `FindWaterBowlGoal` y
`CritterMotherCleanBabyGoal` declaran la cadena `drink_*` de forma explícita en las tres especies. La
cadena `eat_*` (Squirrel y Sifaka) queda reservada para comer hojas y plantas, que es otra pose —
sentado sujetando la comida, con el hueso `Grab`. El Civet no necesita `eat_*`.

**D3 — Distribución: dependencia obligatoria aparte.**
`mods.toml` con `mandatory=true`; el usuario instala `deluxelib.jar` junto al mod. Es lo único fiable
con mixins y access transformers propios. **Prerrequisito**: aislar el paquete `test` de la librería
antes de publicar, para que Athenian, Arpy, Spartan y Minotaur no aparezcan en la partida (§8-5).

### Cerradas por evidencia, sin consulta

**D4 — `isDoingSpecialActivity()` se amplía con `isClimbing()`.**
De los 6 goals que lo consultan, `CritterEatItemGoal`, `CritterExploreHeightGoal` y `CritterIdleGoal`
ya comprueban `isClimbing()` por su cuenta; ampliarlo solo los vuelve redundantes. Los dos que no lo
comprueban —`CritterCleanGoal` y `CritterMotherCleanBabyGoal`— son precisamente los que deberían, así
que la ampliación arregla el bug de §5.1 sin regresiones.

**D5 — `SitWhenOrderedToGoal` se deja vanilla.** Su `canUse` ya es correcto; lo único que le falta es
retener durante los 20 ticks de salida, y eso lo cubre `locksMovement()` + `AnimationLockGoal`.

**D6 — `Animatable` va en `DiverseCritter`,** no en cada entidad: 6 cadenas comunes × 3 especies, y
`LionEntity` lo hereda gratis.

**D7 — Una sola capa de animación (la 0)** más el aditivo look-at del `Rig`. Athenian y Arpy usan solo
la capa 0; los critters nunca necesitan dos clips de cuerpo entero simultáneos.

**D8 — `applyWalk` no entra en la fase 1** (cero usuarios en la librería; el overlay actual funciona).

**D9 — Squirrel primero y solo.** Tiene planeo y trepada, o sea los casos raros; si el diseño le
sirve, Sifaka y Civet son mecánicos.
