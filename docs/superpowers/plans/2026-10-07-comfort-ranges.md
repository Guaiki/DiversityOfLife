# Rangos de confort (módulo 5) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Los rangos de confort de las tres especies quedan como pide el encargo.

**Architecture:** Cambiar los `return` de los cuatro overrides de confort de cada entidad, con un
gametest que fija la tabla.

**Tech Stack:** Forge 47.4.10 (MC 1.20.1), GameTest de Forge. Spec:
`docs/superpowers/specs/2026-10-07-comfort-ranges-design.md`.

## Global Constraints

- Rutas de Java relativas a `src/main/java/com/evirapo/diversityofcritters/`.
- Valores exactos: ardilla FREEZING–WARM / ARID–SATURATED; civeta COOL–HOT / MODERATE–WET; sifaka
  MODERATE–HOT / ARID–MODERATE.
- Nunca `gradlew clean`. No añadir `build.gradle` ni `mods.toml` a los commits.

---

### Task 1: Los nuevos rangos

**Files:**
- Create: `gametest/ComfortRangeGameTests.java`
- Modify: `common/entity/custom/CivetEntity.java:72-87`, `common/entity/custom/SquirrelEntity.java:64-80`, `common/entity/custom/SifakaEntity.java:56-72`

- [ ] **Step 1: Escribir el gametest**

```java
package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.humidity.HumidityTier;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Each species' comfort ranges, as the client asked for them (module 5 of the quote).
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class ComfortRangeGameTests {

    private static final String ARENA = "mantle_arena";

    private ComfortRangeGameTests() {
    }

    private static void check(GameTestHelper helper, EntityType<? extends DiverseCritter> type, String name,
                              TemperatureTier tempMin, TemperatureTier tempMax,
                              HumidityTier humidityMin, HumidityTier humidityMax) {
        DiverseCritter critter = helper.spawn(type, new Vec3(5.5, 3, 5.5));
        helper.assertTrue(critter.comfortTierMin() == tempMin, name + " temperature min: " + critter.comfortTierMin());
        helper.assertTrue(critter.comfortTierMax() == tempMax, name + " temperature max: " + critter.comfortTierMax());
        helper.assertTrue(critter.comfortHumidityMin() == humidityMin, name + " humidity min: " + critter.comfortHumidityMin());
        helper.assertTrue(critter.comfortHumidityMax() == humidityMax, name + " humidity max: " + critter.comfortHumidityMax());
    }

    @GameTest(template = ARENA)
    public static void comfortRangesMatchTheQuote(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }

        check(helper, DOCEntities.SQUIRREL.get(), "squirrel",
                TemperatureTier.FREEZING, TemperatureTier.WARM, HumidityTier.ARID, HumidityTier.SATURATED);
        check(helper, DOCEntities.CIVET.get(), "civet",
                TemperatureTier.COOL, TemperatureTier.HOT, HumidityTier.MODERATE, HumidityTier.WET);
        check(helper, DOCEntities.SIFAKA.get(), "sifaka",
                TemperatureTier.MODERATE, TemperatureTier.HOT, HumidityTier.ARID, HumidityTier.MODERATE);

        helper.killAllEntities();
        helper.succeed();
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew runGameTestServer`
Expected: `comfortrangesmatchthequote failed! squirrel temperature min: COOL`.

- [ ] **Step 3: Cambiar los rangos**

`SquirrelEntity`: `comfortTierMin()` → `TemperatureTier.FREEZING`; `comfortHumidityMin()` →
`HumidityTier.ARID`; `comfortHumidityMax()` → `HumidityTier.SATURATED` (mantener el estilo de nombre
completo que ya usa el archivo).

`CivetEntity`: `comfortTierMin()` → `TemperatureTier.COOL`; `comfortHumidityMin()` →
`HumidityTier.MODERATE`.

`SifakaEntity`: `comfortTierMin()` → `TemperatureTier.MODERATE`; `comfortHumidityMin()` →
`HumidityTier.ARID` (nombre completo, como el archivo).

Los máximos que no aparecen aquí no cambian.

- [ ] **Step 4: Ver pasar los gametests y el build**

Run: `./gradlew runGameTestServer`
Expected: `All 20 required tests passed :)`

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/gametest/ComfortRangeGameTests.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SquirrelEntity.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SifakaEntity.java
git commit -m "Comfort ranges: squirrel, civet and sifaka ranges as the client asked"
```

### Task 2: Prueba en juego y guía

- [ ] Checklist en juego para el usuario; con su confirmación, sección del módulo 5 en la guía del
  cliente (https://claude.ai/artifact/3U5ZEiYMzyqd1KVUHafyCK).
