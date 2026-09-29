# Curado pasivo y adopción de crías — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un critter bien alimentado se cure solo, y que un bebé tenga una cuidadora concreta —su madre— que otra hembra libre pueda relevar si la madre muere o desaparece.

**Architecture:** El vínculo vive **solo en el bebé**, como un UUID más el tick en que vio a su cuidadora por última vez. Una sola fuente de verdad: "mi madre murió" y "se la llevaron" son el mismo hecho —dejó de estar cerca— así que una sola regla cubre los dos. El goal de amamantar pasa de aceptar a cualquier bebé a consultar ese vínculo.

**Tech Stack:** Minecraft 1.20.1, Forge 47.4.10, Parchment 2023.09.03, Java 17.

Spec: [2026-09-04-heal-and-adoption-design.md](../specs/2026-09-04-heal-and-adoption-design.md)

## Global Constraints

- **No hay tests automatizados en este repo.** `src/test/java` está vacío, no hay JUnit en `build.gradle` y la config de GameTest de Forge no registra ningún test. El ciclo de cada tarea es: **compilar → comprobación estática → comprobación en juego con comandos**. No inventes un `src/test`; montar ese arnés es trabajo aparte.
- **Nunca ejecutar `gradlew clean`.** Rompe el `fg_cache` de ForgeGradle y produce errores engañosos que cuestan una tarde.
- **No hagas commits ni push.** El usuario los hace él. Los pasos de commit de este plan son puntos de parada para que él decida, no acciones tuyas.
- Compilación: `./gradlew compileJava --console=plain`. Cliente: `./gradlew runClient`.
- Comentarios y javadoc **en español**, como el resto del repo. Sin tildes dentro de literales de código Java para evitar problemas de encoding; en javadoc sí se usan.
- Los comandos del mod exigen permiso 2: en un mundo de un jugador basta activar trucos.
- Entidad de pruebas: `diversityofcritters:civet`. En Minecraft **un corazón son 2 HP**, así que medio corazón es `heal(1.0F)`.

---

## Estructura de archivos

**Se crean:**

| Archivo | Responsabilidad |
|---|---|
| `common/command/FamilyDebugCommand.java` | `/doc_family`: quién es cría de quién, y por qué un bebé es o no huérfano. |

**Se modifican:**

| Archivo | Cambio |
|---|---|
| `common/entity/custom/base/DiverseCritter.java` | Curado pasivo; campos del vínculo, NBT, presencia, orfandad, liberación al morir, asignación al nacer. |
| `common/entity/ai/FindCryingBabyGoal.java` | Filtro por vínculo y acto de adopción. |
| `common/event/DOCForgeEvents.java` | Registro del comando. |

Todo el estado nuevo vive en `DiverseCritter` porque el vínculo es una propiedad de la entidad, no del goal: el goal es un lector. Eso es lo que evita que dos goals distintos acaben con dos ideas distintas de quién cuida a quién — el mismo error que ya costó caro con la detección de amenazas duplicada.

---

## Orden y por qué

El curado va primero porque es independiente de todo y se verifica en un minuto literal.

Después, el vínculo **llega acompañado de su comando** antes de que ningún goal cambie de comportamiento. Un vínculo que no se puede observar no es verificable: al terminar la Task 2 puedes criar un bebé, mirar `/doc_family` y ver a quién apunta, mientras el juego se comporta exactamente igual que antes. La Task 3 mueve la máquina de orfandad, también observable y todavía sin cambiar comportamiento. Solo la Task 4 cambia lo que hacen los bichos.

---

### Task 1: Curado pasivo

Independiente del resto del plan. Al terminar, un critter herido y bien alimentado se cura solo.

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java`

**Interfaces:**
- Consumes: `getHunger()`, `getThirst()`, `maxHunger()`, `maxThirst()` (ya existen).
- Produces: nada que consuman otras tareas.

- [ ] **Step 1: Añadir la constante**

En `DiverseCritter`, junto a las demás constantes de la clase (busca `PREGNANCY_TIME_TICKS`):

```java
    /**
     * Cada cuantos ticks se regenera medio corazon. Un corazon son 2 HP, asi que medio corazon es
     * heal(1.0F) y no 0.5F.
     */
    private static final int HEAL_INTERVAL_TICKS = 1200;
```

- [ ] **Step 2: Añadir el curado al bloque de servidor de `aiStep()`**

En `DiverseCritter.aiStep()`, dentro del `if (!this.level().isClientSide)`, justo **después** de la línea del hambre:

```java
            if ((this.getHunger() <= 0 || this.getThirst() <= 0) && random.nextInt(10) > 8){ this.starve(); }
```

añade:

```java
            this.tickPassiveHealing();
```

- [ ] **Step 3: Escribir el método**

Al final de `DiverseCritter`, antes de la llave de cierre de la clase:

```java
    /**
     * Medio corazon por minuto, solo si esta bien alimentado.
     *
     * <p>Los umbrales son relativos porque cada especie tiene su escala: la civeta tiene 4000 de
     * hambre maxima y las demas otra cosa.
     *
     * <p>El intervalo se escalona con {@code getId()} para que veinte critters no se curen todos en
     * el mismo tick.
     *
     * <p>No trata distinto a los recien nacidos, no se apaga durmiendo y no consulta el sistema de
     * wariness. Cada condicion extra seria un sitio mas donde el curado se queda mudo sin que se
     * entienda por que; el vinculo con la comida y el agua ya cumple el objetivo de diseno.
     */
    private void tickPassiveHealing() {
        if ((this.tickCount + this.getId()) % HEAL_INTERVAL_TICKS != 0) return;
        if (this.getHealth() >= this.getMaxHealth()) return;
        if (this.getHunger() <= this.maxHunger() / 2) return;
        if (this.getThirst() <= this.maxThirst() / 2) return;

        this.heal(1.0F);
    }
```

- [ ] **Step 4: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Comprobar en juego**

```bash
./gradlew runClient
```

```
/summon diversityofcritters:civet ~ ~ ~
/doc_hunger 4000
/doc_thirst 4000
```

| Prueba | Esperado |
|---|---|
| Pégale una vez y espera un minuto | Recupera medio corazón |
| Baja el hambre por debajo de la mitad y espera | **No** se cura |
| Déjalo a vida completa | No pasa nada (no hay curado sobre el máximo) |

Para ver la vida exacta: `/data get entity @e[type=diversityofcritters:civet,limit=1] Health`.

- [ ] **Step 6: Punto de commit**

El usuario commitea. Sugerencia de mensaje:

```
Curado pasivo: medio corazon por minuto si esta bien alimentado

Los umbrales son relativos (maxHunger()/2) porque cada especie tiene su
escala. El intervalo se escalona con getId() para no curar a veinte
critters en el mismo tick.
```

---

### Task 2: El vínculo madre-cría y su comando

Sin cambiar comportamiento. Al terminar, crías un bebé, ejecutas `/doc_family` y ves a quién apunta — y el juego se comporta exactamente igual que antes.

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java`
- Create: `src/main/java/com/evirapo/diversityofcritters/common/command/FamilyDebugCommand.java`
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/event/DOCForgeEvents.java`

**Interfaces:**
- Produces: `DiverseCritter.getCaregiver()` → `@Nullable UUID`, `setCaregiver(@Nullable UUID)`, `clearCaregiver()`, `isOrphan()` → `boolean`, `ticksSinceCaregiverSeen()` → `int`, y las constantes `CAREGIVER_RANGE` (double) y `ORPHAN_TIMEOUT_TICKS` (int). Los usan las tareas 3 y 4.

- [ ] **Step 1: Añadir los campos y las constantes**

En `DiverseCritter`, junto a los demás campos privados (cerca de `loadedNbtAge`):

```java
    /**
     * A partir de que distancia una cuidadora deja de contar como presente.
     *
     * <p><b>Este mismo radio se usa en los dos lados</b> —el bebe buscando a su cuidadora y la
     * cuidadora comprobando si ya tiene cria— y tiene que seguir siendo asi. Si se separan, aparece
     * una banda de distancias en la que la madre se cree libre mientras la cria la sigue
     * considerando suya, y acaba con dos crias. Ver el spec, "El sistema se auto-repara".
     */
    public static final double CAREGIVER_RANGE = 32.0D;

    /** Cuanto aguanta un bebe sin ver a su cuidadora antes de declararse huerfano. 1200 = 60 s. */
    public static final int ORPHAN_TIMEOUT_TICKS = 1200;

    /**
     * Quien cuida de este bebe. Solo significativo en recien nacidos; en un adulto es siempre null.
     *
     * <p>Solo servidor y persistido en NBT. No se sincroniza al cliente porque nada se dibuja a
     * partir de el.
     *
     * <p>El vinculo vive SOLO aqui, y no tambien en la cuidadora, a proposito: dos copias del mismo
     * hecho se desincronizan en cuanto una de las dos entidades se descarga, muere o se serializa a
     * medias.
     */
    private @Nullable UUID caregiverUUID;

    /** Tick en que se vio por ultima vez a la cuidadora. Ver {@link #readAdditionalSaveData}. */
    private int lastCaregiverSeenTick;
```

Imports necesarios: `java.util.UUID` y `org.jetbrains.annotations.Nullable` (comprueba si ya están; el archivo ya usa `@Nullable` en otros sitios).

- [ ] **Step 2: Añadir los accesores**

Al final de `DiverseCritter`, antes de la llave de cierre:

```java
    // ─────────────────────────────────────────────────────────────────────────
    //  VINCULO MADRE-CRIA
    // ─────────────────────────────────────────────────────────────────────────

    public @Nullable UUID getCaregiver() { return this.caregiverUUID; }

    /** Vincula esta cria a una cuidadora y reinicia el contador de ausencia. */
    public void setCaregiver(@Nullable UUID uuid) {
        this.caregiverUUID = uuid;
        this.lastCaregiverSeenTick = this.tickCount;
    }

    public void clearCaregiver() {
        this.caregiverUUID = null;
    }

    /**
     * Un bebe sin cuidadora, y por tanto adoptable. Un adulto nunca lo es: el vinculo solo significa
     * algo mientras es recien nacido, y devolver true para todos los adultos haria que el goal los
     * tratara como candidatos a adopcion.
     */
    public boolean isOrphan() {
        return this.isNewborn() && this.caregiverUUID == null;
    }

    public int ticksSinceCaregiverSeen() {
        return this.tickCount - this.lastCaregiverSeenTick;
    }
```

- [ ] **Step 3: Persistir en NBT**

En `addAdditionalSaveData`, junto a los demás `put`:

```java
        if (this.caregiverUUID != null) {
            pCompound.putUUID("Caregiver", this.caregiverUUID);
        }
```

En `readAdditionalSaveData`, al final del método:

```java
        // El contador NO se persiste a proposito. `tickCount` vuelve a 0 al cargar del disco, asi que
        // guardar el valor viejo daria una resta negativa enorme y el bebe no se declararia huerfano
        // jamas. Arrancando a cero, la cria vuelve del disco con sus 60 s completos para reencontrar
        // a su madre: recargar un mundo no huerfana a nadie, ni perpetua un vinculo con una madre que
        // ya no existe.
        this.lastCaregiverSeenTick = this.tickCount;
        this.caregiverUUID = pCompound.hasUUID("Caregiver") ? pCompound.getUUID("Caregiver") : null;
```

- [ ] **Step 4: Asignar la madre al nacer**

En `DiverseCritter.giveBirth()`, dentro del `if (baby != null)` y **antes** de `addFreshEntityWithPassengers`:

```java
            // giveBirth() corre sobre la madre, asi que `this` es ella. Un bebe invocado con /summon
            // o con huevo no pasa por aqui: nace sin cuidadora, huerfano y adoptable, que es el
            // comportamiento correcto por defecto.
            if (baby instanceof DiverseCritter cria) {
                cria.setCaregiver(this.getUUID());
            }
```

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Crear `FamilyDebugCommand`**

```java
package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * {@code /doc_family}: quien es cria de quien, y por que un bebe es o no huerfano.
 *
 * <p>Igual que con el sensor de amenazas, lo que mas tiempo ahorra no es ver el vinculo cuando
 * funciona, sino saber <b>que puerta esta cerrada</b> cuando un bebe llora y nadie acude: puede que
 * su madre siga viva y lejos, que el contador aun no haya expirado, o que la unica hembra libre ya
 * tenga cria.
 */
public class FamilyDebugCommand {

    private static final double RADIUS = 24.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_family")
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
            source.sendFailure(Component.literal(
                    "[Family] No critters within " + (int) RADIUS + " blocks."));
            return 0;
        }

        send(source, "§e=== FAMILY (" + critters.size() + " critters) ===");
        for (DiverseCritter critter : critters) {
            send(source, "§f[" + critter.getId() + "] " + critter.getType().getDescriptionId()
                    + "  " + (critter.getIsMale() ? "male" : "female")
                    + (critter.isNewborn() ? " §dNEWBORN" : critter.isBaby() ? " §djuvenile" : ""));

            if (critter.isNewborn()) {
                baby(source, critter);
            } else if (!critter.getIsMale()) {
                adult(source, critter);
            }
        }
        send(source, "§e=== END ===");
        return 1;
    }

    /** Estado del vinculo desde el lado de la cria. */
    private static void baby(CommandSourceStack source, DiverseCritter baby) {
        UUID uuid = baby.getCaregiver();
        if (uuid == null) {
            send(source, "  §bcaregiver: none §c[ORPHAN - adoptable]");
            return;
        }

        Entity mother = source.getLevel().getEntity(uuid);
        String where;
        if (mother == null) {
            where = "§7not loaded or dead";
        } else {
            double dist = baby.distanceTo(mother);
            where = String.format("§7[%d] at %.1f %s", mother.getId(), dist,
                    dist <= DiverseCritter.CAREGIVER_RANGE ? "§aIN RANGE" : "§cOUT OF RANGE");
        }

        int since = baby.ticksSinceCaregiverSeen();
        send(source, "  §bcaregiver: " + where);
        send(source, "  §borphan in: " + Math.max(0, DiverseCritter.ORPHAN_TIMEOUT_TICKS - since)
                + "t  (unseen for " + since + "t of " + DiverseCritter.ORPHAN_TIMEOUT_TICKS + ")");
    }

    /** Estado del vinculo desde el lado de la cuidadora. */
    private static void adult(CommandSourceStack source, DiverseCritter adult) {
        List<DiverseCritter> wards = source.getLevel().getEntitiesOfClass(
                DiverseCritter.class,
                adult.getBoundingBox().inflate(DiverseCritter.CAREGIVER_RANGE),
                other -> other.isNewborn() && adult.getUUID().equals(other.getCaregiver()));

        if (wards.isEmpty()) {
            send(source, "  §7no ward §a(free to adopt)");
        } else {
            StringBuilder ids = new StringBuilder();
            for (DiverseCritter ward : wards) {
                if (ids.length() > 0) ids.append(", ");
                ids.append(ward.getId());
            }
            send(source, "  §bward: " + ids + " §c(will not adopt)");
        }
    }

    private static void send(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
    }
}
```

- [ ] **Step 7: Registrar el comando**

En `DOCForgeEvents.onCommandsRegister`, junto a los otros cuatro:

```java
        FamilyDebugCommand.register(event.getDispatcher());
```

Y el import correspondiente.

- [ ] **Step 8: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Comprobar en juego**

El comportamiento **no debe cambiar**: cualquier hembra sigue alimentando a cualquier bebé. Lo único nuevo es que el vínculo existe y se ve.

```bash
./gradlew runClient
```

```
/summon diversityofcritters:civet ~2 ~ ~
/summon diversityofcritters:civet ~4 ~ ~
```

Haz que se apareen y espera a que la hembra quede preñada. **Solo entonces** ponte a menos de 5 bloques
de ella y ejecuta `/doc_pregnancy 20`: el comando actúa únicamente sobre hembras que ya están preñadas,
y el temporizador cuenta hacia atrás, así que 20 son unos segundos hasta el parto. Si lo ejecutas antes
del apareamiento no hace nada y parece que está roto.

Cuando nazca el bebé:

| Prueba | Esperado |
|---|---|
| `/doc_family` | El bebé muestra `caregiver: [id] at X.X IN RANGE`, y la madre `ward: <id del bebé>` |
| Las otras hembras adultas | Muestran `no ward (free to adopt)` |
| Guarda y recarga el mundo | El bebé **sigue** apuntando a su madre, y `unseen for` arranca desde 0 |
| `/summon` de un bebé suelto | Muestra `caregiver: none [ORPHAN - adoptable]` |

El caso de la recarga es el que valida el paso 3: si tras recargar dice `orphan in: 0t`, el contador se está persistiendo cuando no debe.

- [ ] **Step 10: Punto de commit**

```
Adopcion: vinculo madre-cria y /doc_family

El comando llega antes que la maquina de orfandad a proposito: un vinculo
que no se puede observar no es verificable. De momento el comportamiento no
cambia, solo se ve a quien apunta cada cria.

El contador de ausencia no se persiste: tickCount vuelve a 0 al cargar del
disco y la resta daria un negativo enorme.
```

---

### Task 3: Orfandad por ausencia y por muerte

Sigue sin cambiar el comportamiento del goal. Al terminar, ves en el comando cómo un bebé se queda huérfano al alejar o matar a su madre.

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/custom/base/DiverseCritter.java`

**Interfaces:**
- Consumes: `CAREGIVER_RANGE`, `ORPHAN_TIMEOUT_TICKS`, `getCaregiver()`, `clearCaregiver()` de la Task 2.

- [ ] **Step 1: Añadir la constante del intervalo**

Junto a `ORPHAN_TIMEOUT_TICKS`:

```java
    /** Cada cuantos ticks el bebe comprueba si su cuidadora sigue cerca. */
    private static final int CAREGIVER_CHECK_INTERVAL = 20;
```

- [ ] **Step 2: Llamar a la comprobación desde `aiStep()`**

En el mismo bloque de servidor de `aiStep()`, justo después de `this.tickPassiveHealing();`:

```java
            this.tickCaregiverPresence();
```

- [ ] **Step 3: Escribir la comprobación de presencia**

En la sección "VINCULO MADRE-CRIA" que creaste en la Task 2:

```java
    /**
     * Mantiene vivo el vinculo mientras la cuidadora este cerca, y lo corta cuando lleva demasiado
     * tiempo sin aparecer.
     *
     * <p>Usa {@code ServerLevel.getEntity(UUID)}, que es una busqueda en mapa y no un escaneo por
     * area. Si la madre esta en un chunk descargado devuelve null y cuenta como ausencia — correcto,
     * porque desde el punto de vista del bebe lo es.
     *
     * <p>Esta es la regla que cubre los dos casos del encargo con un solo mecanismo: para la cria,
     * "mi madre murio" y "se la llevaron" son el mismo hecho.
     */
    private void tickCaregiverPresence() {
        if (this.caregiverUUID == null) return;
        if (!this.isNewborn()) {
            // Ha crecido: el vinculo deja de significar nada y se suelta para no arrastrarlo en NBT
            // ni confundir al comando de depuracion.
            this.clearCaregiver();
            return;
        }
        if (this.tickCount % CAREGIVER_CHECK_INTERVAL != 0) return;

        Entity caregiver = ((ServerLevel) this.level()).getEntity(this.caregiverUUID);
        if (caregiver instanceof DiverseCritter mother
                && mother.isAlive()
                && this.distanceTo(mother) <= CAREGIVER_RANGE) {
            this.lastCaregiverSeenTick = this.tickCount;
        }

        if (this.ticksSinceCaregiverSeen() > ORPHAN_TIMEOUT_TICKS) {
            this.clearCaregiver();
        }
    }
```

Imports necesarios: `net.minecraft.world.entity.Entity` y `net.minecraft.server.level.ServerLevel` (comprueba si ya están).

- [ ] **Step 4: Liberar a la cría al morir la madre**

En `DiverseCritter`, junto a los demás overrides de ciclo de vida:

```java
    /**
     * Al morir, corta el vinculo con su cria para que quede adoptable <b>en el acto</b> y no dentro
     * de 60 s.
     *
     * <p>Hace falta este camino porque al morir la entidad desaparece del mundo, y desde el bebe eso
     * es indistinguible de "chunk descargado": el temporizador no puede saber que la madre no va a
     * volver. Se resuelve desde el otro lado, que si lo sabe.
     *
     * <p>El temporizador sigue siendo la red para todo lo demas: que se la lleven, que muera lejos de
     * la cria, o que cambie de dimension.
     */
    @Override
    public void die(DamageSource pDamageSource) {
        if (!this.level().isClientSide) {
            for (DiverseCritter ward : this.level().getEntitiesOfClass(
                    DiverseCritter.class,
                    this.getBoundingBox().inflate(CAREGIVER_RANGE),
                    other -> other.isNewborn() && this.getUUID().equals(other.getCaregiver()))) {
                ward.clearCaregiver();
            }
        }
        super.die(pDamageSource);
    }
```

Import necesario: `net.minecraft.world.damagesource.DamageSource` (ya está en el archivo, línea 31).

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Comprobación estática de los radios**

El fallo más caro de esta tarea no da error de compilación: que los dos lados usen radios distintos.

Run: `grep -rn "CAREGIVER_RANGE" src/main/java --include=*.java`
Expected: todas las apariciones usan la constante, ninguna un número suelto. Deben salir la declaración, `tickCaregiverPresence()`, `die()` y el comando de la Task 2.

- [ ] **Step 7: Comprobar en juego**

```bash
./gradlew runClient
```

Cría un bebé y con `/doc_family` delante:

| Prueba | Esperado |
|---|---|
| Madre al lado | `IN RANGE`, y `orphan in:` se queda fijo en 1200 |
| Aleja a la madre más de 32 bloques | `orphan in:` empieza a bajar |
| Devuélvela antes de que llegue a 0 | Vuelve a 1200 y el bebé **sigue** siendo suyo |
| Déjala lejos hasta que llegue a 0 | `caregiver: none [ORPHAN - adoptable]` |
| Mata a la madre estando al lado | Huérfano **al instante**, sin esperar los 60 s |
| Deja que el bebé crezca a juvenil | `caregiver` desaparece: el vínculo se suelta solo |

La fila de la muerte es la que valida el Step 4: si tarda 60 s, el `die()` no está encontrando a la cría.

- [ ] **Step 8: Punto de commit**

```
Adopcion: orfandad por ausencia y por muerte

Una sola regla cubre "murio" y "se la llevaron": para la cria las dos cosas
son que dejo de estar cerca. La muerte ademas corta el vinculo desde el lado
de la madre, porque desde el bebe una entidad muerta es indistinguible de un
chunk descargado.
```

---

### Task 4: El goal respeta el vínculo

La tarea que cambia el comportamiento. Al terminar, solo la madre atiende a su cría, y una hembra libre puede relevarla si queda huérfana.

**Files:**
- Modify: `src/main/java/com/evirapo/diversityofcritters/common/entity/ai/FindCryingBabyGoal.java`

**Interfaces:**
- Consumes: `getCaregiver()`, `setCaregiver(UUID)`, `isOrphan()`, `CAREGIVER_RANGE` de las tareas 2 y 3.

- [ ] **Step 1: Sustituir el filtro de `canUse()`**

Sustituye el cuerpo actual de `canUse()` por:

```java
    @Override
    public boolean canUse() {
        if (parent.isBaby() || parent.isSleeping() || parent.isOrderedToSit()
                || parent.isPassenger() || parent.getIsMale()) {
            return false;
        }

        // Una sola consulta, con el radio del VINCULO y no el de busqueda del goal, y sin filtrar por
        // isCrying: mi propia cria puede estar bien alimentada y en ese caso no llora, pero sigue
        // ocupando mi plaza. Que los dos lados usen CAREGIVER_RANGE es lo que evita la banda de
        // distancias en la que la madre se cree libre y acaba con dos crias.
        List<DiverseCritter> newborns = parent.level().getEntitiesOfClass(
                DiverseCritter.class,
                parent.getBoundingBox().inflate(DiverseCritter.CAREGIVER_RANGE),
                entity -> entity.getType() == parent.getType() && entity.isNewborn());

        boolean hasWard = false;
        DiverseCritter ownCryingChild = null;
        DiverseCritter cryingOrphan = null;

        for (DiverseCritter baby : newborns) {
            if (parent.getUUID().equals(baby.getCaregiver())) {
                hasWard = true;
                if (baby.isCrying() && ownCryingChild == null) ownCryingChild = baby;
            } else if (baby.isOrphan() && baby.isCrying() && cryingOrphan == null
                    && parent.distanceTo(baby) <= this.searchRange) {
                cryingOrphan = baby;
            }
        }

        // La propia cria manda sobre cualquier huerfano: relevar a otra es un extra, alimentar a la
        // tuya es la obligacion.
        this.targetBaby = ownCryingChild != null ? ownCryingChild
                : (hasWard ? null : cryingOrphan);
        return this.targetBaby != null;
    }
```

Nota sobre los dos radios: la consulta se hace a `CAREGIVER_RANGE` (32) porque es lo que determina si tengo cría, pero un huérfano solo se considera si está dentro de `searchRange` (24, el parámetro del constructor). Así el goal no camina más lejos de lo que caminaba antes, pero la plaza se cuenta con el radio correcto.

- [ ] **Step 2: Vincular al adoptar, en `performNursing()`**

Sustituye `performNursing()` por:

```java
    private void performNursing() {
        // Si era huerfano, amamantarlo lo adopta. El vinculo es permanente y sustituye al anterior.
        // No hace falta comprobar si ya era mio: reasignar el mismo UUID no cambia nada, y ademas
        // refresca el contador de ausencia, que es justo lo que toca al haber estado pegados.
        targetBaby.setCaregiver(parent.getUUID());

        targetBaby.setHunger(targetBaby.maxHunger());
        targetBaby.setThirst(targetBaby.maxThirst());
        targetBaby.setCrying(false);

        Level level = parent.level();
        level.broadcastEntityEvent(targetBaby, (byte) 7);
        parent.playSound(SoundEvents.CAT_PURR, 1.0F, 1.0F);
    }
```

- [ ] **Step 3: Ajustar `canContinueToUse()`**

El actual sigue mientras el bebé llore. Añade la condición de que siga siendo un objetivo legítimo, para que dos hembras que salieron a por el mismo huérfano no insistan las dos:

```java
    @Override
    public boolean canContinueToUse() {
        if (targetBaby == null || !targetBaby.isAlive() || !targetBaby.isCrying()) return false;
        if (this.feedTimer >= FEEDING_DURATION + 10) return false;

        // Si otra hembra lo adopto mientras yo iba de camino, dejo de ir. El peor sintoma sin esto
        // seria un paseo inutil, pero tambien dos hembras plantadas encima del mismo bebe.
        UUID caregiver = targetBaby.getCaregiver();
        return caregiver == null || parent.getUUID().equals(caregiver);
    }
```

Import necesario: `java.util.UUID`.

- [ ] **Step 4: Compilar**

Run: `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|^BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Comprobar en juego**

```bash
./gradlew runClient
```

Con `/doc_family` a mano en todas las pruebas:

| Prueba | Esperado |
|---|---|
| Bebé con hambre y varias hembras cerca | Solo acude **su madre**, aunque otra esté más cerca |
| Mata a la madre y espera a que llore | Acude otra hembra libre y la adopta: `/doc_family` muestra el nuevo `caregiver` |
| Aleja a la madre 60 s y espera a que llore | Igual: la adopta otra |
| Devuelve a la madre antes de los 60 s | Ninguna otra la toca |
| Una hembra libre y **dos** huérfanos llorando | Adopta uno; el otro sigue llorando y nadie va |
| Ese segundo huérfano, sin salvarlo | Acaba muriendo de hambre |
| Sálvalo con el biberón | Se recupera, y **sigue** huérfano: el biberón no crea vínculo |
| Una hembra con cría propia y un huérfano al lado | Ignora al huérfano (`will not adopt` en el comando) |

La primera fila es la que valida toda la funcionalidad: si acude cualquier hembra, el filtro no está entrando.

- [ ] **Step 6: Punto de commit**

```
Adopcion: el goal de amamantar respeta el vinculo

Solo la madre atiende a su cria; una hembra libre puede relevarla si queda
huerfana. La consulta usa CAREGIVER_RANGE para contar la plaza y searchRange
para elegir a quien ir, asi que el goal no camina mas lejos que antes.

Sin reserva sobre el huerfano a proposito: la primera que lo amamanta lo
vincula y la segunda lo suelta en su siguiente canUse().
```

---

## Pase final

Con todo montado, los números son un punto de partida:

| Parámetro | Síntoma de que está mal | Hacia dónde moverlo |
|---|---|---|
| `HEAL_INTERVAL_TICKS` 1200 | Los animales heridos tardan una eternidad | Bajar a 600 |
| Umbral de hambre/sed (mitad) | Casi nunca se curan | Bajar a un tercio |
| `CAREGIVER_RANGE` 32 | Las madres pierden a sus crías paseando | Subir |
| `ORPHAN_TIMEOUT_TICKS` 1200 | Te llevas a la madre y tardas demasiado en ver el efecto | Bajar a 600 |

Y el checklist completo de casos está en la sección "Verificación" del spec.

## Lo que este plan deja fuera, a propósito

- **Que el bebé siga a su cuidadora.** Hoy usa `FollowParentGoal` de vanilla, que va al adulto más cercano de su especie.
- **Adopción por el jugador** vía biberón.
- **Machos como cuidadores**, y hembras con más de una cría.
- **Aviso al jugador** de que un bebé ha quedado huérfano.
