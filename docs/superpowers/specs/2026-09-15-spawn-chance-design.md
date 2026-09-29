# Spawn chance system y liberación a la naturaleza — Spec

**Qué:** un sistema de spawn natural configurable para civeta, sifaka y ardilla, donde liberar un
animal domesticado sube de forma permanente el ritmo al que esa especie aparece en el mundo.

Es el módulo 2 del encargo. El módulo 2b —que la población también **baje** por domesticación,
muertes y nacimientos salvajes— no entra aquí, pero el diseño se elige para que 2b sea añadir
ganchos y no reescribir nada. Ver "Preparado para 2b" al final.

---

## El problema central: un gate solo puede restar

Esta es la restricción de la que cuelga todo el diseño, y conviene entenderla antes de seguir.

`SpawnPlacements.checkSpawnRules` es un **predicado**: puede rechazar un intento de spawn, nunca
crear uno extra. Y los pesos de los `biome_modifier` son datapack — se cargan al arrancar el mundo y
no se pueden modificar en caliente desde config ni desde un contador vivo.

Juntando las dos cosas: **no hay ninguna forma de que liberar animales suba el ritmo de spawn por
encima del peso que dice el JSON.**

La solución es invertir el planteamiento:

> El peso del `biome_modifier` se fija en el **máximo** —el ritmo de una población próspera—. El gate
> rechaza intentos para bajarlo hasta el nivel de población actual. Liberar animales **reduce el
> rechazo**.

Con cero liberaciones el gate rechaza mucho y la especie es rara. En el tope no rechaza nada y la
especie aparece al ritmo pleno del JSON. El efecto que pidió el cliente se consigue, pero por
sustracción.

**Consecuencia para la config:** lo que se ajusta por especie es la *tasa base de aceptación*, y el
peso del JSON es el techo. Quien afine estos valores tiene que tenerlo presente o los números no
significan lo que parece.

---

## Semántica del contador: población salvaje, no bonus

El contador significa **cuántos animales hay en libertad gracias al jugador**, desde el primer día,
aunque 2b no esté comprado.

Se descartó la alternativa —un simple acumulador de liberaciones sin semántica— porque el anti-farmeo
habría necesitado un mecanismo aparte, y porque 2b obligaría a reescribir el registro entero. Con
semántica de población, el anti-farmeo sale gratis (ver "Flujo") y 2b solo añade ganchos.

Los animales que aparecen por spawn natural **no incrementan el contador**. Son la línea base, que ya
está representada por el peso del `biome_modifier`; contarlos sería realimentación circular —más
spawns producen más spawns.

---

## Componentes

Paquete nuevo `common.population` para el registro y el gate, más `common.config` para la config.
Una responsabilidad por clase.

### `WildPopulation`

`SavedData` adjunto al overworld, de ámbito global. Un `int` por especie, persistido en NBT.

```java
public int get(CritterSpecies species);
public void credit(CritterSpecies species);   // +1, clampado al tope
// 2b anadira: public void debit(CritterSpecies species);
```

`CritterSpecies` es un enum con `CIVET`, `SIFAKA` y `SQUIRREL`. Se prefiere a `ResourceLocation`
porque las claves de config, las de NBT y el mapeo desde la entidad cuelgan todas del mismo sitio, y
porque un valor imposible deja de ser representable.

Tope y suelo se aplican **al escribir**, no al leer, para que bajar el tope en la config no deje
valores guardados fuera de rango.

No sabe nada de spawns ni de entidades. Esa ignorancia es lo que permite que 2b le cuelgue ganchos
nuevos sin tocarla.

### `SpawnGate`

La única clase que conoce la fórmula. Traduce población a probabilidad de aceptación y resuelve el
predicado.

```java
public static boolean allow(CritterSpecies species, LevelAccessor level,
                            MobSpawnType type, RandomSource random);
public static double acceptance(CritterSpecies species, MinecraftServer server);
public static String lastRejection(CritterSpecies species);
```

**Fórmula, lineal con tope:**

```
aceptacion = min(tasaBase + incrementoPorLiberacion * poblacion, 1.0)
```

Se eligió lineal frente a rendimientos decrecientes y frente a escalones discretos. Rendimientos
decrecientes es más realista pero opaco —el jugador no entiende por qué su liberación número 20 no
hizo nada visible—. Los escalones dan saltos que se notan. Lineal es trivial de explicar, trivial de
afinar, y el jugador ve el efecto de cada animal que suelta.

**Sin caché, a propósito.** La tentación es cachear la tasa, pero leerla ya es un `Map.get` sobre el
`DataStorage` más una aritmética de dos operaciones. Cachear añadiría una ruta de invalidación —al
cambiar el contador y al recargar la config— y con ella la clase de bug más difícil de ver aquí: un
número correcto en el registro y uno viejo decidiendo los spawns. Si alguna vez se mide que esto
pesa, se cachea entonces; hoy no hay nada que lo justifique.

Esto es además lo que mantiene a `WildPopulation` ignorante de `SpawnGate`: sin caché no hay nada que
invalidar, así que la dependencia va en una sola dirección.

**Filtro por `MobSpawnType`.** El gate solo actúa sobre `NATURAL`. Cualquier otro tipo pasa sin
tocar: de lo contrario el filtro bloquearía huevos de spawn, cría y `/summon`, y el jugador vería
fallar cosas que no tienen nada que ver con la población salvaje.

### `DOCConfig`

`ForgeConfigSpec` común, en `common.config`. **Es infraestructura nueva: el mod no tiene hoy ningún
sistema de config**, así que hay que montar el spec, el registro en la clase principal y la carga del
fichero.

Va en su propio paquete y con nombre genérico, no `DOCSpawnConfig`, porque es la config del mod
entero: el módulo 2b le colgará su sección y los gamerules del módulo 3 también.

Por especie: activado, tasa base de aceptación, incremento por liberación, tope de población.

**Valores por defecto**, iguales para las tres especies:

| Clave | Defecto | Significado |
|---|---|---|
| `enabled` | `true` | si la especie aparece de forma natural |
| `baseAcceptance` | `0.25` | con población cero se acepta 1 de cada 4 intentos |
| `acceptancePerRelease` | `0.05` | cada liberación suma 5 puntos de aceptación |
| `populationCap` | `15` | tope del contador |

Están elegidos para que encajen: `0.25 + 15 × 0.05 = 1.0`. O sea que **el tope es exactamente el
punto donde la aceptación llega al 100%** y la especie aparece al ritmo pleno del `biome_modifier`.
Si alguien cambia uno de los tres valores sin ajustar los otros, el tope deja de coincidir con el
máximo —no rompe nada, porque la fórmula clampa, pero el contador seguiría subiendo sin efecto
visible por encima del punto de saturación.

---

## Estado en la entidad

Un solo booleano NBT en `DiverseCritter`:

```java
private boolean countedInWild;   // esta este animal contado en la poblacion salvaje?
```

Solo servidor. **No se sincroniza al cliente**, porque no se dibuja nada a partir de él.

Se guarda y se lee junto al resto del estado del critter, siguiendo lo que ya hacen `TempComfort` y
`HumidityComfort`.

---

## Flujo

| Evento | Efecto |
|---|---|
| Spawn natural | `countedInWild = true`, contador **sin tocar** |
| Liberar, con `countedInWild == false` | contador +1, `countedInWild = true` |
| Liberar, con `countedInWild == true` | nada |

**La tercera fila es el anti-farmeo, y no hace falta nada más.** Domesticar un animal salvaje no le
quita la marca, así que volver a soltarlo no da crédito: el bucle domesticar→liberar→domesticar no
produce nada. No hay flag de un solo uso ni cooldown que mantener.

Y al revés, una cría nacida en cautividad **sí** da crédito, porque nunca estuvo contada. Eso es
deliberado: es el programa de repoblación que quiere el cliente —criar y soltar— y el tope es lo que
impide que sea infinito.

---

## Puntos de enganche

Todos existen ya; ninguno obliga a inventar arquitectura.

| Dónde | Qué se hace |
|---|---|
| `CivetEntity.checkCivetSpawnRules` (hoy `return true`) | delegar en `SpawnGate.allow` |
| `SifakaEntity.checkSifakaSpawnRules` (hoy `return true`) | ídem |
| `SquirrelEntity` | **crear** el método; hoy no existe |
| `DiversityOfCritters.commonSetup` | añadir el `SpawnPlacements.register` de la ardilla, que falta |
| `DiverseCritter.finalizeSpawn` | marcar `countedInWild` cuando el spawn es natural |
| `ReleaseCritterMsg.handle` | acreditar en `WildPopulation` |
| `DOCForgeEvents.onCommandsRegister` | registrar `/doc_spawn` |

---

## Biomas y el fichero muerto del civet

El estado actual tiene un fallo que hay que arreglar aquí, porque es exactamente lo que este módulo
toca.

El civet tiene **dos** ficheros de spawn:

- `biome_modifier/civet_spawns.json` → `#minecraft:is_jungle`, peso 80. Funciona.
- `biome_modifier/entity/add_civet.json` → `#diversityofcritters:is_civet_biome`, peso 6. **Ese tag no
  existe**: no hay ningún fichero de tags de bioma en el mod. Apunta a la nada.

O sea que hoy el civet spawnea solo por el primero, y el segundo o da error de datapack o coincide
con cero biomas. El sifaka está limpio (`#minecraft:is_savanna`, peso 60). La ardilla no tiene nada.

**Resolución:** se crean los tres tags del mod y queda un solo `biome_modifier` por especie apuntando
a su tag.

| Tag | Biomas | Peso | minCount / maxCount |
|---|---|---|---|
| `#diversityofcritters:is_civet_biome` | `#minecraft:is_jungle` | 80 | 2 / 4 |
| `#diversityofcritters:is_sifaka_biome` | `#minecraft:is_savanna` | 60 | 2 / 4 |
| `#diversityofcritters:is_squirrel_biome` | `#minecraft:is_forest`, `#minecraft:is_taiga` | 70 | 2 / 4 |

Los pesos de civeta y sifaka son los que ya tenían. **Ahora significan otra cosa**: ya no son el
ritmo real, sino el techo al que se llega con la población en el tope. El peso 6 del `add_civet.json`
muerto se descarta —era de un intento a medias y nunca llegó a aplicarse—.

`civet_spawns.json` se borra. Esto unifica las tres especies bajo el mismo patrón —que es lo que el
autor de `add_civet.json` ya intentaba hacer— y deja los biomas ajustables por datapack sin tocar el
jar.

Los bosques templados y la taiga para la ardilla encajan con la ardilla voladora gigante real y con
su rango de temperatura Freezing–Warm.

---

## `/doc_spawn`

Sin este comando, probar spawns es a ciegas: hay que volar a chunks nuevos y esperar, sin saber si
algo no aparece porque el gate lo rechazó, porque el bioma no coincide o porque el sitio no valía.

Muestra, por especie: población actual, tope, tasa de aceptación resultante, y el motivo del último
rechazo.

---

## Verificación

El proyecto **no tiene tests** —`src/test` está vacío—, así que la verificación es build limpia más
comprobación en juego con `/doc_spawn`.

Lo que hay que ver funcionando:

1. Con población cero, la especie aparece notablemente menos que con el tope.
2. Liberar un animal salvaje recién domesticado **no** sube el contador.
3. Liberar una cría nacida en cautividad **sí** lo sube.
4. El contador sobrevive a recargar el mundo.
5. Huevos de spawn, cría y `/summon` siguen funcionando con población cero.
6. La ardilla aparece en bosque y taiga, y el civet sigue apareciendo en jungla tras borrar
   `civet_spawns.json`.

El punto 5 es el que más fácil se rompe y el que peor se diagnostica si se cuela.

---

## Lo que NO hace, a propósito

- **No toca domesticación, nacimientos ni muertes.** Eso es el módulo 2b.
- **No decae.** El contador es permanente, como pidió el cliente.
- **No es por dimensión ni por jugador.** Es global.
- **No incluye al Lion.** La entidad no está en uso.
- **No sincroniza nada al cliente.** Nada de esto se dibuja.

---

## Preparado para 2b

Lo que 2b tendrá que añadir, y que este diseño ya deja servido:

- `WildPopulation.debit(species)`, simétrico al `credit` que ya existe.
- Ganchos en domesticación, nacimiento y muerte, que consultan y voltean el mismo `countedInWild`.
- Un suelo configurable, que ya se aplica en la misma ruta de escritura que el tope.

Ninguno de esos cambios obliga a tocar `SpawnGate` ni la fórmula: 2b mueve el número, y el gate se
limita a leerlo.
