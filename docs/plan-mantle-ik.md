# Plan de implementación — Mantle: subir un bloque con IK procedimental

**Ámbito:** Civet (completo), Squirrel (después). Sifaka y Lion quedan fuera; ver §10.
**Versión objetivo:** Minecraft 1.20.1 / Forge 47.4.10 / mappings Parchment 2023.09.03.
**Estimación total:** 23.5 – 36.5 días de trabajo efectivo (§9).

## Estado de implementación

| Fase | Estado |
|---|---|
| 0 — Pipeline de locomoción base + rig de terreno en el civet | 🟡 0.1 – 0.3 hechas (55 tests en verde). Siguiente: 0.5, luego 0.4 |
| 1 — `LedgeSampler` | ⬜ Pendiente |
| 2 — Objetivos de pata anclados al mundo | ⬜ Pendiente |
| 3 — `MantleDriver` + `GripPlanner` + arqueo | ⬜ Pendiente |
| 4 — Lado servidor: controlador de mantle | ⬜ Pendiente |
| 5 — Overlay de debug del mantle (en paralelo con 3) | ⬜ Pendiente |
| 6 — Integración y tuning (civet → squirrel) | ⬜ Pendiente |

---

## 1. Decisiones tomadas

| # | Decisión | Por qué |
|---|---|---|
| D1 | **La pose del mantle se genera de forma procedimental.** Sin clip autoral. | Sirve para cualquier cuadrúpedo del mod sin animar uno por especie. |
| D2 | **El pipeline de locomoción es código propio del mod**, dimensionado a lo que necesitan sus critters (patas de un hueso primero). | Nada de lo que hay en el mod hace IK hoy; se construye la base mínima y se amplía por fases. |
| D3 | **El rig no depende de ninguna clase base de entidad.** Bound `T extends LivingEntity & ILocomotionSubject`. | Cualquier critter (o mob futuro) lo usa implementando una interfaz, sin tocar su jerarquía ni su sistema de animación. |
| D4 | **El mantle lo conduce el `MoveControl`, no un `Goal`.** | En DoL la trepada ya la manejan `CritterNodeEvaluator` + `CivetMoveControl` (lo dice el javadoc de `CivetClimbGoal`). Un `Goal` competiría por el flag `MOVE` con el path que lo originó. |
| D5 | **La fase del mantle se deriva de la Y del mundo, no de un reloj.** | Sin clip no hay duración. Un timer libre diverge entre cliente y servidor con lag; la Y ya viene sincronizada e interpolada. |
| D6 | **El contacto de cada pata lo decide el rig (heurística de apoyo) o el driver del mantle (override por pata).** Sin tablas de contacto por animación. | Sin clips autorales no hay nada que tabular; un canal de override por pata basta. |

---

## 2. Auditoría previa

### 2.1 DoL — lo que ya existe

| Punto | Estado hoy | Archivo |
|---|---|---|
| Estado de trepada sincronizado | `CLIMB_STATE` (byte) + `CLIMB_FACING_YAW` (float) | `common/entity/custom/CivetEntity.java:107-115` |
| Paso de un bloque | `setMaxUpStep(1)` en constructor **y reimpuesto cada tick** | `CivetEntity.java:158`, `:245-252` |
| Impulso de step-up | Si `dy > maxUpStep` y cerca: impulso vertical | `common/entity/ai/CivetMoveControl.java:92` |
| Nodos de trepada | Vecinos arriba/abajo junto a `CIVET_CLIMBABLE` | `common/entity/ai/CritterNodeEvaluator.java` |
| Selección de CLIMB_UP/DOWN | Desde el path | `CivetMoveControl.java:108` (`updateClimbDirectionFromPath`) |
| Pose de trepada | Hack: `renderToBuffer` rota el modelo −90° en X | `client/models/CivetModel.java:224` |
| Animación | `AnimationState` vanilla + `KeyframeAnimations.animate`; walk/run con **peso explícito** | `CivetModel.java:112`, `animateWalkWithWeight` |
| Clase base | `DiverseCritter extends TamableAnimal` | `common/entity/custom/base/DiverseCritter.java:43` |
| IK / adaptación al terreno | **No existe** | — |
| Tests | **No existe** `src/test` ni dependencia de JUnit | — |

### 2.2 Geometría del civet — números que condicionan el diseño

- `CivetModel.renderToBuffer` aplica **`scale(0.5)` + `translate(0, 1.5, 0)` propios**, y `CivetRenderer.scale`
  añade 0.65 (juvenil) o 0.45 (newborn). Cualquier cálculo de "dónde está esta suela en el mundo" que replique sólo
  el renderer mide en el sitio equivocado. Por eso el modelo expone esa transformación como hook (§4, tarea 0.5).
  El squirrel y el sifaka hacen lo mismo (`SquirrelModel.java:377`, `SifakaModel.java:238`).
- Con escala 0.5: **32 unidades de modelo por bloque**.
- Patas: un hueso cada una, caja `y ∈ [0, 11]` → **11 u** de cadera a suela → **0.34 bloques**.
- Caderas delantera y trasera a `z = ±6.5` → **0.41 bloques** entre ellas.
- Presupuesto de alcance, erguido a 90° sobre las patas traseras: 0.34 + 0.41 + 0.34 ≈ **1.09 bloques**,
  +15 % de estiramiento en la pata delantera ≈ 1.14. **El labio está a 1.0.** Llega, pero justo: cualquier
  pérdida (pitch < 90°, juvenil a 0.65 → 0.74 bloques) no llega. De ahí el micro-salto de §7.
- Un escalón de un bloque contra 0.41 de separación de caderas pide ~68° de inclinación: el plano de apoyo
  por sí solo nunca puede resolverlo, que es justamente por qué hace falta la maniobra.
- El pivote de `BandedPlamCivet` (la raíz, que hace de pelvis) está **a ras de suelo** (`y = 24`), no en las caderas.
  Para inclinar sobre un plano da igual —se compensa con la altura—, pero para encabritarse el pivote correcto
  es la cadera trasera. Lo resuelve la compensación de pivote de la Fase 3; contingencia en §10.
- El torso es **una sola caja** (`Torse`, 20 u). No puede curvarse: el arqueo del civet se expresa en
  `Head` y `Tail` contrarrotando, y en el pivote. Partir `Torse` en dos huesos es opcional (§10).

---

## 3. Arquitectura

### 3.1 Paquetes nuevos

```
common/locomotion/                     — sin tipos de cliente; lo usan ambos lados
    ILocomotionSubject.java            — interfaz que implementa la entidad
    LedgeSampler.java                  — sonda hacia adelante (Fase 1)
    MantlePhase.java                   — enum NONE/REACH/RISE/CREST/RECOVER
    MantleProgress.java                — Y del mundo → (fase, t local). Función pura (Fase 3)
    MantleProfile.java                 — constantes por especie: alcance, altura máx., velocidades

common/entity/ai/mantle/
    MantleController.java              — estado del mantle en servidor; lo posee la entidad (Fase 4)

client/locomotion/                     — pipeline de IK (Fase 0)
    LocomotionRig        base: gate, dt por frame, estado por entidad, protección contra NaN
    LegChain             hueso(s) de una pata, longitud y huella de la suela
    GroundProbe          altura del suelo bajo una huella (formas de colisión reales)
    StanceTracker        cuánto está apoyada cada pata, leído de la pose animada
    LocomotionState      estado por entidad (muelles, pesos, datos de debug)
    ModelSpace           pose del modelo ↔ mundo, con el hook de transformación del modelo
    ILocomotionModel     hook que implementa el modelo
    QuadrupedRig         rig de cuatro patas de un hueso
    SpineRules           respuestas secundarias de cabeza y cola (lag, estabilizar; ARCH en Fase 3)
    LocomotionDebug      overlay + dump + bypass
    math/                Spring, DampedFollower, PlaneFit, TrimmedMean, SupportFilter
    mantle/              MantleDriver, GripPlanner (Fase 3)

src/test/java/com/evirapo/diversityofcritters/...   — tests unitarios
```

### 3.2 Flujo por frame (cliente)

```
CivetModel.setupAnim
 ├─ clips vanilla (idle/walk/run…)           walk × (1 − autoridadMantle)
 └─ locomotionRig.apply(entity, this, pt)
     ├─ gate  ← ILocomotionSubject.isLocomotionSuspended()
     ├─ por pata: suela en el mundo (ModelSpace) + GroundProbe + StanceTracker
     ├─ MantleDriver.update                  lee mantle sincronizado → MantleProgress
     │    ├─ LedgeSampler (forma exacta del labio)
     │    ├─ GripPlanner → ancla + peso por pata delantera
     │    ├─ traseras: ancla al contacto capturado en REACH
     │    ├─ pitch de maniobra + compensación de pivote
     │    └─ arco de columna
     └─ QuadrupedRig.solve
          ├─ pesos de apoyo (SupportFilter) → PlaneFit → pitch/roll; TrimmedMean → altura
          ├─ pitch = lerp(plano, pitchMantle, autoridad)   límite de pitch ignorado bajo autoridad
          ├─ pata anclada → apuntar hueso al target + estirar; faltante → pelvis
          ├─ pata no anclada → contrarrotar + estirar hasta el suelo
          └─ SpineRules (LAG/STABILISE/ARCH)
```

### 3.3 Flujo por tick (servidor)

```
CivetMoveControl.tick
 ├─ ¿siguiente nodo +1 Y? → LedgeSampler → ¿mantle o climb? (§7.3)
 ├─ MantleController.start(lip, facing)       maxUpStep 0, yaw bloqueado, synced data
 ├─ MantleController.tick                     Y y XZ por perfil de fase; salidas (§7.3)
 └─ MantleController.finish/abort             restaura TODO en un único punto
```

---

## 4. Fase 0 — Pipeline base + rig de terreno en el civet (5 – 7 d)

**Entregable propio:** al terminar, el civet ya adapta las patas a escaleras, losas y bordes, aunque todavía no
haga mantle. Es el primer hito visible y tiene valor por sí mismo.

| # | Tarea | Detalle |
|---|---|---|
| 0.1 | Infra de tests | JUnit 5 en `build.gradle` (`junit-jupiter`, launcher, `test { useJUnitPlatform() }`). |
| 0.2 | `math/` + tests | `Spring` (muelle críticamente amortiguado por semivida), `DampedFollower` (subamortiguado, para el lag de cabeza/cola), `PlaneFit` (plano por mínimos cuadrados ponderado, con penalización de pendiente para contactos débiles y caso colineal), `TrimmedMean` (media ponderada del 50 % central: la altura en la que coinciden las patas, sin que una pata en un escalón arrastre al resto), `SupportFilter` (qué patas sostienen de verdad el cuerpo). Sin tipos de Minecraft. |
| 0.3 | Núcleo del rig | `ILocomotionSubject` (en `common/locomotion`; un método: `isLocomotionSuspended()` — la entidad decide: sueño, sentado, agua, trepada, cargado por CarryOn), `LocomotionState` + caché por entidad, `LegChain`, `GroundProbe`, `StanceTracker`, `LocomotionRig`. |
| 0.4 | `QuadrupedRig` | Por pata: elevación pedida → muelle (rápido al apoyar, lento al soltar) → peso de apoyo. Plano ponderado → pitch/roll acotados; `TrimmedMean` → altura de pelvis. Pata: contrarrota la inclinación y estira/encoge (±15 %) para el resto. Todo multiplicado por gate × contacto, y la altura se suelta en el aire. |
| 0.5 | `ModelSpace` + `ILocomotionModel` | `ILocomotionModel.applyRootTransform(PoseStack)` contiene el `scale(0.5)` + `translate(0,1.5,0)`; **`renderToBuffer` pasa a llamarlo** para que no haya dos copias. `ModelSpace.worldPos(...)` replica `LivingEntityRenderer` a mano: posición interpolada → `rotY(180 − bodyYaw)` → `scale(-1,-1,1)` → escala del renderer → `translate(0,-1.501,0)` → `applyRootTransform` → cadena de huesos. La escala del renderer sale de un `static float CivetRenderer.scaleFor(entity)` que usan ambos. |
| 0.6 | `SpineRules` | `LAG` (cola que sigue con retraso) y `STABILISE` (cabeza que conserva parte de su orientación en el mundo cuando el cuerpo se inclina). |
| 0.7 | `LocomotionDebug` | Overlay (F6), dump a log (F7), bypass (F9), categoría de teclas propia del mod. Registro en `client/event/DOCClientEvents.java`. |
| 0.8 | Rig en `CivetModel` | Construido una vez en el constructor: pelvis `BandedPlamCivet`, patas `Leg`, `Leg2`, `Leg3`, `Leg4`, escala de render 0.5. `apply(...)` al final de `setupAnim`, tras todos los `animate`. |
| 0.9 | Tests de conversión | Posición de la suela en el mundo con el civet en `(0,64,0)`, yaw 0 y 90, adulto y juvenil. Es el test que caza el problema de §2.2. |

**Orden real:** 0.5 va antes que 0.4, porque `QuadrupedRig` necesita la posición de cada suela en el mundo.

**Notas de lo hecho en 0.3**
- `GroundProbe` lee el suelo a través de `SurfaceQuery`; `BlockSurfaceQuery` lo implementa con las formas de
  colisión reales y **salta los salientes** (una forma cuyo fondo está > 0.25 por encima del pie es techo, no suelo).
- El recorrido de huesos usa sólo API pública (`ModelPart.visit` + `getChild`); no hace falta access transformer.
- Los tests no pueden arrancar `Bootstrap` de Minecraft fuera de FML, así que el escaneo de columnas se prueba con
  tramos sintéticos; la lectura de `VoxelShape` (orden de coordenadas `(z, x)` en el eje Y) se verifica en juego.
- `LocomotionRig.isActive` ignora copias que no están en el nivel (civet cargado con CarryOn, previews).
- `DiverseCritter` implementa `ILocomotionSubject`; `CivetEntity` añade excavar y rascar.

**Aceptación**
- `gradlew test` y `gradlew compileJava` en verde.
- F6 sobre un civet quieto en suelo plano: suelas corregidas a < 0.5 u del suelo; pitch/roll ≈ 0.
- Escalera de bloques completos y losas: patas apoyadas, sin temblor.
- F9 (bypass) devuelve exactamente la pose sin IK.
- Civet trepando un tronco (`CLIMB_UP`), durmiendo o sentado: rig inactivo (gate a 0), pose idéntica a hoy.
- Cero allocations por frame en el camino caliente (revisar con el perfilador del IDE sobre 20 civets).

---

## 5. Fase 1 — `LedgeSampler` (2 – 3 d)

La sonda hacia adelante. En `common/locomotion` porque la usan cliente (forma exacta del labio) y servidor
(decidir si hay mantle): **el mismo código en ambos lados es la mitad de la sincronía**.

**Firma**

```java
public static void sample(BlockGetter level, Vec3 feet, float headingYawDeg,
                          float bodyHalfWidth, float bodyHeight, MantleProfile profile,
                          LedgeSample out);
```

**`LedgeSample`** (holder reutilizable, cero allocations): `found`, `reason` (enum de rechazo, para el debug),
`wallAxis` (`Direction`), `wallCoord` (coordenada del plano), `lipY`, `topY` (superficie pasado el labio),
`lipHeight` (= `lipY − feet.y`), `headroom`, `continuesUp` (hay bloque trepable encima del labio), `confidence ∈ [0,1]`,
`lipX/Y/Z` del bloque.

**Algoritmo**
1. Dirección: heading del cuerpo, eje dominante (X o Z). Confianza × `|cos|` del ángulo con ese eje: un labio
   en diagonal aparece y desaparece suave, sin voto.
2. Marchar hacia adelante en pasos de 1/8 de bloque hasta `bodyHalfWidth + profile.probeDistance`.
3. En cada paso, tope de colisión real de la columna (`VoxelShape.max(Axis.Y)`), no `isSolid`.
4. Primer paso cuyo tope supere `feet.y + profile.minMantleHeight` (0.6): eso es la pared. Su tope es `lipY`.
5. Rechazos: `lipHeight > profile.maxMantleHeight` (1.0625 → vallas y muros fuera); sin hueco de `bodyHeight`
   sobre el labio (`noCollision` con la AABB en la cima); superficie de la cima no caminable (fluido, fuego).
6. Escaleras: si hay un escalón intermedio a ≤ 0.5 entre los pies y la pared, no es mantle, son dos pasos
   normales → `found = false`, `reason = STEPPED`.
7. `continuesUp` = el bloque sobre el labio tiene tag `CIVET_CLIMBABLE` (lo consume §7.3).

**Tests** — `LedgeSamplerTest`, con un `BlockGetter` sintético: bloque completo, losa inferior (no mantle), losa
superior colocada a 1 (sí), escalera (no), valla (no), muro de 2 (no), bloque con techo encima (no, `headroom`),
labio a 30° y a 45° (confianza decreciente), pared sin cima alcanzable, tronco de 1 y de 3 bloques (`continuesUp`).

**Aceptación:** tests en verde; en juego, el overlay dibuja plano de pared + línea de labio al acercarse a
cualquier bloque de la lista y nada ante losas/escaleras.

---

## 6. Fase 2 — Objetivos de pata anclados al mundo (2 – 3 d)

| # | Tarea | Detalle |
|---|---|---|
| 2.1 | Campos en el estado de pata | `anchorX/Y/Z` (double), `anchorWeight` (0 = sin ancla, la ruta de suelo de siempre), `anchorShortfall` (salida: lo que no se alcanzó, en u). |
| 2.2 | Mundo → pelvis | `ModelSpace` expone la matriz de la cadena hasta la pelvis; se invierte **una vez por frame** en una `Matrix4f` de scratch. |
| 2.3 | Rama anclada en `QuadrupedRig` | Sin ancla, la pata contrarrota la inclinación de la pelvis para quedar vertical. Con ancla: vector cadera→target en el marco de la pelvis → `xRot`/`zRot` del hueso para apuntar la suela → `yScale` para la longitud, acotado por un límite propio (más generoso que el de terreno, p. ej. 0.25) → el resto va a `anchorShortfall`. Mezcla con el resultado de suelo por `anchorWeight`. |
| 2.4 | Faltante a la pelvis | Si hay patas ancladas con faltante, la pelvis se desplaza la media ponderada: pelvis primero, pata después. |
| 2.5 | Tecla de prueba | En debug: fijar la pata delantera izquierda a la posición actual de su suela. |

**Test:** `QuadrupedAnchorTest` (escena sintética): ancla dentro de alcance → error < 0.25 u; fuera →
`anchorShortfall` = distancia sobrante; `anchorWeight = 0` → pose bit a bit igual que sin la rama.

**Aceptación:** con la tecla 2.5, la suela fijada se queda quieta en el mundo mientras el civet gira y anda
en el sitio (overlay: cruz del target y suela coincidentes).

---

## 7. Fases 3 y 4 — Maniobra (cliente + servidor)

### 7.1 Progreso anclado a la Y (común)

`MantleProgress.of(entityY, startY, lipY)` → `(phase, t)`, con `h = (entityY − startY) / (lipY − startY)`:

| Fase | Rango de `h` | Manos delanteras | Traseras | Pelvis (pitch) | Columna |
|---|---|---|---|---|---|
| REACH | primeros ~6 ticks, `h ≈ 0` (sub-progreso por tiempo *de servidor*, sincronizado vía tick de inicio) | dejan el suelo, se clavan en la cara a la altura alcanzable | ancladas al contacto capturado | 0 → 35° | recta |
| RISE | 0 → 0.55 | ancladas a la cara; suben en agarres alternos | ancladas; soportan todo | 35° → ~80° | recta |
| CREST | 0.55 → 1.0 | pasan el labio y se plantan en la cima | despegan (contacto 0), barren la cara | 80° → 20° | arqueo máximo |
| RECOVER | cima, avanzando `profile.recoverDistance` | en la cima | tocan la cara y luego la cima | 20° → 0 | se deshace |

Los umbrales viven en `MantleProfile`, no en el código. REACH es la única fase que no puede derivarse de la Y
(el cuerpo aún no ha subido): se sincroniza el tick de inicio y el cliente mide contra `level.getGameTime()`.

### 7.2 Fase 3 — `MantleDriver` + `GripPlanner` + arqueo (5 – 8 d)

| # | Tarea | Detalle |
|---|---|---|
| 3.1 | `MantlePhase`, `MantleProgress`, `MantleProfile` | Puras, con `MantleProgressTest` (bordes de fase, `h` fuera de rango, labio a 0.6 y a 1.0). |
| 3.2 | `GripPlanner` | Pura, sin tipos de Minecraft. Entrada: fase, `t`, distancia al plano, `lipY`, altura de hombro, alcance, nº de agarres. Salida por pata delantera: target relativo a la pared (altura, profundidad), peso de ancla, peso de swing. Agarres alternos izquierda/derecha; el swing entre agarres es un arco (sube, se separa de la pared, vuelve). `GripPlannerTest`: nunca dos manos en swing a la vez; target nunca por encima del labio antes de CREST; continuidad en los bordes de fase. |
| 3.3 | `MantleDriver` | Módulo del rig (se activa desde el builder con un `MantleProfile`), no un rig nuevo. Cada frame: lee el mantle de la entidad; si no hay, autoridad → 0 por muelle. Si hay: `MantleProgress` → `GripPlanner` → anclas de las delanteras; captura el contacto de las traseras al entrar en REACH y las ancla ahí hasta CREST; escribe el override de contacto por pata. |
| 3.4 | Autoridad y blend | La autoridad del mantle es un `Spring` (semivida 0.2 s), no un corte. `CivetModel` multiplica el peso de walk/run por `1 − autoridad` (ya existe el parámetro en `animateWalkWithWeight`). |
| 3.5 | Pitch de maniobra | En `QuadrupedRig`: `pitchTarget = lerp(plano, mantlePitch, autoridad)`; bajo autoridad no aplica el límite de pitch. |
| 3.6 | Compensación de pivote | El pitch se aplica en `BandedPlamCivet` (pivote a ras de suelo). El driver traslada la pelvis `R(pitch)·p − p`, con `p` = punto medio de las caderas traseras, para que el giro ocurra alrededor de ellas. Las traseras ancladas (3.3) absorben el error residual. |
| 3.7 | `SpineRules.ARCH` | Nueva regla: curvatura escalar repartida por peso: `.arch("Head", -0.5f).arch("Tail", 0.6f)`. En el civet (torso rígido) el arqueo es cabeza hacia la cima + cola de contrapeso. Test: con arco 0 las demás reglas dan exactamente lo mismo que antes. |
| 3.8 | Micro-salto | Si `lipHeight > profile.reach` (juvenil, o adulto con labio a 1.0 y mala geometría): el servidor mete un impulso al final de RISE (§7.3). En cliente no cambia nada: la Y sube más rápido y las fases la siguen. |
| 3.9 | Hack de 90° | No aplica al mantle: `renderToBuffer` sólo rota para `CLIMB_UP/DOWN/HANG`, y el mantle nunca pone esos estados (§7.3). Verificar que no se cruzan. |

**Recorte para el MVP:** 3.1 + 3.3 sin agarres (manos directas al labio) + 3.4 + 3.5 + 3.6. Sin 3.2 ni 3.7.

### 7.3 Fase 4 — Servidor: `MantleController` (2 – 3 d)

| # | Tarea | Detalle |
|---|---|---|
| 4.1 | Datos sincronizados | En `DiverseCritter` (lo reutilizan squirrel y sifaka): `MANTLE_LIP` (`OPTIONAL_BLOCK_POS`, vacío = sin mantle), `MANTLE_FACING` (byte, `Direction` horizontal), `MANTLE_START` (int, tick de inicio) y `MANTLE_START_Y` (float). La fase **no** se sincroniza: sale de la Y (D5). |
| 4.2 | Disparo | En `CivetMoveControl.tick`, rama del step-up (`:92`): si el siguiente nodo del path está a +1 Y y `LedgeSampler` da `found` con confianza ≥ 0.6 → `MantleController.start(...)` en lugar del impulso. |
| 4.3 | Exclusión con `CLIMB_UP` | Mantle sólo si `!continuesUp`. Con `continuesUp` sigue la trepada de siempre. Además, en `updateClimbDirectionFromPath` (`:108`): una columna trepable de altura 1 se trata como mantle, no como `CLIMB_UP`. `MantleController` nunca toca `CLIMB_STATE`, y la rama de `updateClimbState` que pasa a `CLIMB_UP` (`CivetEntity.java:836-855`, disparada por `horizontalCollision` + path que sube) no arranca si hay mantle activo. |
| 4.4 | `maxUpStep` | `CivetEntity.tick` reimpone `desiredStep` **cada tick** (`:245-252`): pasa a `desiredStep = isMantling() ? 0 : (newborn ? 0.5 : 1.0)`. Si no, vanilla sube el cuerpo de golpe y la animación queda decorativa. |
| 4.5 | Movimiento | Yaw bloqueado a `MANTLE_FACING` (patrón de `lockClimbRotation`). REACH: quieto contra la cara. RISE/CREST: Y por perfil de velocidad de `MantleProfile`, XZ contra el plano (a `bbWidth/2` de él). RECOVER: avanza sobre la cima y termina. Gravedad: `setNoGravity(true)` sólo entre RISE y CREST. |
| 4.6 | Salidas | Un **único** `finish()`/`abort()` restaura `noGravity`, `maxUpStep`, yaw y datos sincronizados. Motivos: timeout (`profile.maxTicks`, ~40), labio perdido (bloque roto/cambiado; re-muestreo cada 4 ticks), daño (`hurtTime > 0`), agua, sueño/sentado, cargado por CarryOn, path cancelado, entidad removida. Si `h > 0.8` al abortar → completar (snap a la cima); si no → soltar. |
| 4.7 | Red de seguridad | En `tick()`: si no hay mantle activo y `noGravity` quedó puesto por el mantle → quitarlo y avisar en log. Al cargar NBT, el mantle no se persiste: una entidad cargada nunca empieza a media maniobra. |
| 4.8 | Comando | `CivetDebugCommand`: `/civetdebug mantle <dirección>` fuerza una maniobra contra el bloque de enfrente. Imprescindible para capturas repetibles en la Fase 6. |

Sonidos/partículas por agarre: en cliente, disparados por eventos de `GripPlanner`. No hace falta paquete: la
fase ya es la misma en ambos lados.

### 7.4 Fase 5 — Debug del mantle (1 – 2 d, en paralelo con la 3)

Sobre el `LocomotionDebug` de la tarea 0.7:
- Texto: fase, `t`, `h`, autoridad, `lipHeight` vs alcance, motivo de rechazo de `LedgeSampler`.
- Mundo: plano de pared (quad), línea del labio, superficie de la cima, cruz por target anclado + suela alcanzada,
  pivote de la compensación, arco de columna.
- Dump F7: una fila por pata con ancla, peso, faltante; una fila de mantle.

Se hace en paralelo porque una pose generada no se tunea a ciegas: cada constante tiene que salir de una captura.

---

## 8. Fase 6 — Integración y tuning

| # | Tarea | Estimado |
|---|---|---|
| 6.1 | Civet adulto, completo. Ajuste de `MantleProfile` y de los pesos de arqueo sobre capturas con `/civetdebug mantle`. | 1 – 2 d |
| 6.2 | Civet juvenil (escala 0.65): verificar micro-salto y presupuesto. Newborn: excluido (`maxUpStep` 0.5, no necesita mantle). | 0.5 d |
| 6.3 | Squirrel: rig en `SquirrelModel` sobre los huesos raíz de cada pata (los hijos siguen rígidos); si hace falta doblar rodilla, solver analítico de dos huesos (ley de cosenos) en `math/`. `applyRootTransform` compartido con `translateToBone11` para que la capa de hojas no se desalinee; reconciliar con `SquirrelGlideGoal` y sus estados de trepada. | 2 – 3 d |
| 6.4 | Tuning general sobre capturas: losas, escaleras, bordes en diagonal, grupos de civets, lag simulado. | 3 – 5 d |

---

## 9. Estimación

Un desarrollador que ya conoce el mod. "Día" = jornada de trabajo enfocada.

| Fase | Estimado |
|---|---|
| 0 · Pipeline base + rig de terreno en el civet | 5 – 7 d |
| 1 · `LedgeSampler` + tests | 2 – 3 d |
| 2 · Anclaje + tests | 2 – 3 d |
| 3 · `MantleDriver` + `GripPlanner` + `ARCH` | 5 – 8 d |
| 5 · Debug del mantle (en paralelo con 3) | 1 – 2 d |
| 4 · `MantleController` en servidor | 2 – 3 d |
| 6.1–6.3 · Integración civet + squirrel | 3.5 – 5.5 d |
| 6.4 · Tuning | 3 – 5 d |
| **Total** | **23.5 – 36.5 d** |

En calendario: **5 – 7.5 semanas** a tiempo completo; **8.5 – 12 semanas** a ~3 días por semana.

### Hitos

| Hito | Contenido | Acumulado |
|---|---|---|
| H1 — Civet con IK de terreno | Fase 0 | 5 – 7 d |
| H2 — MVP de mantle | Fases 1, 2, recorte de 3 (§7.2) y 4 | 13 – 18 d |
| H3 — Mantle completo en el civet | Resto de 3, 5, 6.1, 6.2 | 18 – 28 d |
| H4 — Squirrel + tuning | 6.3, 6.4 | 23.5 – 36.5 d |

H2 es el punto de decisión: valida la idea con un civet que se apoya contra el bloque y se levanta, antes de pagar
el ciclo de agarres y el arqueo.

### Dónde está la varianza

- **Fase 3 y tuning**, casi toda. Al ser procedimental, el resultado vive entero en los números y no hay clip al que volver.
- **Fase 0**: la adaptación al terreno sin temblor (lecturas de suelo que saltan un bloque al cruzar un borde,
  patas a medio paso que parecen apoyadas) es donde se va el tiempo, no en la geometría.
- **Presupuesto de alcance** (§2.2): con 1.09 bloques frente a un labio a 1.0, la maniobra del adulto es posible
  pero justa. Si en captura no convence, el micro-salto pasa de caso raro a caso normal.
- **Fase 1** puede sorprender con bloques no cúbicos de otros mods.
- **Fase 4**: `CritterNodeEvaluator` y `CivetMoveControl` tienen ramas especiales (descenso desde la cima de una
  columna, amortiguación post-trepada) que asumen el step-up viejo.
- Fases 2 y 5 son las predecibles.

---

## 10. Riesgos y contingencias

| Riesgo | Contingencia |
|---|---|
| El pivote a ras de suelo de `BandedPlamCivet` no convence ni con compensación (3.6). | Insertar un hueso `Pelvis` a altura de cadera en `createBodyLayer`, padre de `Body` y de las cuatro patas, recalculando sus offsets para que la pose en reposo no cambie. Los clips de `CivetAnims` animan por nombre de hueso y no se tocan. ~0.5 d. |
| El arqueo con torso rígido no se lee. | Partir `Torse` en `TorseFront`/`TorseBack` en Blockbench. Toca textura y `CivetAnims` → decisión de arte, fuera del estimado. |
| La lectura del suelo salta al cruzar el borde de un escalón y la pelvis tiembla. | Límite de hundimiento de la pelvis y muelles asimétricos por pata (Fase 0.4); si no basta, suavizar la lectura del suelo sobre la huella en vez de por mayoría. |
| CarryOn levanta al civet a media maniobra. | Salida 4.6; el gate del rig ya lo cubre. |
| Sifaka | Queda fuera: es bípedo trepador (brazos colgando de `Body`), necesitaría un rig de patas articuladas y a 0.9 de alto apenas se beneficia. Si se quiere después: +4 – 6 d. |
| Lion | Fuera: no tiene trepada ni el perfil de cuerpo bajo. |

---

## 11. Forma de trabajo

- Rama `feature/mantle-ik`. Un commit por tarea numerada (0.1, 0.2, …), tests en verde en cada uno.
- Antes de cerrar cada fase: `gradlew test`, `gradlew compileJava` y la lista de aceptación en juego.
- Este documento se actualiza al cerrar cada fase (tabla de estado + lo que haya cambiado respecto al plan).
