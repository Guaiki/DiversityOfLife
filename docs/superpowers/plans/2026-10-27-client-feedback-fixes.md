# Fixes del feedback del cliente (27-oct) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que los critters ignoren bowls, digboxes y troncos a los que no pueden llegar andando, que
los recién nacidos domesticados no sigan al dueño, que los juveniles vayan solos al bowl aunque
estén siguiendo a la madre, y que la digbox acepte cualquier item.

**Architecture:** Un helper nuevo, `Reachability`, elige el objetivo más cercano *al que hay camino
completo* (`Path.canReach()`, no `!= null`), probando como mucho 3 candidatos. Los cuatro goals que
buscan bloques lo usan al elegir y, al recalcular la ruta a mitad de camino, abandonan con
enfriamiento si el objetivo deja de ser alcanzable. Los otros tres arreglos son cambios de una
línea: `isFollowing()` excluye a los recién nacidos, `FollowParentGoal` pasa a reclamar `MOVE` (hoy
no declara flags, así que corre a la vez que el goal del bowl y le pisa la navegación cada 10 ticks)
y la digbox pierde su filtro de palos.

**Tech Stack:** Forge 47 (MC 1.20.1), GameTest de Forge (`./gradlew runGameTestServer`).

**Spec:** no hay documento aparte. Los requisitos salen del mensaje del cliente del 27-oct y del
diagnóstico de esa sesión:
1. Las crías recién nacidas domesticadas no deben seguir al dueño.
2. Los animales no deben ir hacia bloques (bowl incluido) que tienen una pared delante.
3. La digbox debe aceptar todos los items.
4. Las crías **juveniles** (solo juveniles, lo confirmó el cliente) deben ir solas al bowl en vez de
   morirse de hambre siguiendo a la madre. Los recién nacidos siguen sin usar bowls.

## Global Constraints

- Paquete base `com.evirapo.diversityofcritters`. Rutas de Java relativas a
  `src/main/java/com/evirapo/diversityofcritters/`.
- Rama nueva `fix/client-feedback` creada desde `feature/sifaka-deluxelib`, que es la build que tiene
  el cliente.
- Nunca `gradlew clean` (rompe el `fg_cache` de ForgeGradle).
- "Alcanzable" quiere decir `path != null && path.canReach()`. `createPath` devuelve un camino parcial
  cuando no llega, así que `!= null` no basta (ver el comentario de `CritterDrinkChainGoal`).
- Radio de ruta: `searchRadius * 2.0F`. Máximo de candidatos con pathfinding por búsqueda: 3.
- Las hojas y plantas (`SquirrelEatLeavesChainGoal`, `SifakaEatLeavesChainGoal`) quedan **fuera**: se
  alcanzan trepando y el pathfinder de suelo no lo modela, así que `canReach()` las descartaría todas.
- Los gametests corren a la vez en el mismo mundo: cada uno acaba con `helper.killAllEntities()`. La
  civeta es nocturna, así que sus tests fijan `MIDNIGHT = 18000` y van en batch propio.
- Comentarios en el idioma y la densidad del archivo que se toca. Commits en inglés, frase descriptiva
  como los del historial, con el trailer `Co-Authored-By` de la sesión.
- Verificación: el implementador corre gametests y `./gradlew build`; el usuario prueba en juego.

## Review Focus

1. **Bowl alcanzable solo dando un rodeo** (pared con hueco): debe seguir contando como alcanzable. Si
   el radio de ruta o el presupuesto de nodos se quedan cortos, el critter dejaría de comer de bowls
   legítimos. → test `reachabilityFollowsADetour` (Task 1).
2. **Pared puesta a mitad de camino**: el critter abandona ese bowl y usa otro en vez de quedarse
   empujando la pared. → test `aCivetSwitchesBowlsWhenItsBowlGetsWalledOff` (Task 2).
3. **El recién nacido sigue a su madre** ahora que `FollowParentGoal` reclama `MOVE` y puede perder
   contra goals de prioridad 17 o menos. → test `aNewbornStillFollowsItsMother` (Task 7).
4. **Juvenil domesticado en modo seguir**: tiene que seguir obedeciendo al dueño; el cambio de
   `isFollowing()` solo afecta a recién nacidos. → aserción del juvenil en
   `aTameNewbornIsNotFollowingButATameJuvenileIs` (Task 6).
5. **Bowl en el límite del radio de búsqueda** (unos 14 bloques en campo abierto): no cabe en la arena
   de los gametests. Va a la lista de pruebas en juego (Task 9).

---

### Task 1: Helper `Reachability`

**Files:**
- Create: `common/entity/util/Reachability.java`
- Modify: `common/entity/ai/CritterNavigation.java` (método público nuevo)
- Create: `gametest/ReachabilityGameTests.java`

**Interfaces:**
- Produces:
  - `CritterNavigation.createPathWithin(BlockPos target, int accuracy, float range): @Nullable Path`,
    que delega en el `createPath(Set<BlockPos>, int, boolean, int, float)` protegido con
    `ImmutableSet.of(target)`, `regionOffset = 8` y `offsetUpward = false`.
  - `Reachability.MAX_PATH_CHECKS = 3`
  - `Reachability.canReach(Mob mob, BlockPos target, int accuracy, float range): boolean`. Usa
    `createPathWithin` si la navegación es `CritterNavigation` y, si no, `createPath(target, accuracy)`.
    Devuelve `path != null && path.canReach()`.
  - `Reachability.nearestReachable(Mob mob, Collection<BlockPos> candidates, Function<BlockPos, @Nullable BlockPos> pathTarget, int accuracy, float range): @Nullable BlockPos`.
    Ordena los candidatos por `distSqr` a `mob.blockPosition()` y prueba como mucho los
    `MAX_PATH_CHECKS` primeros cuyo `pathTarget` no sea null. Devuelve el **candidato** (no el
    `pathTarget`) del primero alcanzable, o null si no hay ninguno.
  - En el gametest, `sealIn(GameTestHelper helper, BlockPos center, int radius, int height)`: pone
    cristal en todas las celdas de la caja `[center.x−radius−1 .. center.x+radius+1] × [3 .. 3+height] × [center.z−radius−1 .. center.z+radius+1]`
    que quedan fuera del interior `[center.x±radius] × [3 .. 3+height−1] × [center.z±radius]`. Es una
    jaula sellada con techo y el interior queda intacto. Va con `walledFloor` (copiado de
    `DrinkingGameTests`) y las constantes `ARENA = "mantle_arena"`, `MIDNIGHT`,
    `BATCH = "reachability_civet"`.

- [ ] **Step 1: Escribir los gametests**

En `ReachabilityGameTests` (`@GameTestHolder`, `@PrefixGameTestTemplate(false)`, javadoc de clase como
en `DrinkingGameTests`):
  - `reachabilityRejectsASealedBlock`: `walledFloor`; `sealIn(helper, (4,3,4), 0, 1)` y un
    `DOCBlocks.BOWL` en `(4,3,4)`; civeta en `(2.5, 3, 2.5)`. En `succeedWhen`: primero
    `civet.onGround()` (sin suelo, `canUpdatePath()` hace que `createPath` devuelva null), después
    `assertFalse(Reachability.canReach(civet, helper.absolutePos((4,3,4)), 1, 32F))`.
  - `reachabilityFollowsADetour`: `walledFloor`; muro de piedra en `z = 6`, `x = 2..8`, `y = 3..4`
    (el hueco queda en `x = 9`); bowl en `(5,3,8)`; civeta en `(2.5, 3, 2.5)`. Primero `onGround()` y
    después `assertTrue(Reachability.canReach(civet, helper.absolutePos((5,3,8)), 1, 32F))`.

- [ ] **Step 2: Comprobar que no compila**

Run: `./gradlew compileJava`
Expected: FAIL, `cannot find symbol: Reachability`.

- [ ] **Step 3: Implementar `createPathWithin` y `Reachability`**

Javadoc del helper: por qué `canReach()` y no `!= null`, y por qué se limita a 3 pruebas (coste del
pathfinding por búsqueda).

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew runGameTestServer`
Expected: los dos tests en `passed`; el resto de la suite sigue en verde.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/util/Reachability.java src/main/java/com/evirapo/diversityofcritters/common/entity/ai/CritterNavigation.java src/main/java/com/evirapo/diversityofcritters/gametest/ReachabilityGameTests.java
git commit -m "Reachability helper: nearest target with a full path, not a partial one"
```

---

### Task 2: El bowl de comida ignora los que no se alcanzan

**Files:**
- Modify: `common/entity/ai/FindFoodBowlGoal.java` (`canUse`, `navigateToBowl`, `tick`, `findNearestFoodBowl`)
- Modify: `gametest/ReachabilityGameTests.java`

**Interfaces:**
- Consumes: `Reachability.nearestReachable`, `Reachability.canReach` y `sealIn` (Task 1).

- [ ] **Step 1: Escribir los gametests** (batch `reachability_civet`, `MIDNIGHT`)
  - `aCivetEatsFromTheReachableBowlNotTheSealedOne`: bowl sellado en `(4,3,4)` (`sealIn` r=0, h=1) y
    bowl abierto en `(8,3,8)`, los dos con `new ItemStack(Items.BEEF, 8)` en el slot 0 de
    `BowlBlockEntity.getInventory()` (`setItem` dispara el cambio de `CONTENT`). Civeta en
    `(2.5,3,2.5)` con `setHunger(maxHunger() / 4)`. Éxito: `getHunger() == maxHunger()`.
  - `aCivetSwitchesBowlsWhenItsBowlGetsWalledOff`: un único bowl con carne en `(8,3,8)` y la civeta
    hambrienta en `(2.5,3,2.5)`. Con `startSequence`: `thenIdle(20)` (ya va de camino, porque el goal
    barre cada 20 ticks), sellar el bowl con `sealIn` r=0, h=1, poner un segundo bowl con carne en
    `(2,3,9)` y esperar a `getHunger() == maxHunger()`.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew runGameTestServer`
Expected: los dos fallan por timeout. La civeta se queda pegada a la jaula porque
`canContinueToUse()` sigue en true mientras el bowl tenga comida.

- [ ] **Step 3: Implementar**
  - `findNearestFoodBowl()` pasa a `findFoodBowls(): List<BlockPos>` con el mismo barrido y el mismo
    filtro `hasFoodFor`, y `canUse()` elige con
    `Reachability.nearestReachable(critter, bowls, Function.identity(), 1, searchRadius * 2.0F)`.
  - `navigateToBowl()` devuelve `boolean`: crea la ruta con el mismo objetivo y la misma precisión
    que usa `canReach`, y devuelve false si es null o `!canReach()`. Actualizar el comentario que dice
    que el pathfinder devuelve null.
  - En `tick()`, si el reintento de navegación devuelve false: `cooldown = 100; stop(); return;`
    (el mismo patrón que ya se usa cuando el bowl se vacía).

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew runGameTestServer` — Expected: los dos en `passed`.

- [ ] **Step 5: Commit** — `"Critters skip food bowls they cannot walk to"`

---

### Task 3: El bowl de agua ignora los que no se alcanzan

**Files:**
- Modify: `common/entity/ai/FindWaterBowlGoal.java`
- Modify: `gametest/ReachabilityGameTests.java`

**Interfaces:**
- Consumes: lo mismo que la Task 2.

- [ ] **Step 1: Escribir el gametest** `aCivetDrinksFromTheReachableBowlNotTheSealedOne`: mismo
  montaje que en la Task 2, pero con `PotionUtils.setPotion(new ItemStack(Items.POTION, 4), Potions.WATER)`
  y `setThirst(maxThirst() / 4)`. Éxito: `getThirst() == maxThirst()`.
- [ ] **Step 2: Comprobar que falla** (timeout: hoy `canContinueToUse()` devuelve true sin condición).
- [ ] **Step 3: Implementar**
  - `findNearestWaterBowl()` pasa a `findWaterBowls(): List<BlockPos>` y la elección se hace con
    `nearestReachable` (precisión 1).
  - Añadir `lastScanTime` y `SCAN_INTERVAL_TICKS = 20` como en `FindFoodBowlGoal`. Hoy barre en cada
    `canUse()` y ahora cada barrido cuesta hasta 3 pathfindings.
  - El reintento de `moveTo` en `tick()` pasa por una ruta comprobada. Si no se alcanza:
    `cooldown = 100; stop(); return;`.
- [ ] **Step 4: Comprobar que pasa.**
- [ ] **Step 5: Commit** — `"Critters skip water bowls they cannot walk to"`

---

### Task 4: La digbox ignora las que no se alcanzan

**Files:**
- Modify: `common/entity/ai/FindDigBoxGoal.java`
- Modify: `gametest/ReachabilityGameTests.java`

**Interfaces:**
- Consumes: lo mismo que la Task 2. La reserva (`setDigTarget`) se hace sobre la caja elegida, como
  hasta ahora.

- [ ] **Step 1: Escribir el gametest** `aCivetDigsInTheReachableBoxNotTheSealedOne`: digbox
  (`DOCBlocks.DIG_BOX`) sellada en `(4,3,4)` con 1 palo y digbox abierta en `(8,3,8)` con 2 palos (se
  meten con `ITEM_HANDLER.insertItem`). Civeta en `(2.5,3,2.5)` con `setEnrichment(0)`. Éxito: a la
  caja abierta le queda 1 palo.
- [ ] **Step 2: Comprobar que falla** (timeout).
- [ ] **Step 3: Implementar**
  - `findNearestValidDigBox(claimed)` pasa a `findValidDigBoxes(claimed): List<BlockPos>` y la
    elección se hace con `nearestReachable` (precisión 1: vale estar encima o al lado).
  - Añadir un intervalo de barrido de 20 ticks en `canUse()`.
  - El `moveTo` de `start()` y el reintento de `tick()` pasan por una ruta comprobada. Si no se
    alcanza, `stop()`, que ya libera la reserva.
- [ ] **Step 4: Comprobar que pasa.**
- [ ] **Step 5: Commit** — `"Critters skip dig boxes they cannot walk to"`

---

### Task 5: El tronco para arañar ignora los que no se alcanzan

**Files:**
- Modify: `common/entity/ai/CritterScratchLogChainGoal.java` (`shouldStart`, `findNearestLog`)
- Modify: `gametest/ReachabilityGameTests.java`

**Interfaces:**
- Consumes: `Reachability.nearestReachable` con `pathTarget = this::standNextTo` y precisión 0 (el
  mismo criterio que usa `CritterDrinkChainGoal` para su `standPos`).

- [ ] **Step 1: Escribir el gametest** `aCivetScratchesTheReachableLogNotTheSealedOne`
  (`timeoutTicks = 1200`, porque el goal tiene `chance`/enfriamiento): `Blocks.OAK_LOG` sellado en
  `(5,3,5)` con `sealIn` r=1, h=2 (deja un anillo de aire para poder pararse) y `OAK_LOG` abierto en
  `(9,3,2)`. Civeta en `(8.5,3,8.5)` con `setEnrichment(0)`. Éxito: `getEnrichment() == maxEnrichment()`.
- [ ] **Step 2: Comprobar que falla.** Hoy `createPath(standPos, 0) != null` acepta el camino parcial
  hasta la jaula.
- [ ] **Step 3: Implementar.** `findNearestLog(claimed)` pasa a devolver los candidatos válidos (con
  `standNextTo != null` y sin reserva) y `shouldStart()` elige con `nearestReachable`. Con eso sobra
  la comprobación `createPath(...) == null`: se quita y su comentario pasa a explicar el nuevo gate.
- [ ] **Step 4: Comprobar que pasa.**
- [ ] **Step 5: Commit** — `"Civets skip scratching logs they cannot walk to"`

---

### Task 6: Los recién nacidos domesticados no siguen al dueño

**Files:**
- Modify: `common/entity/custom/base/DiverseCritter.java:710` (`isFollowing`)
- Create: `gametest/BabyCareGameTests.java`

**Interfaces:**
- Produces: `isFollowing()` = `!isNewborn() && !isOrderedToSit() && !isWandering()`. Solo la usan los
  `FollowOwnerGoal` de las tres especies, y eso no cambia.

- [ ] **Step 1: Escribir el gametest** `aTameNewbornIsNotFollowingButATameJuvenileIs`: dos civetas,
  las dos con `setTame(true)`. Una con `setBaby(true)` y la otra con
  `setAge(DiverseCritter.JUVENILE_AGE_THRESHOLD / 2)`. Comprobar
  `newborn.isNewborn() && !newborn.isFollowing()` y `juvenile.isJuvenile() && juvenile.isFollowing()`,
  y después `killAllEntities()` y `succeed()`.
- [ ] **Step 2: Comprobar que falla** (`isFollowing()` devuelve true para el recién nacido).
- [ ] **Step 3: Implementar** con una línea de comentario: un recién nacido no puede recibir órdenes
  (lo rechaza el palo de entrenamiento), así que tampoco puede estar en modo seguir.
- [ ] **Step 4: Comprobar que pasa.**
- [ ] **Step 5: Commit** — `"Tame newborns follow their mother, not the owner"`

---

### Task 7: Los juveniles van solos al bowl

**Files:**
- Create: `common/entity/ai/CritterFollowParentGoal.java`
- Modify: `common/entity/custom/CivetEntity.java:196`, `SquirrelEntity.java:199`, `SifakaEntity.java:170`
- Modify: `gametest/BabyCareGameTests.java`

**Interfaces:**
- Produces: `CritterFollowParentGoal extends FollowParentGoal`, con el constructor
  `(Animal animal, double speed)` y `setFlags(EnumSet.of(Flag.MOVE))`. El javadoc explica que el de
  vanilla no declara flags, así que el selector lo ejecuta a la vez que cualquier otro goal y su
  `moveTo(parent)` cada 10 ticks le pisa la ruta al goal del bowl. Las tres especies lo registran en
  el lugar del `FollowParentGoal`, con la misma prioridad (18) y la misma velocidad.

- [ ] **Step 1: Escribir los gametests** (`MIDNIGHT`, batch `baby_care_civet`)
  - `aHungryJuvenileWalksAwayFromItsMotherToEat`: civeta adulta sellada en `(4,3,4)` (`sealIn` r=1,
    h=2, con la adulta en `(4.5,3,4.5)`); juvenil (`setAge(JUVENILE_AGE_THRESHOLD / 2)`) en
    `(7.5,3,4.5)` con `setHunger(maxHunger() / 4)`; bowl con carne en `(8,3,9)`. Éxito: el juvenil
    tiene `getHunger() == maxHunger()`.
  - `aNewbornStillFollowsItsMother` (Review Focus 3): la misma adulta sellada y un recién nacido
    (`setBaby(true)`, hambre llena) en `(9.5,3,9.5)`. Éxito: `newborn.distanceTo(adult) <= 4.0`.
- [ ] **Step 2: Comprobar que el primero falla** (timeout: el juvenil oscila a unos 3 bloques de la
  madre). El segundo debería pasar ya: es de regresión. Si el primero pasa antes del arreglo, parar y
  revisar el montaje antes de seguir.
- [ ] **Step 3: Implementar** `CritterFollowParentGoal` y sustituirlo en las tres especies.
- [ ] **Step 4: Comprobar que pasan los dos.**
- [ ] **Step 5: Commit** — `"Juveniles leave their mother to eat: FollowParentGoal now claims MOVE"`

---

### Task 8: La digbox acepta cualquier item

**Files:**
- Modify: `common/block/entity/DigBoxBlockEntity.java:36` (quitar el override de `isItemValid`)
- Modify: `common/block/DigBoxMenu.java:46-49` (quitar el override de `mayPlace`, porque
  `SlotItemHandler` ya delega en `isItemValid`)
- Create: `gametest/DigBoxGameTests.java`

- [ ] **Step 1: Escribir el gametest** `aDigBoxAcceptsAnyItem`: colocar `DIG_BOX` y, sobre su
  `ITEM_HANDLER`, comprobar que `insertItem(0, new ItemStack(Items.DIRT), false)` y
  `insertItem(1, new ItemStack(Items.DIAMOND_SWORD), false)` devuelven `ItemStack.EMPTY`.
- [ ] **Step 2: Comprobar que falla** (devuelve el stack).
- [ ] **Step 3: Implementar.** `quickMoveStack` sigue llamando a `isItemValid(0, …)` y con el default
  acepta todo, así que no hay que tocarlo.
- [ ] **Step 4: Comprobar que pasa.**
- [ ] **Step 5: Commit** — `"Dig box holds any item, not just sticks"`

---

### Task 9: Build y entrega

- [ ] **Step 1:** `./gradlew build`. Expected: `BUILD SUCCESSFUL`. Informar de los avisos que ya
  existían para que no parezcan nuevos.
- [ ] **Step 2:** Pasarle al usuario la lista de pruebas en juego:
  1. Domesticar a un recién nacido con el biberón y alejarse: se queda con la madre, no te sigue.
  2. Bowl con comida detrás de una pared de 2 bloques y otro accesible más lejos: el animal va al
     accesible. Si solo está el de detrás de la pared, no se queda empujándola.
  3. Lo mismo con un bowl de agua y con una digbox.
  4. Bowl en campo abierto a unos 14 bloques (Review Focus 5): lo encuentra y come.
  5. Juvenil con hambre y la madre lejos del bowl: va a comer y luego vuelve con ella.
  6. Digbox: meter tierra, una espada y comida; las acepta, y al excavar la civeta las va sacando.
- [ ] **Step 3:** Con el visto bueno del usuario, añadir una sección "Fixes from client feedback" a la
  guía de pruebas del cliente (artifact `https://claude.ai/artifact/3U5ZEiYMzyqd1KVUHafyCK`; leerla
  antes con `read`), con esos pasos en inglés y en formato `.step` + "Expected".
