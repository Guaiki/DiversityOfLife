# Spec: Wariness & Flight System

Sistema de detección por zonas concéntricas y huida para `DiverseCritter`, construido como base
genérica reutilizable. Primera especie visible: la civeta, que ya tiene los clips `ALERT_STARTING` y
`ALERT` en `CivetAnims`.

Complementa [spec-goals-animaciones.md](../../spec-goals-animaciones.md) (reparto de prioridades y
`PhasedActivityGoal`) y [deluxelib-anim-integration.md](../../deluxelib-anim-integration.md).

---

## 1. Hallazgos previos que cambian el alcance

Antes de diseñar nada, tres cosas que ya están en el repo y que el documento de encargo no sabía.

### 1.1 La interrupción del sueño está construida a medias

`SleepBehaviorGoal.canContinueToUse()` ya hace despertar forzado:

```java
if (entity.hurtTime > 0 || entity.isInWater()) {
    this.startWaking(true);   // -> SleepState.AWAKENING -> el clip suena entero
    return true;
}
```

Y `stop()` ya arma un cooldown de re-dormirse (`noSleepUntilTick`), y `canKeepSleeping()` ya
implementa el *"window-aware recovery"* del encargo. Lo que falta es **un punto de entrada para la
amenaza** y **un plazo distinto** para ese caso.

### 1.2 `ISleepThreatEvaluator` e `ISleepAwareness` son API muerta

Declaradas en `common.entity.util.sleep`, nunca implementadas ni llamadas. Este sistema no las usa:
la detección vive en `ThreatSensor` y el predicado en `DiverseCritter.isThreat()`. **Se borran** como
parte del trabajo — dejarlas induce a pensar que hay dos sistemas de amenazas.

### 1.3 Sólo la civeta tiene los clips de alerta

`ALERT_STARTING` y `ALERT` existen únicamente en `CivetAnims`. `SquirrelAnims` y `SifakaAnims` no los
tienen. Por eso el sistema es **opt-in por especie** (§7).

---

## 2. Decisiones tomadas

| # | Decisión | Motivo |
|---|---|---|
| D1 | **Sólo critters no domesticados.** | Elimina la lógica de exención de dueño del encargo: todo se cierra con `!isTame()`. Riesgo de regresión cero sobre lo domesticado. |
| D2 | **`TemptGoal` se limita a domesticados.** | Tira del animal *hacia* el jugador mientras la alerta lo aleja: dos goals peleando por `MOVE` con intenciones opuestas dan tembleque. Gateado por `isTame()` los dos sistemas nunca coexisten. Conserva poder llamar con comida a un pet en modo *wandering*, donde `FollowOwnerGoal` no corre. |
| D3 | **Amenaza = jugadores en v1, con predicado abierto.** | Hoy **ningún mob caza** a estos critters: los lobos vanilla no atacan bichos moddeados. Construir el filtrado y las pruebas de una categoría que nada satisface es trabajo no verificable. `isThreat(LivingEntity)` queda sobrescribible. |
| D4 | **Alerta congelada todo el rato, con timer de 4 s** (no 10). | 10 s clavado deja rebaños petrificados y obliga al goal a retener `MOVE` una eternidad. 4 s es tenso y asumible. |
| D5 | **La alerta y la huida respetan `AnimationLockGoal`.** | Los `*_exit` de las actividades llevan `locksMovement()` y el lock está en prioridad 0. Saltárselo obliga a reordenar prioridades ya estabilizadas y devuelve los cortes duros de pose. Medio segundo de retardo se lee como tiempo de reacción, no como fallo. |
| D6 | **Arquitectura: sensor en la entidad + dos goals finos.** | El sueño necesita detectar amenazas *mientras es él quien retiene al bicho*. Si la detección viviera en un goal, habría que duplicarla dentro de `SleepBehaviorGoal` con sus propios radios y su propio LoS, y las dos copias se desincronizarían. |
| D7 | **La huida es sólo por suelo; trepar viene después, no durante.** | Integrar la trepada *en* la huida es un trozo de trabajo comparable a todo lo demás. Al soltar `MOVE`, `CivetClimbGoal` toma el relevo por sí solo: el bicho escapa corriendo y acaba subido a un árbol. Efecto casi idéntico, coste cero. |

---

## 3. Arquitectura

```
DiverseCritter
├─ ThreatSensor sensor          servidor, tickeado desde aiStep()
│    ├─ scan()                  cada 5 ticks: jugadores en outerRadius + LoS
│    └─ estado CALM / ALERT / FLEE   ← dueño del timer de 4 s
├─ IS_ALERT, IS_FLEEING         EntityDataAccessor<Boolean>, sincronizados
├─ isThreat(LivingEntity)       predicado sobrescribible
└─ isWary()                     false por defecto — opt-in por especie
```

El sensor es **el cerebro**; los goals son **las manos** y quedan casi vacíos:

```java
class CritterFleeGoal extends Goal {      // prio 2, MOVE
    public boolean canUse() { return mob.sensor().isFleeing(); }
}
class CritterAlertGoal extends Goal {     // prio 2, MOVE+LOOK
    public boolean canUse() { return mob.sensor().isAlerted(); }
}
```

Comparten prioridad 2 porque son **mutuamente excluyentes por construcción**: el sensor está en un
solo estado. Con 2 les basta para ganar a apareamiento (4), tentación (5), seguir al dueño (6),
atacar (7), actividades (8+) e idle (17+).

### 3.1 Cuatro consumidores, un solo sensor

| Consumidor | Qué le pide |
|---|---|
| `CritterAlertGoal` | `isAlerted()` |
| `CritterFleeGoal` | `isFleeing()`, `threatPos()` |
| `SleepBehaviorGoal` | `hasThreat()` para el despertar; `lastThreatTick()` para el cooldown de re-dormirse |
| `/doc_wariness` | todo el estado, en crudo |

Esa tabla **es** el argumento de D6.

### 3.2 Lo que no hay que construir

- **La huida no necesita animación nueva.** El goal hace `setSprinting(true)`; el clip `run` ya está
  registrado en las tres especies con `playCondition = isMoving() && isSprinting()`.
- **La cancelación de actividades tampoco.** Registrar la huida en prioridad 2 desaloja
  `CritterCleanChainGoal` (8) y compañía, y su `stop()` ya reproduce el clip de salida.
- **El secuenciado tras el despertar tampoco.** El goal de sueño (1) retiene hasta que el clip
  `AWAKENING` termina; al soltar `MOVE`, la huida (2) se lo lleva.

### 3.3 Exclusiones

El sistema se apaga entero si `isTame()`, si `!isWary()`, o si `isNewborn()` — un recién nacido no
supera a nada corriendo, y su cadena de llanto (prio 2) importa más.

---

## 4. El sensor

### 4.1 Escaneo

Cada **5 ticks**: `level.getEntitiesOfClass(Player.class, box.inflate(outerRadius))`, filtrado por
`isThreat()` y por `mob.getSensing().hasLineOfSight()` — que vanilla cachea por tick.

5 ticks basta: el timer son 80 ticks, así que la granularidad mete un 6 % de error imperceptible. Un
jugador lo bastante rápido como para colarse entre dos escaneos ya está en la zona roja, que dispara
igual.

El LoS aplica **también a la zona roja**, a propósito: un jugador pegado al otro lado de una pared no
es una amenaza, y es lo que hace que acercarse por detrás de cobertura funcione.

### 4.2 Máquina de estados

```
CALM
 ├─ amenaza en zona roja              → FLEE
 └─ amenaza en zona amarilla          → ALERT, timer = alertDwellTicks()

ALERT                                   IS_ALERT=true, congelado
 ├─ nadie en la amarilla               → CALM
 ├─ amenaza en la roja                 → FLEE
 ├─ alguna amenaza esprintando         → FLEE
 ├─ todas agachadas                    → timer congelado
 ├─ si no                              → timer--, y al llegar a 0 → FLEE

FLEE                                    IS_FLEEING=true, setSprinting(true)
 ├─ nadie a menos de safeDistance      → CALM
 └─ llevo maxFleeTicks huyendo         → CALM  (acorralado, se rinde)

CUALQUIERA
 └─ getLastHurtByMob() vivo y cargado  → cuenta como amenaza, ignorando radios y LoS
```

### 4.3 Varias amenazas: reglas asimétricas

- **Zona roja y sprint:** basta **una**.
- **Agachado:** el timer sólo se congela si **todas** las de la zona amarilla lo están.

Si bastara con una agachada, dos jugadores romperían el sistema: uno se agacha y el otro entra
corriendo con impunidad.

### 4.4 Anti-loop por histéresis, no por cooldown

Invariante:

```
safeDistance() > outerThreatRadius()
```

Al terminar la huida la amenaza ya está fuera de la zona amarilla, así que se vuelve a `CALM` sin
poder re-alertarse en el acto. Un cooldown temporal haría lo mismo pero dejando una ventana en la que
el bicho te ignora; la histéresis no tiene ese agujero.

**No se comprueba con `assert`**: las aserciones de Java están desactivadas en runtime salvo que se
arranque con `-ea`, así que una configuración mal puesta pasaría desapercibida justo en producción. El
sensor **loguea un error una vez y clampa** `safeDistance` a `outerThreatRadius() * 1.5`. Mejor un
critter con los radios corregidos y un aviso en el log que uno en bucle silencioso.

`maxFleeTicks` cubre el caso del acorralado, que nunca alcanzaría `safeDistance` y huiría para
siempre.

### 4.5 Daño como disparador incondicional

Quien te ha pegado **entra en la lista de amenazas ignorando radios y LoS**, en vez de ser una
transición aparte. Así la huida termina por la regla normal de `safeDistance` y no hay un segundo
camino de salida que mantener.

Sin esto, una flecha desde 30 bloques dejaría al critter quieto y el sistema parecería roto en el caso
más obvio. Se descarta si la entidad ha muerto o se ha descargado; vanilla mantiene `lastHurtByMob`
unos 100 ticks, tiempo de sobra para que la huida arranque.

### 4.6 Deuda declarada

El escaneo consulta `Player.class` porque es lo barato. Añadir depredadores exigirá tocar **dos**
sitios: sobrescribir `isThreat()` y ampliar esa consulta en `ThreatSensor.scan()`. Un
`threatScanClass()` sobrescribible lo dejaría en uno, pero es un punto de extensión de más para algo
que hoy no existe. Queda anotado en el javadoc de `isThreat()`.

---

## 5. Integración con el sueño

### 5.1 Despertar forzado: una línea

```java
// SleepBehaviorGoal.canContinueToUse()
if (entity.hurtTime > 0 || entity.isInWater() || entity.sensor().hasThreat()) {
    this.startWaking(true);
    return true;
}
```

`startWaking` pasa de `private` a *package-private* o a un método público acotado; el resto de la
máquina de fases no cambia.

### 5.2 El cooldown de re-dormirse, sin re-armar nada

El encargo pide que la cuenta de 5 minutos empiece sólo cuando la zona esté despejada y que se
**re-arme** mientras siga habiendo amenazas. Se resuelve con una resta, no con un timer:

```java
// el sensor, en cada escaneo con resultado
if (threatFound) this.lastThreatTick = mob.tickCount;

// el goal de sueño, en canUse()
tickCount - sensor.lastThreatTick() >= reSleepCooldownTicks()
```

Mientras haya alguien cerca, `lastThreatTick` se refresca y la diferencia nunca crece. Cuando el
último se va, la cuenta arranca sola. Es el mismo idioma de ticks absolutos que el sueño ya usa para
evitar los contadores que avanzan a media velocidad.

**Va separado del `FORCED_WAKE_COOLDOWN` existente** (600 ticks). Ése cubre daño y agua; subirlo a 5
minutos haría que salpicarte con agua bloqueara el sueño durante cinco minutos. Dos causas distintas,
dos plazos distintos.

El *"window-aware recovery"* es **cero trabajo**: `canUse()` ya exige `inSleepWindow()`.

---

## 6. Animaciones y estado sincronizado

```java
alert_enter = ALERT_STARTING, PLAY_ONCE, 0.5F, P_ALERT
alert_loop  = ALERT,          REPEATING, 0.5F, P_ALERT

alert_enter.blendInMs(120).blendOutMs(150).setNextAnimation("alert_loop");
alert_loop.blendInMs(120).blendOutMs(200).setPlayCondition(a -> this.isAlert());
```

`P_ALERT = 4`, **compartida con `P_ATTACK`**. No chocan: `CritterAlertGoal` (prioridad de goal 2)
desaloja al goal de ataque (7), así que dejan de producirse impactos y con ellos los clips de ataque.

**No hay clip de salida y no hace falta:** al caer `isAlert()` el bucle se para e idle entra por su
`blendOutMs`, igual que nadar o trepar.

La entrada la lanza el goal con `play()`, no el auto-arranque. El orden de ticks lo respalda: Forge
dispara `LivingTickEvent` —donde corre `animator().tick()`— **antes** de `aiStep()`, que es donde el
sensor enciende `IS_ALERT`. En el tick en que se alerta el animator aún no ve la condición; el goal
arranca después y hace `play(alert_enter)`. Al tick siguiente `alert_loop` no puede pisarla porque el
guarda del auto-arranque es `current.getPriority() <= base.getPriority()` y comparten prioridad.

`ALERT_STARTING` debe entrar en `FORCE_NON_LOOPING` de `tools/swap_civet_anims.py` (ya está) para que
un re-export no la devuelva marcada como looping.

### 6.1 `IS_FLEEING` no dibuja nada

El galope sale de `isMoving()` y `isSprinting()`, ya sincronizados. `IS_FLEEING` existe para
introspección — el comando de debug, y que otro goal pueda preguntar sin hablar con el sensor, que es
servidor. El encargo lo pide explícitamente; su valor es ése y no el visual.

---

## 7. Configuración por especie

Métodos sobrescribibles en `DiverseCritter`, el idioma que ya usan `getPreparingSleepDuration()` y
`comfortTierMin()`:

```java
public boolean isWary()               { return false; }   // opt-in
public double  innerThreatRadius()    { return 4.0D;  }
public double  outerThreatRadius()    { return 10.0D; }
public int     alertDwellTicks()      { return 80;    }   // 4 s
public double  fleeSpeed()            { return 1.6D;  }
public double  safeDistance()         { return 16.0D; }   // invariante: > outerThreatRadius()
public int     maxFleeTicks()         { return 200;   }   // 10 s
public int     reSleepCooldownTicks() { return 6000;  }   // 5 min
```

**`isWary()` en `false` por defecto es la decisión importante.** La ardilla y la sifaka no tienen
clips de alerta; encenderlas las dejaría congeladas 4 segundos con la pose de idle, que se lee como
un bug y no como miedo. Con opt-in, la base carga todo el sistema y una especie se suma con **una
línea** cuando lleguen sus clips. Eso es el *"plug-and-play expansion"* del encargo.

`CivetEntity` sobrescribe `isWary()` a `true` y ajusta radios.

---

## 8. Debug

Comando **nuevo y genérico**, `/doc_wariness` — el sistema vive en `DiverseCritter` y `/civet_debug`
es sólo de civeta.

Por cada critter salvaje en el radio imprime: estado (`CALM`/`ALERT`/`FLEE`), amenaza más cercana con
distancia, zona y LoS, timer restante y si está congelado por agachados, y la cuenta atrás de
re-dormirse.

Y sobre todo **por qué está apagado cuando lo está** — domesticado, `!isWary()`, o recién nacido.
Mismo formato que la sección de puertas de `/civet_debug`: lo que ahorra tiempo no es ver el estado
cuando funciona, sino saber qué puerta está cerrada cuando no pasa nada.

Los radios visuales se dibujan con **partículas emitidas desde servidor** sobre las dos
circunferencias mientras el debug esté activo: funciona en multijugador y no necesita código de
cliente.

---

## 9. QA

**En este repo no hay infraestructura de tests**: `src/test/java` existe pero está vacío, no hay
JUnit en `build.gradle`, y la única configuración es la de GameTest de Forge sin ningún test
registrado.

El alcance de QA es, por tanto, **checklist de verificación en juego + el comando de debug como
instrumento**:

1. Jugador andando entra en la zona amarilla → alerta congelada, 4 s, luego huida.
2. Jugador esprintando entra en la amarilla → huida inmediata, sin esperar el timer.
3. Jugador agachado en la amarilla → timer congelado indefinidamente; se puede llegar y domesticar.
4. Dos jugadores, uno agachado y otro no → el timer **corre** (§4.3).
5. Entrada directa en la zona roja → huida inmediata.
6. Amenaza tras una pared → no dispara nada (LoS).
7. Flechazo desde lejos → huida (§4.5).
8. Critter durmiendo + amenaza → despertar con el clip completo, y **después** huida.
9. Zona despejada 5 min y dentro de la ventana de sueño → se vuelve a dormir. Fuera de ventana → no.
10. Critter acicalándose + susto → el `clean_exit` suena entero, luego reacciona (D5).
11. Critter domesticado → el sistema no se activa; `TemptGoal` sí funciona (D2).
12. Recién nacido → no se activa.
13. Huida contra una pared → se rinde a los `maxFleeTicks`.
14. Tras huir, con un árbol cerca → `CivetClimbGoal` toma el relevo (D7).
15. Coste: 20+ civetas salvajes en pantalla sin caída de TPS.

Si se quiere un suite automatizado de verdad, hay que montar JUnit y un arnés capaz de instanciar un
`Level`: es trabajo aparte y se presupuesta aparte.

---

## 10. Fuera de alcance

- Creación de modelos, texturas y clips nuevos. El cliente aporta los de alerta y carrera.
- Depredadores como amenaza (D3). El predicado queda abierto.
- Trepar **durante** la huida (D7). Trepar *después* sí entra.
- Alerta en ardilla y sifaka hasta que existan sus clips: la base queda cableada y en `isWary()=false`.
- Suite de tests automatizado (§9).

---

## 11. Orden de implementación

1. `ThreatSensor` + `isThreat()` + `isWary()` + los flags sincronizados, sin goals. Verificable con el
   comando de debug antes de que nada se mueva.
2. `/doc_wariness`, incluido el "por qué está apagado".
3. `CritterAlertGoal` + registro de `alert_enter`/`alert_loop` en la civeta.
4. `CritterFleeGoal`.
5. Integración con `SleepBehaviorGoal` y `reSleepCooldownTicks()`.
6. `TemptGoal` gateado por `isTame()` en las tres especies (D2).
7. Borrado de `ISleepThreatEvaluator` e `ISleepAwareness` (§1.2).
8. Ajuste de parámetros en juego con el checklist de §9.

El paso 1 es deliberadamente el más grande y el primero: el sensor es la única pieza con lógica de
verdad, y se puede observar entera con el comando antes de conectarle un solo goal.
