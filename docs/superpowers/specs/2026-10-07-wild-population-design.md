# Modelo de población salvaje (módulo 2b) — Spec

**Qué:** el contador de liberaciones del módulo 2 se convierte en un modelo de población salvaje por
especie. Todo lo que saca un animal de la naturaleza la baja; todo lo que devuelve uno la sube. El
chance de spawn natural pasa a ser proporcional a esa población.

**Ámbito:** civeta, sifaka y ardilla. El Lion no está en `DOCSpecies` y ningún gancho cuenta para él.

**Parte del encargo:** módulo 2b ($55). Requiere el módulo 2, ya implementado.

---

## Por qué no basta con "añadir un `debit`"

El spec del módulo 2 (`2026-09-15-spawn-chance-design.md`) prometía que 2b sería colgar ganchos de un
`WildPopulation` con `debit()` ya previsto. Lo que se implementó sigue otro documento
(`docs/spec-spawn-chance-release.md`): un `ReleaseRegistry` que **solo sube**, cuyo cero es "chance
base" y cuyo anti-farmeo es un set de UUIDs que depende justamente de que el contador no baje.

Con esa forma no se puede representar una especie mermada (no hay nada por debajo de cero) ni la
extinción (población 0 = no aparece). Hay que cambiar la escala del contador.

---

## Sección 1 — Registro y fórmula

### Escala: población base

Cada especie arranca con `basePopulation` (20). El chance es proporcional a la población relativa a
esa base:

```
multiplicador = clamp(poblacion / basePopulation, 0, 1 + maxBonus)
chanceFinal   = chanceBase * multiplicador
```

| Población | Multiplicador | Situación |
|---|---|---|
| 0 | 0 % | extinta (solo alcanzable con `populationFloor = 0`) |
| 5 | 25 % | suelo por defecto: rara |
| 20 | 100 % | mundo nuevo |
| 40 | 200 % | |
| 60 | 300 % | tope: `populationCap`, saturación de `maxBonus = 2.0` |

Cada animal vale `1 / basePopulation` = +5 % con los valores por defecto, lo mismo que valía una
liberación en el módulo 2. El "bono" que se le enseña al jugador es `multiplicador − 1` y puede ser
negativo (−75 % con población 5).

Se descartó un contador con signo (arranca en 0, puede ser negativo): mismas matemáticas, pero el
jugador vería poblaciones negativas.

### `WildPopulation` (antes `ReleaseRegistry`)

`SavedData` en el Overworld, global a la partida. Mismo archivo `doc_release_registry.dat`, esquema
**versión 2**.

- **Un `int` de población por especie.** Una especie sin entrada vale `basePopulation`.
- **Migración v1 → v2:** `poblacion = basePopulation + Count`, clampada al tope. Los UUIDs del v1 se
  descartan.
- **Sin set de UUIDs.** Con semántica de población el anti-farmeo es el propio modelo: domesticar
  resta 1 y soltar suma 1, el bucle domesticar→soltar da neto 0. Lo único que suma de verdad es
  soltar una cría nacida en cautividad, que es el programa de repoblación que se quiere; el tope lo
  acota.
- **Suelo y tope al escribir**, no al leer, para que cambiar la config no deje valores guardados
  fuera de rango.
- **Caché lock-free** igual que hoy (`volatile int[]` reemplazado entero en cada escritura), porque
  el ciclo de spawn la lee desde hilos de generación.
- **Historial para la tendencia:** 7 valores por especie, uno por día de juego (se muestrea cuando
  cambia `dayTime / 24000` del Overworld). Tendencia = población actual − la de hace 7 días; si aún
  no hay 7 días de historia, contra el valor más antiguo.

API:

```java
int  get(DOCSpecies species);
/** Aplica un evento con su peso de config. Devuelve el cambio real tras el clamp. */
int  apply(DOCSpecies species, PopulationEvent event, MinecraftServer server);
void set(DOCSpecies species, int population);   // comandos
int  trend(DOCSpecies species);
```

`PopulationEvent`: `TAME`, `RELEASE`, `WILD_BIRTH`, `STARVATION`, `PLAYER_KILL`, `TEMPERATURE_DEATH`,
`HUMIDITY_DEATH`. Cada uno lleva su signo y su clave de peso.

### `PopulationMath`

Toda la aritmética, **sin tipos de Minecraft**, para poder testearla con JUnit: multiplicador,
clamp, migración v1→v2, tendencia sobre el historial, detección de cruce del umbral y escalado de la
tirada de cría. `WildPopulation` persiste y delega; `SpawnBonusFormula` delega el multiplicador.

### Config

Nueva subsección `[spawn.population]` del TOML SERVER existente:

| Clave | Defecto | Rango | Significado |
|---|---|---|---|
| `basePopulation` | 20 | 1–1000 | población inicial; equivale al chance base |
| `populationFloor` | 5 | 0–1000 | mínimo; **0 = extinción real** |
| `populationCap` | 60 | 1–100000 | máximo |
| `endangeredThreshold` | 10 | 0–1000 | por debajo, la especie está en peligro |
| `wildBreedingRate` | 1.0 | 0.0–10.0 | multiplica la frecuencia de cría de los salvajes; 0 = no crían |
| `weights.tame` … `weights.humidityDeath` | 1 | 0–100 | cuánto mueve cada evento; 0 = no cuenta |

Se eliminan `bonusPerRelease` y `releaseCap`. `maxBonus` se queda. Si el suelo queda por encima del
tope por mala configuración, gana el tope.

---

## Sección 2 — Pertenencia y ganchos

### Pertenencia por animal

`DiverseCritter` gana `boolean inWild` (NBT `DOCInWild`), solo servidor, sin sincronizar. Hace falta
además de `isTame()` porque un animal de huevo o de `/summon` no está domesticado pero tampoco estaba
contado: si su muerte restara, las herramientas de admin corromperían el contador.

| Origen | `inWild` | Contador |
|---|---|---|
| Spawn `NATURAL` / `CHUNK_GENERATION` | `true` | sin tocar (es la línea base) |
| Huevo, `/summon`, spawner, otros | `false` | sin tocar |
| Nace sin ningún progenitor domesticado | `true` | **+1** `WILD_BIRTH` |
| Nace con algún progenitor domesticado | `false`, nace domesticada | sin tocar |
| Save viejo sin la etiqueta | `!isTame()` | sin tocar |

Las entidades que se recrean desde NBT conservan la etiqueta porque viaja en el propio NBT.

### Ganchos

**Doma — override de `tame(Player)`.** Si `inWild`: aplica `TAME` (−1) y pone `inWild = false`. No se
usa `setTame()` porque `TamableAnimal.readAdditionalSaveData` lo llama al cargar un animal
domesticado desde disco: descontaría en cada carga de chunk.

**Release — `ReleaseCritterMsg`.** Aplica `RELEASE` (+1), `inWild = true`, `wasReleased = true` (este
último se mantiene para el despawn de fauna salvaje del módulo 2). Desaparece el camino `DUPLICATE`.

**Nacimiento.**
- Al quedar preñada en `NaturalMateGoal`, la madre guarda el dueño del padre en NBT
  (`DOCSireOwner`, ausente si el padre es salvaje).
- En `giveBirth`: dueño = dueño de la madre, o si es salvaje, `DOCSireOwner`.
  - Con dueño: la cría nace domesticada (`setTame(true)` + `setOwnerUUID`), `inWild = false`, no cuenta.
  - Sin dueño: `inWild = true`, aplica `WILD_BIRTH` (+1).
- `DOCSireOwner` se borra al parir.
- Como vive en `giveBirth` de la clase base, nacer domesticada también aplica al Lion; el contador
  no, porque no tiene `DOCSpecies`.

Esto cambia cómo se juega la cría: ya no hay que domesticar cada cría nacida en el recinto. Y como
los domesticados no despawnean, una granja grande acumula entidades cargadas para siempre. Ambas
consecuencias están aceptadas en el presupuesto.

**Muerte — `die()`.** Se evalúa en orden y se para en la primera regla que aplica:

1. Domesticado, o `inWild == false` → nada.
2. `genericKill` (`/kill`) o `fellOutOfWorld` → nada.
3. Damage type propio:
   - `starvation` → `STARVATION`
   - `hypothermia`, `heatstroke` → `TEMPERATURE_DEATH`
   - `humidity` → `HUMIDITY_DEATH`
   - `dehydration` → nada (sed excluida a petición del cliente)
4. `lastHurtByPlayer != null` y el jugador no está en creativo ni espectador → `PLAYER_KILL`. Es la
   regla con la que vanilla reparte la XP: válida 5 s tras el golpe, cubre flechas, empujar a la lava,
   prender fuego y las mascotas del jugador.
5. Cualquier otra causa → nada: depredadores, caídas, ahogamiento, cactus.

**Despawn** no pasa por `die()` y no cuenta.

### Frecuencia de cría salvaje

Solo cuando el animal **no** está domesticado, `NaturalMateGoal` usa
`tirada = round(400 / wildBreedingRate)` y divide el cooldown posterior por `wildBreedingRate`. Con
`wildBreedingRate = 0` los salvajes no crían. La cría en cautividad no cambia.

### Aviso de peligro

Cuando `apply` o `set` hace cruzar a una especie por debajo de `endangeredThreshold`, se envía a todos
los jugadores un mensaje por chat: *"The banded palm civet is now endangered in this world
(population 9)."*. Al volver a superarlo: *"…is recovering…"*. Solo en el cruce, no en cada evento.

---

## Sección 3 — Damage types, hambre/sed, comando

### Damage types

JSON a mano en `data/diversityofcritters/damage_type/`, como los biome modifiers. Claves en una
clase `DOCDamageTypes` (`ResourceKey<DamageType>`).

| Tipo | Sustituye a | Cuenta |
|---|---|---|
| `starvation` | `starve()` cuando hambre ≤ 0 | sí |
| `dehydration` | `starve()` cuando sed ≤ 0 | no |
| `hypothermia` | `freeze()` por frío | sí, temperatura |
| `heatstroke` | `onFire()` por calor | sí, temperatura |
| `humidity` | `dryOut()` | sí, humedad |

- Los cinco en `data/minecraft/tags/damage_type/bypasses_armor.json`, como el `starve` vanilla.
  Ninguno en `is_fire` ni `is_freezing`.
- `scaling: never`, `exhaustion: 0.0`.
- Mensaje de muerte propio en `en_us.json` (`death.attack.diversityofcritters.<tipo>`). Vanilla ya lo
  envía al dueño cuando muere una mascota.

### Hambre y sed separadas

La línea `if ((hunger <= 0 || thirst <= 0) && …) starve();` pasa a dos ramas. Si ambas están a 0
gana el hambre: es la causa que cuenta y la más grave.

### `/doc_spawn`

- **Resumen por especie:** población, suelo/tope, tendencia de 7 días (`+3 this week`), chance base →
  final y `ENDANGERED` si aplica.
- `max <especie>` → población al tope.
- `reset [especie]` → población a `basePopulation`, no a 0.
- **Nuevo** `population <especie> <n>` → fija la población exacta (clampada).
- `set <clave> <valor>` → lista de claves globales actualizada: fuera `bonusPerRelease` y
  `releaseCap`, dentro las nuevas, pesos incluidos.

### Feedback del botón Release

- Contado: *"Civet returned to the wild. Civet population: 23 (+15% spawn rate)"*.
- En el tope: *"…population is already at its peak."*.
- Desaparece el mensaje de duplicado.

---

## Verificación

**Automática (Claude):** `gradlew compileJava` y `gradlew test`, nunca `clean`. JUnit sobre
`PopulationMath`: multiplicador y su clamp, suelo/tope en escritura y con suelo > tope, migración
v1→v2, tendencia con historial corto y completo, cruce de umbral en ambos sentidos y sin cruce, tirada
de cría con tasa 0, 1 y 2.

**En juego (usuario):**

1. Mundo nuevo: población 20 en las tres especies.
2. Mundo con datos v1: migra a 20 + liberados.
3. Domesticar un salvaje resta 1; soltarlo suma 1 (neto 0).
4. Cría de padres domesticados nace domesticada; al soltarla suma 1.
5. Pareja mixta: la cría es del dueño del progenitor domesticado.
6. Matar un salvaje natural resta 1; matar uno de huevo de spawn no.
7. `/kill` y la muerte por sed no cuentan.
8. Muertes por temperatura, humedad y hambre restan y muestran su mensaje.
9. La muerte de una mascota no cuenta.
10. Bajar del umbral avisa por chat; volver a subir avisa también.
11. Con suelo 0 y población 0 la especie deja de aparecer; con suelo 5 es rara.
12. Huevos, `/summon` y cría siguen funcionando con población mínima.

---

## Fuera de alcance

- Extinción "total" que bloquee huevos y cría (el presupuesto lo descarta por tamaño).
- Población por dimensión o por jugador: es global.
- Contar animales en chunks descargados.
- Contar los spawns naturales: son la línea base; contarlos sería realimentación circular.
