# Spawn chance system y liberación a la naturaleza — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que liberar un animal domesticado suba de forma permanente el ritmo al que su especie aparece de forma natural, con todo el sistema de spawn configurable por especie.

**Architecture:** un gate probabilístico solo puede **rechazar** spawns, y los pesos de los `biome_modifier` son datapack inmutable en caliente. Así que el peso del JSON se fija en el máximo y el gate rechaza para bajar hasta el nivel de población actual: liberar animales reduce el rechazo. El contador vive en un `SavedData` global con semántica de población salvaje desde el día uno, y cada animal lleva un booleano de si está contado — que es lo que hace de anti-farmeo sin mecanismo aparte.

**Tech Stack:** Minecraft 1.20.1, Forge 47.4.10, Parchment 2023.09.03, Java 17.

Spec: [2026-09-15-spawn-chance-design.md](../specs/2026-09-15-spawn-chance-design.md)

## Global Constraints

- **No hay tests automatizados en este repo.** `src/test/java` está vacío, no hay JUnit en `build.gradle` y no hay GameTest registrado. El ciclo de cada tarea es: **compilar → comprobación en juego con comandos**. No inventes un `src/test`; montar ese arnés es trabajo aparte.
- **Nunca ejecutar `gradlew clean`.** Rompe el `fg_cache` de ForgeGradle y produce errores engañosos que cuestan una tarde.
- **No hagas commits ni push.** El usuario los hace él. Los pasos de commit de este plan son puntos de parada para que él decida, no acciones tuyas.
- Compilación: `./gradlew compileJava --console=plain`. Cliente: `./gradlew runClient`.
- Comentarios y javadoc **en español**, como el resto del repo. Sin tildes dentro de literales de código Java para evitar problemas de encoding; en javadoc sí se usan.
- Los comandos del mod exigen permiso 2: en un mundo de un jugador basta activar trucos.
- `MODID` es `diversityofcritters`.
- **El Lion queda fuera de todo este módulo.** La entidad no está en uso.
- Las tres especies del sistema son civeta, sifaka y ardilla. Si añades una cuarta a `CritterSpecies`, hay que darle entrada de config, tag de bioma y `biome_modifier`, o el gate la rechazará entera.

---

## Estructura de archivos

**Se crean:**

| Archivo | Responsabilidad |
|---|---|
| `common/population/CritterSpecies.java` | Enum de las tres especies. Clave de config, clave de NBT y mapeo desde la entidad. |
| `common/population/WildPopulation.java` | `SavedData` global: cuántos animales de cada especie hay en libertad. No sabe nada de spawns. |
| `common/population/SpawnGate.java` | La única clase que conoce la fórmula. Traduce población a aceptación y resuelve el predicado. |
| `common/config/DOCConfig.java` | `ForgeConfigSpec` común. Infraestructura nueva: el mod no tiene config hoy. |
| `common/command/SpawnDebugCommand.java` | `/doc_spawn`: población, aceptación y motivo del último rechazo. |
| `data/.../tags/worldgen/biome/is_civet_biome.json` | Biomas de la civeta. |
| `data/.../tags/worldgen/biome/is_sifaka_biome.json` | Biomas del sifaka. |
| `data/.../tags/worldgen/biome/is_squirrel_biome.json` | Biomas de la ardilla. |
| `data/.../forge/biome_modifier/squirrel_spawns.json` | Spawn de la ardilla, que hoy no existe. |

**Se modifican:**

| Archivo | Cambio |
|---|---|
| `common/entity/custom/base/DiverseCritter.java` | Campo `countedInWild`, NBT, marcado en `finalizeSpawn`. |
| `common/entity/custom/CivetEntity.java` | `checkCivetSpawnRules` delega en el gate. |
| `common/entity/custom/SifakaEntity.java` | `checkSifakaSpawnRules` delega en el gate. |
| `common/entity/custom/SquirrelEntity.java` | **Crear** `checkSquirrelSpawnRules`; hoy no existe. |
| `DiversityOfCritters.java` | Registro de la config y `SpawnPlacements` de la ardilla, que falta. |
| `network/ReleaseCritterMsg.java` | Acreditar en `WildPopulation` al liberar. |
| `common/event/DOCForgeEvents.java` | Registro de `/doc_spawn`. |
| `data/.../forge/biome_modifier/entity/add_civet.json` | Apuntar al tag, que ahora sí existirá. |
| `data/.../forge/biome_modifier/sifaka_spawns.json` | Apuntar al tag. |

**Se borra:**

| Archivo | Por qué |
|---|---|
| `data/.../forge/biome_modifier/civet_spawns.json` | Duplicado del de la civeta. Se queda el de `entity/`, que ya usaba el patrón de tag. |

---

## Orden y por qué

La config va primero porque todo lo demás lee de ella: sin defaults cargados, el registro no sabe dónde clampar y el gate no tiene fórmula. Luego el registro, luego el estado en la entidad, y solo entonces el gate, que es el único que necesita las tres piezas anteriores.

El comando va **antes** que los ficheros de datos a propósito. Los JSON de bioma son la parte que más silenciosamente falla —un tag mal escrito no da error, simplemente no coincide con nada— y `/doc_spawn` es lo que convierte ese fallo mudo en un mensaje.

La verificación en juego va al final y entera, porque comprobar spawns exige volar a chunks nuevos y varias de las comprobaciones se hacen del tirón en el mismo viaje.

---

### Task 1: Enum de especies y config

**Files:**
- Create: `src/main/java/com/evirapo/diversityofcritters/common/population/CritterSpecies.java`
- Create: `src/main/java/com/evirapo/diversityofcritters/common/config/DOCConfig.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/DiversityOfCritters.java`

**Interfaces:**
- Consumes: nada.
- Produces: `CritterSpecies.values()`, `CritterSpecies.key()`, `CritterSpecies.of(Entity)`; `DOCConfig.SPAWN` (un `EnumMap<CritterSpecies, DOCConfig.SpeciesSpawn>`) y `DOCConfig.SPEC`. El record `SpeciesSpawn` expone `enabled()`, `baseAcceptance()`, `acceptancePerRelease()` y `populationCap()`.

- [ ] **Step 1: Crear el enum de especies**

```java
package com.evirapo.diversityofcritters.common.population;

import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Las especies que participan en el sistema de poblacion salvaje.
 *
 * <p>Se usa un enum y no un {@code ResourceLocation} porque la clave de config, la de NBT y el
 * mapeo desde la entidad cuelgan todas del mismo sitio, y porque asi una especie inexistente deja
 * de ser representable.
 *
 * <p>El leon no esta aqui: la entidad no esta en uso.
 */
public enum CritterSpecies {
    CIVET("civet"),
    SIFAKA("sifaka"),
    SQUIRREL("squirrel");

    private final String key;

    CritterSpecies(String key) {
        this.key = key;
    }

    /** Clave estable para config y NBT. No usar {@code name()}: cambiarlo romperia mundos guardados. */
    public String key() {
        return this.key;
    }

    /** Devuelve null para cualquier entidad que no participe en el sistema, el leon incluido. */
    @Nullable
    public static CritterSpecies of(Entity entity) {
        if (entity instanceof CivetEntity) return CIVET;
        if (entity instanceof SifakaEntity) return SIFAKA;
        if (entity instanceof SquirrelEntity) return SQUIRREL;
        return null;
    }
}
```

- [ ] **Step 2: Crear la config**

```java
package com.evirapo.diversityofcritters.common.config;

import com.evirapo.diversityofcritters.common.population.CritterSpecies;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * Config comun del mod. Es infraestructura nueva: antes de esto el mod no tenia ninguna.
 *
 * <p><b>Los valores por defecto encajan entre si:</b> {@code 0.25 + 15 * 0.05 = 1.0}, o sea que el
 * tope de poblacion es exactamente el punto donde la aceptacion llega al 100% y la especie aparece
 * al ritmo pleno de su {@code biome_modifier}. Cambiar uno de los tres sin ajustar los otros no
 * rompe nada —la formula clampa— pero el contador seguiria subiendo sin efecto visible por encima
 * del punto de saturacion.
 */
public final class DOCConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final Map<CritterSpecies, SpeciesSpawn> SPAWN = new EnumMap<>(CritterSpecies.class);

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Spawn natural y poblacion salvaje, por especie.").push("spawn");

        for (CritterSpecies species : CritterSpecies.values()) {
            BUILDER.push(species.key());
            SPAWN.put(species, new SpeciesSpawn(
                    BUILDER.comment("Si la especie aparece de forma natural.")
                            .define("enabled", true),
                    BUILDER.comment("Aceptacion con poblacion cero. 0.25 = se acepta 1 de cada 4 intentos.")
                            .defineInRange("baseAcceptance", 0.25D, 0.0D, 1.0D),
                    BUILDER.comment("Aceptacion que suma cada animal liberado.")
                            .defineInRange("acceptancePerRelease", 0.05D, 0.0D, 1.0D),
                    BUILDER.comment("Tope del contador de poblacion salvaje.")
                            .defineInRange("populationCap", 15, 0, 10000)));
            BUILDER.pop();
        }

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public record SpeciesSpawn(ForgeConfigSpec.BooleanValue enabled,
                               ForgeConfigSpec.DoubleValue baseAcceptance,
                               ForgeConfigSpec.DoubleValue acceptancePerRelease,
                               ForgeConfigSpec.IntValue populationCap) {}

    private DOCConfig() {}
}
```

- [ ] **Step 3: Registrar la config en la clase principal**

En el constructor de `DiversityOfCritters`, junto al resto de registros y **antes** de `MinecraftForge.EVENT_BUS.register(this)`:

```java
ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DOCConfig.SPEC);
```

Imports nuevos en esa clase:

```java
import com.evirapo.diversityofcritters.common.config.DOCConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
```

- [ ] **Step 4: Compilar**

Ejecuta: `./gradlew compileJava --console=plain`
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Comprobar que el fichero de config se genera**

Ejecuta: `./gradlew runClient`, crea o abre un mundo, cierra el juego.
Esperado: existe `run/config/diversityofcritters-common.toml` con una sección `[spawn.civet]`, otra `[spawn.sifaka]` y otra `[spawn.squirrel]`, cada una con las cuatro claves y sus defaults.

Si el fichero no aparece, el registro del paso 3 no se está ejecutando: comprueba que está en el constructor y no dentro de `commonSetup`.

- [ ] **Step 6: Punto de commit**

Sugerencia de mensaje: `feat(spawn): enum de especies y config comun`

---

### Task 2: Registro de población salvaje

**Files:**
- Create: `src/main/java/com/evirapo/diversityofcritters/common/population/WildPopulation.java`

**Interfaces:**
- Consumes: `CritterSpecies` y `DOCConfig.SPAWN` de la Task 1.
- Produces: `WildPopulation.get(MinecraftServer)`, y sobre la instancia `get(CritterSpecies)` y `credit(CritterSpecies)`.

- [ ] **Step 1: Crear el SavedData**

```java
package com.evirapo.diversityofcritters.common.population;

import com.evirapo.diversityofcritters.common.config.DOCConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cuantos animales de cada especie hay en libertad gracias al jugador.
 *
 * <p><b>Ambito global.</b> Va colgado del overworld y vale para todas las dimensiones: una especie
 * no esta mas o menos presente en el Nether segun lo que hayas soltado alli.
 *
 * <p><b>No cuenta los spawns naturales.</b> Esos son la linea base, que ya esta representada por el
 * peso del {@code biome_modifier}. Contarlos seria realimentacion circular: mas spawns produciendo
 * mas spawns.
 *
 * <p>Esta clase no sabe nada de spawns ni de entidades, y esa ignorancia es deliberada: es lo que
 * permite que el modulo 2b le cuelgue ganchos nuevos —domesticar, nacer, morir— sin tocarla.
 */
public class WildPopulation extends SavedData {

    private static final String DATA_NAME = "diversityofcritters_wild_population";

    private final Map<CritterSpecies, Integer> counts = new EnumMap<>(CritterSpecies.class);

    public WildPopulation() {}

    public static WildPopulation get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WildPopulation::load, WildPopulation::new, DATA_NAME);
    }

    public static WildPopulation load(CompoundTag tag) {
        WildPopulation data = new WildPopulation();
        for (CritterSpecies species : CritterSpecies.values()) {
            if (tag.contains(species.key())) {
                data.counts.put(species, tag.getInt(species.key()));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<CritterSpecies, Integer> entry : this.counts.entrySet()) {
            tag.putInt(entry.getKey().key(), entry.getValue());
        }
        return tag;
    }

    public int get(CritterSpecies species) {
        return this.counts.getOrDefault(species, 0);
    }

    /** Suma uno, clampado al tope de la config. */
    public void credit(CritterSpecies species) {
        set(species, get(species) + 1);
    }

    /**
     * Unico camino de escritura. El clampeo se hace <b>al escribir</b> y no al leer, para que bajar
     * el tope en la config no deje valores guardados fuera de rango esperando a sorprender a nadie.
     *
     * <p>El modulo 2b anadira aqui un {@code debit} simetrico; el suelo ya esta puesto en 0.
     */
    private void set(CritterSpecies species, int value) {
        int cap = DOCConfig.SPAWN.get(species).populationCap().get();
        int clamped = Math.max(0, Math.min(cap, value));
        if (clamped != get(species)) {
            this.counts.put(species, clamped);
            this.setDirty();
        }
    }
}
```

- [ ] **Step 2: Compilar**

Ejecuta: `./gradlew compileJava --console=plain`
Esperado: `BUILD SUCCESSFUL`.

No hay comprobación en juego en esta tarea: nada llama todavía a `credit`, así que el registro no tiene forma de mostrar nada. Se verifica en la Task 6, cuando liberar empiece a acreditarlo.

- [ ] **Step 3: Punto de commit**

Sugerencia de mensaje: `feat(spawn): registro persistente de poblacion salvaje`

---

### Task 3: Marca de población en la entidad

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java`

**Interfaces:**
- Consumes: nada de tareas anteriores.
- Produces: `DiverseCritter.isCountedInWild()` y `DiverseCritter.setCountedInWild(boolean)`.

- [ ] **Step 1: Añadir el campo y sus accesos**

Junto al resto de campos de estado de `DiverseCritter`:

```java
/**
 * Si este animal cuenta dentro de la poblacion salvaje de su especie.
 *
 * <p>Solo servidor y sin sincronizar: no se dibuja nada a partir de el.
 *
 * <p><b>Esto es el anti-farmeo.</b> Un animal salvaje nace con la marca puesta, asi que
 * domesticarlo y volver a soltarlo no da credito —el bucle no produce nada—. Una cria nacida en
 * cautividad nace sin ella, asi que soltarla si cuenta: ese es el programa de repoblacion, y el
 * tope de la config es lo que impide que sea infinito.
 */
private boolean countedInWild = false;

public boolean isCountedInWild() { return this.countedInWild; }

public void setCountedInWild(boolean value) { this.countedInWild = value; }
```

- [ ] **Step 2: Persistirlo en NBT**

En `addAdditionalSaveData`, junto a `TempComfort` y `HumidityComfort`:

```java
pCompound.putBoolean("CountedInWild", this.countedInWild);
```

En `readAdditionalSaveData`, en el mismo bloque:

```java
if (pCompound.contains("CountedInWild")) this.countedInWild = pCompound.getBoolean("CountedInWild");
```

Los animales de mundos anteriores a este cambio no tienen la clave y quedan en `false`. Es el valor correcto: sin registro previo no hay nada que decir, y lo peor que puede pasar es que el jugador gane un crédito por soltar un animal que ya tenía.

- [ ] **Step 3: Marcar los spawns naturales**

Dentro de `finalizeSpawn`, después de la llamada a `super.finalizeSpawn(...)`:

```java
// Los que aparecen solos ya son parte de la poblacion salvaje. No incrementan el contador —esa es
// la linea base, que ya representa el peso del biome_modifier— pero si llevan la marca, y eso es
// lo que impide que domesticarlos y soltarlos de credito.
if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
    this.countedInWild = true;
}
```

`MobSpawnType` ya está importado en esta clase.

- [ ] **Step 4: Compilar**

Ejecuta: `./gradlew compileJava --console=plain`
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Punto de commit**

Sugerencia de mensaje: `feat(spawn): marca countedInWild en DiverseCritter`

---

### Task 4: El gate de spawn

**Files:**
- Create: `src/main/java/com/evirapo/diversityofcritters/common/population/SpawnGate.java`

**Interfaces:**
- Consumes: `CritterSpecies`, `DOCConfig.SPAWN`, `WildPopulation.get(MinecraftServer)`.
- Produces: `SpawnGate.allow(CritterSpecies, LevelAccessor, MobSpawnType, RandomSource)`, `SpawnGate.acceptance(CritterSpecies, MinecraftServer)`, `SpawnGate.lastRejection(CritterSpecies)`.

- [ ] **Step 1: Crear el gate**

```java
package com.evirapo.diversityofcritters.common.population;

import com.evirapo.diversityofcritters.common.config.DOCConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Traduce poblacion salvaje a probabilidad de spawn. Es la unica clase que conoce la formula.
 *
 * <p><b>Por que el gate solo puede restar.</b> {@code SpawnPlacements.checkSpawnRules} es un
 * predicado: rechaza intentos, nunca crea uno extra. Y los pesos de los {@code biome_modifier} son
 * datapack, inmutables en caliente. Asi que el peso del JSON se fija en el maximo —el ritmo de una
 * poblacion prospera— y esto rechaza para bajarlo hasta el nivel actual. Liberar animales reduce el
 * rechazo, y ese es el efecto que se pidio, conseguido por sustraccion.
 *
 * <p><b>Sin cache, a proposito.</b> Leer la tasa es un {@code Map.get} sobre el {@code DataStorage}
 * mas dos operaciones. Cachearla anadiria una ruta de invalidacion y con ella el bug mas dificil de
 * ver aqui: un numero correcto en el registro y uno viejo decidiendo los spawns.
 */
public final class SpawnGate {

    /** Ultimo motivo de rechazo por especie. Existe solo para que /doc_spawn pueda contarlo. */
    private static final Map<CritterSpecies, String> LAST_REJECTION =
            new EnumMap<>(CritterSpecies.class);

    /**
     * El predicado que consumen los {@code checkXSpawnRules} de las tres especies.
     *
     * <p><b>Solo actua sobre {@code NATURAL}.</b> Cualquier otro tipo pasa sin tocar: si no,
     * bloquearia huevos de spawn, cria y {@code /summon}, y el jugador veria fallar cosas que no
     * tienen nada que ver con la poblacion salvaje. Es el fallo mas facil de colar aqui y el peor
     * de diagnosticar.
     */
    public static boolean allow(CritterSpecies species, LevelAccessor level,
                                MobSpawnType type, RandomSource random) {
        if (type != MobSpawnType.NATURAL) return true;
        if (!(level instanceof ServerLevelAccessor serverLevel)) return true;

        DOCConfig.SpeciesSpawn config = DOCConfig.SPAWN.get(species);

        if (!config.enabled().get()) {
            LAST_REJECTION.put(species, "desactivado en la config");
            return false;
        }

        double acceptance = acceptance(species, serverLevel.getLevel().getServer());

        if (random.nextDouble() >= acceptance) {
            LAST_REJECTION.put(species, String.format(
                    "sorteo fallido (aceptacion %.0f%%)", acceptance * 100.0D));
            return false;
        }

        return true;
    }

    /** Lineal con tope: aceptacion = min(base + incremento * poblacion, 1.0). */
    public static double acceptance(CritterSpecies species, MinecraftServer server) {
        DOCConfig.SpeciesSpawn config = DOCConfig.SPAWN.get(species);
        int population = WildPopulation.get(server).get(species);
        return Math.min(1.0D,
                config.baseAcceptance().get() + config.acceptancePerRelease().get() * population);
    }

    public static String lastRejection(CritterSpecies species) {
        return LAST_REJECTION.getOrDefault(species, "ninguno todavia");
    }

    private SpawnGate() {}
}
```

- [ ] **Step 2: Compilar**

Ejecuta: `./gradlew compileJava --console=plain`
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Punto de commit**

Sugerencia de mensaje: `feat(spawn): gate probabilistico de spawn natural`

---

### Task 5: Conectar las tres especies

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java:822`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SifakaEntity.java:893`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SquirrelEntity.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/DiversityOfCritters.java`

**Interfaces:**
- Consumes: `SpawnGate.allow(...)` y `CritterSpecies` de las tareas anteriores.
- Produces: `SquirrelEntity.checkSquirrelSpawnRules(...)`, con la misma firma que las otras dos.

- [ ] **Step 1: Delegar en el gate desde la civeta**

Sustituye el cuerpo de `checkCivetSpawnRules`, que hoy es `return true`:

```java
public static boolean checkCivetSpawnRules(EntityType<CivetEntity> t, LevelAccessor lvl, MobSpawnType type, BlockPos pos, RandomSource rnd) {
    return SpawnGate.allow(CritterSpecies.CIVET, lvl, type, rnd);
}
```

Imports nuevos:

```java
import com.evirapo.diversityofcritters.common.population.CritterSpecies;
import com.evirapo.diversityofcritters.common.population.SpawnGate;
```

- [ ] **Step 2: Lo mismo en el sifaka**

```java
public static boolean checkSifakaSpawnRules(EntityType<SifakaEntity> t, LevelAccessor lvl, MobSpawnType type, BlockPos pos, RandomSource rnd) {
    return SpawnGate.allow(CritterSpecies.SIFAKA, lvl, type, rnd);
}
```

Con los mismos dos imports.

- [ ] **Step 3: Crear el método de la ardilla, que no existe**

Añádelo a `SquirrelEntity`, junto a `getBreedOffspring` para seguir la colocación de las otras dos clases:

```java
public static boolean checkSquirrelSpawnRules(EntityType<SquirrelEntity> t, LevelAccessor lvl, MobSpawnType type, BlockPos pos, RandomSource rnd) {
    return SpawnGate.allow(CritterSpecies.SQUIRREL, lvl, type, rnd);
}
```

Imports, que pueden faltar varios en esta clase:

```java
import com.evirapo.diversityofcritters.common.population.CritterSpecies;
import com.evirapo.diversityofcritters.common.population.SpawnGate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
```

- [ ] **Step 4: Registrar el SpawnPlacements de la ardilla**

En `commonSetup`, junto a los dos que ya están:

```java
SpawnPlacements.register(DOCEntities.SQUIRREL.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SquirrelEntity::checkSquirrelSpawnRules);
```

Sin esta línea la ardilla no aparece de forma natural por mucho `biome_modifier` que tenga: hoy le falta, y es una de las razones por las que no spawnea.

Import nuevo si no está:

```java
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
```

- [ ] **Step 5: Compilar**

Ejecuta: `./gradlew compileJava --console=plain`
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Punto de commit**

Sugerencia de mensaje: `feat(spawn): conectar el gate a las tres especies`

---

### Task 6: Acreditar al liberar

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/network/ReleaseCritterMsg.java`

**Interfaces:**
- Consumes: `CritterSpecies.of(Entity)`, `WildPopulation.get(MinecraftServer).credit(...)`, `DiverseCritter.isCountedInWild()` / `setCountedInWild(boolean)`.
- Produces: nada que consuman tareas posteriores.

- [ ] **Step 1: Acreditar dentro del handler**

En `handle`, dentro del bloque `if (e instanceof TamableAnimal ta && ta.isOwnedBy(player))`, justo **antes** del `player.displayClientMessage(...)` que ya está:

```java
// Solo cuenta si este animal no estaba ya en la poblacion salvaje. Un salvaje domesticado
// conserva la marca, asi que soltarlo no da credito: ahi es donde muere el bucle de farmeo.
// Una cria nacida en cautividad si la da, que es el programa de repoblacion.
if (ta instanceof DiverseCritter critter && !critter.isCountedInWild()) {
    CritterSpecies species = CritterSpecies.of(critter);
    if (species != null) {
        WildPopulation.get(player.server).credit(species);
        critter.setCountedInWild(true);
    }
}
```

Imports nuevos:

```java
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.population.CritterSpecies;
import com.evirapo.diversityofcritters.common.population.WildPopulation;
```

- [ ] **Step 2: Compilar**

Ejecuta: `./gradlew compileJava --console=plain`
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Punto de commit**

Sugerencia de mensaje: `feat(spawn): liberar acredita en la poblacion salvaje`

---

### Task 7: Comando `/doc_spawn`

**Files:**
- Create: `src/main/java/com/evirapo/diversityofcritters/common/command/SpawnDebugCommand.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/event/DOCForgeEvents.java:39`

**Interfaces:**
- Consumes: `SpawnGate.acceptance(...)`, `SpawnGate.lastRejection(...)`, `WildPopulation.get(...)`, `DOCConfig.SPAWN`.
- Produces: `SpawnDebugCommand.register(CommandDispatcher<CommandSourceStack>)`.

- [ ] **Step 1: Crear el comando**

```java
package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.config.DOCConfig;
import com.evirapo.diversityofcritters.common.population.CritterSpecies;
import com.evirapo.diversityofcritters.common.population.SpawnGate;
import com.evirapo.diversityofcritters.common.population.WildPopulation;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /doc_spawn}: poblacion salvaje, aceptacion resultante y motivo del ultimo rechazo.
 *
 * <p>Sin esto, probar spawns es a ciegas: hay que volar a chunks nuevos y esperar, sin saber si algo
 * no aparece porque el gate lo rechazo, porque el bioma no coincide o porque el sitio no valia. Las
 * tres cosas se ven exactamente igual desde dentro del juego.
 */
public class SpawnDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_spawn")
                .requires(source -> source.hasPermission(2))
                .executes(context -> run(context.getSource())));
    }

    private static int run(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        WildPopulation population = WildPopulation.get(server);

        source.sendSuccess(() -> Component.literal("[Spawn] Poblacion salvaje:"), false);

        for (CritterSpecies species : CritterSpecies.values()) {
            DOCConfig.SpeciesSpawn config = DOCConfig.SPAWN.get(species);
            int count = population.get(species);
            int cap = config.populationCap().get();
            double acceptance = SpawnGate.acceptance(species, server) * 100.0D;
            String enabled = config.enabled().get() ? "" : " [DESACTIVADO]";

            String line = String.format("  %s: %d/%d  aceptacion %.0f%%%s  ultimo rechazo: %s",
                    species.key(), count, cap, acceptance, enabled,
                    SpawnGate.lastRejection(species));

            source.sendSuccess(() -> Component.literal(line), false);
        }

        return 1;
    }
}
```

- [ ] **Step 2: Registrar el comando**

En `DOCForgeEvents.onCommandsRegister`, junto a los cinco que ya hay:

```java
SpawnDebugCommand.register(event.getDispatcher());
```

Import nuevo:

```java
import com.evirapo.diversityofcritters.common.command.SpawnDebugCommand;
```

- [ ] **Step 3: Compilar**

Ejecuta: `./gradlew compileJava --console=plain`
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Comprobar el comando en juego**

Ejecuta: `./gradlew runClient`, mundo creativo con trucos, `/doc_spawn`.
Esperado: tres líneas, una por especie, todas a `0/15` y aceptación `25%`.

Si la aceptación sale `100%`, la config no se está leyendo y `baseAcceptance` está viniendo de un default distinto al del fichero: comprueba la Task 1.

- [ ] **Step 5: Punto de commit**

Sugerencia de mensaje: `feat(spawn): comando /doc_spawn`

---

### Task 8: Tags de bioma y limpieza de los biome_modifier

**Files:**
- Create: `src/main/resources/data/diversityofcritters/tags/worldgen/biome/is_civet_biome.json`
- Create: `src/main/resources/data/diversityofcritters/tags/worldgen/biome/is_sifaka_biome.json`
- Create: `src/main/resources/data/diversityofcritters/tags/worldgen/biome/is_squirrel_biome.json`
- Create: `src/main/resources/data/diversityofcritters/forge/biome_modifier/squirrel_spawns.json`
- Modify: `src/main/resources/data/diversityofcritters/forge/biome_modifier/entity/add_civet.json`
- Modify: `src/main/resources/data/diversityofcritters/forge/biome_modifier/sifaka_spawns.json`
- Delete: `src/main/resources/data/diversityofcritters/forge/biome_modifier/civet_spawns.json`

**Interfaces:**
- Consumes: nada de código.
- Produces: nada de código. Los pesos de estos ficheros son el **techo** que el gate rebaja.

**Contexto:** hoy la civeta tiene dos ficheros de spawn. `civet_spawns.json` apunta a `#minecraft:is_jungle` con peso 80 y funciona; `entity/add_civet.json` apunta a `#diversityofcritters:is_civet_biome` con peso 6 y **ese tag no existe**, así que no coincide con nada. Esta tarea deja un solo fichero por especie, todos con el patrón de tag propio.

- [ ] **Step 1: Crear los tres tags de bioma**

`is_civet_biome.json`:

```json
{
  "values": [
    "#minecraft:is_jungle"
  ]
}
```

`is_sifaka_biome.json`:

```json
{
  "values": [
    "#minecraft:is_savanna"
  ]
}
```

`is_squirrel_biome.json`:

```json
{
  "values": [
    "#minecraft:is_forest",
    "#minecraft:is_taiga"
  ]
}
```

Bosque templado y taiga encajan con la ardilla voladora gigante real y con su rango de temperatura Freezing–Warm.

- [ ] **Step 2: Corregir el biome_modifier de la civeta**

Reescribe `entity/add_civet.json` entero. El peso pasa de 6 a 80 —el 6 era de un intento a medias que nunca llegó a aplicarse, y el 80 es el que estaba realmente en uso—:

```json
{
  "type": "forge:add_spawns",
  "biomes": "#diversityofcritters:is_civet_biome",
  "spawners": [
    {
      "type": "diversityofcritters:civet",
      "weight": 80,
      "minCount": 2,
      "maxCount": 4
    }
  ]
}
```

- [ ] **Step 3: Borrar el duplicado**

Borra `src/main/resources/data/diversityofcritters/forge/biome_modifier/civet_spawns.json`.

Si se dejara, la civeta se añadiría dos veces a la jungla y el ritmo real sería el doble del que dice cualquiera de los dos ficheros.

- [ ] **Step 4: Apuntar el sifaka a su tag**

```json
{
  "type": "forge:add_spawns",
  "biomes": "#diversityofcritters:is_sifaka_biome",
  "spawners": [
    {
      "type": "diversityofcritters:sifaka",
      "weight": 60,
      "minCount": 2,
      "maxCount": 4
    }
  ]
}
```

- [ ] **Step 5: Crear el de la ardilla**

`squirrel_spawns.json`:

```json
{
  "type": "forge:add_spawns",
  "biomes": "#diversityofcritters:is_squirrel_biome",
  "spawners": [
    {
      "type": "diversityofcritters:squirrel",
      "weight": 70,
      "minCount": 2,
      "maxCount": 4
    }
  ]
}
```

- [ ] **Step 6: Comprobar que los datapacks cargan sin error**

Ejecuta: `./gradlew runClient`, crea un mundo **nuevo**, y revisa el log.
Esperado: ningún error de datapack ni de tag que mencione `diversityofcritters`.

Un tag mal escrito **no rompe el arranque**: simplemente no coincide con ningún bioma y la especie no aparece nunca. Por eso hay que mirar el log en vez de fiarse de que el juego arrancó.

- [ ] **Step 7: Punto de commit**

Sugerencia de mensaje: `feat(spawn): tags de bioma propios y limpieza de biome_modifier`

---

### Task 9: Verificación en juego

**Files:** ninguno. Esta tarea no escribe código.

Es la comprobación que el usuario hace a mano. Las seis pruebas se hacen del tirón en el mismo mundo porque todas exigen volar a chunks sin generar.

Prepara un mundo **nuevo** en supervivencia con trucos activados.

- [ ] **Step 1: Población cero rinde menos que el tope**

Vuela a jungla sin generar y cuenta civetas durante un rato. Luego pon `populationCap` y la población al máximo —la forma rápida es subir `baseAcceptance` a `1.0` en el TOML y reiniciar— y repite en otra zona sin generar.
Esperado: notablemente más civetas en el segundo caso. Es una medida cualitativa; no busques un número exacto.

- [ ] **Step 2: Soltar un salvaje domesticado NO sube el contador**

`/doc_spawn` y apunta la cifra. Domestica una civeta salvaje, ábrele la pantalla de stats, pulsa el botón de liberar, y `/doc_spawn` otra vez.
Esperado: **la misma cifra**. Este es el anti-farmeo; si sube, la marca de `finalizeSpawn` de la Task 3 no se está poniendo.

- [ ] **Step 3: Soltar una cría nacida en cautividad SÍ lo sube**

Cría dos civetas domesticadas, espera a que nazca la cría, domestícala, y libérala. `/doc_spawn`.
Esperado: **+1**.

- [ ] **Step 4: El contador sobrevive a recargar**

Sal al menú principal y vuelve a entrar al mundo. `/doc_spawn`.
Esperado: la misma cifra que antes de salir.

- [ ] **Step 5: Huevos, cría y `/summon` siguen funcionando con población cero**

En una zona con población 0 para esa especie: usa un huevo de spawn, haz `/summon diversityofcritters:squirrel`, y cría dos animales domesticados.
Esperado: **las tres cosas funcionan**. Si alguna falla, el filtro por `MobSpawnType` de la Task 4 no está bien: es el fallo más fácil de colar aquí y el que peor se diagnostica, porque el jugador ve romperse algo que no tiene nada que ver con spawns naturales.

- [ ] **Step 6: Los biomas nuevos funcionan**

Vuela a bosque y a taiga sin generar y busca ardillas. Vuelve a jungla y busca civetas.
Esperado: aparecen ardillas en los dos biomas nuevos, y las civetas siguen apareciendo en jungla después de haber borrado `civet_spawns.json`.

- [ ] **Step 7: Punto de commit**

Si todo pasa, el módulo está listo. Si algo falla, `/doc_spawn` dice cuál de las tres causas posibles fue: gate, bioma o sitio.
