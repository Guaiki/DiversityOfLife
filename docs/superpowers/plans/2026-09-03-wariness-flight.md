# Wariness & Flight System — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un critter salvaje detecte jugadores en dos zonas concéntricas, se alerte congelándose y huya, integrado con el ciclo de sueño existente.

**Architecture:** Un `ThreatSensor` vive en `DiverseCritter` y es el cerebro: escanea, mantiene la máquina `CALM/ALERT/FLEE` y escribe dos flags sincronizados. Dos goals finos leen esos flags y actúan. El sueño y el comando de debug consultan el mismo sensor, que es lo que evita tener dos detectores desincronizados.

**Tech Stack:** Minecraft 1.20.1, Forge 47.4.10, Parchment 2023.09.03, DeluxeLib 1.4.11-1.20.1 para animaciones.

Spec: [2026-09-03-wariness-flight-design.md](../specs/2026-09-03-wariness-flight-design.md)

## Global Constraints

- **No hay tests automatizados en este repo.** `src/test/java` está vacío, no hay JUnit en `build.gradle`, y la config de GameTest de Forge no tiene ningún test registrado. El ciclo de verificación de cada tarea es: **compilar → comprobación estática → comprobación en juego con comandos → commit**. No inventes un `src/test`; montar ese arnés es trabajo aparte.
- **Nunca ejecutar `gradlew clean`.** Rompe el `fg_cache` de ForgeGradle y produce errores engañosos que cuestan una tarde.
- Comando de compilación: `./gradlew compileJava --console=plain`. Cliente de pruebas: `./gradlew runClient`.
- Id de la entidad de pruebas: `diversityofcritters:civet`.
- Todos los comandos del mod exigen permiso 2: en un mundo de un jugador basta activar trucos; si no, `/op <jugador>`.
- Comentarios y javadoc **en español**, como el resto del repo. Sin tildes dentro de literales de código Java para evitar problemas de encoding; en javadoc sí se usan.
- La invariante `safeDistance() > outerThreatRadius()` **no** se comprueba con `assert` (Java las desactiva en runtime sin `-ea`): se loguea y se clampa.

---

## Estructura de archivos

**Se crean:**

| Archivo | Responsabilidad |
|---|---|
| `common/entity/util/ThreatSensor.java` | Escaneo + máquina de estados. Única pieza con lógica de verdad. No sabe de animaciones ni de goals. |
| `common/entity/ai/CritterAlertGoal.java` | Congela y lanza el clip de alerta mientras `sensor().isAlerted()`. |
| `common/entity/ai/CritterFleeGoal.java` | Corre en dirección contraria a la amenaza mientras `sensor().isFleeing()`. |
| `common/command/WarinessDebugCommand.java` | `/doc_wariness`: estado del sensor, y por qué está apagado cuando lo está. |

**Se modifican:**

| Archivo | Cambio |
|---|---|
| `common/entity/custom/base/DiverseCritter.java` | Campo del sensor, flags `IS_ALERT`/`IS_FLEEING`, `isThreat()`, `isWary()`, la configuración por especie, `P_ALERT`, y el tick del sensor en `aiStep()`. |
| `common/entity/ai/SleepBehaviorGoal.java` | Despertar por amenaza y puerta del cooldown de re-dormirse. |
| `common/entity/custom/CivetEntity.java` | `isWary()`, radios, registro de los dos goals y de los clips de alerta. |
| `common/entity/custom/SquirrelEntity.java`, `SifakaEntity.java` | `TemptGoal` gateado por `isTame()`. |
| `common/event/DOCForgeEvents.java` | Registro del comando. |

**Se borran:** `common/entity/util/sleep/ISleepThreatEvaluator.java`, `common/entity/util/sleep/ISleepAwareness.java` (API muerta, §1.2 del spec).

---

## Orden y por qué

El sensor es lo primero **y va acompañado de su comando** en las dos primeras tareas. Un sensor sin forma de observarlo no es verificable, y la regla de esta lista es que cada tarea termine en algo que puedas comprobar tú mismo. Con las tareas 1 y 2 hechas, el cerebro entero es observable **antes de que ningún goal cambie el comportamiento del bicho**: caminas hacia una civeta y ves la máquina de estados moverse mientras ella sigue a lo suyo.

---

### Task 1: Configuración, flags y comando de debug

Sin sensor todavía. Al terminar, `/doc_wariness` funciona y dice que el sistema está apagado — que es la verdad, porque ninguna especie ha hecho opt-in.

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java`
- Create: `src/main/java/com/evirapo/diversityofcritters/common/command/WarinessDebugCommand.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/event/DOCForgeEvents.java`

**Interfaces:**
- Produces: `DiverseCritter.isWary()`, `isThreat(LivingEntity)`, `innerThreatRadius()`, `outerThreatRadius()`, `alertDwellTicks()`, `fleeSpeed()`, `safeDistance()`, `maxFleeTicks()`, `reSleepCooldownTicks()`, `isAlert()`, `setAlert(boolean)`, `isFleeing()`, `setFleeing(boolean)`, `P_ALERT`. Los usan las tareas 2-6.

- [ ] **Step 1: Añadir los dos flags sincronizados a `DiverseCritter`**

Junto a los demás `EntityDataAccessor` (busca `IS_CRYING`, sobre la línea 96):

```java
    private static final EntityDataAccessor<Boolean> IS_ALERT = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FLEEING = SynchedEntityData.defineId(DiverseCritter.class, EntityDataSerializers.BOOLEAN);
```

Y en `defineSynchedData()`, junto a los demás `define`:

```java
        this.entityData.define(IS_ALERT, false);
        this.entityData.define(IS_FLEEING, false);
```

- [ ] **Step 2: Añadir los accesores y la configuración por especie**

Al final de `DiverseCritter`, antes de la llave de cierre de la clase:

```java
    // ─────────────────────────────────────────────────────────────────────────
    //  WARINESS & FLIGHT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Los escribe {@code ThreatSensor} en servidor; el cliente los lee para las playCondition y el
     * comando de debug para introspeccion.
     *
     * <p><b>{@code IS_FLEEING} no dibuja nada.</b> El galope sale de {@code isMoving()} y
     * {@code isSprinting()}, que ya viajan sincronizados. Existe para que otro goal pueda preguntar
     * "esta huyendo?" sin hablar con el sensor, que es solo de servidor.
     */
    public boolean isAlert() { return this.entityData.get(IS_ALERT); }
    public void setAlert(boolean value) { this.entityData.set(IS_ALERT, value); }

    public boolean isFleeing() { return this.entityData.get(IS_FLEEING); }
    public void setFleeing(boolean value) { this.entityData.set(IS_FLEEING, value); }

    /**
     * Opt-in por especie. **Falso por defecto a proposito**: la ardilla y la sifaka no tienen clips
     * de alerta, y encenderlas las dejaria congeladas cuatro segundos con la pose de idle puesta,
     * que se lee como un bug y no como miedo. Cuando lleguen sus clips, se suman con esta linea.
     */
    public boolean isWary() { return false; }

    /**
     * Que cuenta como amenaza. Por defecto cualquier jugador que no sea espectador.
     *
     * <p>Los de creativo SI cuentan, para poder probar el sistema volando.
     *
     * <p><b>Deuda declarada:</b> anadir depredadores exige tocar dos sitios — sobrescribir esto y
     * ampliar la consulta de {@code ThreatSensor.scan()}, que hoy pide solo {@code Player.class}
     * porque es lo barato. Hoy ningun mob del juego caza a estos critters, asi que la rama seria
     * codigo sin nada que la dispare.
     */
    public boolean isThreat(LivingEntity other) {
        return other instanceof Player player && !player.isSpectator();
    }

    /** Zona roja: huida inmediata. */
    public double innerThreatRadius() { return 4.0D; }
    /** Zona amarilla: alerta y arranque del temporizador. */
    public double outerThreatRadius() { return 10.0D; }
    /** Cuanto aguanta alertado antes de salir corriendo. 80 ticks = 4 s. */
    public int alertDwellTicks() { return 80; }
    /** Multiplicador de velocidad al huir. */
    public double fleeSpeed() { return 1.6D; }
    /**
     * Distancia a la que deja de huir. **Invariante: mayor que {@link #outerThreatRadius()}.** Es el
     * anti-loop: al terminar la huida la amenaza ya esta fuera de la zona amarilla, asi que no puede
     * re-alertarse en el acto. {@code ThreatSensor} loguea y clampa si se configura mal.
     */
    public double safeDistance() { return 16.0D; }
    /** Techo de la huida, para el acorralado que nunca alcanzaria safeDistance. 200 ticks = 10 s. */
    public int maxFleeTicks() { return 200; }
    /** Espera sin amenazas antes de volver a dormirse. 6000 ticks = 5 min. */
    public int reSleepCooldownTicks() { return 6000; }
```

- [ ] **Step 3: Añadir la prioridad de animación `P_ALERT`**

Junto a las demás, después de `P_ATTACK` (sobre la línea 161):

```java
    /**
     * Misma que {@link #P_ATTACK} a proposito. No chocan: {@code CritterAlertGoal} esta en prioridad
     * de goal 2 y desaloja al goal de ataque (7), asi que dejan de producirse impactos y con ellos
     * los clips de ataque.
     */
    protected static final int P_ALERT = 4;
```

- [ ] **Step 4: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Crear `WarinessDebugCommand`**

```java
package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code /doc_wariness}: estado del sensor de amenazas de los critters cercanos.
 *
 * <p>Lo que mas tiempo ahorra no es ver el estado cuando el sistema funciona, sino saber **que
 * puerta esta cerrada** cuando no pasa nada: domesticado, {@code !isWary()} o recien nacido apagan
 * el sistema entero y desde fuera se ve igual que un bug.
 */
public class WarinessDebugCommand {

    private static final double RADIUS = 16.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_wariness")
                .requires(source -> source.hasPermission(2))
                .executes(context -> run(context.getSource())));
    }

    private static int run(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<DiverseCritter> critters = source.getLevel().getEntitiesOfClass(
                DiverseCritter.class,
                new AABB(pos.x - RADIUS, pos.y - RADIUS, pos.z - RADIUS,
                         pos.x + RADIUS, pos.y + RADIUS, pos.z + RADIUS));

        if (critters.isEmpty()) {
            source.sendFailure(Component.literal("[Wariness] No hay critters a " + (int) RADIUS + " bloques."));
            return 0;
        }

        send(source, "§e=== WARINESS (" + critters.size() + " critters) ===");
        for (DiverseCritter critter : critters) {
            send(source, "§f[" + critter.getId() + "] " + critter.getType().getDescriptionId());
            send(source, "  §7zonas: roja=" + fmt(critter.innerThreatRadius())
                    + "  amarilla=" + fmt(critter.outerThreatRadius())
                    + "  segura=" + fmt(critter.safeDistance())
                    + "  dwell=" + critter.alertDwellTicks() + "t");
            send(source, "  §7flags: alert=" + critter.isAlert() + " fleeing=" + critter.isFleeing());
            send(source, "  " + reason(critter));
        }
        send(source, "§e=== FIN ===");
        return 1;
    }

    /** Por que esta apagado, o que esta haciendo si no lo esta. */
    private static String reason(DiverseCritter critter) {
        if (!critter.isWary())      return "§cAPAGADO §7- la especie no ha hecho opt-in (isWary() = false)";
        if (critter.isTame())       return "§cAPAGADO §7- domesticado; el sistema es solo para salvajes";
        if (critter.isNewborn())    return "§cAPAGADO §7- recien nacido";
        if (!critter.isAlive())     return "§cAPAGADO §7- muerto";
        return "§aACTIVO";
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static void send(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
    }
}
```

- [ ] **Step 6: Registrar el comando**

En `DOCForgeEvents.onCommandsRegister`, junto a los otros tres:

```java
        WarinessDebugCommand.register(event.getDispatcher());
```

Y el import correspondiente.

- [ ] **Step 7: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Comprobar en juego**

```bash
./gradlew runClient
```

Crea un mundo creativo con trucos y ejecuta:

```
/summon diversityofcritters:civet ~ ~ ~
/doc_wariness
```

Esperado — la parte importante es la última línea:

```
=== WARINESS (1 critters) ===
[123] entity.diversityofcritters.civet
  zonas: roja=4.0  amarilla=10.0  segura=16.0  dwell=80t
  flags: alert=false fleeing=false
  APAGADO - la especie no ha hecho opt-in (isWary() = false)
=== FIN ===
```

Si dice `APAGADO`, la tarea está bien: el sistema todavía no existe y el comando lo dice en vez de mentir.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/java/com/evirapo/diversityofcritters/common/command/WarinessDebugCommand.java src/main/java/com/evirapo/diversityofcritters/common/event/DOCForgeEvents.java
git commit -m "Wariness: configuracion por especie, flags sincronizados y /doc_wariness

El comando llega antes que el sensor a proposito: sin forma de observarlo, un
sensor no es verificable. De momento informa de que el sistema esta apagado
porque ninguna especie ha hecho opt-in, que es la verdad.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: El sensor

El cerebro entero, observable **antes** de que ningún goal cambie el comportamiento. Al terminar, caminas hacia una civeta salvaje y ves la máquina de estados moverse en el comando mientras ella sigue paseando tan tranquila.

**Files:**
- Create: `src/main/java/com/evirapo/diversityofcritters/common/entity/util/ThreatSensor.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/command/WarinessDebugCommand.java`

**Interfaces:**
- Consumes: toda la configuración de la Task 1.
- Produces: `ThreatSensor.State`, `DiverseCritter.sensor()`, y del sensor: `isAlerted()`, `isFleeing()`, `hasThreat()`, `lastThreatTick()`, `nearestThreat()`, `state()`, `dwellTicksLeft()`, `isTimerFrozen()`, `isEnabled()`. Los usan las tareas 3, 4 y 5.

- [ ] **Step 1: Crear `ThreatSensor`**

```java
package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Detecta amenazas alrededor de un {@link DiverseCritter} y decide si esta tranquilo, alerta o
 * huyendo. Es el cerebro del sistema; los goals son las manos.
 *
 * <p><b>Por que vive en la entidad y no en un goal.</b> El sueno necesita detectar amenazas
 * <i>mientras es el quien retiene al bicho</i>: {@code SleepBehaviorGoal} esta en prioridad 1 y un
 * goal de alerta nunca llegaria a ejecutarse con el critter dormido. Si la deteccion viviera dentro
 * de un goal habria que duplicarla ahi, con sus propios radios y su propio LoS, y las dos copias se
 * desincronizarian a la primera.
 *
 * <p>Cuatro consumidores leen este objeto: los dos goals, el goal de sueno y el comando de debug.
 * Una sola fuente de verdad.
 *
 * <p>Solo servidor. Se tickea desde {@code DiverseCritter.aiStep()}, que corre todos los ticks — a
 * diferencia de la pasada del {@code GoalSelector}, que va en ticks alternos. Por eso el
 * temporizador de alerta puede contar en ticks de juego de verdad.
 */
public final class ThreatSensor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cada cuantos ticks se rehace el escaneo. El temporizador sigue contando todos los ticks. */
    private static final int SCAN_INTERVAL = 5;

    public enum State { CALM, ALERT, FLEE }

    private final DiverseCritter mob;

    private State state = State.CALM;
    private int dwellTicksLeft;
    private int fleeTicks;
    /** Tick absoluto del ultimo escaneo con amenaza. Muy negativo al principio para que la resta
     *  del cooldown de re-dormirse no bloquee el primer sueno de un critter recien aparecido. */
    private int lastThreatTick = -1_000_000;
    private boolean loggedRadiusWarning;

    // --- resultado del ultimo escaneo ---
    private @Nullable LivingEntity nearest;
    private double nearestDistSq = Double.MAX_VALUE;
    private boolean anyInner;
    private boolean anyOuter;
    private boolean anySprinting;
    private boolean allCrouching;

    public ThreatSensor(DiverseCritter mob) {
        this.mob = mob;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CICLO
    // ─────────────────────────────────────────────────────────────────────────

    public void tick() {
        if (!this.isEnabled()) {
            this.reset();
            return;
        }
        if (this.mob.tickCount % SCAN_INTERVAL == 0) {
            this.scan();
        }
        this.advance();
        this.mob.setAlert(this.state == State.ALERT);
        this.mob.setFleeing(this.state == State.FLEE);
    }

    /**
     * El sistema entero se apaga si el critter es domesticado, si su especie no ha hecho opt-in, o
     * si es recien nacido — un recien nacido no supera a nada corriendo y su cadena de llanto
     * (prioridad 2) importa mas.
     */
    public boolean isEnabled() {
        return !this.mob.level().isClientSide()
                && this.mob.isAlive()
                && this.mob.isWary()
                && !this.mob.isTame()
                && !this.mob.isNewborn();
    }

    private void reset() {
        if (this.state != State.CALM) {
            this.state = State.CALM;
            this.mob.setAlert(false);
            this.mob.setFleeing(false);
        }
        this.nearest = null;
        this.anyInner = this.anyOuter = this.anySprinting = this.allCrouching = false;
        this.fleeTicks = 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ESCANEO
    // ─────────────────────────────────────────────────────────────────────────

    private void scan() {
        this.nearest = null;
        this.nearestDistSq = Double.MAX_VALUE;
        this.anyInner = false;
        this.anyOuter = false;
        this.anySprinting = false;

        boolean sawAny = false;
        boolean everyoneCrouching = true;

        // Solo Player.class porque es lo barato y esta indexado por chunk. Ampliar a depredadores
        // exige tocar aqui ademas de sobrescribir isThreat(); ver el javadoc de aquel.
        for (Player player : this.mob.level().getEntitiesOfClass(
                Player.class, this.mob.getBoundingBox().inflate(this.mob.outerThreatRadius()))) {
            if (!this.mob.isThreat(player)) continue;
            // El LoS aplica tambien a la zona roja a proposito: alguien pegado al otro lado de una
            // pared no es una amenaza, y es lo que hace que acercarse por detras de cobertura
            // funcione. getSensing() cachea por tick, asi que esto es barato.
            if (!this.mob.getSensing().hasLineOfSight(player)) continue;

            this.consider(player, false);
            sawAny = true;
            if (!player.isCrouching()) everyoneCrouching = false;
        }

        // Quien te ha pegado cuenta ignorando radios y LoS: sin esto, una flecha desde 30 bloques
        // dejaria al critter quieto y el sistema pareceria roto en el caso mas obvio. Entra en la
        // lista normal en vez de ser una transicion aparte, asi la huida termina por la regla de
        // safeDistance y no hay un segundo camino de salida que mantener.
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (attacker != null && attacker.isAlive()) {
            this.consider(attacker, true);
            sawAny = true;
            everyoneCrouching = false;
        }

        this.anyOuter = sawAny;
        // Solo se congela el temporizador si TODAS estan agachadas. Si bastara con una, dos
        // jugadores romperian el sistema: uno se agacha y el otro entra corriendo con impunidad.
        this.allCrouching = sawAny && everyoneCrouching;
        if (sawAny) {
            this.lastThreatTick = this.mob.tickCount;
        }
    }

    private void consider(LivingEntity threat, boolean forceInner) {
        double distSq = this.mob.distanceToSqr(threat);
        if (distSq < this.nearestDistSq) {
            this.nearestDistSq = distSq;
            this.nearest = threat;
        }
        double inner = this.mob.innerThreatRadius();
        if (forceInner || distSq <= inner * inner) {
            this.anyInner = true;
        }
        if (threat.isSprinting()) {
            this.anySprinting = true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MAQUINA DE ESTADOS
    // ─────────────────────────────────────────────────────────────────────────

    private void advance() {
        switch (this.state) {
            case CALM -> {
                if (this.anyInner) this.enterFlee();
                else if (this.anyOuter) this.enterAlert();
            }
            case ALERT -> {
                if (!this.anyOuter) {
                    this.state = State.CALM;
                } else if (this.anyInner || this.anySprinting) {
                    this.enterFlee();
                } else if (!this.allCrouching && --this.dwellTicksLeft <= 0) {
                    this.enterFlee();
                }
            }
            case FLEE -> {
                double safe = this.effectiveSafeDistance();
                boolean clear = this.nearest == null || this.nearestDistSq > safe * safe;
                // maxFleeTicks cubre al acorralado, que nunca alcanzaria safeDistance y huiria para
                // siempre. Al agotarse se rinde.
                if (clear || ++this.fleeTicks >= this.mob.maxFleeTicks()) {
                    this.state = State.CALM;
                    this.fleeTicks = 0;
                }
            }
        }
    }

    private void enterAlert() {
        this.state = State.ALERT;
        this.dwellTicksLeft = this.mob.alertDwellTicks();
    }

    private void enterFlee() {
        this.state = State.FLEE;
        this.fleeTicks = 0;
    }

    /**
     * El anti-loop es histeresis, no un cooldown: al terminar la huida la amenaza ya esta fuera de
     * la zona amarilla, asi que no puede re-alertarse en el acto. Un cooldown temporal haria lo
     * mismo pero dejando una ventana en la que el bicho te ignora.
     *
     * <p>La invariante NO se comprueba con {@code assert}: Java las desactiva en runtime salvo que
     * se arranque con {@code -ea}, asi que una configuracion mal puesta pasaria desapercibida justo
     * en produccion. Se loguea una vez y se clampa.
     */
    private double effectiveSafeDistance() {
        double safe = this.mob.safeDistance();
        double outer = this.mob.outerThreatRadius();
        if (safe > outer) return safe;

        if (!this.loggedRadiusWarning) {
            this.loggedRadiusWarning = true;
            LOGGER.error("{}: safeDistance ({}) debe ser mayor que outerThreatRadius ({}). "
                            + "Se clampa a {} para evitar el bucle huida-alerta-huida.",
                    this.mob.getType().getDescriptionId(), safe, outer, outer * 1.5D);
        }
        return outer * 1.5D;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LECTURA
    // ─────────────────────────────────────────────────────────────────────────

    public State state() { return this.state; }
    public boolean isAlerted() { return this.state == State.ALERT; }
    public boolean isFleeing() { return this.state == State.FLEE; }
    /** Hay alguna amenaza detectada ahora mismo. Lo usa el despertar forzado del sueno. */
    public boolean hasThreat() { return this.isEnabled() && this.anyOuter; }
    /** Tick absoluto del ultimo escaneo con amenaza; el sueno resta contra este valor. */
    public int lastThreatTick() { return this.lastThreatTick; }
    public @Nullable LivingEntity nearestThreat() { return this.nearest; }
    public double nearestDistance() {
        return this.nearest == null ? -1.0D : Math.sqrt(this.nearestDistSq);
    }
    public int dwellTicksLeft() { return this.dwellTicksLeft; }
    public boolean isTimerFrozen() { return this.allCrouching; }

    /** Dibuja las dos circunferencias con particulas. Solo lo llama el comando de debug. */
    public void spawnDebugRings(ServerLevel level) {
        ring(level, this.mob.innerThreatRadius(), true);
        ring(level, this.mob.outerThreatRadius(), false);
    }

    private void ring(ServerLevel level, double radius, boolean inner) {
        for (int i = 0; i < 48; i++) {
            double angle = (Math.PI * 2 / 48) * i;
            level.sendParticles(
                    inner ? ParticleTypes.FLAME : ParticleTypes.HAPPY_VILLAGER,
                    this.mob.getX() + Math.cos(angle) * radius,
                    this.mob.getY() + 0.2D,
                    this.mob.getZ() + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
```

- [ ] **Step 2: Enganchar el sensor a `DiverseCritter`**

Campo, justo debajo del `MovementHysteresis` (sobre la línea 140):

```java
    private final ThreatSensor threatSensor = new ThreatSensor(this);

    public ThreatSensor sensor() { return this.threatSensor; }
```

Import: `com.evirapo.diversityofcritters.common.entity.util.ThreatSensor`.

Y el tick. En `DiverseCritter.aiStep()`, **después** de `super.aiStep()` para que la histéresis de `IS_MOVING` ya esté puesta:

```java
        if (!this.level().isClientSide) {
            this.threatSensor.tick();
        }
```

- [ ] **Step 3: Opt-in de la civeta**

En `CivetEntity`, junto a los demás overrides de configuración (cerca de `maxHunger()`):

```java
    /**
     * La civeta es la primera especie con clips de alerta, asi que es la unica que enciende el
     * sistema. La ardilla y la sifaka lo heredan cableado y apagado hasta que tengan los suyos.
     */
    @Override public boolean isWary() { return true; }
```

- [ ] **Step 4: Ampliar el comando con el estado del sensor**

En `WarinessDebugCommand`, sustituye la línea `send(source, "  " + reason(critter));` por:

```java
            send(source, "  " + reason(critter));
            if (critter.sensor().isEnabled()) {
                ThreatSensor sensor = critter.sensor();
                send(source, "  §bestado: " + sensor.state()
                        + "   dwell restante: " + sensor.dwellTicksLeft() + "t"
                        + (sensor.isTimerFrozen() ? " §e(CONGELADO: todas agachadas)" : ""));
                LivingEntity threat = sensor.nearestThreat();
                send(source, "  §bamenaza: " + (threat == null ? "ninguna"
                        : threat.getName().getString() + " a " + fmt(sensor.nearestDistance())
                          + (sensor.nearestDistance() <= critter.innerThreatRadius() ? " §c[ROJA]" : " §e[AMARILLA]")));
                int since = critter.tickCount - sensor.lastThreatTick();
                send(source, "  §bre-dormirse en: " + Math.max(0, critter.reSleepCooldownTicks() - since)
                        + "t  (hace falta " + critter.reSleepCooldownTicks() + "t sin amenazas)");
                sensor.spawnDebugRings(source.getLevel());
            }
```

Imports nuevos: `ThreatSensor`, `net.minecraft.world.entity.LivingEntity`.

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Comprobar la máquina de estados en juego**

Este es el paso importante de toda la tarea. El critter **no debe cambiar de comportamiento**: sigue paseando. Lo único que se mueve es el estado del sensor.

```bash
./gradlew runClient
```

```
/gamemode survival
/summon diversityofcritters:civet ~5 ~ ~
```

Aléjate más de 10 bloques y ejecuta `/doc_wariness` repetidamente mientras te acercas andando:

| Distancia | Esperado |
|---|---|
| > 10 | `estado: CALM`, `amenaza: ninguna` |
| 10 → 4 | `estado: ALERT`, dwell bajando de 80, amenaza `[AMARILLA]` |
| tras ~4 s | `estado: FLEE` |
| < 4 directo | `estado: FLEE` sin pasar por ALERT |

Y las comprobaciones de las reglas:

- **Sprint:** desde fuera, entra esprintando (doble W). Esperado: `FLEE` inmediato, sin gastar el dwell.
- **Agachado:** en `ALERT`, agáchate. Esperado: `dwell restante` deja de bajar y aparece `(CONGELADO: todas agachadas)`.
- **Dos jugadores** (o un jugador y un aldeano no cuenta — hace falta otro jugador): uno agachado y otro de pie ⇒ el timer **corre**. Si no tienes segundo jugador, sáltalo y anótalo como pendiente.
- **LoS:** ponte a 5 bloques con un bloque sólido entre medias. Esperado: `CALM`, sin amenaza.
- **Daño a distancia:** aléjate 25 bloques y dispárale una flecha. Esperado: `FLEE`.
- **Partículas:** al ejecutar el comando salen dos círculos — llamas en la zona roja, partículas verdes en la amarilla.
- **Domesticado:** dale carne hasta domesticarla. Esperado: `APAGADO - domesticado`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/util/ThreatSensor.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java src/main/java/com/evirapo/diversityofcritters/common/command/WarinessDebugCommand.java
git commit -m "Wariness: ThreatSensor con la maquina CALM/ALERT/FLEE

El cerebro entero, observable con /doc_wariness antes de que ningun goal
cambie el comportamiento del bicho: caminas hacia una civeta y ves la maquina
moverse mientras ella sigue paseando.

El anti-loop es histeresis (safeDistance > outerRadius), no un cooldown, que
dejaria una ventana en la que el critter te ignora. La invariante se clampa y
se loguea en vez de comprobarse con assert, que Java desactiva en runtime.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: El goal de alerta

**Files:**
- Create: `src/main/java/com/evirapo/diversityofcritters/common/entity/ai/CritterAlertGoal.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java`

**Interfaces:**
- Consumes: `sensor().isAlerted()`, `sensor().nearestThreat()`, `P_ALERT`.
- Produces: las animaciones registradas `alert_enter` y `alert_loop`.

- [ ] **Step 1: Crear `CritterAlertGoal`**

```java
package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.NoSuchElementException;

/**
 * Congela al critter en pose de alerta mientras el sensor lo diga.
 *
 * <p>El goal no decide nada: {@code ThreatSensor} es dueno del temporizador, del disparo por sprint
 * y del pausado por agachado. Esto solo clava al bicho y lanza el clip.
 *
 * <p>Prioridad 2, la misma que {@link CritterFleeGoal}. Pueden compartirla porque son mutuamente
 * excluyentes por construccion: el sensor esta en un solo estado.
 */
public class CritterAlertGoal extends Goal {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final DiverseCritter mob;
    /** Se apaga si la especie no registro los clips; ver {@link #playEnterClip()}. */
    private boolean clipMissing;

    public CritterAlertGoal(DiverseCritter mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.sensor().isAlerted();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    /** El congelado tiene que sostenerse todos los ticks, no uno de cada dos. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.plant();
        this.playEnterClip();
    }

    /**
     * Lanza la entrada con {@code play()} y no por auto-arranque. El orden de ticks lo permite:
     * Forge dispara {@code LivingTickEvent} —donde corre {@code animator().tick()}— ANTES de
     * {@code aiStep()}, que es donde el sensor enciende {@code IS_ALERT}. En el tick en que se
     * alerta, el animator aun no ve la condicion; este {@code start()} corre despues. Al tick
     * siguiente {@code alert_loop} ya no puede pisar a {@code alert_enter} porque comparten
     * prioridad y el guarda del auto-arranque es {@code priority <= priority}.
     *
     * <p><b>Degrada en vez de crashear.</b> {@code MobAnimator.getByName()} <i>lanza</i>
     * {@code NoSuchElementException} si el nombre no existe — no devuelve null. Una especie que
     * ponga {@code isWary() = true} sin registrar sus clips reventaria la partida la primera vez que
     * se alertara, que es un fallo horrible para algo que se vende como "anadir una especie es una
     * linea". Asi se queda congelada sin pose, con un error en el log que dice exactamente que falta.
     */
    private void playEnterClip() {
        if (this.clipMissing) return;
        try {
            this.mob.animator().play(this.mob.animator().getByName("alert_enter"));
        } catch (NoSuchElementException e) {
            this.clipMissing = true;
            LOGGER.error("{} tiene isWary() = true pero no ha registrado 'alert_enter' / 'alert_loop' "
                            + "en registerAnimations(). Se alerta sin pose.",
                    this.mob.getType().getDescriptionId());
        }
    }

    @Override
    public void tick() {
        this.plant();
        LivingEntity threat = this.mob.sensor().nearestThreat();
        if (threat != null) {
            this.mob.getLookControl().setLookAt(threat.getX(), threat.getEyeY(), threat.getZ());
        }
    }

    /** Igual que AnimationLockGoal: parar la ruta no basta, la inercia arrastra varios bloques. */
    private void plant() {
        this.mob.getNavigation().stop();
        Vec3 velocity = this.mob.getDeltaMovement();
        this.mob.setDeltaMovement(0.0D, velocity.y, 0.0D);
    }
}
```

- [ ] **Step 2: Registrar los clips de alerta en `CivetEntity`**

En `registerAnimations()`, junto a los demás estados continuos (después del bloque de `attack`):

```java
        // Alerta: entrada de un disparo mas bucle, sin salida. No hace falta: al caer isAlert() el
        // bucle se para e idle entra por su blendOutMs, igual que nadar o trepar.
        StandardAnimation alertEnter = new StandardAnimation("alert_enter",
                new AnimSource(() -> CivetAnims.ALERT_STARTING), Loop.PLAY_ONCE, 0, P_ALERT, 0.5F);
        StandardAnimation alertLoop = new StandardAnimation("alert_loop",
                new AnimSource(() -> CivetAnims.ALERT), Loop.REPEATING, 0, P_ALERT, 0.5F);

        alertEnter.blendInMs(120).blendOutMs(150).setNextAnimation("alert_loop");
        alertLoop.blendInMs(120).blendOutMs(200);
        alertLoop.setPlayCondition(a -> this.isAlert());

        this.animator().register(alertEnter, alertLoop);
```

- [ ] **Step 3: Registrar el goal en `CivetEntity.registerGoals()`**

Justo después de `SleepBehaviorGoal`:

```java
        this.goalSelector.addGoal(2, new CritterAlertGoal(this));
```

- [ ] **Step 4: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Comprobar que los nombres de animación resuelven**

Un nombre mal escrito no da error de compilación, solo una animación que nunca suena.

Run: `grep -n "alert_enter\|alert_loop" src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java src/main/java/com/evirapo/diversityofcritters/common/entity/ai/CritterAlertGoal.java`
Expected: `alert_enter` aparece registrado en `CivetEntity` y pedido en `CritterAlertGoal`; `alert_loop` registrado y referenciado por `setNextAnimation`.

Run: `python tools/check_anim_bones.py Civet`
Expected: sin huesos nuevos inexistentes (sigue saliendo solo el `Ringtailedvontsira` conocido de `SIT_IDLE`).

Y la degradación, que es la parte que no se ve hasta que falla. Pon temporalmente
`isWary() { return true; }` en `SquirrelEntity` —que no tiene clips de alerta— arranca, acércate a
una ardilla y comprueba que **no crashea**: se queda quieta y el log dice
`... no ha registrado 'alert_enter' / 'alert_loop' ...`. Quita el override después.

- [ ] **Step 6: Comprobar en juego**

```
/summon diversityofcritters:civet ~5 ~ ~
```

Acércate andando hasta la zona amarilla. Esperado:

1. La civeta **se para en seco** y te mira.
2. Adopta la pose de alerta — se yergue, no la pose de idle.
3. `/doc_wariness` dice `estado: ALERT`, y `flags: alert=true`.
4. Si te alejas antes de los 4 s, se relaja y vuelve a pasear **sin corte brusco** (el bucle se funde a idle).
5. Si estaba acicalándose cuando entraste, **termina su clip de salida** (~0.5 s) y *después* se congela. Eso es el `AnimationLockGoal` en prioridad 0 y es el comportamiento acordado, no un retraso.

Para el punto 5: `/doc_hygiene 0`, espera a que empiece a acicalarse, y entonces entra en la zona.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/ai/CritterAlertGoal.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java
git commit -m "Wariness: CritterAlertGoal y registro de los clips de alerta

El goal no decide nada: el sensor es dueno del temporizador, del disparo por
sprint y del pausado por agachado. Esto solo clava al bicho y lanza el clip.

La entrada se lanza con play() y no por auto-arranque, porque LivingTickEvent
corre antes que aiStep() y en el tick en que se alerta el animator todavia no
ve la condicion.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: El goal de huida

**Files:**
- Create: `src/main/java/com/evirapo/diversityofcritters/common/entity/ai/CritterFleeGoal.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java`

**Interfaces:**
- Consumes: `sensor().isFleeing()`, `sensor().nearestThreat()`, `fleeSpeed()`.

- [ ] **Step 1: Crear `CritterFleeGoal`**

```java
package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Corre en direccion contraria a la amenaza mientras el sensor lo diga.
 *
 * <p>No registra ninguna animacion: {@code setSprinting(true)} basta, porque el clip {@code run} ya
 * esta registrado en las tres especies con {@code playCondition = isMoving() && isSprinting()}.
 *
 * <p><b>Huye solo por el suelo.</b> Con prioridad 2 desaloja a {@code CivetClimbGoal} (16), asi que
 * no trepa huyendo. Integrar la trepada <i>en</i> la huida es un trozo de trabajo comparable a todo
 * el sistema; en cambio, al soltar MOVE el goal de trepada toma el relevo por si solo, con lo que el
 * bicho escapa corriendo y acaba subido a un arbol. Efecto casi identico, coste cero.
 */
public class CritterFleeGoal extends Goal {

    /** Cada cuanto se recalcula el destino, para que la huida siga al que persigue. */
    private static final int REPATH_INTERVAL = 10;
    private static final int FLEE_DIST_HORIZONTAL = 16;
    private static final int FLEE_DIST_VERTICAL = 7;

    private final DiverseCritter mob;
    private int repathCooldown;

    public CritterFleeGoal(DiverseCritter mob) {
        this.mob = mob;
        // Solo MOVE: sin LOOK, para que siga pudiendo mirar hacia donde corre.
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.mob.sensor().isFleeing();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.mob.setSprinting(true);
        this.repath();
    }

    @Override
    public void tick() {
        if (--this.repathCooldown <= 0) {
            this.repath();
        }
    }

    @Override
    public void stop() {
        this.mob.setSprinting(false);
        this.mob.getNavigation().stop();
    }

    private void repath() {
        this.repathCooldown = REPATH_INTERVAL;
        LivingEntity threat = this.mob.sensor().nearestThreat();
        if (threat == null) return;

        Vec3 away = DefaultRandomPos.getPosAway(
                this.mob, FLEE_DIST_HORIZONTAL, FLEE_DIST_VERTICAL, threat.position());
        if (away != null) {
            this.mob.getNavigation().moveTo(away.x, away.y, away.z, this.mob.fleeSpeed());
        }
    }
}
```

- [ ] **Step 2: Registrar el goal**

En `CivetEntity.registerGoals()`, junto al de alerta:

```java
        this.goalSelector.addGoal(2, new CritterFleeGoal(this));
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Comprobar en juego**

```
/summon diversityofcritters:civet ~5 ~ ~
```

| Prueba | Esperado |
|---|---|
| Entra andando y espera los 4 s | Sale corriendo con el clip `run`, no andando |
| Entra esprintando | Sale corriendo sin esperar el timer |
| Persíguela | Sigue huyendo y **recalcula** el rumbo (no corre contra una pared indefinidamente) |
| Déjala escapar | A 16 bloques deja de correr y vuelve a `CALM`. **No** vuelve a alertarse en el acto |
| Acorrálala en un agujero de 1×1 | A los 10 s se rinde: `estado: CALM` |
| Tras huir, con un árbol cerca | Puede acabar trepando — es `CivetClimbGoal` tomando el relevo, correcto |

El caso de "déjala escapar" es el que valida la histéresis: si la ves alternar `FLEE`/`ALERT`/`FLEE` a la misma distancia, `safeDistance` no es mayor que `outerThreatRadius` y el log tendrá el aviso de clamp.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/ai/CritterFleeGoal.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java
git commit -m "Wariness: CritterFleeGoal

No registra ninguna animacion: setSprinting(true) basta, porque el clip run ya
esta registrado con playCondition = isMoving() && isSprinting().

Huye solo por suelo. Al soltar MOVE, CivetClimbGoal toma el relevo por si
solo, asi que el bicho escapa corriendo y puede acabar subido a un arbol.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Integración con el sueño

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/ai/SleepBehaviorGoal.java`

**Interfaces:**
- Consumes: `sensor().hasThreat()`, `sensor().lastThreatTick()`, `reSleepCooldownTicks()`.

- [ ] **Step 1: Despertar forzado por amenaza**

En `canContinueToUse()`, la condición existente pasa a incluir el sensor:

```java
        // La amenaza despierta igual que el dano o el agua, y por el mismo camino: AWAKENING retiene
        // al bicho hasta que el clip termina, y solo entonces stop() suelta MOVE y CritterFleeGoal
        // (prioridad 2) se lo lleva. El "flight sequenced after awakening" sale del reparto de
        // prioridades, no hay que construirlo.
        if (entity.hurtTime > 0 || entity.isInWater() || entity.sensor().hasThreat()) {
            this.startWaking(true);
            return true;
        }
```

**No hace falta cambiar visibilidades:** la llamada a `startWaking` está dentro de la misma clase que lo declara.

- [ ] **Step 2: Puerta del cooldown de re-dormirse**

En `canUse()`, **justo después** del bloque `if (this.entity.tickCount < this.noSleepUntilTick) return false;`:

```java
        // Cooldown de re-dormirse tras una amenaza, sin re-armar nada: mientras haya alguien cerca
        // el sensor refresca lastThreatTick y esta resta nunca crece; cuando el ultimo se va, la
        // cuenta arranca sola.
        //
        // Va separado del noSleepUntilTick de arriba a proposito. Aquel cubre despertar por dano o
        // por agua con 600 ticks; subirlo a cinco minutos haria que salpicarte con agua bloqueara el
        // sueno durante cinco minutos. Dos causas distintas, dos plazos distintos.
        if (this.entity.tickCount - this.entity.sensor().lastThreatTick()
                < this.entity.reSleepCooldownTicks()) {
            return false;
        }
```

Va **por debajo** del retorno temprano de adopción de estado huérfano que abre el método: un critter que vuelve del disco dormido tiene que poder reclamar su estado aunque haya un jugador al lado, o se quedaría congelado.

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Comprobar en juego**

La civeta es **nocturna**: duerme de día. Para acelerar, baja el cooldown temporalmente sobrescribiendo en `CivetEntity`:

```java
    @Override public int reSleepCooldownTicks() { return 200; }   // 10 s, SOLO PARA PROBAR
```

```
/time set 1000
/summon diversityofcritters:civet ~10 ~ ~
```

| Prueba | Esperado |
|---|---|
| Espera a que se duerma, acércate | Se despierta con el clip de despertar **entero**, y **después** sale huyendo |
| Con el override de 10 s: aléjate y espera | `/doc_wariness` muestra `re-dormirse en:` bajando; al llegar a 0 se vuelve a acostar |
| Quédate cerca | El contador **no baja**: se refresca mientras estés ahí |
| `/time set 18000` (noche) y espera el cooldown | **No** se duerme: está fuera de su ventana |
| Salpícala con agua | Se despierta, pero el bloqueo es el corto de 600 ticks, no cinco minutos |

**Borra el override de 10 s antes de commitear.**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/ai/SleepBehaviorGoal.java
git commit -m "Wariness: despertar por amenaza y cooldown de re-dormirse

El despertar reutiliza el startWaking(true) que ya existia, asi que la fase
AWAKENING retiene al bicho hasta que el clip termina y la huida entra despues
por reparto de prioridades.

El cooldown no re-arma nada: el sensor sella lastThreatTick en cada escaneo con
amenaza y el goal resta contra el, asi que la cuenta arranca sola cuando el
ultimo se va. Separado del noSleepUntilTick de dano/agua, que subirlo a cinco
minutos haria que el agua bloqueara el sueno cinco minutos.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: `TemptGoal` solo para domesticados

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SquirrelEntity.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SifakaEntity.java`

- [ ] **Step 1: Gatear el goal en las tres especies**

`TemptGoal` tira del animal **hacia** el jugador mientras la alerta lo aleja: dos goals peleando por `MOVE` con intenciones opuestas dan tembleque, no comportamiento. Gateado por `isTame()` nunca coexisten.

En `CivetEntity` sustituye:

```java
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.15D, Ingredient.of(Items.BEEF), false));
```

por:

```java
        // Solo domesticados: en un salvaje pelearia con la alerta por MOVE, tirando del bicho hacia
        // la amenaza de la que el otro sistema lo aparta. Sigue valiendo para llamar con comida a
        // un pet en modo wandering, donde FollowOwnerGoal no corre.
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.15D, Ingredient.of(Items.BEEF), false) {
            @Override public boolean canUse() { return isTame() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isTame() && super.canContinueToUse(); }
        });
```

En `SquirrelEntity` (prioridad 6, `DoCTags.Items.SQUIRREL_FOOD`) y `SifakaEntity` (prioridad 5, `DoCTags.Items.SIFAKA_FOOD`), el mismo patrón con su ingrediente y su prioridad. Es el mismo idioma que ya usan ahí para `FollowOwnerGoal`.

**No toques `LionEntity`**: `extends Animal`, no `DiverseCritter`, y queda fuera del sistema.

- [ ] **Step 2: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Comprobar que el león no se ha tocado**

Run: `grep -n "new TemptGoal" src/main/java/com/evirapo/diversityofcritters/common/entity/custom/*.java`
Expected: cuatro resultados; los tres critters con el `isTame()` y `LionEntity` sin tocar.

- [ ] **Step 4: Comprobar en juego**

| Prueba | Esperado |
|---|---|
| Civeta **salvaje**, sostén carne cruda | **No** se acerca. Se alerta y huye como con cualquier jugador |
| Domestícala (agachado, para que no huya), sostén carne | **Sí** te sigue |
| Ardilla salvaje con su comida | No se acerca |

La primera fila es la que valida el motivo del cambio: si el bicho tiembla entre acercarse y huir, el gate no está puesto.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/evirapo/diversityofcritters/common/entity/custom/CivetEntity.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SquirrelEntity.java src/main/java/com/evirapo/diversityofcritters/common/entity/custom/SifakaEntity.java
git commit -m "Wariness: TemptGoal solo para domesticados

En un salvaje pelearia con la alerta por MOVE con la intencion contraria. El
sigilo pasa a ser el canal para domesticar: agachado se pausa el temporizador y
el critter se queda vigilandote mientras te acercas.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Borrar la API muerta del sueño

**Files:**
- Delete: `src/main/java/com/evirapo/diversityofcritters/common/entity/util/sleep/ISleepThreatEvaluator.java`
- Delete: `src/main/java/com/evirapo/diversityofcritters/common/entity/util/sleep/ISleepAwareness.java`

- [ ] **Step 1: Confirmar que siguen sin usarse**

Run: `grep -rn "ISleepThreatEvaluator\|ISleepAwareness\|shouldInterruptSleepDueTo\|shouldWakeOnPlayerProximity" src/main/java --include=*.java`
Expected: solo las dos declaraciones. Si aparece algo más, **para** y revisa antes de borrar.

- [ ] **Step 2: Borrar**

```bash
rm src/main/java/com/evirapo/diversityofcritters/common/entity/util/sleep/ISleepThreatEvaluator.java
rm src/main/java/com/evirapo/diversityofcritters/common/entity/util/sleep/ISleepAwareness.java
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Build completo**

Run: `./gradlew build --console=plain 2>&1 | grep -E "error:|^BUILD|FAILED"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java/com/evirapo/diversityofcritters/common/entity/util/sleep/
git commit -m "Wariness: borrar ISleepThreatEvaluator e ISleepAwareness

API muerta: declaradas, nunca implementadas ni llamadas. La deteccion de
amenazas vive ahora en ThreatSensor y el predicado en DiverseCritter.isThreat();
dejarlas induciria a pensar que hay dos sistemas de amenazas.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Pase final de ajuste

Con todo montado, los números del spec son un punto de partida, no un resultado. Recórrelos con `/doc_wariness` delante:

| Parámetro | Síntoma de que está mal | Hacia dónde moverlo |
|---|---|---|
| `outerThreatRadius()` 10.0 | No puedes acercarte a nada nunca | Bajar a 7-8 |
| `innerThreatRadius()` 4.0 | Huye antes de que puedas domesticar agachado | Bajar a 2-3 |
| `alertDwellTicks()` 80 | Se ve como un bloqueo, no como tensión | Bajar a 50-60 |
| `fleeSpeed()` 1.6 | La alcanzas andando | Subir a 1.8-2.0 |
| `safeDistance()` 16.0 | Corre demasiado lejos y la pierdes | Bajar, **sin bajar de `outerThreatRadius()`** |

Y el checklist completo de 15 casos está en el §9 del spec.

## Lo que este plan deja fuera, a propósito

- **Depredadores como amenaza.** El predicado queda abierto; hoy ningún mob caza a estos critters.
- **Trepar durante la huida.** Trepar *después* sí, gratis.
- **Alerta en ardilla y sifaka.** Cableado y en `isWary() = false` hasta que existan sus clips: entonces es una línea por especie más registrar sus `alert_enter`/`alert_loop`.
- **Suite de tests automatizado.** No hay infraestructura; se presupuesta aparte.
