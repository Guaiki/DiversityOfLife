# Modelo de población salvaje (módulo 2b) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El contador de liberaciones del módulo 2 pasa a ser la población salvaje de cada especie:
domesticar, matar y las muertes por hambre/temperatura/humedad la bajan; soltar y los nacimientos
salvajes la suben; el chance de spawn es proporcional a ella.

**Architecture:** La aritmética vive en `PopulationMath`, pura y testeada con JUnit. `WildPopulation`
(sustituye a `ReleaseRegistry`, mismo archivo de guardado, esquema v2) persiste un `int` por especie
y delega. `DiverseCritter` lleva un booleano `inWild` por animal y los ganchos (`tame`, `die`,
`giveBirth`) llaman a `WildPopulation.record(entity, event)`. Las causas de muerte se distinguen con
damage types propios en JSON.

**Tech Stack:** Forge 47.4.10 (MC 1.20.1), mappings Parchment 2023.09.03, JUnit 5, GameTest de
Forge. Spec: `docs/superpowers/specs/2026-10-07-wild-population-design.md`.

## Global Constraints

- Paquete base `com.evirapo.diversityofcritters`, mod id `diversityofcritters`.
- Las rutas de Java son relativas a `src/main/java/com/evirapo/diversityofcritters/` salvo que digan
  `src/test/...` o `src/main/resources/...`.
- Valores por defecto: `basePopulation = 20`, `populationFloor = 5`, `populationCap = 60`,
  `endangeredThreshold = 10`, `wildBreedingRate = 1.0`, todos los pesos `= 1`.
- Fórmula: `multiplicador = clamp(poblacion / basePopulation, 0, 1 + maxBonus)`,
  `chanceFinal = clamp(chanceBase * multiplicador, 0, 1)`.
- Guardado: mismo `DATA_NAME = "doc_release_registry"`, `SCHEMA_VERSION = 2`; migración
  `poblacion = basePopulation + Count`.
- Nunca `gradlew clean` (rompe `build/fg_cache`). Compilar con `./gradlew compileJava`.
- No reescribir `.java` con `Set-Content` de PowerShell (mete BOM).
- Comentarios en el estilo del proyecto: en inglés en las clases de spawn/config, explican el porqué.
- En `git add`, añadir solo los archivos de la tarea: `build.gradle` y `mods.toml` tienen cambios
  del usuario sin commitear que no son de este trabajo.

---

### Task 1: `PopulationEvent` y `PopulationMath`

**Files:**
- Create: `common/spawn/PopulationEvent.java`
- Create: `common/spawn/PopulationMath.java`
- Test: `src/test/java/com/evirapo/diversityofcritters/common/spawn/PopulationMathTest.java`

**Interfaces:**
- Produces: `enum PopulationEvent { TAME, RELEASE, WILD_BIRTH, STARVATION, PLAYER_KILL, TEMPERATURE_DEATH, HUMIDITY_DEATH }`
  con `String configKey()`, `int sign()`, `int delta(int weight)`.
- Produces: `PopulationMath` con `HISTORY_DAYS = 7`, `enum Crossing { NONE, BECAME_ENDANGERED, RECOVERED }`,
  `int clamp(int population, int floor, int cap)`,
  `double multiplier(int population, int basePopulation, double maxBonus)`,
  `double finalChance(double chanceBase, double multiplier)`,
  `int migrateV1(int releasedCount, int basePopulation, int floor, int cap)`,
  `int[] pushSample(int[] history, int value)`, `int trend(int current, int[] history)`,
  `Crossing crossing(int before, int after, int threshold)`,
  `int mateRollBound(int baseBound, double rate)`, `int scaledCooldown(int baseTicks, double rate)`.

- [ ] **Step 1: Escribir el test**

```java
package com.evirapo.diversityofcritters.common.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopulationMathTest {

    @Test
    void clampKeepsTheValueBetweenFloorAndCap() {
        assertEquals(5, PopulationMath.clamp(-3, 5, 60));
        assertEquals(60, PopulationMath.clamp(99, 5, 60));
        assertEquals(23, PopulationMath.clamp(23, 5, 60));
    }

    @Test
    void capWinsWhenTheFloorIsAboveIt() {
        assertEquals(10, PopulationMath.clamp(0, 30, 10));
        assertEquals(10, PopulationMath.clamp(50, 30, 10));
    }

    @Test
    void multiplierIsPopulationOverBase() {
        assertEquals(0.25D, PopulationMath.multiplier(5, 20, 2.0D), 1.0e-9);
        assertEquals(1.0D, PopulationMath.multiplier(20, 20, 2.0D), 1.0e-9);
        assertEquals(2.0D, PopulationMath.multiplier(40, 20, 2.0D), 1.0e-9);
    }

    @Test
    void multiplierIsCappedByMaxBonusAndNeverNegative() {
        assertEquals(3.0D, PopulationMath.multiplier(100, 20, 2.0D), 1.0e-9);
        assertEquals(0.0D, PopulationMath.multiplier(0, 20, 2.0D), 1.0e-9);
        assertEquals(0.0D, PopulationMath.multiplier(-4, 20, 2.0D), 1.0e-9);
    }

    @Test
    void finalChanceIsClampedToAProbability() {
        assertEquals(0.1D, PopulationMath.finalChance(0.4D, 0.25D), 1.0e-9);
        assertEquals(1.0D, PopulationMath.finalChance(0.65D, 3.0D), 1.0e-9);
        assertEquals(0.0D, PopulationMath.finalChance(0.65D, 0.0D), 1.0e-9);
    }

    @Test
    void version1CountersBecomeBasePlusReleased() {
        assertEquals(32, PopulationMath.migrateV1(12, 20, 5, 60));
        assertEquals(20, PopulationMath.migrateV1(0, 20, 5, 60));
        assertEquals(60, PopulationMath.migrateV1(50, 20, 5, 60));
    }

    @Test
    void historyKeepsTheLastSevenSamplesOldestFirst() {
        int[] history = new int[0];
        for (int day = 1; day <= 9; day++) {
            history = PopulationMath.pushSample(history, day);
        }
        assertArrayEquals(new int[] {3, 4, 5, 6, 7, 8, 9}, history);
    }

    @Test
    void trendComparesAgainstTheOldestSample() {
        assertEquals(0, PopulationMath.trend(20, new int[0]));
        assertEquals(3, PopulationMath.trend(23, new int[] {20, 21}));
        assertEquals(-6, PopulationMath.trend(14, new int[] {20, 19, 18, 17, 16, 15, 14}));
    }

    @Test
    void crossingOnlyFiresWhenTheThresholdIsCrossed() {
        assertEquals(PopulationMath.Crossing.BECAME_ENDANGERED, PopulationMath.crossing(10, 9, 10));
        assertEquals(PopulationMath.Crossing.RECOVERED, PopulationMath.crossing(9, 10, 10));
        assertEquals(PopulationMath.Crossing.NONE, PopulationMath.crossing(9, 8, 10));
        assertEquals(PopulationMath.Crossing.NONE, PopulationMath.crossing(20, 19, 10));
    }

    @Test
    void mateRollScalesWithTheRateAndZeroMeansNever() {
        assertEquals(400, PopulationMath.mateRollBound(400, 1.0D));
        assertEquals(200, PopulationMath.mateRollBound(400, 2.0D));
        assertEquals(800, PopulationMath.mateRollBound(400, 0.5D));
        assertEquals(0, PopulationMath.mateRollBound(400, 0.0D));
    }

    @Test
    void cooldownShrinksWithAFasterRate() {
        assertEquals(96000, PopulationMath.scaledCooldown(96000, 1.0D));
        assertEquals(48000, PopulationMath.scaledCooldown(96000, 2.0D));
        assertEquals(96000, PopulationMath.scaledCooldown(96000, 0.0D));
    }

    @Test
    void eventsCarryTheirDirection() {
        assertEquals(-1, PopulationEvent.TAME.delta(1));
        assertEquals(3, PopulationEvent.RELEASE.delta(3));
        assertEquals(0, PopulationEvent.PLAYER_KILL.delta(0));
    }
}
```

- [ ] **Step 2: Ejecutar el test y verlo fallar**

Run: `./gradlew test --tests "com.evirapo.diversityofcritters.common.spawn.PopulationMathTest"`
Expected: FAIL de compilación, `cannot find symbol: class PopulationMath`.

- [ ] **Step 3: Implementar `PopulationEvent`**

```java
package com.evirapo.diversityofcritters.common.spawn;

/**
 * Everything that moves a species' wild population, and which way it moves it.
 *
 * How far it moves it is up to the config ({@code [spawn.population.weights]}). The direction is
 * not configurable: a release that subtracted would no longer be a release.
 */
public enum PopulationEvent {

    TAME("tame", -1),
    RELEASE("release", +1),
    WILD_BIRTH("wildBirth", +1),
    STARVATION("starvation", -1),
    PLAYER_KILL("playerKill", -1),
    TEMPERATURE_DEATH("temperatureDeath", -1),
    HUMIDITY_DEATH("humidityDeath", -1);

    private final String configKey;
    private final int sign;

    PopulationEvent(String configKey, int sign) {
        this.configKey = configKey;
        this.sign = sign;
    }

    /** Key of this event's weight in the TOML and in {@code /doc_spawn set weights}. */
    public String configKey() {
        return this.configKey;
    }

    public int sign() {
        return this.sign;
    }

    /** The change this event makes to the population with the given weight. */
    public int delta(int weight) {
        return this.sign * weight;
    }
}
```

- [ ] **Step 4: Implementar `PopulationMath`**

```java
package com.evirapo.diversityofcritters.common.spawn;

/**
 * All the arithmetic of the wild population model, with no Minecraft types so it can be unit
 * tested. {@link WildPopulation} persists, this decides.
 */
public final class PopulationMath {

    /** Days of history kept for the trend shown by {@code /doc_spawn}. */
    public static final int HISTORY_DAYS = 7;

    public enum Crossing { NONE, BECAME_ENDANGERED, RECOVERED }

    /**
     * Keeps a population between the floor and the cap. A floor above the cap is a broken config;
     * the cap wins, so the population can never exceed what the config says is the maximum.
     */
    public static int clamp(int population, int floor, int cap) {
        int effectiveFloor = Math.min(floor, cap);
        return Math.max(effectiveFloor, Math.min(cap, population));
    }

    /** {@code population / basePopulation}, between 0 and {@code 1 + maxBonus}. */
    public static double multiplier(int population, int basePopulation, double maxBonus) {
        if (basePopulation <= 0) return 1.0D;
        double raw = (double) population / basePopulation;
        return Math.max(0.0D, Math.min(1.0D + maxBonus, raw));
    }

    public static double finalChance(double chanceBase, double multiplier) {
        return Math.max(0.0D, Math.min(1.0D, chanceBase * multiplier));
    }

    /**
     * Version 1 stored how many animals had been released, on top of an implicit baseline. The
     * baseline is now explicit, so it gets added.
     */
    public static int migrateV1(int releasedCount, int basePopulation, int floor, int cap) {
        return clamp(basePopulation + Math.max(0, releasedCount), floor, cap);
    }

    /** Appends a daily sample, keeping the last {@link #HISTORY_DAYS}, oldest first. */
    public static int[] pushSample(int[] history, int value) {
        int keep = Math.min(history.length, HISTORY_DAYS - 1);
        int[] next = new int[keep + 1];
        System.arraycopy(history, history.length - keep, next, 0, keep);
        next[keep] = value;
        return next;
    }

    /** Change against the oldest sample kept: a week ago once there is a week of history. */
    public static int trend(int current, int[] history) {
        if (history.length == 0) return 0;
        return current - history[0];
    }

    /** "Endangered" means strictly below the threshold. */
    public static Crossing crossing(int before, int after, int threshold) {
        boolean wasEndangered = before < threshold;
        boolean isEndangered = after < threshold;
        if (!wasEndangered && isEndangered) return Crossing.BECAME_ENDANGERED;
        if (wasEndangered && !isEndangered) return Crossing.RECOVERED;
        return Crossing.NONE;
    }

    /**
     * Bound of the 1-in-N roll a wild animal makes to look for a mate. A faster rate means a
     * smaller N. Returns 0 for "never".
     */
    public static int mateRollBound(int baseBound, double rate) {
        if (rate <= 0.0D) return 0;
        return Math.max(1, (int) Math.round(baseBound / rate));
    }

    /** Breeding cooldown at the given rate. With rate 0 nothing breeds anyway, so it stays as is. */
    public static int scaledCooldown(int baseTicks, double rate) {
        if (rate <= 0.0D) return baseTicks;
        return Math.max(1, (int) Math.round(baseTicks / rate));
    }

    private PopulationMath() {}
}
```

- [ ] **Step 5: Ejecutar el test y verlo pasar**

Run: `./gradlew test --tests "com.evirapo.diversityofcritters.common.spawn.PopulationMathTest"`
Expected: PASS, 12 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/spawn/PopulationEvent.java src/main/java/com/evirapo/diversityofcritters/common/spawn/PopulationMath.java src/test/java/com/evirapo/diversityofcritters/common/spawn/PopulationMathTest.java
git commit -m "Wild population: population math and events"
```

---

### Task 2: Config `[spawn.population]`

Añade las claves nuevas sin quitar todavía `bonusPerRelease` ni `releaseCap` (los usa código que se
reescribe en la Task 3), para que el proyecto compile al final de esta tarea.

**Files:**
- Create: `common/config/PopulationSettings.java`
- Modify: `common/config/DOCServerConfig.java`
- Test: `src/test/java/com/evirapo/diversityofcritters/common/config/PopulationSettingsTest.java`

**Interfaces:**
- Consumes: `PopulationEvent`, `PopulationMath.clamp` (Task 1).
- Produces: `record PopulationSettings(int basePopulation, int floor, int cap, int endangeredThreshold, double wildBreedingRate, Map<PopulationEvent, Integer> weights)`
  con `int delta(PopulationEvent event)` y `int clamp(int population)`.
- Produces: en `DOCServerConfig`: `IntValue BASE_POPULATION, POPULATION_FLOOR, POPULATION_CAP, ENDANGERED_THRESHOLD`,
  `DoubleValue WILD_BREEDING_RATE`, `static IntValue weightValue(PopulationEvent)`,
  `static PopulationSettings population()`.

- [ ] **Step 1: Escribir el test**

```java
package com.evirapo.diversityofcritters.common.config;

import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopulationSettingsTest {

    private static PopulationSettings withWeight(PopulationEvent event, int weight) {
        Map<PopulationEvent, Integer> weights = new EnumMap<>(PopulationEvent.class);
        for (PopulationEvent e : PopulationEvent.values()) weights.put(e, 1);
        weights.put(event, weight);
        return new PopulationSettings(20, 5, 60, 10, 1.0D, weights);
    }

    @Test
    void deltaAppliesTheEventsWeightAndSign() {
        assertEquals(-3, withWeight(PopulationEvent.PLAYER_KILL, 3).delta(PopulationEvent.PLAYER_KILL));
        assertEquals(1, withWeight(PopulationEvent.PLAYER_KILL, 3).delta(PopulationEvent.RELEASE));
    }

    @Test
    void weightZeroMeansTheEventDoesNotCount() {
        assertEquals(0, withWeight(PopulationEvent.TAME, 0).delta(PopulationEvent.TAME));
    }

    @Test
    void clampUsesTheConfiguredFloorAndCap() {
        PopulationSettings settings = withWeight(PopulationEvent.TAME, 1);
        assertEquals(5, settings.clamp(0));
        assertEquals(60, settings.clamp(70));
    }
}
```

- [ ] **Step 2: Ejecutar el test y verlo fallar**

Run: `./gradlew test --tests "com.evirapo.diversityofcritters.common.config.PopulationSettingsTest"`
Expected: FAIL de compilación, `cannot find symbol: class PopulationSettings`.

- [ ] **Step 3: Implementar `PopulationSettings`**

```java
package com.evirapo.diversityofcritters.common.config;

import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import com.evirapo.diversityofcritters.common.spawn.PopulationMath;

import java.util.Map;

/**
 * Already-resolved values of {@code [spawn.population]}.
 *
 * @param basePopulation      starting population, and the one that spawns at exactly the base chance
 * @param floor               lowest population; 0 allows true extinction
 * @param cap                 highest population
 * @param endangeredThreshold below this the species is endangered
 * @param wildBreedingRate    multiplier on how often wild animals breed
 * @param weights             how far each event moves the population
 */
public record PopulationSettings(int basePopulation, int floor, int cap, int endangeredThreshold,
                                 double wildBreedingRate, Map<PopulationEvent, Integer> weights) {

    /** The signed change an event makes with the configured weight. */
    public int delta(PopulationEvent event) {
        return event.delta(this.weights.getOrDefault(event, 1));
    }

    public int clamp(int population) {
        return PopulationMath.clamp(population, this.floor, this.cap);
    }
}
```

- [ ] **Step 4: Añadir las claves a `DOCServerConfig`**

Imports a añadir:

```java
import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import java.util.EnumMap;
```

Junto a las otras declaraciones de `ForgeConfigSpec.*Value` (tras `RELEASED_CAN_DESPAWN`):

```java
    public static final ForgeConfigSpec.IntValue BASE_POPULATION;
    public static final ForgeConfigSpec.IntValue POPULATION_FLOOR;
    public static final ForgeConfigSpec.IntValue POPULATION_CAP;
    public static final ForgeConfigSpec.IntValue ENDANGERED_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue WILD_BREEDING_RATE;
```

Justo debajo de `private static final Map<String, SpeciesSpawnSettings> SETTINGS = ...` (tiene que ir
antes del bloque `static {}`, que lo rellena):

```java
    private static final Map<PopulationEvent, ForgeConfigSpec.IntValue> WEIGHTS = new EnumMap<>(PopulationEvent.class);
```

Dentro del bloque `static {}`, después de definir `RELEASED_CAN_DESPAWN` y antes de los
`SETTINGS.put(...)`:

```java
        builder.comment("Wild population model: taming, killing and harsh habitats lower it,",
                        "releasing and wild births raise it, and the spawn chance follows it.").push("population");

        BASE_POPULATION = builder
                .comment("Starting population of every species, and the population at which it spawns at",
                         "exactly its base spawnChance. Each animal is worth 1/basePopulation of the base chance.")
                .defineInRange("basePopulation", 20, 1, 1000);

        POPULATION_FLOOR = builder
                .comment("Lowest the population can fall. 0 enables true extinction: at 0 the species stops",
                         "spawning naturally. Spawn eggs and breeding keep working regardless.")
                .defineInRange("populationFloor", 5, 0, 1000);

        POPULATION_CAP = builder
                .comment("Highest the population can climb. With basePopulation = 20 and maxBonus = 2.0,",
                         "60 is exactly where the spawn bonus saturates.")
                .defineInRange("populationCap", 60, 1, 100_000);

        ENDANGERED_THRESHOLD = builder
                .comment("Below this population the species is endangered and every player is warned.")
                .defineInRange("endangeredThreshold", 10, 0, 1000);

        WILD_BREEDING_RATE = builder
                .comment("Multiplier on how often wild animals breed. 0 = wild animals never breed.",
                         "Captive breeding is unaffected.")
                .defineInRange("wildBreedingRate", 1.0D, 0.0D, 10.0D);

        builder.comment("How far each event moves the population. 0 = the event is not counted.").push("weights");
        for (PopulationEvent event : PopulationEvent.values()) {
            WEIGHTS.put(event, builder.defineInRange(event.configKey(), 1, 0, 100));
        }
        builder.pop();

        builder.pop();
```

En la sección `CACHE`, junto a `DEFAULT_GLOBALS`:

```java
    private static final PopulationSettings DEFAULT_POPULATION =
            new PopulationSettings(20, 5, 60, 10, 1.0D, defaultWeights());

    private static Map<PopulationEvent, Integer> defaultWeights() {
        Map<PopulationEvent, Integer> weights = new EnumMap<>(PopulationEvent.class);
        for (PopulationEvent event : PopulationEvent.values()) weights.put(event, 1);
        return Collections.unmodifiableMap(weights);
    }

    private static volatile PopulationSettings population = DEFAULT_POPULATION;
```

En `bake()`, al final:

```java
        Map<PopulationEvent, Integer> weights = new EnumMap<>(PopulationEvent.class);
        WEIGHTS.forEach((event, value) -> weights.put(event, value.get()));
        population = new PopulationSettings(
                BASE_POPULATION.get(),
                POPULATION_FLOOR.get(),
                POPULATION_CAP.get(),
                ENDANGERED_THRESHOLD.get(),
                WILD_BREEDING_RATE.get(),
                Collections.unmodifiableMap(weights)
        );
```

En `unbake()`, al final:

```java
        population = DEFAULT_POPULATION;
```

En la sección `READ ACCESS`, tras `releasedCanDespawn()`:

```java
    /** {@code [spawn.population]}. Never null: falls back to the defaults outside a world. */
    public static PopulationSettings population() {
        return population;
    }
```

En la sección `WRITE ACCESS`, tras `settings(String)`:

```java
    /** An event's weight handle, for {@code /doc_spawn set weights}. Writing only. */
    public static ForgeConfigSpec.IntValue weightValue(PopulationEvent event) {
        return WEIGHTS.get(event);
    }
```

- [ ] **Step 5: Ejecutar los tests y compilar**

Run: `./gradlew test --tests "com.evirapo.diversityofcritters.common.config.PopulationSettingsTest"`
Expected: PASS, 3 tests.

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/config/PopulationSettings.java src/main/java/com/evirapo/diversityofcritters/common/config/DOCServerConfig.java src/test/java/com/evirapo/diversityofcritters/common/config/PopulationSettingsTest.java
git commit -m "Wild population: population config"
```

---

### Task 3: `WildPopulation` sustituye a `ReleaseRegistry`

El contador cambia de escala: ahora es la población (arranca en 20), el chance es proporcional a ella,
el botón Release suma por evento y no por UUID, y cruzar el umbral avisa a todos.

**Files:**
- Create: `common/spawn/WildPopulation.java`
- Delete: `common/spawn/ReleaseRegistry.java`
- Modify: `common/spawn/SpawnBonusFormula.java` (reescritura)
- Modify: `common/spawn/SpawnCheckResult.java`
- Modify: `common/spawn/SpawnRuleEngine.java:124-139, 178-188, 312-353`
- Modify: `common/command/SpawnDebugCommand.java`
- Modify: `network/ReleaseCritterMsg.java`
- Modify: `common/event/DOCForgeEvents.java`
- Modify: `common/config/DOCServerConfig.java` (quitar `bonusPerRelease` y `releaseCap`)
- Modify: `src/main/resources/assets/diversityofcritters/lang/en_us.json:73-75`

**Interfaces:**
- Consumes: `PopulationSettings`, `DOCServerConfig.population()` (Task 2); `PopulationMath`, `PopulationEvent` (Task 1).
- Produces: `WildPopulation extends SavedData` con `static WildPopulation get(MinecraftServer)`,
  `static int cachedPopulation(DOCSpecies)`, `static void resetCache()`,
  `static void record(Entity, PopulationEvent)`, `int get(DOCSpecies)`,
  `int apply(DOCSpecies, PopulationEvent, MinecraftServer)` (devuelve el cambio real),
  `int set(DOCSpecies, int, MinecraftServer)` (devuelve lo guardado), `void reset(DOCSpecies, MinecraftServer)`,
  `void resetAll(MinecraftServer)`.
- Produces: `SpawnBonusFormula.multiplier(int)`, `bonus(int)`, `finalChance(double, int)`, `bonusLabel(int)`.
- Produces: `DOCServerConfig.Globals(double maxBonus, int groupCheckRadius, boolean releasedCanDespawn)`.
- Produces: `SpawnRuleEngine.SpawnDiagnostic.population()` (antes `released()`).

- [ ] **Step 1: Crear `WildPopulation`**

```java
package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.config.PopulationSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * The wild population of every species, for the whole save.
 *
 * Lives in {@code saves/<world>/data/doc_release_registry.dat}, attached to the Overworld. The file
 * name is the module 2 release registry's: version 1 data is migrated on load, so worlds keep the
 * progress they already made.
 *
 * Floor and cap are applied on write, not on read, so lowering them in the config never leaves an
 * out-of-range value stored.
 */
public class WildPopulation extends SavedData {

    public static final String DATA_NAME = "doc_release_registry";
    private static final int SCHEMA_VERSION = 2;

    private final Map<DOCSpecies, Integer> populations = new EnumMap<>(DOCSpecies.class);

    // ==============================
    //       LOCK-FREE CACHE
    // ==============================

    // The spawn cycle reads the population from the chunk generation threads, where touching the
    // server's DataStorage is not safe. We mirror it into a volatile array that is swapped
    // wholesale on every write: lock-free reads from any thread.
    private static final int UNKNOWN = -1;
    private static volatile int[] populationCache = freshCache();

    private static int[] freshCache() {
        int[] cache = new int[DOCSpecies.values().length];
        Arrays.fill(cache, UNKNOWN);
        return cache;
    }

    /** Population, safe to read from any thread. Before the save is loaded, the base population. */
    public static int cachedPopulation(DOCSpecies species) {
        int cached = populationCache[species.ordinal()];
        return cached != UNKNOWN ? cached : defaultPopulation();
    }

    /** Called when the server stops, so one world's data does not leak into the next. */
    public static void resetCache() {
        populationCache = freshCache();
    }

    private void syncCache() {
        int[] snapshot = new int[DOCSpecies.values().length];
        for (DOCSpecies species : DOCSpecies.values()) {
            snapshot[species.ordinal()] = get(species);
        }
        populationCache = snapshot;
    }

    private static int defaultPopulation() {
        PopulationSettings settings = DOCServerConfig.population();
        return settings.clamp(settings.basePopulation());
    }

    // ==============================
    //       ACCESS
    // ==============================

    /**
     * The save's population. Creates it if it did not exist.
     *
     * Server thread only: DataStorage is not thread-safe. From another thread use
     * {@link #cachedPopulation(DOCSpecies)}.
     */
    public static WildPopulation get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WildPopulation::load, WildPopulation::new, DATA_NAME);
    }

    /**
     * Entry point for the entity hooks: counts an event for whatever species the entity is.
     * Does nothing for species outside the system (the lion) or off the server.
     */
    public static void record(Entity entity, PopulationEvent event) {
        DOCSpecies species = DOCSpecies.of(entity);
        MinecraftServer server = entity.getServer();
        if (species == null || server == null) return;
        get(server).apply(species, event, server);
    }

    public int get(DOCSpecies species) {
        Integer stored = this.populations.get(species);
        return stored != null ? stored : defaultPopulation();
    }

    /**
     * Moves the population by the event's configured weight.
     *
     * @return the change actually made, after floor and cap. 0 if the weight is 0 or it was
     *         already at the limit.
     */
    public int apply(DOCSpecies species, PopulationEvent event, MinecraftServer server) {
        int delta = DOCServerConfig.population().delta(event);
        if (delta == 0) return 0;

        int before = get(species);
        int after = write(species, before + delta, server);
        return after - before;
    }

    /** Sets the population directly, clamped. For the debug commands. Returns what was stored. */
    public int set(DOCSpecies species, int population, MinecraftServer server) {
        return write(species, population, server);
    }

    /** Back to the base population. Only for {@code /doc_spawn reset}. */
    public void reset(DOCSpecies species, MinecraftServer server) {
        set(species, DOCServerConfig.population().basePopulation(), server);
    }

    public void resetAll(MinecraftServer server) {
        for (DOCSpecies species : DOCSpecies.values()) {
            reset(species, server);
        }
    }

    private int write(DOCSpecies species, int population, MinecraftServer server) {
        int before = get(species);
        int after = DOCServerConfig.population().clamp(population);

        this.populations.put(species, after);
        syncCache();
        setDirty();

        announce(server, species, before, after);
        return after;
    }

    /**
     * Tells every player when a species crosses the endangered line, either way. Only on the
     * crossing itself: a warning on every death would be noise.
     */
    private static void announce(MinecraftServer server, DOCSpecies species, int before, int after) {
        PopulationMath.Crossing crossing = PopulationMath.crossing(
                before, after, DOCServerConfig.population().endangeredThreshold());
        if (crossing == PopulationMath.Crossing.NONE) return;

        boolean endangered = crossing == PopulationMath.Crossing.BECAME_ENDANGERED;
        Component message = Component.translatable(
                endangered ? "message.diversityofcritters.population.endangered"
                           : "message.diversityofcritters.population.recovering",
                species.displayName(), after
        ).withStyle(endangered ? ChatFormatting.GOLD : ChatFormatting.GREEN);

        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    // ==============================
    //       PERSISTENCE
    // ==============================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", SCHEMA_VERSION);

        CompoundTag speciesTag = new CompoundTag();
        for (DOCSpecies species : DOCSpecies.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Population", get(species));
            speciesTag.put(species.key(), entry);
        }
        tag.put("Species", speciesTag);

        return tag;
    }

    public static WildPopulation load(CompoundTag tag) {
        WildPopulation registry = new WildPopulation();
        PopulationSettings settings = DOCServerConfig.population();
        boolean legacy = tag.getInt("Version") < SCHEMA_VERSION;
        CompoundTag speciesTag = tag.getCompound("Species");

        for (DOCSpecies species : DOCSpecies.values()) {
            if (!speciesTag.contains(species.key())) continue;

            CompoundTag entry = speciesTag.getCompound(species.key());
            int population = legacy
                    ? PopulationMath.migrateV1(entry.getInt("Count"),
                            settings.basePopulation(), settings.floor(), settings.cap())
                    : settings.clamp(entry.getInt("Population"));
            registry.populations.put(species, population);
        }

        // Rewrite in the new format on the next save, so the migration only ever runs once.
        if (legacy) registry.setDirty();

        registry.syncCache();
        return registry;
    }
}
```

- [ ] **Step 2: Borrar `ReleaseRegistry`**

```bash
git rm src/main/java/com/evirapo/diversityofcritters/common/spawn/ReleaseRegistry.java
```

- [ ] **Step 3: Reescribir `SpawnBonusFormula`**

Contenido completo del archivo:

```java
package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;

/**
 * Turns a species' wild population into its spawn chance.
 *
 * <pre>
 *   multiplier  = clamp(population / basePopulation, 0, 1 + maxBonus)
 *   chanceFinal = clamp(chanceBase * multiplier, 0, 1)
 * </pre>
 *
 * With the default values (base 20, maxBonus 2.0): 5 animals give 25% of the base chance, 20 give
 * exactly the base chance, 60 give triple. Each animal is worth 5% of the base chance, the same a
 * release was worth in module 2.
 */
public final class SpawnBonusFormula {

    public static double multiplier(int population) {
        return PopulationMath.multiplier(population,
                DOCServerConfig.population().basePopulation(),
                DOCServerConfig.globals().maxBonus());
    }

    /** Bonus over the base chance. Negative when the species is below its base population. */
    public static double bonus(int population) {
        return multiplier(population) - 1.0D;
    }

    /** Effective spawn chance for this population. */
    public static double finalChance(double chanceBase, int population) {
        return PopulationMath.finalChance(chanceBase, multiplier(population));
    }

    /** The bonus as players read it: {@code "+15%"}, {@code "-75%"}. */
    public static String bonusLabel(int population) {
        return String.format("%+d%%", Math.round(bonus(population) * 100.0D));
    }

    private SpawnBonusFormula() {}
}
```

- [ ] **Step 4: `SpawnCheckResult`: `releasedCount` → `population`**

Sustituir la línea del javadoc

```java
 * @param bonus         bonus accumulated from releases (0.5 = +50%)
```
por
```java
 * @param bonus         bonus from the wild population (0.5 = +50%, -0.75 = -75%)
```

la línea
```java
 * @param releasedCount animals of this species released in this save
```
por
```java
 * @param population    wild population of this species in this save
```

y el componente del record
```java
        int releasedCount,
```
por
```java
        int population,
```

- [ ] **Step 5: `SpawnRuleEngine` lee la población**

Sustituir el método `roll` entero por:

```java
    /** Final roll, modulated by the wild population (or by the debug override). */
    private static SpawnCheckResult roll(DOCSpecies species, Snapshot cfg, BlockPos pos, RandomSource random) {
        int population = WildPopulation.cachedPopulation(species);
        double chanceBase = cfg.spawnChance();
        double bonus = SpawnBonusFormula.bonus(population);
        double chanceFinal = effectiveChance(species, chanceBase, population);

        boolean won = random.nextDouble() < chanceFinal;

        return new SpawnCheckResult(
                species,
                won ? SpawnRejectReason.PASSED : SpawnRejectReason.CHANCE_ROLL_FAILED,
                chanceBase, bonus, chanceFinal, population, pos.immutable(),
                String.format("%.3f -> %.3f", chanceBase, chanceFinal)
        );
    }
```

Sustituir el javadoc y el método `effectiveChance` por:

```java
    /**
     * The chance actually used in the roll.
     *
     * When an override is set it replaces the formula's result, so the population is ignored while
     * it is in place. That is deliberate, so that "set it to 100%" means exactly that.
     */
    private static double effectiveChance(DOCSpecies species, double chanceBase, int population) {
        double override = chanceOverrides[species.ordinal()];
        if (override >= 0.0D) return override;
        return SpawnBonusFormula.finalChance(chanceBase, population);
    }
```

En el record `SpawnDiagnostic`, cambiar el último componente `int released` por `int population`:

```java
    public record SpawnDiagnostic(DOCSpecies species, BlockPos pos, List<DiagnosticLine> lines,
                                  @Nullable SpawnRejectReason firstFailure,
                                  double chanceBase, double bonus, double chanceFinal, int population) {
```

Y el final de `diagnose`:

```java
        int population = WildPopulation.cachedPopulation(species);

        return new SpawnDiagnostic(species, pos.immutable(), lines, firstFailure,
                cfg.spawnChance(), SpawnBonusFormula.bonus(population),
                effectiveChance(species, cfg.spawnChance(), population), population);
```

- [ ] **Step 6: `DOCServerConfig` sin `bonusPerRelease` ni `releaseCap`**

Borrar las declaraciones

```java
    public static final ForgeConfigSpec.DoubleValue BONUS_PER_RELEASE;
```
```java
    public static final ForgeConfigSpec.IntValue RELEASE_CAP;
```

Borrar del bloque `static {}` las definiciones completas de `BONUS_PER_RELEASE` (el
`builder.comment("Spawn bonus contributed by each released animal..." ... .defineInRange("bonusPerRelease", ...)`)
y de `RELEASE_CAP` (`.defineInRange("releaseCap", ...)`).

Sustituir el comentario de `MAX_BONUS` por uno que ya no habla de liberaciones:

```java
        MAX_BONUS = builder
                .comment("Cap on the spawn bonus a thriving population gives. 2.0 = at most triple the",
                         "base chance, reached at 3 x basePopulation.")
                .defineInRange("maxBonus", 2.0D, 0.0D, 100.0D);
```

Sustituir el record `Globals`, `DEFAULT_GLOBALS` y su construcción en `bake()`:

```java
    /** Already-resolved global settings. */
    public record Globals(double maxBonus, int groupCheckRadius, boolean releasedCanDespawn) {}

    private static final Globals DEFAULT_GLOBALS = new Globals(2.0D, 24, true);
```

```java
        globals = new Globals(
                MAX_BONUS.get(),
                GROUP_CHECK_RADIUS.get(),
                RELEASED_CAN_DESPAWN.get()
        );
```

- [ ] **Step 7: `ReleaseCritterMsg` suma un evento**

Sustituir los imports de `ReleaseRegistry` por:

```java
import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
```

Sustituir, dentro de `handle`, desde `ReleaseRegistry registry = ReleaseRegistry.get(server);` hasta
`player.sendSystemMessage(feedback(result, ta, species, registry));` por:

```java
            // No per-animal anti-farming needed any more: taming took this animal out of the
            // population, releasing puts it back. Tame-release loops net zero. What really adds is
            // releasing an animal born in captivity, which is the point; the cap bounds it.
            WildPopulation population = WildPopulation.get(server);
            population.apply(species, PopulationEvent.RELEASE, server);

            critter.setWasReleased(true);
            untame(ta);

            player.sendSystemMessage(feedback(ta, species, population));
```

Sustituir el método `feedback` entero por:

```java
    private static Component feedback(TamableAnimal ta, DOCSpecies species, WildPopulation population) {
        Component name = ta.getDisplayName();
        int count = population.get(species);

        if (count >= DOCServerConfig.population().cap()) {
            return Component.translatable("message.diversityofcritters.release.capped",
                    name, species.displayName());
        }

        return Component.translatable("message.diversityofcritters.release.counted",
                name, species.displayName(), count, SpawnBonusFormula.bonusLabel(count));
    }
```

- [ ] **Step 8: `DOCForgeEvents`**

Sustituir el import
```java
import com.evirapo.diversityofcritters.common.spawn.ReleaseRegistry;
```
por
```java
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
```

En `onServerStarted`:
```java
        // Load the wild population at startup so its cache is populated before the first spawn
        // cycle.
        WildPopulation.get(event.getServer());
```

En `onServerStopping`, `ReleaseRegistry.resetCache();` → `WildPopulation.resetCache();`.

- [ ] **Step 9: `SpawnDebugCommand`**

Imports: sustituir `ReleaseRegistry` por `WildPopulation` y añadir:

```java
import com.evirapo.diversityofcritters.common.config.PopulationSettings;
import net.minecraft.server.MinecraftServer;
```

En `register`, borrar los dos nodos `.then(Commands.literal("bonusPerRelease") ...)` y
`.then(Commands.literal("releaseCap") ...)` completos.

Actualizar el javadoc de la clase:

```java
/**
 * {@code /doc_spawn} - diagnostic tool for the spawn system.
 *
 * It answers the two questions that come up while balancing this: "how is each species doing?"
 * and "why is nothing appearing here?".
 */
```

Sustituir `summary` entero por:

```java
    /** Global summary: state, population and chance for every species. */
    private static int summary(CommandSourceStack source) {
        DOCServerConfig.Globals globals = DOCServerConfig.globals();
        PopulationSettings population = DOCServerConfig.population();

        send(source, "§e=== DOC SPAWN ===");
        send(source, "§7basePopulation=" + population.basePopulation()
                + "  floor=" + population.floor()
                + "  cap=" + population.cap()
                + "  endangered<" + population.endangeredThreshold()
                + "  maxBonus=" + globals.maxBonus()
                + "  groupRadius=" + globals.groupCheckRadius());

        for (DOCSpecies species : DOCSpecies.values()) {
            Snapshot cfg = species.settings();
            int count = WildPopulation.cachedPopulation(species);
            double finalChance = SpawnBonusFormula.finalChance(cfg.spawnChance(), count);

            send(source, "");
            send(source, "§b" + species.key() + "§r  " + (cfg.enabled() ? "§aenabled" : "§cdisabled")
                    + (count < population.endangeredThreshold() ? "  §6ENDANGERED" : ""));
            send(source, "  §7population: §f" + count
                    + " §7(floor " + population.floor() + ", cap " + population.cap() + ")"
                    + "   §7bonus: §f" + SpawnBonusFormula.bonusLabel(count));
            if (SpawnRuleEngine.hasChanceOverride(species)) {
                send(source, String.format("  §6chance: §f%.3f §7(OVERRIDE — population ignored)",
                        SpawnRuleEngine.chanceOverride(species)));
            } else {
                send(source, String.format("  §7chance: §f%.3f §7-> §f%.3f", cfg.spawnChance(), finalChance));
            }
            send(source, "  §7Y " + cfg.minY() + ".." + cfg.maxY()
                    + "   light<=" + cfg.maxBlockLight()
                    + "   sky=" + cfg.skyAccess()
                    + "   groupMax=" + cfg.groupMax());
            if (SpawnRuleEngine.isTracing(species)) send(source, "  §dtrace: ON");
        }

        return 1;
    }
```

En `here`: actualizar el javadoc (`how many have been released and how much bonus that grants` →
`how big the wild population is and what it does to the chance`), sustituir
`int cap = DOCServerConfig.globals().releaseCap();` por

```java
        PopulationSettings population = DOCServerConfig.population();
```

y las cuatro líneas desde `send(source, " §7Released: ...` hasta el mensaje del override por:

```java
            send(source, " §7Population:    §f" + diagnostic.population()
                    + " §7(floor " + population.floor() + ", cap " + population.cap() + ")");
            send(source, " §7Pop. bonus:    §f" + SpawnBonusFormula.bonusLabel(diagnostic.population()));

            if (SpawnRuleEngine.hasChanceOverride(species)) {
                send(source, " §6Chance override active — the population is being ignored.");
            }
```

Sustituir `max` entero por:

```java
    /**
     * Sets the species to maximum output: chance at 100% and the population at its cap.
     *
     * The other filters (biome, height, light, ground, density) still apply. Skipping them would
     * make the animal appear in biomes that are not its own, which is exactly what we do not want
     * to test.
     */
    private static int max(CommandSourceStack source, DOCSpecies species) {
        SpawnRuleEngine.setChanceOverride(species, 1.0D);

        int cap = DOCServerConfig.population().cap();
        MinecraftServer server = source.getServer();
        if (server != null) {
            WildPopulation.get(server).set(species, cap, server);
        }

        send(source, "§a" + species.key() + " set to maximum spawn.");
        send(source, "  §7chance: §f100%§7 (override)   population: §f" + cap);
        send(source, "  §7Biome, Y, light, ground and density filters still apply.");
        send(source, "  §7Undo with: §f/doc_spawn chance " + species.key() + " clear");
        send(source, "  §7The population is persistent — put it back with: §f/doc_spawn reset "
                + species.key() + " confirm");

        return 1;
    }
```

En `chance`, las dos frases que hablan del release bonus:

```java
            send(source, "Chance override cleared for " + species.key()
                    + ". Back to config + population.");
```
```java
            send(source, "  §7While set, the population is ignored for this species.");
```

Sustituir `reset` entero por:

```java
    private static int reset(CommandSourceStack source, @Nullable DOCSpecies species) {
        MinecraftServer server = source.getServer();
        if (server == null) return 0;

        WildPopulation registry = WildPopulation.get(server);

        if (species == null) {
            registry.resetAll(server);
            send(source, "§cPopulation reset to its base value for ALL species.");
        } else {
            registry.reset(species, server);
            send(source, "§cPopulation of " + species.key() + " reset to " + registry.get(species) + ".");
        }

        return 1;
    }
```

- [ ] **Step 10: Lang**

Sustituir en `en_us.json` las tres líneas `message.diversityofcritters.release.*` por:

```json
  "message.diversityofcritters.release.counted": "%1$s returned to the wild. %2$s population: %3$s (%4$s spawn rate)",
  "message.diversityofcritters.release.capped": "%1$s returned to the wild. The %2$s population is already at its peak.",
  "message.diversityofcritters.population.endangered": "The %1$s is now endangered in this world (population %2$s). Breed and release them to help it recover.",
  "message.diversityofcritters.population.recovering": "The %1$s population is recovering (population %2$s).",
```

- [ ] **Step 11: Compilar y comprobar que no queda nada del registro viejo**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

Run: `grep -rn "ReleaseRegistry\|releaseCap\|bonusPerRelease\|releasedCount\|BONUS_PER_RELEASE\|RELEASE_CAP" src/main`
Expected: sin resultados.

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/spawn src/main/java/com/evirapo/diversityofcritters/common/command/SpawnDebugCommand.java src/main/java/com/evirapo/diversityofcritters/network/ReleaseCritterMsg.java src/main/java/com/evirapo/diversityofcritters/common/event/DOCForgeEvents.java src/main/java/com/evirapo/diversityofcritters/common/config/DOCServerConfig.java src/main/resources/assets/diversityofcritters/lang/en_us.json
git commit -m "Wild population: the release counter becomes a population"
```

---

### Task 4: Tendencia semanal y `/doc_spawn` ampliado

**Files:**
- Modify: `common/spawn/WildPopulation.java`
- Modify: `common/event/DOCForgeEvents.java`
- Modify: `common/command/SpawnDebugCommand.java`

**Interfaces:**
- Consumes: `PopulationMath.pushSample`, `trend` (Task 1); `WildPopulation` (Task 3); `DOCServerConfig.weightValue`, `BASE_POPULATION`… (Task 2).
- Produces: `WildPopulation.sampleDay(long day)`, `WildPopulation.trend(DOCSpecies)`.

- [ ] **Step 1: Historial en `WildPopulation`**

Campos nuevos, debajo de `populations`:

```java
    // One sample per in-game day, oldest first, for the trend /doc_spawn shows.
    private final Map<DOCSpecies, int[]> history = new EnumMap<>(DOCSpecies.class);
    private long lastSampledDay = Long.MIN_VALUE;
```

Métodos nuevos en la sección `ACCESS`, tras `resetAll`:

```java
    /**
     * Records today's population, once per in-game day. Called from the Overworld tick; it compares
     * the day number for inequality rather than "greater than", so {@code /time set} going back
     * still starts a new sample instead of freezing the history.
     */
    public void sampleDay(long day) {
        if (day == this.lastSampledDay) return;
        this.lastSampledDay = day;

        for (DOCSpecies species : DOCSpecies.values()) {
            int[] previous = this.history.getOrDefault(species, new int[0]);
            this.history.put(species, PopulationMath.pushSample(previous, get(species)));
        }
        setDirty();
    }

    /** Change over the last week (or since the oldest sample, if the save is younger). */
    public int trend(DOCSpecies species) {
        return PopulationMath.trend(get(species), this.history.getOrDefault(species, new int[0]));
    }
```

En `reset(DOCSpecies, MinecraftServer)`, borrar la historia de la especie antes del `set`, para que
la tendencia no enseñe el salto del reset:

```java
    /** Back to the base population, with a clean history. Only for {@code /doc_spawn reset}. */
    public void reset(DOCSpecies species, MinecraftServer server) {
        this.history.remove(species);
        set(species, DOCServerConfig.population().basePopulation(), server);
    }
```

En `save`, dentro del bucle tras `entry.putInt("Population", ...)`:

```java
            entry.putIntArray("History", this.history.getOrDefault(species, new int[0]));
```

y tras `tag.put("Species", speciesTag);`:

```java
        tag.putLong("LastSampledDay", this.lastSampledDay);
```

En `load`, dentro del bucle tras `registry.populations.put(species, population);`:

```java
            if (entry.contains("History")) {
                registry.history.put(species, entry.getIntArray("History"));
            }
```

y antes de `if (legacy) registry.setDirty();`:

```java
        if (tag.contains("LastSampledDay")) {
            registry.lastSampledDay = tag.getLong("LastSampledDay");
        }
```

- [ ] **Step 2: Muestreo diario en `DOCForgeEvents`**

Imports:

```java
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
```

Método nuevo:

```java
    /**
     * Daily population sample for the trend. Every 100 ticks is plenty: a day is 24000 ticks long,
     * and the sample itself is a no-op unless the day changed.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100L != 0L) return;

        WildPopulation.get(level.getServer()).sampleDay(level.getDayTime() / 24000L);
    }
```

- [ ] **Step 3: `/doc_spawn` — claves nuevas en `set`**

Import:

```java
import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
```

En `register`, dentro de `.then(Commands.literal("set")`, justo después del comentario
`// --- global settings ---`:

```java
                        .then(Commands.literal("basePopulation")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> apply(ctx.getSource(), "basePopulation",
                                                DOCServerConfig.BASE_POPULATION,
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("populationFloor")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 1000))
                                        .executes(ctx -> apply(ctx.getSource(), "populationFloor",
                                                DOCServerConfig.POPULATION_FLOOR,
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("populationCap")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 100_000))
                                        .executes(ctx -> apply(ctx.getSource(), "populationCap",
                                                DOCServerConfig.POPULATION_CAP,
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("endangeredThreshold")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 1000))
                                        .executes(ctx -> apply(ctx.getSource(), "endangeredThreshold",
                                                DOCServerConfig.ENDANGERED_THRESHOLD,
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("wildBreedingRate")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 10.0D))
                                        .executes(ctx -> apply(ctx.getSource(), "wildBreedingRate",
                                                DOCServerConfig.WILD_BREEDING_RATE,
                                                DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(weightsSetNode())
```

Método nuevo en la sección `CONFIG EDITING`, tras `speciesSetNode`:

```java
    /** The {@code /doc_spawn set weights <event> <value>} branch, one literal per event. */
    private static LiteralArgumentBuilder<CommandSourceStack> weightsSetNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("weights");

        for (PopulationEvent event : PopulationEvent.values()) {
            ForgeConfigSpec.IntValue weight = DOCServerConfig.weightValue(event);
            String label = "weights." + event.configKey();

            node.then(Commands.literal(event.configKey())
                    .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                            .executes(ctx -> apply(ctx.getSource(), label, weight,
                                    IntegerArgumentType.getInteger(ctx, "value")))));
        }

        return node;
    }
```

- [ ] **Step 4: `/doc_spawn population <especie> <n>`**

En `register`, tras el nodo `max`:

```java
                .then(Commands.literal("population")
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests(SPECIES_SUGGESTIONS)
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100_000))
                                        .executes(ctx -> population(ctx.getSource(), requireSpecies(ctx),
                                                IntegerArgumentType.getInteger(ctx, "value"))))))
```

Método nuevo en `SUBCOMMANDS`, tras `max`:

```java
    /**
     * Sets the population directly. It exists to test the floor, extinction and the endangered
     * warning without having to kill a dozen animals by hand.
     */
    private static int population(CommandSourceStack source, DOCSpecies species, int requested) {
        MinecraftServer server = source.getServer();
        if (server == null) return 0;

        int stored = WildPopulation.get(server).set(species, requested, server);

        send(source, "§a" + species.key() + " population set to " + stored + ".");
        if (stored != requested) {
            send(source, "  §7Clamped from " + requested + " to the configured floor/cap.");
        }

        return 1;
    }
```

- [ ] **Step 5: Tendencia en el resumen**

En `summary`, antes del bucle:

```java
        MinecraftServer server = source.getServer();
        WildPopulation registry = server != null ? WildPopulation.get(server) : null;
```

y sustituir la línea de población por:

```java
            String trend = registry != null
                    ? String.format("%+d this week", registry.trend(species))
                    : "n/a";
            send(source, "  §7population: §f" + count
                    + " §7(floor " + population.floor() + ", cap " + population.cap() + ")"
                    + "   §7trend: §f" + trend
                    + "   §7bonus: §f" + SpawnBonusFormula.bonusLabel(count));
```

- [ ] **Step 6: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/spawn/WildPopulation.java src/main/java/com/evirapo/diversityofcritters/common/event/DOCForgeEvents.java src/main/java/com/evirapo/diversityofcritters/common/command/SpawnDebugCommand.java
git commit -m "Wild population: weekly trend and /doc_spawn population"
```

---

### Task 5: Damage types propios y hambre/sed separadas

**Files:**
- Create: `common/damage/DOCDamageTypes.java`
- Create: `src/main/resources/data/diversityofcritters/damage_type/starvation.json`
- Create: `src/main/resources/data/diversityofcritters/damage_type/dehydration.json`
- Create: `src/main/resources/data/diversityofcritters/damage_type/hypothermia.json`
- Create: `src/main/resources/data/diversityofcritters/damage_type/heatstroke.json`
- Create: `src/main/resources/data/diversityofcritters/damage_type/humidity.json`
- Create: `src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`
- Modify: `common/entity/custom/base/DiverseCritter.java:786, 839, 929, 1104`
- Modify: `src/main/resources/assets/diversityofcritters/lang/en_us.json`

**Interfaces:**
- Produces: `DOCDamageTypes.STARVATION, DEHYDRATION, HYPOTHERMIA, HEATSTROKE, HUMIDITY` (`ResourceKey<DamageType>`),
  `static DamageSource source(Level level, ResourceKey<DamageType> type)`.
- Produces: `DiverseCritter.dehydrate()` (protected).

- [ ] **Step 1: `DOCDamageTypes`**

```java
package com.evirapo.diversityofcritters.common.damage;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/**
 * The critters' own causes of death.
 *
 * They exist so the wild population can tell them apart: with vanilla's freeze, onFire, dryOut and
 * starve, an animal killed by its habitat was indistinguishable from one a player set on fire or
 * buried in powder snow. The types themselves are data, in {@code data/diversityofcritters/damage_type/}.
 */
public final class DOCDamageTypes {

    /** Hunger at zero. Counts against the wild population. */
    public static final ResourceKey<DamageType> STARVATION = key("starvation");
    /** Thirst at zero. Does NOT count: excluded by the client. */
    public static final ResourceKey<DamageType> DEHYDRATION = key("dehydration");
    /** Temperature below the comfort range. */
    public static final ResourceKey<DamageType> HYPOTHERMIA = key("hypothermia");
    /** Temperature above the comfort range. */
    public static final ResourceKey<DamageType> HEATSTROKE = key("heatstroke");
    /** Humidity outside the comfort range, either way. */
    public static final ResourceKey<DamageType> HUMIDITY = key("humidity");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DiversityOfCritters.MODID, name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    private DOCDamageTypes() {}
}
```

- [ ] **Step 2: Los cinco JSON de damage type**

`src/main/resources/data/diversityofcritters/damage_type/starvation.json`:

```json
{
  "exhaustion": 0.0,
  "message_id": "diversityofcritters.starvation",
  "scaling": "never"
}
```

`dehydration.json`:

```json
{
  "exhaustion": 0.0,
  "message_id": "diversityofcritters.dehydration",
  "scaling": "never"
}
```

`hypothermia.json`:

```json
{
  "exhaustion": 0.0,
  "message_id": "diversityofcritters.hypothermia",
  "scaling": "never"
}
```

`heatstroke.json`:

```json
{
  "exhaustion": 0.0,
  "message_id": "diversityofcritters.heatstroke",
  "scaling": "never"
}
```

`humidity.json`:

```json
{
  "exhaustion": 0.0,
  "message_id": "diversityofcritters.humidity",
  "scaling": "never"
}
```

- [ ] **Step 3: Tag `bypasses_armor`**

`src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json` (como el `starve` vanilla;
`replace: false` para no pisar el tag vanilla):

```json
{
  "replace": false,
  "values": [
    "diversityofcritters:starvation",
    "diversityofcritters:dehydration",
    "diversityofcritters:hypothermia",
    "diversityofcritters:heatstroke",
    "diversityofcritters:humidity"
  ]
}
```

- [ ] **Step 4: Usarlos en `DiverseCritter`**

Import:

```java
import com.evirapo.diversityofcritters.common.damage.DOCDamageTypes;
```

En `tickTemperature`, sustituir

```java
            DamageSource source = tooCold ? this.damageSources().freeze() : this.damageSources().onFire();
```
por
```java
            DamageSource source = DOCDamageTypes.source(this.level(),
                    tooCold ? DOCDamageTypes.HYPOTHERMIA : DOCDamageTypes.HEATSTROKE);
```

En `tickHumidity`, sustituir

```java
            this.hurt(this.damageSources().dryOut(), HUMIDITY_DANGER_DAMAGE);
```
por
```java
            this.hurt(DOCDamageTypes.source(this.level(), DOCDamageTypes.HUMIDITY), HUMIDITY_DANGER_DAMAGE);
```

Sustituir la línea

```java
            if ((this.getHunger() <= 0 || this.getThirst() <= 0) && random.nextInt(10) > 8){ this.starve(); }
```
por
```java
            // Hunger and thirst kill through different causes because only starvation counts against
            // the wild population. With both empty, hunger wins: it is the one that counts.
            if (random.nextInt(10) > 8) {
                if (this.getHunger() <= 0) {
                    this.starve();
                } else if (this.getThirst() <= 0) {
                    this.dehydrate();
                }
            }
```

Sustituir

```java
    protected void starve() { this.hurt(level().damageSources().starve(), 1); }
```
por
```java
    protected void starve() { this.hurt(DOCDamageTypes.source(this.level(), DOCDamageTypes.STARVATION), 1); }
    protected void dehydrate() { this.hurt(DOCDamageTypes.source(this.level(), DOCDamageTypes.DEHYDRATION), 1); }
```

- [ ] **Step 5: Mensajes de muerte**

En `en_us.json`, tras las líneas `message.diversityofcritters.population.*` de la Task 3:

```json
  "death.attack.diversityofcritters.starvation": "%1$s starved to death",
  "death.attack.diversityofcritters.dehydration": "%1$s died of thirst",
  "death.attack.diversityofcritters.hypothermia": "%1$s froze to death",
  "death.attack.diversityofcritters.heatstroke": "%1$s died of heatstroke",
  "death.attack.diversityofcritters.humidity": "%1$s could not survive the humidity",
```

- [ ] **Step 6: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (Que los JSON cargan se comprueba en los gametests de la Task 8.)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/damage src/main/resources/data/diversityofcritters/damage_type src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/resources/assets/diversityofcritters/lang/en_us.json
git commit -m "Wild population: own damage types, hunger and thirst split"
```

---

### Task 6: Pertenencia a la población, doma y muertes

**Files:**
- Create: `common/spawn/WildDeathRules.java`
- Modify: `common/entity/custom/base/DiverseCritter.java` (NBT, `finalizeSpawn`, `tame`, `die`, javadoc de `wasReleased`)
- Modify: `network/ReleaseCritterMsg.java`

**Interfaces:**
- Consumes: `DOCDamageTypes` (Task 5); `WildPopulation.record`, `PopulationEvent` (Tasks 1, 3).
- Produces: `WildDeathRules.classify(DamageSource, @Nullable Player) → @Nullable PopulationEvent`.
- Produces: `DiverseCritter.isInWild()`, `DiverseCritter.setInWild(boolean)`.

- [ ] **Step 1: `WildDeathRules`**

```java
package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.damage.DOCDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Which deaths of a wild animal count against its population.
 *
 * The caller has already checked that the animal is wild and counted; this only looks at the
 * cause. Rules are checked in order and the first that applies decides.
 */
public final class WildDeathRules {

    /**
     * @param lastHurtByPlayer the player vanilla would give the kill to (the same rule that hands
     *                         out XP: valid for 5 seconds after the hit, and it already covers
     *                         arrows, pushing into lava, setting on fire and the player's pets)
     * @return the event to count, or null if this death does not count
     */
    @Nullable
    public static PopulationEvent classify(DamageSource source, @Nullable Player lastHurtByPlayer) {
        // Admin tooling and the void: otherwise testing and moderation would corrupt the counters.
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return null;

        // The habitat killed it. Our own types, so a player's fire or powder snow never lands here.
        if (source.is(DOCDamageTypes.STARVATION)) return PopulationEvent.STARVATION;
        if (source.is(DOCDamageTypes.HYPOTHERMIA) || source.is(DOCDamageTypes.HEATSTROKE)) {
            return PopulationEvent.TEMPERATURE_DEATH;
        }
        if (source.is(DOCDamageTypes.HUMIDITY)) return PopulationEvent.HUMIDITY_DEATH;
        // Thirst is excluded at the client's request.
        if (source.is(DOCDamageTypes.DEHYDRATION)) return null;

        if (lastHurtByPlayer != null && !lastHurtByPlayer.isCreative() && !lastHurtByPlayer.isSpectator()) {
            return PopulationEvent.PLAYER_KILL;
        }

        // Predators, falls, drowning, cactus...: the ecosystem working as intended.
        return null;
    }

    private WildDeathRules() {}
}
```

- [ ] **Step 2: Campo `inWild` en `DiverseCritter`**

Imports:

```java
import com.evirapo.diversityofcritters.common.spawn.PopulationEvent;
import com.evirapo.diversityofcritters.common.spawn.WildDeathRules;
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
```

En `addAdditionalSaveData`, tras `pCompound.putBoolean("DOCWasReleased", this.wasReleased);`:

```java
        pCompound.putBoolean("DOCInWild", this.inWild);
```

Sustituir el javadoc de `wasReleased` (ya no hace de anti-farmeo):

```java
    /**
     * Whether this animal has ever been returned to the wild. It marks the animal as wild fauna for
     * despawn purposes, see {@link #removeWhenFarAway(double)}. Counting it towards the population
     * is {@link #inWild}'s job.
     */
```

Tras los métodos `wasReleased()` / `setWasReleased(...)`:

```java
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
```

En `readAdditionalSaveData`, tras `this.wasReleased = pCompound.getBoolean("DOCWasReleased");`:

```java
        // Animals saved before this flag existed: the wild ones are counted, the tame ones are not.
        // super.readAdditionalSaveData() has already restored the tame state.
        this.inWild = pCompound.contains("DOCInWild") ? pCompound.getBoolean("DOCInWild") : !this.isTame();
```

- [ ] **Step 3: Spawn natural marca `inWild`**

En `finalizeSpawn`, tras el bloque `if (reason != MobSpawnType.EVENT) { ... }`:

```java
        // Natural spawns are wild animals, but they do not add to the population: they are the
        // baseline the biome modifier already accounts for. Counting them would feed back on itself.
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            this.inWild = true;
        }
```

- [ ] **Step 4: Gancho de doma**

Tras `finalizeSpawn`:

```java
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
```

- [ ] **Step 5: Gancho de muerte**

En `die(DamageSource pDamageSource)`, dentro de `if (!this.level().isClientSide) {`, antes del bucle
de `ward`:

```java
            // A tame animal was already subtracted when it was tamed; counting its death would charge
            // the player twice. The dead/removed check guards against die() running twice.
            if (this.inWild && !this.isTame() && !this.dead && !this.isRemoved()) {
                PopulationEvent event = WildDeathRules.classify(pDamageSource, this.lastHurtByPlayer);
                if (event != null) WildPopulation.record(this, event);
            }
```

- [ ] **Step 6: Release marca `inWild`**

En `ReleaseCritterMsg.handle`, tras `critter.setWasReleased(true);`:

```java
            critter.setInWild(true);
```

- [ ] **Step 7: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. Si `DamageTypes.GENERIC_KILL` no existe en estos mappings, buscar el
nombre real con `grep -n "GENERIC_KILL\|generic_kill" build/fg_cache -r --include=*.java | head` y
usarlo; no sustituirlo por otra cosa.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/spawn/WildDeathRules.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/java/com/evirapo/diversityofcritters/network/ReleaseCritterMsg.java
git commit -m "Wild population: taming, release and wild deaths move the population"
```

---

### Task 7: Nacimientos — crías domesticadas, +1 salvaje y frecuencia de cría

**Files:**
- Modify: `common/entity/custom/base/DiverseCritter.java` (`sireOwner`, `giveBirth`, `wildScaledCooldown`)
- Modify: `common/entity/ai/NaturalMateGoal.java`

**Interfaces:**
- Consumes: `PopulationMath.mateRollBound`, `scaledCooldown` (Task 1); `DOCServerConfig.population()` (Task 2);
  `WildPopulation.record` (Task 3); `setInWild`, `isInWild` (Task 6).
- Produces: `DiverseCritter.setSireOwner(@Nullable UUID)`, `DiverseCritter.wildScaledCooldown(int)`.

- [ ] **Step 1: Dueño del padre en `DiverseCritter`**

Junto a `inWild`:

```java
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
```

Import:

```java
import com.evirapo.diversityofcritters.common.spawn.PopulationMath;
```

En `addAdditionalSaveData`, tras `DOCInWild`:

```java
        if (this.sireOwner != null) {
            pCompound.putUUID("DOCSireOwner", this.sireOwner);
        }
```

En `readAdditionalSaveData`, tras la línea de `DOCInWild`:

```java
        this.sireOwner = pCompound.hasUUID("DOCSireOwner") ? pCompound.getUUID("DOCSireOwner") : null;
```

- [ ] **Step 2: Reescribir `giveBirth`**

```java
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
```

- [ ] **Step 3: `NaturalMateGoal`**

Imports:

```java
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.spawn.PopulationMath;
```

Constante nueva junto a `COURTSHIP_TICKS`:

```java
    /** One chance in this many per goal check to go looking for a mate, at wildBreedingRate 1. */
    private static final int MATE_ROLL = 400;
```

En `canUse`, sustituir

```java
        if (this.animal.getRandom().nextInt(400) != 0) {
            return false;
        }
```
por
```java
        // Wild animals breed at wildBreedingRate: it is the lever that balances winter deaths.
        // Captive breeding is the player's business and keeps its own pace.
        double rate = this.animal.isTame() ? 1.0D : DOCServerConfig.population().wildBreedingRate();
        int bound = PopulationMath.mateRollBound(MATE_ROLL, rate);
        if (bound <= 0 || this.animal.getRandom().nextInt(bound) != 0) {
            return false;
        }
```

Sustituir `breed()` entero por:

```java
    private void breed() {
        DiverseCritter female = this.animal.getIsMale() ? this.mate : this.animal;
        DiverseCritter male = this.animal.getIsMale() ? this.animal : this.mate;

        female.setPregnant(true);
        female.pregnancyTimer = DiverseCritter.PREGNANCY_TIME_TICKS;
        // At birth the baby is tame if either parent is; the father may be gone by then.
        female.setSireOwner(male.getOwnerUUID());

        int cooldown = 96000 + this.animal.getRandom().nextInt(24000);
        female.breedCooldown = female.wildScaledCooldown(cooldown);
        male.breedCooldown = male.wildScaledCooldown(cooldown);

        this.level.broadcastEntityEvent(female, (byte) 18);
        this.level.broadcastEntityEvent(male, (byte) 18);
    }
```

- [ ] **Step 4: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/java/com/evirapo/diversityofcritters/common/entity/ai/NaturalMateGoal.java
git commit -m "Wild population: captive-born tame, wild births count, wild breeding rate"
```

---

### Task 8: GameTests de los ganchos y build final

Gametests headless que prueban los ganchos con entidades reales. De paso comprueban que los JSON de
damage type cargan (si no, `getHolderOrThrow` revienta). Cada test va en su propio `batch` porque la
población es global al mundo: dos tests en el mismo batch corren a la vez y se pisarían los números.

**Files:**
- Create: `gametest/WildPopulationGameTests.java`

**Interfaces:**
- Consumes: todo lo anterior. `DiverseCritter.pregnancyTimer` y `setPregnant` son públicos (ya los usa `NaturalMateGoal`).

- [ ] **Step 1: Escribir los gametests**

```java
package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.damage.DOCDamageTypes;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The wild population hooks with real critters, headless: {@code gradlew runGameTestServer}.
 *
 * <p>Every test sits in its own batch. The population is global to the world, and tests in one
 * batch run at the same time: sharing a batch, they would move each other's numbers.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class WildPopulationGameTests {

    private static final String ARENA = "mantle_arena";
    private static final Vec3 SPOT = new Vec3(5.5, 3, 5.5);
    private static final int START = 30;

    private WildPopulationGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** Puts the civet population at a known value, away from floor, cap and the endangered line. */
    private static WildPopulation start(GameTestHelper helper) {
        floor(helper);
        WildPopulation population = WildPopulation.get(server(helper));
        population.set(DOCSpecies.CIVET, START, server(helper));
        return population;
    }

    private static CivetEntity wildCivet(GameTestHelper helper) {
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        civet.setInWild(true);
        return civet;
    }

    private static void killWith(GameTestHelper helper, CivetEntity civet, ResourceKey<DamageType> type) {
        civet.hurt(DOCDamageTypes.source(helper.getLevel(), type), Float.MAX_VALUE);
    }

    private static void expect(GameTestHelper helper, WildPopulation population, int expected, String what) {
        int actual = population.get(DOCSpecies.CIVET);
        helper.assertTrue(actual == expected, what + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = ARENA, batch = "population_tame")
    public static void tamingAWildAnimalSubtractsOnce(GameTestHelper helper) {
        WildPopulation population = start(helper);
        CivetEntity civet = wildCivet(helper);
        Player player = helper.makeMockPlayer();

        civet.tame(player);
        civet.tame(player);

        expect(helper, population, START - 1, "after taming twice");
        helper.assertFalse(civet.isInWild(), "a tamed animal must leave the wild population");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "population_spawn_egg")
    public static void animalsThatWereNeverWildDoNotCount(GameTestHelper helper) {
        WildPopulation population = start(helper);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);

        civet.tame(helper.makeMockPlayer());
        killWith(helper, civet, DOCDamageTypes.STARVATION);

        expect(helper, population, START, "spawn-egg civet tamed and starved");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "population_habitat_deaths")
    public static void habitatDeathsSubtract(GameTestHelper helper) {
        WildPopulation population = start(helper);

        killWith(helper, wildCivet(helper), DOCDamageTypes.STARVATION);
        killWith(helper, wildCivet(helper), DOCDamageTypes.HYPOTHERMIA);
        killWith(helper, wildCivet(helper), DOCDamageTypes.HEATSTROKE);
        killWith(helper, wildCivet(helper), DOCDamageTypes.HUMIDITY);

        expect(helper, population, START - 4, "starvation, cold, heat and humidity");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "population_excluded_deaths")
    public static void thirstKillAndTameDeathsDoNotCount(GameTestHelper helper) {
        WildPopulation population = start(helper);

        killWith(helper, wildCivet(helper), DOCDamageTypes.DEHYDRATION);
        wildCivet(helper).kill();

        CivetEntity pet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        pet.tame(helper.makeMockPlayer());
        killWith(helper, pet, DOCDamageTypes.STARVATION);

        expect(helper, population, START, "thirst, /kill and a pet starving");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "population_wild_birth", timeoutTicks = 100)
    public static void aWildBirthAddsOne(GameTestHelper helper) {
        WildPopulation population = start(helper);
        CivetEntity mother = wildCivet(helper);
        mother.setIsMale(false);
        mother.setPregnant(true);
        mother.pregnancyTimer = 1;

        helper.succeedWhen(() -> {
            List<CivetEntity> babies = helper.getEntities(DOCEntities.CIVET.get()).stream()
                    .filter(CivetEntity::isBaby).toList();
            helper.assertTrue(babies.size() == 1, "waiting for the birth");
            helper.assertTrue(babies.get(0).isInWild(), "a wild-born baby must be wild");
            helper.assertFalse(babies.get(0).isTame(), "a wild-born baby must not be tame");
            expect(helper, population, START + 1, "after a wild birth");
        });
    }

    @GameTest(template = ARENA, batch = "population_captive_birth", timeoutTicks = 100)
    public static void aCaptiveBirthIsBornTameAndDoesNotCount(GameTestHelper helper) {
        WildPopulation population = start(helper);
        Player player = helper.makeMockPlayer();
        CivetEntity mother = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        mother.tame(player);
        mother.setIsMale(false);
        mother.setPregnant(true);
        mother.pregnancyTimer = 1;

        helper.succeedWhen(() -> {
            List<CivetEntity> babies = helper.getEntities(DOCEntities.CIVET.get()).stream()
                    .filter(CivetEntity::isBaby).toList();
            helper.assertTrue(babies.size() == 1, "waiting for the birth");
            CivetEntity baby = babies.get(0);
            helper.assertTrue(baby.isTame(), "a captive-born baby must be tame");
            helper.assertTrue(player.getUUID().equals(baby.getOwnerUUID()), "the baby must be the mother's owner's");
            helper.assertFalse(baby.isInWild(), "a captive-born baby must not be wild");
            expect(helper, population, START, "after a captive birth");
        });
    }

    @GameTest(template = ARENA, batch = "population_floor")
    public static void thePopulationNeverFallsBelowTheFloor(GameTestHelper helper) {
        floor(helper);
        WildPopulation population = WildPopulation.get(server(helper));
        population.set(DOCSpecies.CIVET, 0, server(helper));

        expect(helper, population, 5, "set to 0 with the default floor");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Ejecutar los gametests**

Run: `./gradlew runGameTestServer`
Expected: todos los tests pasan, los de `MantleGameTests` incluidos (`All N required tests passed`).
Si falla `animalsThatWereNeverWildDoNotCount` o `thirstKillAndTameDeathsDoNotCount` porque
`helper.makeMockPlayer()` no existe con ese nombre en 1.20.1, mirar
`GameTestHelper` en `build/fg_cache` y usar el método que crea un jugador falso.

- [ ] **Step 3: Build completo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (compila, pasa los JUnit y genera el jar).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/gametest/WildPopulationGameTests.java
git commit -m "Wild population: gametests for the population hooks"
```

- [ ] **Step 5: Entregar el checklist de prueba en juego al usuario**

Copiar la sección "Verificación → En juego" del spec. Esas pruebas las hace el usuario; no se marcan
como hechas sin su confirmación.
