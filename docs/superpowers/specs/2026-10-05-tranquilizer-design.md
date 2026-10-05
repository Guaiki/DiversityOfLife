# Sistema de tranquilizantes — Spec

Encargo: *Tranquilizer System — $45*. Tres items nuevos (rifle, dardo, píldora), un proyectil nuevo y
un sueño forzado que se engancha a la máquina de estados de sueño que ya existe.

---

## 1. Comportamiento

### Tranquilizer Rifle (`tranquilizer_rifle`)

- **Clic derecho mantenido** = apuntar. Usa la interfaz del catalejo vanilla tal cual: zoom de FOV
  ×0.1, overlay de mira, sensibilidad del ratón reducida y la mano oculta en primera persona.
- **Clic izquierdo mientras apunta** = disparar. Sin apuntar, el clic izquierdo es un ataque normal.
- Munición: **Tranquilizer Dart**. Se busca primero en la mano secundaria y después en el inventario.
  En creativo dispara sin gastar munición.
- Sin munición: sonido de fallo de dispensador y aviso en la barra de acción.
- Cadencia: 1 disparo por segundo (cooldown de item vanilla, 20 ticks).
- No apila (`stacksTo(1)`), sin durabilidad.

### Tranquilizer Dart (`tranquilizer_dart`)

- Item de munición (apila a 64) y **entidad proyectil propia** (`TranquilizerDartEntity`), no una
  bola de nieve reutilizada.
- Vuelo: velocidad 3.5, gravedad 0.01 (más tenso que una bola de nieve, que va a 0.03), sin
  dispersión. Se descarta a los 100 ticks si no ha chocado con nada.
- Al impactar contra cualquier entidad: `hurt(thrown, 0)` — knockback y parpadeo rojo, cero daño,
  exactamente lo que hace la bola de nieve. Partículas del item al romperse.
- Si lo que golpea es un `DiverseCritter` (domesticado o salvaje) intenta tranquilizarlo; ver §2.

### Medicine Pill (`medicine_pill`)

- Clic derecho sobre un critter **domesticado** (de cualquier dueño). Sobre uno salvaje no hace nada
  y avisa.
- Si la dosis se acepta, se consume una píldora (no en creativo).
- Apila a 16.

### Feedback al jugador (barra de acción)

| Situación | Mensaje |
|---|---|
| Dosis aceptada | `<Nombre> is getting drowsy...` |
| Critter en cooldown | `<Nombre> is still recovering. Try again in m:ss` |
| Píldora sobre salvaje | `Medicine only works on tamed animals` |
| Rifle sin dardos | `No tranquilizer darts` |

Con el dardo el mensaje va al **dueño del proyectil**; con la píldora, a quien la usa.

---

## 2. Sedación

### Máquina de estados

Tres contadores en ticks, en una clase pura sin tipos de Minecraft (`Sedation`) que vive dentro de
cada `DiverseCritter`:

```
            dose()                30 s                 60 s
  NONE  ───────────►  DROWSY  ───────────►  SEDATED  ───────────►  NONE
                         └──────────── cooldown 5 min ────────────────┘ (corre en paralelo)
```

| Constante | Ticks | Tiempo |
|---|---|---|
| `DROWSY_TICKS` | 600 | 30 s hasta tumbarse |
| `SEDATED_TICKS` | 1200 | 1 min de sueño forzado |
| `COOLDOWN_TICKS` | 6000 | 5 min antes de otra dosis |

- `dose()` devuelve `ACCEPTED` o `ON_COOLDOWN`. Aceptar arranca DROWSY **y el cooldown a la vez**: el
  cooldown se mide desde la dosis, no desde el despertar. Como 5 min > 30 s + 60 s, cualquier segunda
  dosis durante el efecto cae en cooldown y no hace falta un tercer resultado.
- `tick()` se llama **cada tick de juego** desde el bloque de servidor de `DiverseCritter.tick()`, no
  desde un goal (los goals van en ticks alternos; ver el javadoc de `SleepBehaviorGoal#phaseUntilTick`).
- SEDATED cuenta desde que se tumba (entrada de `PREPARING` incluida), pase lo que pase: así nunca se
  queda colgado si algo le impide dormir.
- Los contadores pausan con el chunk descargado, igual que el resto del critter.

### Persistencia y sincronización

- Los tres contadores se guardan en NBT (`TranqDrowsy`, `TranqSedated`, `TranqCooldown`) como ticks
  restantes, no como tiempo absoluto del mundo: sobreviven a recargas y cambios de dimensión sin
  aritmética de relojes.
- No se sincronizan al cliente: lo visible es el `SleepState`, que ya viaja por `SynchedEntityData` y
  ya se guarda. Las animaciones de dormir salen solas.

### Integración con `SleepBehaviorGoal`

El goal sigue siendo **el único dueño de `SleepState`**. La sedación sólo le dice "duerme" y "ya
puedes despertar":

1. **`canUse()`** — justo después de la adopción de estado huérfano: si `isSedated()`, devuelve
   `!isInWater()` saltándose ventana, retardo de asentarse, espera entre siestas, cooldown de amenaza
   y la orden de sentarse. Esto es "a cualquier hora del día".
2. **`canContinueToUse()`** — si `isSedated()`, la tanda pasa a ser forzada (`sedatedRun = true`).
   Esto cubre también al animal que ya estaba dormido de forma natural cuando le llegó la sedación.
   En una tanda forzada:
   - **No** le despiertan el daño, las amenazas ni el fin de la ventana ni de su tanda natural.
   - **Sí** le despierta el agua (forzado), como válvula de seguridad. La sedación sigue corriendo; al
     salir del agua `canUse()` le vuelve a tumbar si queda tiempo.
   - Cuando `isSedated()` pasa a falso: `startWaking(false)` → `AWAKENING` con su clip → `stop()`.
     Es un despertar normal, con la espera normal entre siestas.
3. **`start()`/`stop()`** — `start()` fija `sedatedRun` según `isSedated()`; `stop()` lo limpia.

Prioridades: `SleepBehaviorGoal` está en prioridad 1, por delante de alerta/huida (2) y de la orden
de sentarse (3–4), así que una vez sedado gana a todo salvo al flotar (0). Durante los 30 s de
DROWSY el animal se comporta con normalidad y puede huir: el sedante tarda en hacer efecto.

### Lo que NO hace, a propósito

- No ralentiza ni cambia nada durante DROWSY (fuera del alcance; se puede añadir después).
- No configura los tiempos por config: son los del encargo.
- No añade comandos de depuración.

---

## 3. Rifle: cómo se reutiliza el catalejo vanilla

Todo el comportamiento del catalejo cuelga de `Player.isScoping()`
(`isUsingItem() && getUseItem().is(Items.SPYGLASS)`): overlay en `ForgeGui`, FOV en
`AbstractClientPlayer`, sensibilidad en `MouseHandler`, mano oculta en `ItemInHandRenderer`. Un único
mixin común `PlayerScopingMixin` (`@Inject` en HEAD, cancelable) devuelve `true` también cuando el
item en uso es el rifle. El refmap ya lo genera el procesador de anotaciones del build.

Animación de uso `UseAnim.BOW`: en tercera persona se ve apuntando con las dos manos, que para un
rifle se lee mejor que el brazo al ojo del catalejo. En primera persona no se ve (mano oculta).
Duración de uso 72000 ticks, como el arco, para que apuntar no se corte al minuto como el catalejo.
Sonidos de subir y bajar el catalejo vanilla.

### Disparo

`Minecraft.handleKeybinds()` **descarta** los clics de ataque mientras se usa un item. Pero
`ClientTickEvent` en fase `START` corre antes, así que ahí, si el jugador está usando el rifle, se
consumen los clics de `keyAttack` y por cada uno se manda `FireTranquilizerMsg` (sin carga) al
servidor. El servidor es la autoridad: comprueba que siga usando el rifle, el cooldown y la munición,
crea el dardo, gasta munición, pone el cooldown y reproduce el sonido.

---

## 4. Recetas

| Item | Receta |
|---|---|
| Rifle | con forma: `" G "`, `"III"`, `"W  "` — G catalejo, I lingote de hierro, W tablones (tag) |
| Medicine Pill ×2 | sin forma: azúcar + bola de slime + membrana de phantom |
| Dart ×4 | con forma vertical: pepita de hierro / Medicine Pill / pluma |

La membrana de phantom liga el sedante al insomnio; el dardo lleva una píldora dentro, así que las
dos vías de sedar comparten coste.

---

## 5. Archivos

Nuevos:

- `common/entity/util/Sedation.java` — máquina de estados pura.
- `common/entity/custom/TranquilizerDartEntity.java` — proyectil.
- `common/item/custom/TranquilizerRifleItem.java` y `MedicinePillItem.java`. El dardo como item es
  un `Item` plano.
- `common/item/TranquilizerHelper.java` — aplicar dosis a un critter y dar el feedback, compartido
  por dardo y píldora.
- `network/FireTranquilizerMsg.java`.
- `client/event/TranquilizerClientEvents.java` — captura del clic.
- `mixin/PlayerScopingMixin.java`.
- Texturas, modelos de item, recetas.
- `src/test/.../common/entity/util/SedationTest.java`.

Modificados: `DiverseCritter` (campo, tick, NBT, `isSedated()`, `tryTranquilize()`),
`SleepBehaviorGoal`, `DOCItems`, `DOCEntities`, `DOCClientEvents` (renderer), `DOCNetworkHandler`,
`DOCEntitiesCreativeTab`, `diversityofcritters.mixins.json`, `en_us.json`.

Al vivir todo en `DiverseCritter` y `SleepBehaviorGoal`, Civet, Sifaka, Squirrel y cualquier especie
futura sobre la misma base lo heredan sin tocarlas. El león no es `DiverseCritter` y queda fuera.

---

## 6. Verificación

- **Unit tests** (`SedationTest`): transiciones DROWSY→SEDATED→NONE en los ticks exactos, cooldown
  rechaza y caduca, restaurar desde valores guardados.
- **Build**: `gradlew build`.
- **En juego** (lo prueba el usuario): lista en el plan.
