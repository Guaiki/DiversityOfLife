# Despierto mientras tenga hambre (módulo 4) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un critter con hambre (`isHungry()`) no se duerme, y si le entra hambre durmiendo se despierta
por el camino de despertar forzado.

**Architecture:** Dos líneas en `SleepBehaviorGoal`: un guarda en `canUse()` y una condición más en el
despertar forzado de `canContinueToUse()`. Gametests con la hora fijada en la ventana de sueño.

**Tech Stack:** Forge 47.4.10 (MC 1.20.1), GameTest de Forge. Spec:
`docs/superpowers/specs/2026-10-07-awake-while-hungry-design.md`.

## Global Constraints

- Paquete base `com.evirapo.diversityofcritters`. Rutas de Java relativas a
  `src/main/java/com/evirapo/diversityofcritters/`.
- Umbral: `DiverseCritter.isHungry()` (hambre ≤ 50 %), sin umbral nuevo.
- El tranquilizante tiene prioridad sobre el hambre.
- Nunca `gradlew clean`. No añadir `build.gradle` ni `mods.toml` a los commits (cambios del usuario).
- Los gametests corren a la vez en el mismo mundo: limpian sus entidades con `helper.killAllEntities()`.

---

### Task 1: El hambre impide dormir y despierta

**Files:**
- Create: `gametest/AwakeWhileHungryGameTests.java`
- Modify: `common/entity/ai/SleepBehaviorGoal.java` (`canUse`, `canContinueToUse`)

**Interfaces:**
- Consumes: `DiverseCritter.isHungry()`, `getSleepState()`, `setHunger(int)`, `maxHunger()`, `SleepState`.

- [ ] **Step 1: Escribir los gametests**

```java
package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter.SleepState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Hungry critters stay awake, headless: {@code gradlew runGameTestServer}.
 *
 * <p>The civet is nocturnal and sleeps by day, so the world clock is set to noon, inside its sleep
 * window. Its hunger is set to a quarter: hungry, but far from starving to death during the test.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class AwakeWhileHungryGameTests {

    private static final String ARENA = "mantle_arena";
    private static final int NOON = 6000;
    private static final int TIMEOUT = 600;

    private AwakeWhileHungryGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    private static void makeHungry(CivetEntity civet) {
        civet.setHunger(civet.maxHunger() / 4);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void aHungryCivetDoesNotFallAsleep(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(NOON);
        CivetEntity fed = helper.spawn(DOCEntities.CIVET.get(), new Vec3(3.5, 3, 3.5));
        CivetEntity hungry = helper.spawn(DOCEntities.CIVET.get(), new Vec3(8.5, 3, 8.5));
        makeHungry(hungry);

        helper.onEachTick(() -> {
            if (hungry.getSleepState() != SleepState.AWAKE) {
                helper.fail("the hungry civet went to sleep: " + hungry.getSleepState());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fed.getSleepState() == SleepState.SLEEPING, "waiting for the fed civet to sleep");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void aSleepingCivetWakesUpWhenItGetsHungry(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(NOON);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(5.5, 3, 5.5));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.SLEEPING,
                        "waiting for the civet to sleep"))
                .thenExecute(() -> makeHungry(civet))
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.AWAKE,
                        "waiting for the hungry civet to wake up"))
                .thenExecuteFor(60, () -> helper.assertTrue(civet.getSleepState() == SleepState.AWAKE,
                        "the hungry civet fell asleep again"))
                .thenExecute(helper::killAllEntities)
                .thenSucceed();
    }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew runGameTestServer`
Expected: `ahungrycivetdoesnotfallasleep failed!` y `asleepingcivetwakesupwhenitgetshungry failed!`.

- [ ] **Step 3: Guarda en `canUse()`**

En `SleepBehaviorGoal.canUse()`, justo después del bloque `if (this.entity.consumeScheduleChanged()) { ... }`
y antes del comentario `// Una orden directa mira el periodo natural en crudo...`:

```java
        // Con hambre no se duerme, hasta que coma. Va después de guardar la orden del jugador y antes
        // de cumplirla, para que un "a dormir" pulsado con hambre se cumpla en cuanto haya comido.
        // El tranquilizante ya ha salido arriba: un sedado duerme aunque tenga hambre.
        if (this.entity.isHungry()) {
            return false;
        }
```

- [ ] **Step 4: Despertar forzado en `canContinueToUse()`**

Sustituir

```java
        // La amenaza despierta igual que el dano o el agua, y por el mismo camino: AWAKENING retiene
        // al bicho hasta que el clip termina, y solo entonces stop() suelta MOVE y CritterFleeGoal
        // (prioridad 2) se lo lleva. La huida "encadenada despues del despertar" sale del reparto de
        // prioridades; no hay que construirla.
        if (entity.hurtTime > 0 || entity.isInWater() || entity.sensor().hasThreat()) {
```
por
```java
        // La amenaza despierta igual que el dano o el agua, y por el mismo camino: AWAKENING retiene
        // al bicho hasta que el clip termina, y solo entonces stop() suelta MOVE y CritterFleeGoal
        // (prioridad 2) se lo lleva. La huida "encadenada despues del despertar" sale del reparto de
        // prioridades; no hay que construirla. El hambre despierta por el mismo camino, y canUse() le
        // impide volver a dormirse hasta que coma.
        if (entity.hurtTime > 0 || entity.isInWater() || entity.sensor().hasThreat() || entity.isHungry()) {
```

- [ ] **Step 5: Ver pasar los gametests**

Run: `./gradlew runGameTestServer`
Expected: `All 19 required tests passed :)`

- [ ] **Step 6: Build completo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/gametest/AwakeWhileHungryGameTests.java src/main/java/com/evirapo/diversityofcritters/common/entity/ai/SleepBehaviorGoal.java
git commit -m "Awake while hungry: hunger keeps critters awake until they eat"
```

---

### Task 2: Prueba en juego y guía

- [ ] **Step 1:** Entregar al usuario el checklist en juego. No se da por bueno sin su confirmación.
- [ ] **Step 2:** Con su confirmación, añadir la sección del módulo 4 a la guía del cliente
  (https://claude.ai/artifact/3U5ZEiYMzyqd1KVUHafyCK) y poner su estado en "Ready to test".
