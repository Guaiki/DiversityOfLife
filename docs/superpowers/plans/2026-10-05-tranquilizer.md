# Sistema de tranquilizantes — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rifle con mira de catalejo que dispara dardos tranquilizantes, píldora para mascotas, y un
sueño forzado (30 s de espera, 1 min dormido, 5 min de cooldown) integrado en `SleepBehaviorGoal`.

**Architecture:** La lógica de tiempos vive en una clase pura `Sedation` (testeable con JUnit) que
cada `DiverseCritter` tickea en servidor y guarda en NBT. `SleepBehaviorGoal` sigue siendo el único
dueño de `SleepState`: sólo consulta `isSedated()`. El rifle reutiliza el catalejo vanilla con un
mixin sobre `Player.isScoping()`, y el disparo se captura en cliente y se valida en servidor.

**Tech Stack:** Forge 47.4.10 (MC 1.20.1), mappings Parchment 2023.09.03, Mixin 0.8.5 + refmap,
JUnit 5. Spec: `docs/superpowers/specs/2026-10-05-tranquilizer-design.md`.

## Global Constraints

- Paquete base `com.evirapo.diversityofcritters`, mod id `diversityofcritters`.
- Tiempos: espera 600 ticks, sueño forzado 1200 ticks, cooldown 6000 ticks.
- Nunca `gradlew clean` (rompe `build/fg_cache`). Compilar con `gradlew build`.
- No reescribir `.java` con `Set-Content` de PowerShell (mete BOM).
- Las rutas de Java de abajo son relativas a `src/main/java/com/evirapo/diversityofcritters/`.
- Comentarios en el estilo del proyecto: explican el porqué.

---

### Task 1: `Sedation`, la máquina de estados pura

**Files:**
- Create: `common/entity/util/Sedation.java`
- Test: `src/test/java/com/evirapo/diversityofcritters/common/entity/util/SedationTest.java`

**Interfaces:**
- Produces: `Sedation` con `DROWSY_TICKS`, `SEDATED_TICKS`, `COOLDOWN_TICKS`,
  `enum DoseResult { ACCEPTED, ON_COOLDOWN }`, `DoseResult dose()`, `void tick()`,
  `boolean isDrowsy()`, `boolean isSedated()`, `int drowsyTicks()`, `int sedatedTicks()`,
  `int cooldownTicks()`, `void restore(int drowsy, int sedated, int cooldown)`.

- [ ] **Step 1: Escribir el test**

```java
package com.evirapo.diversityofcritters.common.entity.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SedationTest {

    private static void tick(Sedation s, int ticks) {
        for (int i = 0; i < ticks; i++) s.tick();
    }

    @Test
    void freshIsIdle() {
        Sedation s = new Sedation();
        assertFalse(s.isDrowsy());
        assertFalse(s.isSedated());
        assertEquals(0, s.cooldownTicks());
    }

    @Test
    void doseMakesDrowsyThenSedatedThenIdle() {
        Sedation s = new Sedation();
        assertEquals(Sedation.DoseResult.ACCEPTED, s.dose());
        assertTrue(s.isDrowsy());

        tick(s, Sedation.DROWSY_TICKS - 1);
        assertTrue(s.isDrowsy());
        assertFalse(s.isSedated());

        tick(s, 1);
        assertFalse(s.isDrowsy());
        assertTrue(s.isSedated());

        tick(s, Sedation.SEDATED_TICKS - 1);
        assertTrue(s.isSedated());

        tick(s, 1);
        assertFalse(s.isSedated());
        assertFalse(s.isDrowsy());
    }

    @Test
    void cooldownRejectsUntilItRunsOut() {
        Sedation s = new Sedation();
        s.dose();
        assertEquals(Sedation.COOLDOWN_TICKS, s.cooldownTicks());

        tick(s, Sedation.COOLDOWN_TICKS - 1);
        assertEquals(Sedation.DoseResult.ON_COOLDOWN, s.dose());
        assertEquals(1, s.cooldownTicks());

        tick(s, 1);
        assertEquals(Sedation.DoseResult.ACCEPTED, s.dose());
    }

    @Test
    void cooldownOutlastsTheWholeEffect() {
        assertTrue(Sedation.COOLDOWN_TICKS > Sedation.DROWSY_TICKS + Sedation.SEDATED_TICKS);
    }

    @Test
    void rejectedDoseLeavesStateAlone() {
        Sedation s = new Sedation();
        s.dose();
        tick(s, 10);
        s.dose();
        assertEquals(Sedation.DROWSY_TICKS - 10, s.drowsyTicks());
    }

    @Test
    void restoreResumesWhereItWasSaved() {
        Sedation s = new Sedation();
        s.restore(0, 5, 100);
        assertTrue(s.isSedated());
        tick(s, 5);
        assertFalse(s.isSedated());
        assertEquals(95, s.cooldownTicks());
    }

    @Test
    void restoreClampsNegatives() {
        Sedation s = new Sedation();
        s.restore(-3, -1, -50);
        assertFalse(s.isDrowsy());
        assertFalse(s.isSedated());
        assertEquals(0, s.cooldownTicks());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `./gradlew test --tests "*SedationTest"`
Expected: FAIL de compilación, `Sedation` no existe.

- [ ] **Step 3: Implementar**

```java
package com.evirapo.diversityofcritters.common.entity.util;

public final class Sedation {

    public static final int DROWSY_TICKS = 600;
    public static final int SEDATED_TICKS = 1200;
    public static final int COOLDOWN_TICKS = 6000;

    public enum DoseResult { ACCEPTED, ON_COOLDOWN }

    private int drowsyTicks;
    private int sedatedTicks;
    private int cooldownTicks;

    public DoseResult dose() {
        if (this.cooldownTicks > 0) return DoseResult.ON_COOLDOWN;
        this.drowsyTicks = DROWSY_TICKS;
        this.sedatedTicks = 0;
        this.cooldownTicks = COOLDOWN_TICKS;
        return DoseResult.ACCEPTED;
    }

    public void tick() {
        if (this.cooldownTicks > 0) this.cooldownTicks--;
        if (this.drowsyTicks > 0) {
            if (--this.drowsyTicks == 0) this.sedatedTicks = SEDATED_TICKS;
        } else if (this.sedatedTicks > 0) {
            this.sedatedTicks--;
        }
    }

    public boolean isDrowsy() { return this.drowsyTicks > 0; }
    public boolean isSedated() { return this.sedatedTicks > 0; }
    public int drowsyTicks() { return this.drowsyTicks; }
    public int sedatedTicks() { return this.sedatedTicks; }
    public int cooldownTicks() { return this.cooldownTicks; }

    public void restore(int drowsy, int sedated, int cooldown) {
        this.drowsyTicks = Math.max(0, drowsy);
        this.sedatedTicks = Math.max(0, sedated);
        this.cooldownTicks = Math.max(0, cooldown);
    }
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `./gradlew test --tests "*SedationTest"` — Expected: PASS (7 tests).

- [ ] **Step 5: Commit** — `git commit -m "Tranquilizer: sedation timers"`

---

### Task 2: Sedación en `DiverseCritter` y `SleepBehaviorGoal`

**Files:**
- Modify: `common/entity/custom/base/DiverseCritter.java` (campo, `tick()`, NBT, API pública)
- Modify: `common/entity/ai/SleepBehaviorGoal.java` (`canUse`, `canContinueToUse`, `start`, `stop`)

**Interfaces:**
- Consumes: `Sedation` (Task 1).
- Produces: en `DiverseCritter`: `boolean isSedated()`, `Sedation.DoseResult tryTranquilize()`,
  `int tranquilizerCooldownTicks()`.

- [ ] **Step 1: `DiverseCritter`** — campo junto al `threatSensor`:

```java
    private final Sedation sedation = new Sedation();

    /** Dormido por un tranquilizante, pase lo que pase con su ciclo natural. Servidor. */
    public boolean isSedated() { return this.sedation.isSedated(); }

    /** Servidor. Lo llaman el dardo y la píldora, vía {@code TranquilizerHelper}. */
    public Sedation.DoseResult tryTranquilize() { return this.sedation.dose(); }

    public int tranquilizerCooldownTicks() { return this.sedation.cooldownTicks(); }
```

En el bloque de servidor de `tick()`, junto a `tickTemperature()`: `this.sedation.tick();`.
En `addAdditionalSaveData`: `putInt("TranqDrowsy", ...)`, `putInt("TranqSedated", ...)`,
`putInt("TranqCooldown", ...)`. En `readAdditionalSaveData`:
`this.sedation.restore(getInt("TranqDrowsy"), getInt("TranqSedated"), getInt("TranqCooldown"))`
(los ausentes leen 0 = nada pendiente).

- [ ] **Step 2: `SleepBehaviorGoal`** — campo `private boolean sedatedRun;`.

En `canUse()`, justo tras el `if (getSleepState() != AWAKE) return true;`:

```java
        if (this.entity.isSedated()) {
            return !this.entity.isInWater();
        }
```

En `canContinueToUse()`, tras la rama de `AWAKENING`:

```java
        if (this.entity.isSedated()) {
            this.sedatedRun = true;
        }
        if (this.sedatedRun) {
            if (!this.entity.isSedated()) {
                this.startWaking(false);
            } else if (this.entity.isInWater()) {
                this.startWaking(true);
            }
            return true;
        }
```

En `start()`: `this.sedatedRun = this.entity.isSedated();`. En `stop()`: `this.sedatedRun = false;`.

- [ ] **Step 3: Compilar** — `./gradlew compileJava` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `git commit -m "Tranquilizer: forced sleep in the sleep cycle"`

---

### Task 3: Dardo, píldora y su registro

**Files:**
- Create: `common/item/TranquilizerHelper.java`
- Create: `common/item/custom/MedicinePillItem.java`
- Create: `common/entity/custom/TranquilizerDartEntity.java`
- Modify: `common/item/DOCItems.java`, `common/entity/DOCEntities.java`,
  `client/event/DOCClientEvents.java`, `misc/creative/DOCEntitiesCreativeTab.java`
- Create: texturas `assets/diversityofcritters/textures/item/{tranquilizer_rifle,tranquilizer_dart,medicine_pill}.png`
  (copiadas de `Sniper.png`, `Dart.png`, `Sleepy_gummy.png`), modelos de item, recetas.
- Modify: `assets/diversityofcritters/lang/en_us.json`

**Interfaces:**
- Consumes: `DiverseCritter#tryTranquilize()`, `#tranquilizerCooldownTicks()` (Task 2).
- Produces: `TranquilizerHelper.dose(DiverseCritter, @Nullable Player) : boolean`,
  `DOCItems.TRANQUILIZER_RIFLE / TRANQUILIZER_DART / MEDICINE_PILL`,
  `DOCEntities.TRANQUILIZER_DART`, `TranquilizerDartEntity(Level, LivingEntity)`.

- [ ] **Step 1: `TranquilizerHelper`**

```java
public final class TranquilizerHelper {
    private TranquilizerHelper() {}

    public static boolean dose(DiverseCritter critter, @Nullable Player feedbackTo) {
        Sedation.DoseResult result = critter.tryTranquilize();
        if (feedbackTo != null) {
            if (result == Sedation.DoseResult.ACCEPTED) {
                feedbackTo.displayClientMessage(Component.translatable(
                        "message.diversityofcritters.tranquilizer.drowsy", critter.getName()), true);
            } else {
                int seconds = (critter.tranquilizerCooldownTicks() + 19) / 20;
                feedbackTo.displayClientMessage(Component.translatable(
                        "message.diversityofcritters.tranquilizer.cooldown", critter.getName(),
                        String.format("%d:%02d", seconds / 60, seconds % 60)), true);
            }
        }
        return result == Sedation.DoseResult.ACCEPTED;
    }
}
```

- [ ] **Step 2: `MedicinePillItem`** — `interactLivingEntity`: si no es `DiverseCritter`, `PASS`. En
  servidor: salvaje → mensaje `not_tamed`; domesticado → `TranquilizerHelper.dose(critter, player)`,
  y si se acepta, sonido de comer y `shrink(1)` salvo en creativo. Devuelve
  `sidedSuccess(isClientSide)`.

- [ ] **Step 3: `TranquilizerDartEntity extends ThrowableItemProjectile`** — gravedad 0.01, se
  descarta a los 100 ticks; `onHitEntity`: `hurt(thrown(this, owner), 0)` y, en servidor y si es
  `DiverseCritter`, `TranquilizerHelper.dose(critter, owner instanceof Player p ? p : null)`;
  `onHit`: evento 3 (partículas del item) y `discard()`.

- [ ] **Step 4: Registro** — en `DOCItems`:

```java
    public static final RegistryObject<Item> TRANQUILIZER_RIFLE = ITEM_TYPES.register("tranquilizer_rifle",
            () -> new TranquilizerRifleItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TRANQUILIZER_DART = ITEM_TYPES.register("tranquilizer_dart",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MEDICINE_PILL = ITEM_TYPES.register("medicine_pill",
            () -> new MedicinePillItem(new Item.Properties().stacksTo(16)));
```

En `DOCEntities`: `TRANQUILIZER_DART`, `MobCategory.MISC`, `sized(0.25f, 0.25f)`,
`clientTrackingRange(4)`, `updateInterval(10)`. En `DOCClientEvents#registerRenderer`:
`ThrownItemRenderer::new`. Pestaña de items: los tres al final.

- [ ] **Step 5: Assets** — modelos (`item/handheld` para el rifle, `item/generated` para los otros
  dos), recetas del spec §4, lang:

```json
  "item.diversityofcritters.tranquilizer_rifle": "Tranquilizer Rifle",
  "item.diversityofcritters.tranquilizer_dart": "Tranquilizer Dart",
  "item.diversityofcritters.medicine_pill": "Medicine Pill",
  "entity.diversityofcritters.tranquilizer_dart": "Tranquilizer Dart",
  "message.diversityofcritters.tranquilizer.drowsy": "%s is getting drowsy...",
  "message.diversityofcritters.tranquilizer.cooldown": "%s is still recovering. Try again in %s",
  "message.diversityofcritters.tranquilizer.not_tamed": "Medicine only works on tamed animals",
  "message.diversityofcritters.tranquilizer.no_darts": "No tranquilizer darts"
```

- [ ] **Step 6: Commit** (junto con la Task 4, porque `DOCItems` referencia `TranquilizerRifleItem`).

---

### Task 4: Rifle con mira y disparo

**Files:**
- Create: `common/item/custom/TranquilizerRifleItem.java`
- Create: `mixin/PlayerScopingMixin.java`; Modify: `resources/diversityofcritters.mixins.json`
- Create: `network/FireTranquilizerMsg.java`; Modify: `network/DOCNetworkHandler.java`
- Create: `client/event/TranquilizerClientEvents.java`

**Interfaces:**
- Consumes: `DOCItems.TRANQUILIZER_DART`, `TranquilizerDartEntity(Level, LivingEntity)` (Task 3).
- Produces: `TranquilizerRifleItem.fire(ServerPlayer)`, `FireTranquilizerMsg` (sin carga).

- [ ] **Step 1: `TranquilizerRifleItem`** — `getUseDuration` 72000, `UseAnim.BOW`, `use()` con sonido
  `SPYGLASS_USE` y `ItemUtils.startUsingInstantly`, `releaseUsing`/`finishUsingItem` con
  `SPYGLASS_STOP_USING`. `static fire(ServerPlayer)`: exige estar usando el rifle y sin cooldown;
  busca dardo (mano secundaria, luego inventario); sin dardo y sin creativo → `DISPENSER_FAIL` +
  mensaje `no_darts`; si no, crea el dardo, `shootFromRotation(player, xRot, yRot, 0, 3.5F, 0)`,
  gasta uno salvo creativo, sonido `CROSSBOW_SHOOT` agudo y cooldown de 20 ticks.

- [ ] **Step 2: Mixin**

```java
@Mixin(Player.class)
public abstract class PlayerScopingMixin {
    @Inject(method = "isScoping", at = @At("HEAD"), cancellable = true)
    private void doc$scopeWithRifle(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.isUsingItem() && self.getUseItem().getItem() instanceof TranquilizerRifleItem) {
            cir.setReturnValue(true);
        }
    }
}
```

Añadir `"PlayerScopingMixin"` a `"mixins"` en `diversityofcritters.mixins.json`.

- [ ] **Step 3: Red** — `FireTranquilizerMsg` vacío cuyo `handle` llama a
  `TranquilizerRifleItem.fire(sender)` en `enqueueWork`; registrarlo al final de
  `DOCNetworkHandler.init()`.

- [ ] **Step 4: Cliente** — `TranquilizerClientEvents` (bus FORGE, `Dist.CLIENT`),
  `ClientTickEvent` fase `START`: si el jugador está usando el rifle, sin pantalla ni overlay,
  consumir todos los `keyAttack.consumeClick()` y, si hubo alguno y el rifle no está en cooldown,
  `CHANNEL.sendToServer(new FireTranquilizerMsg())`.

- [ ] **Step 5: Build** — `./gradlew build` → BUILD SUCCESSFUL, tests en verde.

- [ ] **Step 6: Commit** — `git commit -m "Tranquilizer: rifle, darts and medicine pill"`

---

### Task 5: Verificación en juego (la hace el usuario)

1. Creativo → pestaña *DOL: Items*: aparecen rifle, dardo y píldora con su textura y nombre.
2. Recetas en la mesa de crafteo (JEI): rifle, 2 píldoras, 4 dardos.
3. Supervivencia, rifle en mano, clic derecho mantenido: zoom y overlay del catalejo, mano oculta.
   Soltar: vuelve todo. En F5 se ve apuntando con las dos manos.
4. Apuntando, clic izquierdo con dardos: sale el dardo, gasta uno, 1 s de cadencia. Sin dardos:
   sonido de fallo y "No tranquilizer darts". Sin apuntar, clic izquierdo pega normal.
5. Dardo a una civeta salvaje de día: knockback + parpadeo rojo, sin daño, "is getting drowsy...".
   Sigue activa ~30 s (puede huir), luego se tumba con la animación normal, duerme ~1 min aunque
   te acerques o le pegues, y se despierta con su animación.
6. Segundo dardo dentro de los 5 min: "is still recovering. Try again in m:ss".
7. Píldora a una mascota propia: se consume y pasa lo mismo que en 5. A un salvaje: no se consume,
   "Medicine only works on tamed animals".
8. Repetir 5 con Sifaka y Squirrel.
9. Sedar, salir del mundo durante los 30 s o durante el sueño, volver: retoma donde iba. Cooldown
   también sobrevive.
10. Mascota sentada por orden + píldora: se duerme, y al despertar sigue sentada.
11. Multijugador (LAN): el otro jugador ve al animal tumbarse y despertar.
