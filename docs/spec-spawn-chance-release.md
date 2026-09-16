# Plan de desarrollo — Spawn Chance System + Release-to-Wild

**Ámbito:** Civet, Sifaka y Squirrel (Lion queda fuera).
**Versión objetivo:** Minecraft 1.20.1 / Forge 47.4.10 / mappings Parchment 2023.09.03.
**Estimación total:** ~11.5 h de desarrollo + pruebas.

## Estado de implementación

| Fase | Estado |
|---|---|
| 0 — Limpieza previa | ✅ Hecho |
| 1 — Infraestructura de config | ✅ Hecho |
| 2 — Motor de reglas de spawn | ✅ Hecho |
| 3 — Alta del Squirrel | ✅ Hecho |
| 4 — `ReleaseRegistry` | ✅ Hecho |
| 5 — Botón Release + anti-farmeo | ✅ Hecho |
| 6 — Fórmula del bono (integración) | ✅ Hecho |
| 7 — Comando `/doc_spawn` | ✅ Hecho |
| 8 — Pruebas y lang | 🟡 Lang hecho, pruebas en juego pendientes |

Compila (`gradlew compileJava`) y el datagen genera los tags correctamente (`gradlew runData`).
Fases 0-3 validadas en juego. Fases 4-7 sin probar todavía.

---

## 0. Estado actual del repositorio (auditoría previa)

| Punto | Estado hoy | Archivo |
|---|---|---|
| Sistema de config | **No existe.** Ni `ForgeConfigSpec`, ni carpeta `config/`, ni parser propio. | — |
| Reglas de spawn Civet | Placeholder: `return true;` | `common/entity/custom/CivetEntity.java:870` |
| Reglas de spawn Sifaka | Placeholder: `return true;` | `common/entity/custom/SifakaEntity.java:867` |
| Reglas de spawn Squirrel | Placeholder: `return true;` y **nunca se llama** | `common/entity/custom/SquirrelEntity.java:981` |
| `SpawnPlacements` | Solo Civet y Sifaka registrados. **Squirrel ausente.** | `DiversityOfCritters.java:55-56` |
| Biome modifiers | `civet_spawns.json`, `sifaka_spawns.json` y un **duplicado huérfano** `entity/add_civet.json` | `data/diversityofcritters/forge/biome_modifier/` |
| Tags de bioma | Solo `is_civet_biome`, y `civet_spawns.json` **ni siquiera lo usa** (usa `#minecraft:is_jungle` directo) | `misc/tags/DoCTags.java`, `datagen/providers/DoCBiomeTagGenerator.java` |
| Botón Release | Existe en las 3 pantallas, envía `ReleaseCritterMsg` | `client/screen/{Civet,Sifaka,Squirrel}StatScreen.java` |
| Handler de Release | Solo hace `setTame(false)` + `setOwnerUUID(null)` + `setPersistenceRequired()`. **No toca ningún contador.** | `network/ReleaseCritterMsg.java:38-56` |
| Persistencia de mundo | **No existe ninguna `SavedData`** en todo el mod. | — |

### Deuda técnica a corregir de paso

1. **Doble biome modifier para Civet.** `civet_spawns.json` (weight 80, `#minecraft:is_jungle`) y `entity/add_civet.json` (weight 6, `#diversityofcritters:is_civet_biome`) se aplican **los dos**, sumando dos entradas de spawner para la misma entidad. Hay que eliminar uno.
2. **Los animales liberados nunca desaparecen.** `ReleaseCritterMsg.java:42` llama a `setPersistenceRequired()`, pero el problema es más profundo de lo que parece. Verificado contra el código desobfuscado de vanilla:
   - `Mob.persistenceRequired` es **privado** (`Mob.java:106`) y solo existe `setPersistenceRequired()`. **No hay forma pública de devolverlo a `false`** sin un Access Transformer.
   - `Animal.removeWhenFarAway(double)` devuelve **`false`**. Como `DiverseCritter -> TamableAnimal -> Animal`, estos bichos **no despawnean nunca, con flag o sin flag**.

   Es decir: **quitar la llamada no arregla nada por sí solo.** El arreglo real es sobreescribir `removeWhenFarAway` en `DiverseCritter`. Ver Fase 5.
3. **`is_civet_biome` sin usar.** El tag existe en datagen pero el JSON lo ignora. Unificar todo bajo tags propios.

---

## 1. Arquitectura propuesta

### Paquetes nuevos

```
com.evirapo.diversityofcritters.common.config
    DOCConfig.java              <- registro de specs, listeners de carga/recarga
    DOCServerConfig.java        <- ForgeConfigSpec SERVER + subsecciones por especie
    SpeciesSpawnSettings.java   <- agrupa los ConfigValue de una especie

com.evirapo.diversityofcritters.common.spawn
    DOCSpecies.java             <- enum: CIVET, SIFAKA, SQUIRREL
    SpawnRuleEngine.java        <- evaluación centralizada de reglas
    SpawnCheckResult.java       <- record con veredicto + motivo + diagnóstico
    SpawnRejectReason.java      <- enum de motivos de rechazo
    ReleaseRegistry.java        <- SavedData persistente (contador global + UUIDs)
    SpawnBonusFormula.java      <- cálculo del multiplicador acumulado

com.evirapo.diversityofcritters.common.command
    SpawnDebugCommand.java      <- /doc_spawn
```

### Flujo de spawn

```
NaturalSpawner (vanilla)
  -> SpawnPlacements.checkSpawnRules(SPECIES, level, type, pos, rnd)
       -> XxxEntity.checkXxxSpawnRules(...)      // hoy: return true
            -> SpawnRuleEngine.evaluate(DOCSpecies.XXX, ...)
                 |- lee DOCServerConfig (enabled, Y, luz, cielo, grupo)
                 |- lee ReleaseRegistry.getCount(species) del mundo
                 |- SpawnBonusFormula.finalChance(base, released)
                 -> SpawnCheckResult{allowed, reason, chanceBase, bonus, chanceFinal}
                      -> (si trace ON) ring buffer para /doc_spawn why
```

### Flujo de liberación

```
Jugador pulsa Release en la pantalla de stats
  -> ReleaseCritterMsg (cliente -> servidor)
       -> validaciones anti-farmeo
            |- ¿es una de las 3 especies soportadas?
            |- ¿el jugador es el dueño?  (ya existe)
            |- ¿el animal NO tiene la flag NBT `DOCWasReleased`?
            |- ¿su UUID NO está en ReleaseRegistry?
            -> si pasa todo:
                 |- ReleaseRegistry.register(species, uuid) -> nuevo count
                 |- entidad.setWasReleased(true)   [NBT persistente]
                 |- setTame(false), setOwnerUUID(null), quitar persistencia
                 -> feedback al jugador: contador + % de bono actual
```

---

## 2. Fase 1 — Infraestructura de configuración (1.5 h)

**Tipo:** `ForgeConfigSpec` de tipo **SERVER**. Se guarda en `saves/<mundo>/serverconfig/diversityofcritters-server.toml`, se sincroniza automáticamente al cliente en multijugador y permite valores distintos por mundo/modpack.

### TOML generado

```toml
[spawn]
    # Bono de spawn por animal liberado (multiplicador aditivo).
    bonusPerRelease = 0.05
    # Tope del bono acumulado. 2.0 = como máximo el triple del chance base.
    maxBonus = 2.0
    # Tope duro del contador de liberados por especie.
    releaseCap = 50
    # Radio en bloques para el conteo de densidad de grupo.
    groupCheckRadius = 24
    # Si true, un animal liberado puede despawnear como fauna salvaje.
    # Si false, se queda en el mundo para siempre (comportamiento pasivo vanilla).
    releasedCanDespawn = true

    [spawn.civet]
        enabled = true
        # Probabilidad base de que un intento de spawn sea aceptado (0.0 - 1.0).
        spawnChance = 0.35
        groupMin = 2
        groupMax = 4
        minY = 55
        maxY = 140
        # Nivel máximo de luz de bloque permitido (0-15).
        maxBlockLight = 7
        # NONE | REQUIRED | FORBIDDEN
        skyAccess = "NONE"

    [spawn.sifaka]
        ...  # mismas claves, valores propios

    [spawn.squirrel]
        ...  # mismas claves, valores propios
```

### Detalles de implementación

- `SpeciesSpawnSettings` construye las 9 claves dentro de un `builder.push(nombreEspecie)` / `builder.pop()`, para no repetir el bloque tres veces.
- Rangos validados en el propio spec (`defineInRange`) para que un TOML editado a mano no tire el servidor.
- `skyAccess` como enum (`defineEnum`) en vez de booleano: permite "da igual", "tiene que ver el cielo" y "tiene que estar bajo dosel".
- `DOCConfig.register()` se llama desde el constructor de `DiversityOfCritters`, con `ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC)`.
- **Importante:** los valores de config SERVER **no están disponibles en `commonSetup`** (se cargan al abrir el mundo). Por eso el registro de `SpawnPlacements` NO puede consultar la config; todo el gating vive dentro de `checkSpawnRules`, que se ejecuta con el mundo ya cargado.
- Cachear los valores en un `record` inmutable al recibir `ModConfigEvent.Loading` / `Reloading`, para evitar el coste de `ConfigValue.get()` en un hot path que se llama cientos de veces por tick.

---

## 3. Fase 2 — Motor de reglas de spawn (2 h)

### `SpawnRejectReason` (enum)

| Valor | Significado |
|---|---|
| `PASSED` | Spawn aceptado |
| `DISABLED` | `enabled = false` en config |
| `WRONG_DIMENSION` | No es el Overworld |
| `Y_OUT_OF_RANGE` | Fuera de `[minY, maxY]` |
| `TOO_BRIGHT` | Luz de bloque > `maxBlockLight` |
| `SKY_REQUIRED` | `skyAccess = REQUIRED` y el bloque no ve el cielo |
| `SKY_FORBIDDEN` | `skyAccess = FORBIDDEN` y el bloque ve el cielo |
| `NO_VALID_GROUND` | El bloque de abajo no es válido para spawn |
| `NO_TREES_NEARBY` | (solo Squirrel) sin bloques trepables en el radio corto |
| `GROUP_LIMIT` | Ya hay `>= groupMax` de la especie en `groupCheckRadius` |
| `CHANCE_ROLL_FAILED` | Pasó todos los filtros pero falló la tirada de probabilidad |
| `BYPASS_EXPLICIT` | Huevo/spawner/comando: se acepta sin gate |

### `SpawnCheckResult` (record)

```java
public record SpawnCheckResult(
        boolean allowed,
        SpawnRejectReason reason,
        double chanceBase,
        double bonus,
        double chanceFinal,
        int releasedCount,
        BlockPos pos
) {}
```

### Orden de evaluación

Barato -> caro, cortocircuitando en el primer fallo:

1. Si `MobSpawnType` es `SPAWN_EGG`, `SPAWNER`, `COMMAND`, `BUCKET` o `EVENT` -> `BYPASS_EXPLICIT` inmediato. **Nunca bloqueamos spawns explícitos del jugador ni el `getBreedOffspring`.**
2. `enabled`.
3. Dimensión.
4. Rango Y.
5. Luz de bloque / acceso al cielo.
6. Suelo válido (reusando `checkAnimalSpawnRules` de vanilla como base).
7. Requisito específico de especie (árboles cercanos para el Squirrel).
8. Conteo de densidad de grupo (`getEntitiesOfClass` en un AABB de `groupCheckRadius`) — **el paso más caro, por eso va el último antes de la tirada**.
9. Tirada de probabilidad con el chance final (Fase 6).

### Reglas específicas por especie

- **Civet** — nocturno/arbóreo de jungla: `maxBlockLight` bajo, `skyAccess = NONE`, Y 55-140.
- **Sifaka** — diurno de sabana: `skyAccess = REQUIRED`, Y 60-120, luz alta permitida.
- **Squirrel** — arborícola planeador: requiere **al menos un bloque trepable en un radio corto**, reutilizando el escaneo de `DoCTags.Blocks.CIVET_CLIMBABLE` que `SquirrelEntity` ya hace en las líneas 787-884. `skyAccess = NONE`, Y 60-130.

### Ring buffer de diagnóstico

`SpawnRuleEngine` mantiene un `ArrayDeque<SpawnCheckResult>` de tamaño 20 **por especie**, alimentado **solo si el trace está activado** por `/doc_spawn trace`. Sin trace, coste cero: `checkSpawnRules` se llama con muchísima frecuencia y no puede permitirse alocaciones por llamada.

---

## 4. Fase 3 — Alta del Squirrel en el spawn natural (1 h)

1. **`DiversityOfCritters.commonSetup`** — añadir la línea que falta:
   ```java
   SpawnPlacements.register(DOCEntities.SQUIRREL.get(), SpawnPlacements.Type.ON_GROUND,
           Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SquirrelEntity::checkSquirrelSpawnRules);
   ```
2. **`DoCTags.Biomes`** — añadir `is_sifaka_biome` y `is_squirrel_biome` junto al `is_civet_biome` existente.
3. **`DoCBiomeTagGenerator`** — poblar los tres tags:
   - `is_civet_biome` -> `BiomeTags.IS_JUNGLE`
   - `is_sifaka_biome` -> `BiomeTags.IS_SAVANNA`
   - `is_squirrel_biome` -> `BiomeTags.IS_FOREST` **+** `BiomeTags.IS_TAIGA` (decidido: ambos)
4. **Biome modifiers** — dejar exactamente **uno por especie**, todos apuntando al tag propio:
   - Borrar `forge/biome_modifier/entity/add_civet.json` (duplicado) y la carpeta `entity/`.
   - Reescribir `civet_spawns.json` y `sifaka_spawns.json` para usar `#diversityofcritters:is_*_biome`.
   - Crear `squirrel_spawns.json`.
5. **Pesos** — el `weight` del JSON es el techo estático; el gate de probabilidad del motor es lo que modula en runtime. Esta relación se documenta como comentario en el propio TOML.

> **Nota de diseño:** los tres son `MobCategory.CREATURE`, que comparte el cap global de criaturas por chunk con vacas, ovejas, etc. Si en pruebas aparecen demasiado poco, la palanca correcta es subir el `weight` del JSON, no el `spawnChance`.

---

## 5. Fase 4 — Registro persistente de liberados (1.5 h)

### `ReleaseRegistry extends SavedData`

- Adjunta al **Overworld** (`server.overworld().getDataStorage()`), de modo que el contador es **global para la partida**, no por dimensión.
- Archivo: `saves/<mundo>/data/doc_release_registry.dat`.

### Esquema NBT

```
{
  "Version": 1,
  "Species": {
    "civet":    { "Count": 12, "Released": [I; ...uuids... ] },
    "sifaka":   { "Count": 3,  "Released": [I; ... ] },
    "squirrel": { "Count": 0,  "Released": [I; ... ] }
  }
}
```

- `Count`: contador global, topado a `releaseCap`.
- `Released`: UUIDs ya contabilizados, serializados vía `NbtUtils.createUUID`.
- `Version`: permite migrar el esquema en el futuro.

### Acotación de memoria

El set de UUIDs crece con cada liberación, pero **una vez que `Count` alcanza `releaseCap` dejamos de registrar UUIDs nuevos**: a partir de ahí el contador ya no sube, así que el deduplicado deja de aportar valor. Con `releaseCap = 50` el archivo nunca pasa de ~50 UUIDs por especie (unos pocos KB). Sin este corte, un servidor de larga vida acumularía UUIDs indefinidamente.

### API

```java
int     getCount(DOCSpecies species);
boolean isAlreadyReleased(UUID entityUuid);
/** @return el nuevo contador, o -1 si no se contabilizó (duplicado o tope alcanzado) */
int     register(DOCSpecies species, UUID entityUuid);
```

Cada mutación llama a `setDirty()`.

### Carácter permanente

No se expone ninguna API pública de decremento. El contador **solo sube**. El único modo de bajarlo es `/doc_spawn reset` (nivel 2), reservado para testing.

---

## 6. Fase 5 — Cableado del botón Release + anti-farmeo (1.5 h)

### Cambios en `DiverseCritter`

**1. Nuevo campo persistente**, guardado y leído en `addAdditionalSaveData` / `readAdditionalSaveData` (líneas 174 y 197):

```java
private boolean wasReleased;   // NBT: "DOCWasReleased"
```

Primera línea de defensa local: sobrevive al guardado de la entidad y viaja con ella si otro mod la serializa (por ejemplo Carry On, del que ya hay mixins de compat en el repo), evitando tocar la `SavedData` en el caso común.

**2. Override de `removeWhenFarAway`** — este es el arreglo real al problema de persistencia descrito en la sección 0:

```java
@Override
public boolean removeWhenFarAway(double distanceSq) {
    // Animal.removeWhenFarAway() devuelve false: los pasivos nunca despawnean.
    // Solo los liberados vuelven a comportarse como fauna salvaje.
    if (!DOCServerConfig.releasedCanDespawn()) return false;
    return this.wasReleased && !this.isTame();
}
```

**Por qué así y no de otra forma:**

- `Mob.persistenceRequired` es privado y no tiene setter a `false`, así que "des-persistir" una entidad exigiría añadir una línea al `accesstransformer.cfg` ya existente del proyecto. Evitable.
- `Animal.removeWhenFarAway()` devuelve `false` de base, así que borrar el `setPersistenceRequired()` **no cambiaría el comportamiento**: seguiría sin despawnear nunca.
- El override es **quirúrgico**: solo afecta a animales liberados y sin dueño. Los que spawnean de forma natural y los domesticados conservan el comportamiento pasivo de vanilla, igual que una vaca. No se cambia nada que ya funcionaba.
- Queda tras un flag de config (`releasedCanDespawn`) por si un servidor prefiere que los liberados se queden de adorno.

El `setPersistenceRequired()` de `ReleaseCritterMsg.java:42` **se elimina**, porque si se deja activo pisa el override (`checkDespawn` comprueba `isPersistenceRequired()` antes que `removeWhenFarAway`) y el flag ya no se podría revertir.

> **Nota de diseño:** que el animal liberado despawnee no pierde progreso. El registro permanente es el **contador**, no el ejemplar concreto: el animal vuelve a la población salvaje y el bono de spawn ya está cobrado para siempre.

### Nuevo `ReleaseCritterMsg.handle`

```
1. player != null y entidad resuelta en el nivel del jugador
2. la entidad es DiverseCritter y su tipo mapea a un DOCSpecies soportado
   (Lion u otra cosa -> comportamiento antiguo, sin contador)
3. ta.isOwnedBy(player)                             <- ya existe
4. !critter.wasReleased()                           <- anti-farmeo local
5. !registry.isAlreadyReleased(entity.getUUID())    <- anti-farmeo global
6. registry.register(species, uuid) -> newCount
7. critter.setWasReleased(true)
8. setTame(false) / setOwnerUUID(null) / setOrderedToSit(false) / setInSittingPose(false)
9. ELIMINAR la llamada a setPersistenceRequired()
   -> con el override de removeWhenFarAway, vuelve a comportarse como fauna salvaje
10. feedback al jugador
```

### Salvaguarda anti-farmeo: UUID único por animal

Domesticar de nuevo al mismo animal y volver a soltarlo **no** vuelve a sumar. Esto cierra el exploit principal (soltar/recapturar el mismo animal en bucle). Las crías siguen siendo UUIDs nuevos, pero el `releaseCap` global limita el techo total: criar en masa solo acelera llegar al tope, no lo supera.

### Feedback al jugador

Tres mensajes según el resultado, todos como `Component.translatable` con claves nuevas en `en_us.json`:

| Situación | Mensaje (borrador EN) |
|---|---|
| Contabilizado | `%s returned to the wild. %s population: %d/%d (+%d%% spawn rate)` |
| Duplicado | `%s was already released before — population unchanged.` |
| Tope alcanzado | `%s returned to the wild. %s population is already at its peak.` |

Se envían por chat (no action bar) porque llevan cifras que el jugador querrá leer con calma.

---

## 7. Fase 6 — Fórmula del chance de spawn (0.5 h)

```java
public static double bonus(int released, double bonusPerRelease, double maxBonus) {
    return Math.min(released * bonusPerRelease, maxBonus);
}

public static double finalChance(double base, int released, double bonusPerRelease, double maxBonus) {
    return Mth.clamp(base * (1.0 + bonus(released, bonusPerRelease, maxBonus)), 0.0, 1.0);
}
```

### Ejemplo con los valores por defecto

`base = 0.35`, `bonusPerRelease = 0.05`, `maxBonus = 2.0`, `releaseCap = 50`:

| Liberados | Bono | Chance final | Efecto percibido |
|---|---|---|---|
| 0 | +0 % | 35 % | Especie rara |
| 5 | +25 % | 43.8 % | Se empieza a notar |
| 10 | +50 % | 52.5 % | Frecuente en su bioma |
| 20 | +100 % | 70 % | Común |
| 40 | +200 % (tope) | 100 % | Saturado: solo limita el `weight` del JSON y el cap de `MobCategory` |
| 50 (cap) | +200 % | 100 % | Idéntico a 40 — el tope de bono llega antes que el cap del contador |

> Pensado para que el tope se alcance con ~40 liberaciones: un objetivo de progresión largo pero alcanzable. Todo ajustable sin recompilar.

---

## 8. Fase 7 — Comando `/doc_spawn` (1.5 h)

Registrado en `DOCForgeEvents.onCommandsRegister` (línea 37), junto a los comandos ya existentes. Nivel de permiso 2.

| Subcomando | Salida |
|---|---|
| `/doc_spawn` | Resumen por especie: `enabled`, liberados `N/cap`, bono `+X%`, chance base -> chance final, y si tiene `SpawnPlacements` registrado. |
| `/doc_spawn here [especie]` | Vista de jugador: si la especie aparece en este sitio, con qué probabilidad, cuántos lleva liberados y cuánto bono le dan. Si no aparece, dice el motivo en una línea. |
| `/doc_spawn here [especie] details` | Lo anterior **más** el desglose filtro por filtro con los valores reales (Y actual vs rango, luz actual vs máximo, densidad vs `groupMax`). Es la vista de depuración, la que responde "¿por qué exactamente no me spawnea aquí?". |
| `/doc_spawn set <clave> <valor>` | Edita un ajuste **global** (`bonusPerRelease`, `maxBonus`, `releaseCap`, `groupCheckRadius`, `releasedCanDespawn`). Escribe el TOML del mundo y aplica el cambio en el acto. |
| `/doc_spawn set <especie> <clave> <valor>` | Edita un ajuste **de especie** (`enabled`, `spawnChance`, `groupMin`, `groupMax`, `minY`, `maxY`, `maxBlockLight`, `skyAccess`). Mismo comportamiento. |
| `/doc_spawn max <especie>` | Pone la especie a rendimiento máximo: chance al 100 % y contador de liberados al tope, sin tocar el TOML. Los filtros de bioma, altura, luz, suelo y densidad **siguen aplicándose** — saltárselos haría aparecer al animal en biomas que no son suyos. |
| `/doc_spawn chance <especie> <0.0-1.0>` | Override temporal del chance final. No se guarda en el config y desaparece al parar el servidor. Mientras está puesto, el bono de liberaciones se ignora para esa especie. |
| `/doc_spawn chance <especie> clear` | Quita el override. |
| `/doc_spawn why <especie>` | Vuelca el ring buffer: los últimos 20 intentos con su `SpawnRejectReason`, posición y chance calculado. Requiere `trace` activo. |
| `/doc_spawn trace <especie> <on\|off>` | Activa/desactiva la grabación en el ring buffer. |
| `/doc_spawn reset [especie]` | Pone el contador a 0 y vacía el set de UUIDs. Pide confirmación escribiendo `confirm`. **Solo para testing.** |

Los `set` no son un override en memoria: llaman a `ConfigValue.set()`, luego a `ForgeConfigSpec.save()` y por último reconstruyen el caché a mano. Lo último es deliberado — no podemos depender de que el vigilante de archivos de Forge dispare el evento de recarga a tiempo, y queremos que el cambio se note en el mismo tick.

Todos los rangos de los argumentos son los mismos que valida el spec del TOML, así que el comando no puede meter un valor corrupto en el archivo.

> **Nota multijugador:** Forge sincroniza los configs SERVER al cliente solo al conectarse, no cuando cambian. Un cliente ya conectado se queda con la copia vieja. No afecta a nada, porque todos estos valores se leen únicamente en el servidor.

Salida de `/doc_spawn here squirrel`:

```
=== Red and White Giant Flying Squirrel ===
 Location:      128, 71, -344
 Can spawn here: NO — too many nearby already
 Spawn chance:  48%
 Released:      12 / 50
 Release bonus: +60%
```

Y con `details`, se le añade debajo:

```
 ----------------------------------------
 [OK]   enabled........... true
 [OK]   dimension......... minecraft:overworld
 [OK]   Y range........... 71 in [60, 130]
 [OK]   block light....... 4 <= 12
 [OK]   sky access........ NONE (covered)
 [OK]   valid ground...... block.minecraft.grass_block
 [OK]   nearby trees...... found within 9
 [FAIL] group density..... 4 within 24 (max 4)
```

---

## 9. Fase 8 — Pruebas, lang y documentación (1.5 h)

### Checklist de pruebas manuales

- [ ] Mundo nuevo genera `serverconfig/diversityofcritters-server.toml` con las 3 secciones.
- [ ] `enabled = false` en una especie -> `/doc_spawn here` devuelve `DISABLED` y no aparecen ejemplares naturales.
- [ ] Huevo de spawn y `/summon` **siguen funcionando** aunque la especie esté deshabilitada (bypass de `MobSpawnType`).
- [ ] La cría por `getBreedOffspring` no se ve afectada por el gate.
- [ ] El Squirrel aparece de forma natural en su bioma (**regresión clave**: antes no aparecía nunca).
- [ ] Liberar un animal sube el contador y el mensaje muestra `1/50`.
- [ ] Volver a domesticar **ese mismo** animal y soltarlo -> mensaje de duplicado, contador sin cambios.
- [ ] Guardar, cerrar el mundo y reabrir -> el contador se conserva.
- [ ] Liberar hasta `releaseCap` -> el contador se detiene y el mensaje cambia al de tope.
- [ ] Un animal liberado despawnea al alejarse >128 bloques; uno **domesticado** y uno **salvaje natural** NO (regresión del override de `removeWhenFarAway`).
- [ ] `releasedCanDespawn = false` -> el liberado deja de despawnear.
- [ ] `/doc_spawn reset` deja el contador a 0.
- [ ] Servidor dedicado + cliente: el config SERVER se sincroniza y las pantallas no crashean.
- [ ] Perfilado: con trace **desactivado**, `checkSpawnRules` no genera basura medible (comparar tick time con y sin el mod en un mundo con chunks de spawn activos).

### Lang

Claves nuevas en `assets/diversityofcritters/lang/en_us.json`:

```
commands.diversityofcritters.spawn.*      (salida del comando)
message.diversityofcritters.release.*     (feedback de liberación)
```

### Documentación

Actualizar este archivo con los valores por defecto finales tras el balanceo, y añadir una nota breve en `README.txt` sobre dónde vive el config.

---

## 10. Riesgos y decisiones abiertas

| Riesgo | Mitigación |
|---|---|
| El `weight` del biome modifier es estático y no se puede cambiar en caliente | Por diseño: el JSON define el **techo** y el gate de probabilidad modula hacia abajo. Documentado en el TOML. |
| `groupMin` de config no puede sobreescribir el `minCount` del JSON | `groupMax` se implementa como **cap de densidad en radio** (equivalente y más útil en la práctica); `groupMin` queda como valor informativo que hay que reflejar a mano en el JSON. Alternativa si se quiere control total: generar los JSON por datagen desde constantes compartidas. |
| Coste de `getEntitiesOfClass` en el hot path de spawn | Va el último en la cadena de filtros, solo se ejecuta si todo lo demás pasó. |
| `MobCategory.CREATURE` comparte cap con la fauna vanilla | Documentado; la palanca es el `weight`, no el `spawnChance`. |
| Config SERVER no disponible en `commonSetup` | Todo el gating vive en `checkSpawnRules` (runtime), no en el registro. |
| El override de `removeWhenFarAway` cambia comportamiento existente | Acotado a `wasReleased && !isTame()`, y detrás del flag `releasedCanDespawn`. Nada de lo que ya funcionaba se ve afectado. |
| Un jugador malicioso "regala" spawn no deseado en un server | El bono es global y solo sube. Un admin puede revertir con `/doc_spawn reset` o bajar `maxBonus`. |

### Decisiones cerradas

| # | Decisión | Resuelto |
|---|---|---|
| 1 | Tipo de config | `ForgeConfigSpec` **SERVER** (TOML por mundo, sincronizado al cliente) |
| 2 | Fórmula del bono | **Multiplicador aditivo con tope**: `base * (1 + min(n * bonusPerRelease, maxBonus))` |
| 3 | Anti-farmeo | **UUID único por animal** + flag NBT local `DOCWasReleased` |
| 4 | Implementación del gate | **Gate probabilístico en `checkSpawnRules`**; el `weight` del JSON queda como techo |
| 5 | Biomas del Squirrel | **`IS_FOREST` + `IS_TAIGA`** (ambos) |
| 6 | Persistencia del liberado | **Override de `removeWhenFarAway`** en `DiverseCritter` + eliminar `setPersistenceRequired()`, tras el flag `releasedCanDespawn = true` |
| 7 | Alcance del contador | **Global por partida.** En multijugador el esfuerzo de un jugador sube el spawn para todos |
| 8 | Curva de progresión | La de la Fase 6: tope (+200 %) a las ~40 liberaciones, `releaseCap = 50` |

---

## 11. Desglose de esfuerzo

| Fase | Tarea | Horas |
|---|---|---|
| 0 | Limpieza previa (biome modifier duplicado, tags sin usar) | 0.5 |
| 1 | Infraestructura de config (SERVER TOML) | 1.5 |
| 2 | Motor de reglas + reglas reales de las 3 especies | 2.0 |
| 3 | Alta del Squirrel + datagen de tags y biome modifiers | 1.0 |
| 4 | `ReleaseRegistry` (SavedData persistente) | 1.5 |
| 5 | Cableado del botón Release + anti-farmeo + feedback | 1.5 |
| 6 | Fórmula del bono e integración en el motor | 0.5 |
| 7 | Comando `/doc_spawn` con sus 5 subcomandos | 1.5 |
| 8 | Pruebas, lang, documentación | 1.5 |
| | **Total** | **11.5 h** |

### Orden de ejecución recomendado

Las fases 1 -> 2 -> 3 forman el bloque "el spawn funciona", entregable y verificable por sí solo (el Squirrel ya aparece en el mundo, las reglas ya filtran). Las fases 4 -> 5 -> 6 forman el bloque "el release alimenta el spawn". La fase 7 es transversal y conviene empezarla en paralelo a la 2, porque `/doc_spawn here` es la herramienta con la que se depuran las fases 2 y 3.

---

## 12. Mapa de archivos tocados

### Nuevos (10 clases + 1 JSON)

```
common/config/DOCConfig.java
common/config/DOCServerConfig.java
common/config/SpeciesSpawnSettings.java
common/spawn/DOCSpecies.java
common/spawn/SpawnRuleEngine.java
common/spawn/SpawnCheckResult.java
common/spawn/SpawnRejectReason.java
common/spawn/SpawnBonusFormula.java
common/spawn/ReleaseRegistry.java
common/command/SpawnDebugCommand.java
data/diversityofcritters/forge/biome_modifier/squirrel_spawns.json
```

### Modificados

```
DiversityOfCritters.java                       (+1 SpawnPlacements, +registro de config)
common/entity/custom/CivetEntity.java          (checkCivetSpawnRules real)
common/entity/custom/SifakaEntity.java         (checkSifakaSpawnRules real)
common/entity/custom/SquirrelEntity.java       (checkSquirrelSpawnRules real)
common/entity/custom/base/DiverseCritter.java  (+campo wasReleased, +NBT, +removeWhenFarAway)
network/ReleaseCritterMsg.java                 (contador + anti-farmeo + feedback)
common/event/DOCForgeEvents.java               (+registro de /doc_spawn)
misc/tags/DoCTags.java                         (+2 tags de bioma)
datagen/providers/DoCBiomeTagGenerator.java    (+2 tags poblados)
data/.../biome_modifier/civet_spawns.json      (usar tag propio)
data/.../biome_modifier/sifaka_spawns.json     (usar tag propio)
assets/.../lang/en_us.json                     (+claves de comando y feedback)
```

### Eliminados

```
data/diversityofcritters/forge/biome_modifier/entity/add_civet.json   (duplicado)
```
