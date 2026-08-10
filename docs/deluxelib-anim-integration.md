# DeluxeLib como dependencia de animación de DiversityOfCritters

Análisis de `C:\Andres\Trabajo\Deluxe_Lib` (mod id `deluxelib`, `net.darkblade.deluxelib:deluxelib:1.3.0-1.20.1`,
Forge 47.4.10 / MC 1.20.1 / Parchment 2023.09.03 — **idénticos a los del mod**) y plan de adopción
para `SquirrelEntity`, `SifakaEntity` y `CivetEntity`.

---

## 1. Cómo funciona el sistema de animaciones de la librería

Hay dos mitades bien separadas: una **máquina de estados servidor-autoritativa** (paquete `anim`)
y un **mezclador de keyframes cliente** (paquetes `client.anim` y `client.rig`).

### 1.1 Lado lógico: `MobAnimator` + `BaseAnimation`

```
Animatable<T>          interfaz que implementa la entidad: animator() + registerAnimations()
 └─ MobAnimator<T>     registro de animaciones, dividido en BlendLayer por capa
     └─ BlendLayer     mapa nombre→Animation; current() = la de MENOR número de prioridad
         └─ BaseAnimation / StandardAnimation
```

`MobAnimator.Events` (en la propia librería, `@Mod.EventBusSubscriber`) engancha tres eventos Forge
para **cualquier** entidad que implemente `Animatable`, sin que tengas que tocar nada:

| Evento | Qué hace |
|---|---|
| `EntityJoinLevelEvent` | llama a `registerAnimations()` |
| `LivingEvent.LivingTickEvent` | llama a `animator().tick()` — **en ambos lados** |
| `LivingDeathEvent` (LOWEST) | arranca la variante de muerte registrada |

Cada `StandardAnimation` se declara con:

```java
new StandardAnimation(nombre, AnimSource, Loop, capa, prioridad, duraciónSegundos)
```

- **`Loop.PLAY_ONCE`**: nunca arranca sola. Hay que `animator.play(...)` o encadenarla.
- **`Loop.REPEATING`**: **auto-arranca** en `MobAnimator.tick()` en cuanto su `playCondition` se cumple
  y no hay nada de prioridad igual o mejor sonando en la capa. Y **se auto-detiene** en el mismo tick
  en que la condición deja de cumplirse (solo en servidor — el cliente solo renderiza lo que le llega).
- **Prioridad: número más bajo = gana.** `play(x)` para todo lo que suene en la capa con
  `prioridad <= x.prioridad`.
- La duración en segundos se convierte a ticks (`× 20`) y **es la duración de gameplay**, independiente
  de la longitud real del clip. Athenian las hace coincidir a propósito (`ATTACK = 0.8125F` = el
  `withLength` del clip) para que el swing termine su recuperación antes de mezclar a `guard`.

Builder fluido sobre `BaseAnimation` (lo relevante para nosotros):

```java
anim.blendInMs(150).blendOutMs(300)   // crossfade de entrada/salida propio de esta anim
    .playbackSpeed(1.2F)              // solo reloj visual; el ciclo de servidor no cambia
    .blockAdditive()                  // apaga las capas aditivas (look-at) mientras suena
    .setNextAnimation("otra")         // encadena al TERMINAR por duración
    .setPlayCondition(a -> cond)      // gate lógico
    .onStart(e -> ...)                // acción servidor al arrancar
    .onFrame(4, e -> e.playSound(...))// acción servidor en el tick N
```

Lo demás (`hyperArmor()`, `invulnerable()`, `invulnerableLastTicks()`, `registerStagger`, `HitWindow`,
`PoiseTracker`) es de combate tipo souls-like y **no aplica a los critters**.

**Sincronización**: `BaseAnimation.start()/stop()` emiten `AnimationSyncClientboundPacket` a los
jugadores del nivel. El cliente no evalúa `playCondition` nunca (`tick()` corta esa rama con
`clientSide`) — solo reproduce lo que el servidor le dice. Esto elimina de raíz todos los contadores
`clientDrinkTick` / `clientDrinkEndingTick` / `clientEatPlantEndingTick` que hoy tenemos en las
entidades del mod.

### 1.2 Lado visual: `BlendState` → `KeyframeBlender`

Aquí está el valor real de la librería. `AnimBlend.applyBlended()` mantiene, **por entidad y por capa**,
un `BlendState` con:

- la animación *current* con su progreso de blend-in,
- una **pila N-aria de capas fading** (no un simple prev/current), para que una interrupción rápida
  (idle→walk cortado a mitad por una acción) no tire ninguna pose de golpe;
- **follow-through**: una `PLAY_ONCE` desplazada *sigue avanzando* mientras se desvanece, con dilatación
  temporal proporcional a su peso — el gesto interrumpido completa su arco. Una `REPEATING` desplazada se
  **congela** (un ciclo no tiene arco que completar; dejarlo correr bombea la zancada durante el fade);
- **blend-in y blend-out independientes** (fast-in / slow-out), con el saliente sostenido al menos el
  `blendInMs` del entrante para que no haya hueco de peso hacia la pose de reposo;
- **relleno complementario**: mientras el entrante rampa, la pila fading se reescala a exactamente
  `1 - pesoActual` (con tope 1.5×), evitando el parpadeo hacia bind-pose típico de cadenas encadenadas;
- **continuidad al reanudar**: si vuelves a A mientras A aún se desvanecía, recupera su peso exacto
  (vía `inverseSmoothstep`) y, si es un loop, hace `seekTo()` al tiempo que se estaba mostrando —
  sin saltos de un frame.

`KeyframeBlender.blend()` acumula todos los `AnimSample` (definición + tiempo + peso) por hueso y
canal, y **solo normaliza hacia abajo**: un déficit de peso se conserva a propósito, porque los
targets se aplican como offsets aditivos sobre la pose base — ese déficit *es* el fundido hacia reposo.

Comparado con lo que hace hoy `SquirrelModel.setupAnim`, que usa `AnimationState.animateWhen` +
`animate(...)` con peso 1.0F: eso es un **corte duro** entre fases. Todo el "pop" entre
`SIT_STARTING → SIT_IDLE → SIT_ENDING` desaparece con la librería sin tocar un solo keyframe.

### 1.3 El `Rig` (composición del `setupAnim`)

`Rig` es una lista ordenada de `RigComponent<T, M>` (interfaz funcional). Athenian:

```java
private static final Rig<AthenianEntity, AthenianModel> RIG =
    Rig.<AthenianEntity, AthenianModel>builder()
        .resetPoses()
        .keyframeBlend(400L, 0)          // duración por defecto, capa 0
        .lookAt(m -> m.head, 40f, 30f)   // aditivo sobre los keyframes
        .build();

@Override public void setupAnim(...) {
    RIG.apply(entity, this, AnimContext.of(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch));
}
```

Arpy es igual con `keyframeBlend(200L, 0)` y `lookAt(m -> m.head, 30f, 20f)`.

`LookAtAdditive` **suma** (`part.yRot +=`) en vez de asignar, y se auto-atenúa a 0 con constante de
tiempo cuando `animator.isAdditiveBlocked()` — es decir, cuando suena una anim marcada
`blockAdditive()`. Existe también `lookAtChain(...)` para repartir el giro entre head/neck/chest.

Como `RigComponent` es funcional, **el mod puede añadir sus propios componentes** sin tocar la
librería (pose de bebé, offsets de trepada, ocultar partes del planeo).

### 1.4 El sistema de *walk* (marcha con `limbSwing`) — existe pero está sin usar

`MobAnimator.registerWalk(nombre, AnimSource)` + `setWalkSelector(...)` + `AnimBlend.applyWalk(...)`
implementan crossfade **con phase matching**: dos marchas de longitud de ciclo distinta se muestrean
en la *misma fase normalizada de zancada*, no en el mismo tiempo acumulado, y la fase avanza según
la distancia recorrida sobre la longitud de ciclo interpolada. Es exactamente el remedio contra las
"piernas de goma" al pasar de walk a run.

**Ningún modelo de test lo usa** (ni Athenian ni Arpy) y **no hay componente de `Rig` para él** — hay
que llamar `AnimBlend.applyWalk(...)` a mano en `setupAnim`, o añadir un `WalkBlend` al builder
(recomendado, ver §5).

Esto es justo lo que hoy hace a mano `SquirrelModel.animateWalkWithWeight` con `sprintProgress`,
pero sin phase matching.

---

## 2. Integrarla como dependencia

### 2.1 Publicar la librería en su repo local

El `build.gradle` de la librería ya está preparado: publica el jar **reobfuscado** (SRG) a
`file://<Deluxe_Lib>/mcmodsrepo`, con `artifactId` fijado a `deluxelib`. El directorio aún no existe,
así que hay que ejecutarlo una vez (y de nuevo cada vez que cambie la librería):

```bash
cd /c/Andres/Trabajo/Deluxe_Lib && ./gradlew publish
```

### 2.2 `build.gradle` del mod

En `repositories`:

```gradle
maven { url = "file:///C:/Andres/Trabajo/Deluxe_Lib/mcmodsrepo" }
```

En `dependencies`:

```gradle
implementation fg.deobf("net.darkblade.deluxelib:deluxelib:1.3.0-1.20.1")
```

`fg.deobf` es obligatorio: el jar publicado está reobfuscado a propósito (igual que uno de CurseForge).

> **Por qué no un composite build (`includeBuild`)**: ForgeGradle 6 no resuelve bien proyectos
> incluidos que dependen del artefacto de Minecraft. El flujo `publish` → `fg.deobf` es el que la
> librería ya tiene montado y el que funciona.

### 2.3 `mods.toml` del mod

```toml
[[dependencies.diversityofcritters]]
    modId="deluxelib"
    mandatory=true
    versionRange="[1.3.0,)"
    ordering="AFTER"
    side="BOTH"
```

### 2.4 Access Transformers

La librería declara en su AT:

```
public net.minecraft.world.entity.AnimationState f_216970_ # lastTime
public net.minecraft.world.entity.AnimationState f_216971_ # accumulatedTime
public net.minecraft.client.model.geom.ModelPart f_104212_ # cubes
public net.minecraft.client.model.geom.ModelPart f_104213_ # children
```

`AnimSource extends AnimationState` y toca `accumulatedTime`/`lastTime` directamente. En producción
Forge aplica el AT del jar de la librería, pero **en el workspace de desarrollo del mod el AT de una
dependencia deobf no se aplica al artefacto de MC**, y eso puede dar `IllegalAccessError` en runtime
de dev. Copia esas cuatro líneas a
`src/main/resources/META-INF/accesstransformer.cfg` del mod (la línea de `LivingEntity` que ya tenemos
no entra en conflicto).

### 2.5 Dos cosas que hay que limpiar en la librería antes de depender de ella

1. **`DeluxeLib` registra las entidades de test siempre**: `TestRegistry.register(modEventBus)` y
   `registerGameplay()` corren incondicionalmente, así que Athenian, Arpy, Spartan y Minotaur (con su
   datagen, sus tabs y sus paquetes de posesión) entrarían en el juego junto al mod. Hay que meterlos
   detrás de un flag (`Boolean.getBoolean("deluxelib.tests")`) o moverlos a un sourceSet aparte.
2. **`MobAnimator.DEBUG = true`**: loguea cada START/STOP/BLEND de cada entidad, cada tick. Ponerlo a
   `false` (o a una system property) antes de integrar; con 20 ardillas en pantalla es ruido puro.

---

## 3. El patrón de 3 pasos (starting → idle → ending)

Es el patrón dominante del mod. Inventario actual:

| Entidad | Cadenas de 3 pasos |
|---|---|
| `DiverseCritter` (base) | `sleep` (preparing/sleep/awakening), `ordered_sit` |
| `SquirrelEntity` | `sit`, `lay`, `drink`, `clean`, `cry`, `eat_plant` |
| `SifakaEntity` | `sit`, `lay`, `drink`, `clean`, `cry`, `eat_plant` |
| `CivetEntity` | `sit`, `lay`, `drink`, `clean`, `cry`, `dig`, `scratch` |

Ocho o nueve cadenas por entidad, hoy cada una con su bloque de `animateWhen` con aritmética de ticks
a mano en `setupAnimationStatesClient()` (`this.clientDrinkTick <= 5`, `clientEatPlantEndingTick > 6`…),
más campos `prev*Client` para detectar flancos.

### 3.1 Lo que la librería resuelve tal cual

**Entrada → bucle: gratis**, vía `setNextAnimation`. Cuando la `PLAY_ONCE` de entrada agota su
duración, `triggerNextAnimation()` arranca la siguiente por nombre (comprobando su `canPlay()`).

**Bucle → salida: NO está cubierto.** Cuando una `REPEATING` se detiene porque su `playCondition` dejó
de cumplirse, `BaseAnimation.tick()` hace `this.stop(); return;` **sin** llamar a
`triggerNextAnimation()`. Ese camino solo se recorre al agotarse la duración. Así que hoy el `ending`
hay que lanzarlo explícitamente desde el goal/entidad.

### 3.2 Patrón recomendado tal como está la librería hoy

Regla clave: **las tres fases van en la misma capa y con la misma prioridad.** El auto-arranque de
`REPEATING` en `MobAnimator.tick()` se salta la anim si `current.getPriority() <= base.getPriority()`,
así que con prioridades iguales el bucle no puede pisar a su propia entrada. Y `play(ending)` para
al bucle porque `prioridadBucle <= prioridadEnding`.

```java
// --- registro (registerAnimations) ---
StandardAnimation drinkStart = new StandardAnimation("drink_start",
        new AnimSource(() -> SquirrelAnims.DRINK_STARTING), Loop.PLAY_ONCE, 0, P_ACTION, 0.25F);
StandardAnimation drinkIdle  = new StandardAnimation("drink_idle",
        new AnimSource(() -> SquirrelAnims.DRINK_IDLE),     Loop.REPEATING, 0, P_ACTION, 1.0F);
StandardAnimation drinkEnd   = new StandardAnimation("drink_end",
        new AnimSource(() -> SquirrelAnims.DRINK_ENDING),   Loop.PLAY_ONCE, 0, P_ACTION, 0.30F);

drinkStart.blendInMs(150).blendOutMs(120).setNextAnimation("drink_idle");
drinkIdle .blendInMs(120).blendOutMs(150).setPlayCondition(a -> this.IsDrinking());
drinkEnd  .blendInMs(120).blendOutMs(200);

animator.register(drinkStart, drinkIdle, drinkEnd);
```

```java
// --- disparo desde el goal (SOLO servidor, se sincroniza sola) ---
// al empezar a beber:
animator.play(animator.getByName("drink_start"));
// al dejar de beber:
animator.play(animator.getByName("drink_end"));
```

Con eso desaparecen: `clientDrinkTick`, `clientDrinkEndingTick`, `prevDrinkingClient`, los tres
`AnimationState`, las tres llamadas a `animateWhen` y las tres a `animate(...)` en el modelo. Por cadena.

### 3.3 Mejora sugerida en la librería: `setExitAnimation`

Con 25+ cadenas en el mod, merece la pena cerrar el hueco en la propia librería. Cambio mínimo en
`BaseAnimation`:

```java
@Nullable String exitAnimation = null;   // + @Setter (ya hay @Setter a nivel de clase)

// en tick(), rama de corte por condición:
if (!clientSide && !this.canPlay()) {
    if (this.canInterrupt) {
        this.stop();
        this.triggerExitAnimation();     // nuevo: play del exit si está definido y canPlay()
        return;
    }
}
```

Entonces una cadena de 3 pasos queda **puramente declarativa** y el goal solo dispara la entrada:

```java
drinkStart.setNextAnimation("drink_idle");
drinkIdle .setPlayCondition(a -> this.IsDrinking()).setExitAnimation("drink_end");
```

Recomiendo hacerlo antes de migrar la primera entidad: ahorra ~25 llamadas explícitas de `play(end)`
repartidas por goals y evita que se olvide alguna (síntoma: la pose se queda clavada hasta que otra
animación la desplace).

---

## 4. Plan concreto para `SquirrelEntity`

Es la entidad más compleja de las tres (tiene planeo y trepada además de todo lo demás), así que si
el diseño le sirve, sirve para Sifaka y Civet.

### 4.1 Firma de la entidad

```java
public class SquirrelEntity extends DiverseCritter implements Animatable<SquirrelEntity> {
    private final MobAnimator<SquirrelEntity> animator = new MobAnimator<>(this);
    @Override public @NotNull MobAnimator<SquirrelEntity> animator() { return this.animator; }
    @Override public void registerAnimations() { /* … */ }
}
```

`registerAnimations()` lo llama la librería en `EntityJoinLevelEvent`; el constructor no debe depender
de él. Si `Animatable` se pone en `DiverseCritter` en vez de en cada hoja, las tres entidades heredan
el cableado y las cadenas comunes (`sleep`, `ordered_sit`) se pueden registrar una sola vez en la base.

### 4.2 Reparto de prioridades (capa 0, cuerpo completo)

Número más bajo gana. Propuesta:

| Prioridad | Animaciones | Notas |
|---|---|---|
| 0 | `glide` | `blockAdditive()` — sin look-at planeando |
| 1 | `climb_up`, `climb_down`, `climb_idle` | `blockAdditive()` |
| 2 | `swim` | |
| 3 | `sleep_prep` / `sleep_idle` / `sleep_end` | `blockAdditive()` |
| 4 | `ordered_sit_*` | |
| 5 | acciones: `drink_*`, `clean_*`, `eat_*`, `cry_*` | |
| 6 | variantes idle: `stand_up`, `sniff_left`, `sniff_right`, `sit_*`, `lay_*` | |
| 9 | `idle` | fondo; `playCondition = !isMoving()` |

Con esto, la maraña de booleanos de `setupAnimationStatesClient()` (`groundLocomotion`,
`isVariantPlaying`, `isCleanPlaying` con su `&& !isOrderedSitPlaying`…) se convierte en **orden de
prioridad**: no hace falta excluir a mano cada estado contra los demás.

`SIT_STARTING/IDLE/ENDING` se usan hoy para dos máquinas distintas (`idleSit*` y `orderedSit*`). En la
librería son dos tríos de animaciones con **nombres distintos** que comparten el mismo
`AnimationDefinition` — `AnimSource` envuelve un `Supplier`, así que basta apuntar los dos al mismo campo.

### 4.3 Marcha (walk / run)

Mantener el enfoque actual dirigido por `limbSwing`, pero con el phase matching de la librería:

```java
// registerAnimations()
animator.registerWalk("walk", new AnimSource(() -> SquirrelAnims.Walk));
animator.registerWalk("run",  new AnimSource(() -> SquirrelAnims.Run));
animator.setWalkSelector((e, a) -> a.setWalk(e.isSprinting() ? "run" : "walk", 1.0F));
```

y en el `Rig`, un componente propio (o el `WalkBlend` de §5):

```java
.add((e, m, ctx) -> AnimBlend.applyWalk(m, e.animator(),
        ctx.limbSwing(), ctx.limbSwingAmount(), ctx.partialTick(), 200L))
```

`applyWalk` ya escala por `limbSwingAmount`, así que en parado aporta 0. El `idle` con
`playCondition = !isMoving()` se funde por su `blendOutMs` en paralelo. Los dos fundidos son
aproximadamente complementarios pero **hay que afinarlos en juego** — si al arrancar a andar se nota
un bajón hacia bind-pose, subir el `blendOutMs` del idle; si se nota "doble pose", bajarlo.
`sprintProgress` y `animateWalkWithWeight` se eliminan.

### 4.4 El `Rig` de `SquirrelModel`

```java
private static final Rig<SquirrelEntity, SquirrelModel<SquirrelEntity>> RIG =
    Rig.<SquirrelEntity, SquirrelModel<SquirrelEntity>>builder()
        .resetPoses()
        .add((e, m, ctx) -> { if (m.young) m.applyStatic(SquirrelAnims.BABY); })
        .keyframeBlend(200L, 0)
        .add((e, m, ctx) -> AnimBlend.applyWalk(m, e.animator(), ctx.limbSwing(),
                ctx.limbSwingAmount(), ctx.partialTick(), 200L))
        .lookAt(m -> m.Head, 40f, 30f)
        .add(SquirrelModel::applyPartVisibility)   // Glide/Glide_torse/bone12 a escala 0, offset de cola
        .build();
```

Notas de orden:
- `resetPoses()` primero (la librería no lo hace sola).
- La pose de bebé va **después** del reset y **antes** del blend, porque el blend suma offsets encima.
- `lookAt` sustituye a las asignaciones directas `this.Head.xRot = headPitch…` y a su gate manual
  `if (!climbing && !gliding && !orderedSitPlaying)`: basta marcar esas animaciones con
  `blockAdditive()` y el aditivo se atenúa solo, además con rampa suave en vez de corte.
- Lo que **no** cambia y sigue en el modelo: el `renderToBuffer` con la rotación de −90° de trepada,
  el `poseStack.scale(0.5f)`, el `Tail.y -= 3.0F` de despeje contra la pared y el escalado a 0 de las
  partes de planeo. Son transformaciones de render, no keyframes.
- `root()` de `SquirrelModel` devuelve `squirrel` ("Giant flying squirrel"), no el root horneado —
  correcto: `KeyframeBlender` resuelve huesos con `getAnyDescendantWithName` desde ahí. Ojo con la
  advertencia de `AthenianModel`: un hueso llamado literalmente `"root"` se resolvería al root
  invisible de la capa horneada. Ninguno de nuestros modelos lo usa.

### 4.5 Sonidos y partículas por frame

`onFrame(tick, entity -> …)` corre **solo en servidor** y es el reemplazo natural de los contadores de
tick para los sorbos de `drink`, los mordiscos de `eat_plant` o el chillido de `cry`:

```java
drinkIdle.onFrame(6,  e -> e.playSound(SoundEvents.GENERIC_DRINK, 0.6F, 1.2F));
eatIdle  .onFrame(4,  e -> e.playSound(SoundEvents.GENERIC_EAT,   0.5F, 1.4F));
```

---

## 5. Qué añadiría a la librería (ordenado por rentabilidad)

1. **`BaseAnimation.setExitAnimation(String)`** (§3.3) — cierra el 50% que falta del patrón de 3 pasos.
   ~10 líneas. Es lo primero.
2. **`Rig.Builder.walkBlend(long ms)`** — envolver `AnimBlend.applyWalk` en un `RigComponent` como ya
   está `KeyframeBlend`. Hoy es la única pieza del sistema que obliga a llamadas sueltas en `setupAnim`.
3. **Aislar el paquete `test`** y `MobAnimator.DEBUG` (§2.5). Bloqueante para publicar.
4. **Limpiar `Animation.canInterrupt()`** — es API muerta: nadie la llama, `BaseAnimation` la
   sobreescribe devolviendo `false` mientras el campo homónimo (leído por `tick()` vía
   `isCanInterrupt()`) vale `true` por defecto. Dos cosas distintas con el mismo nombre; confunde al
   leer `registerDeath`/`registerStagger`, que sí manipulan el campo.
5. Para muertes de critters, si algún día las hay: `registerDeath(...)` + heredar de
   `CustomMobRenderer` (mata el volteo de 90°) + el `LivingEntityRendererMixin` de la librería (mata
   el tinte rojo persistente). Viene gratis con la dependencia.

---

## 6. Qué sirve para Sifaka y Civet

Las tres entidades comparten arquitectura, así que la migración es la misma con distinto inventario:

**Idéntico a Squirrel** (reutilización directa del trabajo):
`sit`, `lay`, `drink`, `clean`, `cry`, `stand_up`, `sniff_left/right`, `swim`, `climb_*`,
`idle`/`walk`/`run`, y las cadenas `sleep` + `ordered_sit` que viven en `DiverseCritter`.

**Solo Squirrel**: `glide` (prioridad 0).

**Solo Civet**: `dig` y `scratch` — dos cadenas de 3 pasos más, mismo patrón, prioridad de acción.

**Sifaka y Civet tienen `attackAnimationState`** (Squirrel no). Ahí sí entra la parte de combate de la
librería, y sustituye a `IAnimatedAttacker` (`isAttacking()` + `attackAnimationTimeout()` contado a mano):

```java
this.goalSelector.addGoal(2, new AnimatableMeleeAttackGoal(this, 1.2, true)
        .reach(2.0F)
        .cooldown(20)
        .onAttack((target, anim) -> anim.play(anim.getByName("attack"))));
```

`AnimatableMeleeAttackGoal` exige que la entidad implemente `Animatable` (lanza en el constructor si
no) y ya para la navegación al entrar en rango. Si además se quiere que el daño caiga en el frame de
impacto en vez de al iniciar el swing, `HitWindow.of(inicio, fin).shape(...).damage(...).applyTo(attack)`
lo hace, con debug visual vía `/deluxelib debug hitboxes`. Para critters probablemente sea excesivo:
`onFrame(n, e -> …)` basta.

**Orden de migración recomendado**: `SquirrelEntity` primero (cubre todos los casos raros), luego
`SifakaEntity` (superconjunto casi exacto menos el planeo, más ataque), y `CivetEntity` al final
(añade `dig`/`scratch`, ya sin sorpresas). Subir `sleep` y `ordered_sit` a `DiverseCritter` en el paso
de Squirrel para no repetirlas tres veces.
