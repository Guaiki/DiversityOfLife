# Gamerules de necesidades y clima (módulo 3) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dos gamerules, `doCritterNeeds` y `doCritterClimate`, que apagan hambre/sed y
temperatura/humedad de los critters con la sintaxis estándar `/gamerule`.

**Architecture:** `DOCGameRules` registra las dos reglas desde el constructor del mod. Cada
`DiverseCritter` copia su estado a un `byte` sincronizado (`RULES_OFF`) cada 20 ticks; las comprobaciones
`needsEnabled()` / `climateEnabled()` leen ese byte, así que servidor y cliente coinciden. Necesidades
apagadas: hambre y sed se mantienen llenas. Clima apagado: las zonas devuelven 0 y las barras de
confort se quedan a 100. Las pantallas de stats usan un helper puro `ClimateLine`.

**Tech Stack:** Forge 47.4.10 (MC 1.20.1), mappings Parchment 2023.09.03, JUnit 5, GameTest de
Forge. Spec: `docs/superpowers/specs/2026-10-07-critter-gamerules-design.md`.

## Global Constraints

- Paquete base `com.evirapo.diversityofcritters`, mod id `diversityofcritters`.
- Las rutas de Java son relativas a `src/main/java/com/evirapo/diversityofcritters/` salvo que digan
  `src/test/...` o `src/main/resources/...`.
- Nombres exactos: `doCritterNeeds`, `doCritterClimate`; categoría `GameRules.Category.MOBS`; ambos
  `true` por defecto.
- Bits de `RULES_OFF`: `1` = necesidades apagadas, `2` = clima apagado. Valor por defecto `0`.
- El estado del gamerule se copia a la entidad cada 20 ticks de servidor.
- Nunca `gradlew clean` (rompe `build/fg_cache`). Compilar con `./gradlew compileJava`.
- No reescribir `.java` con `Set-Content` de PowerShell (mete BOM).
- En `git add`, añadir solo los archivos de la tarea: `build.gradle` y `mods.toml` tienen cambios del
  usuario sin commitear.
- Los gametests corren todos a la vez en el mismo mundo: el que cambia un gamerule lo restaura al
  terminar, y limpia sus entidades con `helper.killAllEntities()`.

---

### Task 1: Los gamerules, la marca sincronizada y `doCritterNeeds`

**Files:**
- Create: `common/gamerule/DOCGameRules.java`
- Create: `gametest/CritterGameRulesGameTests.java`
- Modify: `DiversityOfCritters.java` (constructor)
- Modify: `common/entity/custom/base/DiverseCritter.java` (accessor, `defineSynchedData`, métodos nuevos, `tick`)
- Modify: `src/main/resources/assets/diversityofcritters/lang/en_us.json`

**Interfaces:**
- Produces: `DOCGameRules.CRITTER_NEEDS`, `DOCGameRules.CRITTER_CLIMATE` (`GameRules.Key<GameRules.BooleanValue>`), `DOCGameRules.register()`.
- Produces: `DiverseCritter.needsEnabled()`, `DiverseCritter.climateEnabled()` (`boolean`, públicos).

- [ ] **Step 1: Escribir el gametest de `doCritterNeeds`**

`gametest/CritterGameRulesGameTests.java`:

```java
package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.gamerule.DOCGameRules;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The critter gamerules with a real civet, headless: {@code gradlew runGameTestServer}.
 *
 * <p>Gamerules belong to the whole world and every game test runs at once, so each test touches
 * only its own rule and turns it back on before passing.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class CritterGameRulesGameTests {

    private static final String ARENA = "mantle_arena";
    private static final Vec3 SPOT = new Vec3(5.5, 3, 5.5);

    private CritterGameRulesGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    private static void setRule(GameTestHelper helper, GameRules.Key<GameRules.BooleanValue> rule, boolean value) {
        helper.getLevel().getGameRules().getRule(rule).set(value, helper.getLevel().getServer());
    }

    /**
     * With needs on, a civet at zero hunger and thirst stays there. Only the gamerule can fill both
     * bars within the test's time.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void needsOffKeepsHungerAndThirstFull(GameTestHelper helper) {
        floor(helper);
        setRule(helper, DOCGameRules.CRITTER_NEEDS, false);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        civet.setHunger(0);
        civet.setThirst(0);

        helper.succeedWhen(() -> {
            helper.assertFalse(civet.needsEnabled(), "the civet must see doCritterNeeds as off");
            helper.assertTrue(civet.getHunger() == civet.maxHunger(), "hunger must be full");
            helper.assertTrue(civet.getThirst() == civet.maxThirst(), "thirst must be full");
            helper.assertTrue(civet.isAlive(), "the civet must not starve");
            setRule(helper, DOCGameRules.CRITTER_NEEDS, true);
            helper.killAllEntities();
        });
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew compileJava`
Expected: FAIL, `package com.evirapo.diversityofcritters.common.gamerule does not exist`.

- [ ] **Step 3: Crear `DOCGameRules`**

```java
package com.evirapo.diversityofcritters.common.gamerule;

import net.minecraft.world.level.GameRules;

/**
 * The mod's gamerules. They use the standard syntax, {@code /gamerule doCritterNeeds false}, and
 * show up under Mobs in the Edit Game Rules screen.
 */
public final class DOCGameRules {

    /** Hunger and thirst. Off: both stay full, so no critter starves or dies of thirst. */
    public static final GameRules.Key<GameRules.BooleanValue> CRITTER_NEEDS =
            GameRules.register("doCritterNeeds", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));

    /**
     * Temperature and humidity. Off: every critter is comfortable anywhere, so no climate damage,
     * no hunger/thirst multipliers and no climate block on breeding.
     */
    public static final GameRules.Key<GameRules.BooleanValue> CRITTER_CLIMATE =
            GameRules.register("doCritterClimate", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));

    /**
     * Loads this class, which registers the rules. Must run from the mod constructor: a gamerule
     * registered after a world loads does not exist in that world.
     */
    public static void register() {}

    private DOCGameRules() {}
}
```

- [ ] **Step 4: Registrar desde el constructor**

En `DiversityOfCritters.java`, import:

```java
import com.evirapo.diversityofcritters.common.gamerule.DOCGameRules;
```

y justo después de `DOCConfig.register(modEventBus);`:

```java
        DOCGameRules.register();
```

- [ ] **Step 5: La marca sincronizada en `DiverseCritter`**

Imports:

```java
import com.evirapo.diversityofcritters.common.gamerule.DOCGameRules;
import net.minecraft.world.level.GameRules;
```

Tras `private static final EntityDataAccessor<Integer> HUMIDITY_COMFORT = ...`:

```java
    private static final EntityDataAccessor<Byte> RULES_OFF = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BYTE);
```

En `defineSynchedData`, tras `this.entityData.define(HUMIDITY_COMFORT, 100);`:

```java
        this.entityData.define(RULES_OFF, (byte) 0);
```

Justo antes del bloque de temperatura (antes de `private static final int TEMP_CHECK_INTERVAL = 40;`):

```java
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
```

- [ ] **Step 6: Leer los gamerules y gatear el drenaje en `tick`**

En el bloque de servidor de `tick()`, sustituir

```java
            // Aquí y no en el goal de sueño: tick() corre todos los ticks y los goals en alternos.
            this.sedation.tick();
```
por
```java
            // Once a second is plenty: a gamerule change taking up to a second to land is not noticeable.
            if (this.tickCount % 20 == 0) this.syncGameRules();

            // Aquí y no en el goal de sueño: tick() corre todos los ticks y los goals en alternos.
            this.sedation.tick();
```

Y sustituir los dos bloques de drenaje

```java
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
```
por
```java
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
```

- [ ] **Step 7: Lang**

En `en_us.json`, tras `"death.attack.diversityofcritters.humidity": ...,`:

```json
  "gamerule.doCritterNeeds": "Critter hunger and thirst",
  "gamerule.doCritterNeeds.description": "When off, Diversity of Critters animals stay fed and watered and never starve.",
  "gamerule.doCritterClimate": "Critter temperature and humidity",
  "gamerule.doCritterClimate.description": "When off, Diversity of Critters animals are comfortable in any climate and take no climate damage.",
```

- [ ] **Step 8: Compilar y correr los gametests**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew runGameTestServer`
Expected: `All 16 required tests passed :)` (15 existentes + `needsoffkeepshungerandthirstfull`).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/gamerule src/main/java/com/evirapo/diversityofcritters/gametest/CritterGameRulesGameTests.java src/main/java/com/evirapo/diversityofcritters/DiversityOfCritters.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/resources/assets/diversityofcritters/lang/en_us.json
git commit -m "Critter gamerules: doCritterNeeds keeps hunger and thirst full"
```

---

### Task 2: `doCritterClimate`

**Files:**
- Modify: `common/entity/custom/base/DiverseCritter.java` (`getTemperatureZone`, `getHumidityZone`, `tickTemperature`, `tickHumidity`)
- Modify: `gametest/CritterGameRulesGameTests.java`

**Interfaces:**
- Consumes: `DOCGameRules.CRITTER_CLIMATE`, `DiverseCritter.climateEnabled()` (Task 1).

- [ ] **Step 1: Escribir el gametest**

En `CritterGameRulesGameTests`, tras `needsOffKeepsHungerAndThirstFull`:

```java
    /**
     * The comfort bars start empty. Even in its comfort zone a bar refills at 4 points a second, 25
     * seconds from empty, so only the gamerule can fill it within the test's time.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void climateOffMakesEveryCritterComfortable(GameTestHelper helper) {
        floor(helper);
        setRule(helper, DOCGameRules.CRITTER_CLIMATE, false);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        civet.setTemperatureComfort(0);
        civet.setHumidityComfort(0);

        helper.succeedWhen(() -> {
            helper.assertFalse(civet.climateEnabled(), "the civet must see doCritterClimate as off");
            helper.assertTrue(civet.getTemperatureZone() == 0, "temperature zone must be comfort");
            helper.assertTrue(civet.getHumidityZone() == 0, "humidity zone must be comfort");
            helper.assertTrue(civet.getTemperatureComfort() == 100, "temperature bar must be full");
            helper.assertTrue(civet.getHumidityComfort() == 100, "humidity bar must be full");
            setRule(helper, DOCGameRules.CRITTER_CLIMATE, true);
            helper.killAllEntities();
        });
    }
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew runGameTestServer`
Expected: `climateoffmakeseverycrittercomfortable failed!` (las barras no llegan a 100 en 100 ticks).

- [ ] **Step 3: Zonas a 0 con el clima apagado**

En `DiverseCritter`, sustituir

```java
    /** 0 = Confort, 1 = Estrés, 2+ = Peligro */
    public int getTemperatureZone() {
        return getTemperatureTier().distanceOutside(comfortTierMin(), comfortTierMax());
    }
```
por
```java
    /** 0 = Confort, 1 = Estrés, 2+ = Peligro. Siempre confort con doCritterClimate apagado. */
    public int getTemperatureZone() {
        if (!this.climateEnabled()) return 0;
        return getTemperatureTier().distanceOutside(comfortTierMin(), comfortTierMax());
    }
```

y

```java
    /** 0 = Confort, 1 = Estrés, 2+ = Peligro */
    public int getHumidityZone() {
        return getHumidityTier().distanceOutside(comfortHumidityMin(), comfortHumidityMax());
    }
```
por
```java
    /** 0 = Confort, 1 = Estrés, 2+ = Peligro. Siempre confort con doCritterClimate apagado. */
    public int getHumidityZone() {
        if (!this.climateEnabled()) return 0;
        return getHumidityTier().distanceOutside(comfortHumidityMin(), comfortHumidityMax());
    }
```

Con la zona a 0 ya desaparecen el daño, los multiplicadores y el bloqueo de cría, porque todos
cuelgan de la zona.

- [ ] **Step 4: Barras de confort llenas**

Al principio de `tickTemperature()`:

```java
        if (!this.climateEnabled()) {
            // doCritterClimate off: the bar shows full, and the tier is not worth computing.
            setTemperatureComfort(100);
            return;
        }
```

Al principio de `tickHumidity()`:

```java
        if (!this.climateEnabled()) {
            setHumidityComfort(100);
            return;
        }
```

`SynchedEntityData.set` no marca nada si el valor no cambia, así que escribir 100 cada tick no
sincroniza nada de más.

- [ ] **Step 5: Correr los gametests**

Run: `./gradlew runGameTestServer`
Expected: `All 17 required tests passed :)`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/java/com/evirapo/diversityofcritters/gametest/CritterGameRulesGameTests.java
git commit -m "Critter gamerules: doCritterClimate makes every critter comfortable"
```

---

### Task 3: `Off` en las pantallas de stats

**Files:**
- Create: `client/screen/ClimateLine.java`
- Test: `src/test/java/com/evirapo/diversityofcritters/client/screen/ClimateLineTest.java`
- Modify: `client/screen/CivetStatScreen.java`, `client/screen/SifakaStatScreen.java`, `client/screen/SquirrelStatScreen.java`

**Interfaces:**
- Consumes: `DiverseCritter.climateEnabled()` (Task 1).
- Produces: `record ClimateLine(String text, int color)` con
  `static ClimateLine of(String label, String tierName, int zone, boolean enabled, int offColor)`.

- [ ] **Step 1: Escribir el test**

```java
package com.evirapo.diversityofcritters.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClimateLineTest {

    @Test
    void eachZoneHasItsNameAndColour() {
        assertEquals(new ClimateLine("Temp: Warm (Comfort)", 0x55FF55), ClimateLine.of("Temp", "Warm", 0, true, 0xFFFFFF));
        assertEquals(new ClimateLine("Temp: Cool (Stress)", 0xFFFF55), ClimateLine.of("Temp", "Cool", 1, true, 0xFFFFFF));
        assertEquals(new ClimateLine("Humidity: Arid (Danger)", 0xFF5555), ClimateLine.of("Humidity", "Arid", 3, true, 0xFFFFFF));
    }

    @Test
    void disabledClimateReadsOffInTheNormalTextColour() {
        assertEquals(new ClimateLine("Temp: Off", 0xFFFFFF), ClimateLine.of("Temp", "Warm", 0, false, 0xFFFFFF));
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew test --tests "com.evirapo.diversityofcritters.client.screen.ClimateLineTest"`
Expected: FAIL de compilación, `cannot find symbol: class ClimateLine`.

- [ ] **Step 3: Implementar `ClimateLine`**

```java
package com.evirapo.diversityofcritters.client.screen;

/**
 * One climate line of a stat screen: {@code "Temp: Warm (Stress)"} in its zone's colour, or
 * {@code "Temp: Off"} while doCritterClimate is off. Shared by every critter's stat screen.
 */
public record ClimateLine(String text, int color) {

    public static ClimateLine of(String label, String tierName, int zone, boolean enabled, int offColor) {
        if (!enabled) return new ClimateLine(label + ": Off", offColor);

        String zoneName = zone == 0 ? "Comfort" : (zone == 1 ? "Stress" : "Danger");
        int color = zone == 0 ? 0x55FF55 : (zone == 1 ? 0xFFFF55 : 0xFF5555);
        return new ClimateLine(label + ": " + tierName + " (" + zoneName + ")", color);
    }
}
```

- [ ] **Step 4: Ver pasar el test**

Run: `./gradlew test --tests "com.evirapo.diversityofcritters.client.screen.ClimateLineTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Usarlo en las tres pantallas**

En **cada una** de `CivetStatScreen.java`, `SifakaStatScreen.java` y `SquirrelStatScreen.java`,
sustituir el bloque

```java
        int tempZone = entity.getTemperatureZone();
        String tempZoneName = tempZone == 0 ? "Comfort" : (tempZone == 1 ? "Stress" : "Danger");
        int tempColor = tempZone == 0 ? 0x55FF55 : (tempZone == 1 ? 0xFFFF55 : 0xFF5555);
        String temp = "Temp: " + entity.getTemperatureTier().getDisplayName() + " (" + tempZoneName + ")";

        int humidityZone = entity.getHumidityZone();
        String humidityZoneName = humidityZone == 0 ? "Comfort" : (humidityZone == 1 ? "Stress" : "Danger");
        int humidityColor = humidityZone == 0 ? 0x55FF55 : (humidityZone == 1 ? 0xFFFF55 : 0xFF5555);
        String humidity = "Humidity: " + entity.getHumidityTier().getDisplayName() + " (" + humidityZoneName + ")";
```
por
```java
        ClimateLine tempLine = ClimateLine.of("Temp", entity.getTemperatureTier().getDisplayName(),
                entity.getTemperatureZone(), entity.climateEnabled(), INFO_TEXT_COLOR);
        ClimateLine humidityLine = ClimateLine.of("Humidity", entity.getHumidityTier().getDisplayName(),
                entity.getHumidityZone(), entity.climateEnabled(), INFO_TEXT_COLOR);
```

y en el mismo método, dentro de `String[] info = {...}`,

```java
                temp,
                humidity,
```
por
```java
                tempLine.text(),
                humidityLine.text(),
```

y en `int[] infoColors = {...}`

```java
                INFO_TEXT_COLOR, INFO_TEXT_COLOR, tempColor, humidityColor,
```
por
```java
                INFO_TEXT_COLOR, INFO_TEXT_COLOR, tempLine.color(), humidityLine.color(),
```

- [ ] **Step 6: Compilar y correr todo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (incluye todos los JUnit).

Run: `grep -n "tempZoneName\|humidityZoneName" src/main/java/com/evirapo/diversityofcritters/client/screen/*.java`
Expected: sin resultados.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/client/screen src/test/java/com/evirapo/diversityofcritters/client/screen/ClimateLineTest.java
git commit -m "Critter gamerules: stat screens show Off when the climate is disabled"
```

---

### Task 4: Verificación final

- [ ] **Step 1: Build y gametests**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew runGameTestServer`
Expected: `All 17 required tests passed :)`

- [ ] **Step 2: Entregar al usuario el checklist de prueba en juego**

Los pasos los prueba el usuario; no se marcan como hechos sin su confirmación. Tras confirmarlo, se
añade la sección del módulo 3 a la guía del cliente
(https://claude.ai/artifact/3U5ZEiYMzyqd1KVUHafyCK).
