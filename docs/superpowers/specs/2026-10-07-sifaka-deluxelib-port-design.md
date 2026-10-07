# Port del sifaka a DeluxeLib (módulo 6) — Spec

**Qué:** migrar todos los estados de animación del sifaka (adulto y bebé) del sistema viejo
(`AnimationState` + `animateWhen` + contadores de cliente) al de DeluxeLib que ya usan la civeta y la
ardilla. Mismo comportamiento, mismos clips.

**Encargo:** módulo 6, *Sifaka Port to DeluxeLib* ($40). **Sin animación de caída**: el usuario la
descarta del encargo (no existe la detección de caídas compartida en la que se apoyaba).

---

## Registro (`SifakaEntity.registerAnimations()`)

Con `registerChain` / `registerSingle` y las prioridades `P_*` de `DiverseCritter`. Duraciones = el
`withLength` de cada clip.

| Nombre | Clip(s) | Tipo | Prioridad | Condición |
|---|---|---|---|---|
| `idle` | `Idle` 2.0 | REPEATING | 9 | locomoción en suelo, parado |
| `walk` | `Walk` 1.0, velocidad 0.5 | REPEATING | 8 | en suelo, moviéndose, sin sprint |
| `run` | `Run` 1.0, velocidad 0.5 | REPEATING | 7 | en suelo, moviéndose, sprint |
| `ordered_sit_enter/loop/exit` | `SIT_STARTING` 0.5, `SIT_IDLE` 5.0, `SIT_ENDING` 0.5 | cadena | `P_ORDERED_SIT` | `isOrderedToSit()` |
| `drink_*` | `DRINK_*` 0.25 / 1.0 / 0.25 | cadena | `P_ACTIVITY` | `IsDrinking()` |
| `clean_*` | `GROOMING_*` 0.5 / 0.5 / 0.5 | cadena | `P_ACTIVITY` | `isCleaning()` |
| `eat_*` | `EATING_*` 0.25 / 0.5 / 0.25 | cadena | `P_ACTIVITY` | `isEatingPlant()` |
| `stretch_loop` | `stand_up` 2.0 | PLAY_ONCE | `P_IDLE_VARIANT` | variante `STRETCH` |
| `sniff_left_loop` | `SniffRight` 2.0 | PLAY_ONCE | `P_IDLE_VARIANT` | variante `SNIFF_LEFT` |
| `sniff_right_loop` | `SniffRight2` 2.0 | PLAY_ONCE | `P_IDLE_VARIANT` | variante `SNIFF_RIGHT` |
| `idle_sit_*` | `SIT_*` 0.5 / 5.0 / 0.5 | cadena | `P_IDLE_VARIANT` | variante `SIT` |
| `idle_stand_up_*` | `LAY_*` 1.0 / 1.0 / 1.0 | cadena | `P_IDLE_VARIANT` | variante `STAND_UP` |
| `climb_up` / `climb_down` / `climb_hang` | `CLIMBING_UP` 1.0, `CLIMBING_DOWN` 1.0, `CLIMB_IDLE` 4.0 | REPEATING | `P_CLIMB` | estado de trepada |
| `swim` | `Swim` 2.0, velocidad 1.5 | REPEATING | `P_SWIM` | en agua y vivo |
| `sleep_enter/loop/exit` | `preparing_sleep` 2.0, `Sleep` 4.0, `awakening` 2.0 | REPEATING | `P_SLEEP` | `SleepState` |
| `attack` | `Attack` 0.5 | PLAY_ONCE | `P_ATTACK` | lo lanza `doHurtTarget` |
| capa bebé: `baby_idle_loop`, `baby_crawl_loop` | `idle` 4.0, `move` 4.0 | REPEATING | 9 / 8 | recién nacido, parado / moviéndose |
| capa bebé: `baby_sleep_enter/loop/exit_loop` | `preparing_sleep` 1.0, `sleep` 4.0, `awakening` 1.0 | REPEATING | `P_SLEEP` | recién nacido + `SleepState` |
| capa bebé: `cry_*` | `preparing_cry` 0.5, `cry` 1.0, `stopping_cry` 0.5 | cadena | `P_ACTIVITY` | recién nacido llorando |

"Locomoción en suelo" = vivo, no recién nacido, despierto, sin trepar y fuera del agua (como la
civeta). La velocidad 0.5 de walk/run conserva el ritmo del modelo viejo, que muestreaba el clip a
media velocidad. `Pet` no se usaba y sigue sin usarse.

## Goals

Los mismos que la civeta y la ardilla:

| Antes | Después |
|---|---|
| `BabyCryGoal` | `CritterCryChainGoal` |
| `SitWhenOrderedToGoal` | `CritterOrderedSitGoal` |
| `CritterCleanGoal` | `CritterCleanChainGoal` |
| `CritterEatItemGoal` | `CritterEatItemChainGoal` |
| `CritterDrinkGoal` | `CritterDrinkChainGoal` |
| `SifakaEatPlantGoal` | `SifakaEatLeavesChainGoal` (nuevo, mismo patrón que `SquirrelEatLeavesChainGoal`) |
| `CritterIdleGoal` | `CritterIdleVariantGoal` + `CritterIdleGestureGoal` (stretch 20, sniff_left 25, sniff_right 25; no mientras trepa) |

El sifaka no tiene goal de ataque: el clip `Attack` se registra y lo lanza `doHurtTarget`, pero no se
añade ningún goal (cambiaría el comportamiento).

## Modelos

- `SifakaModel.setupAnim`: reset de poses, escala de cabeza juvenil, `AnimBlend.applyBlended(capa 0)`,
  `LookAtAdditive` en la cabeza (75°/40°) solo durante `idle`, `walk`, `run` y `ordered_sit_loop`, y el
  desplazamiento al nadar. `renderToBuffer` y `translateToRightHand` no cambian.
- `SifakaBabyModel.setupAnim`: reset de poses y `AnimBlend.applyBlended(capa 1)`. Su `root()` pasa a
  devolver el `ModelPart` que envuelve al hueso raíz: si devuelve el hueso, `KeyframeBlender` descarta en
  silencio los canales de ese hueso (ver el javadoc de `CivetModel.meshRoot`).

## Limpieza

- `SifakaEntity`: fuera los 27 `AnimationState`, los contadores `client*`/`prev*Client`,
  `sprintProgress`, `isVariantActive`, `setupAnimationStatesClient()`, `handleCryingAnimationClient()` y
  `triggerFoodSearch()` (el goal de comer en cadena ya escanea solo).
- `doWalkHop()` usaba `isVariantActive`, que solo se rellenaba en el cliente (en servidor siempre era
  `false`): pasa a `isPerformingActivity()`.
- `DiverseCritter`: el sifaka era el último que usaba sus `AnimationState` de sueño y de sentarse por
  orden. Se quitan, con `handleSleepAnimationsClient()`, `updateSitAnimationClient()` y sus contadores.
  `isOrderedSitPlaying()` pasa a leer el animator (la implementación que hoy tiene solo la civeta) y la
  civeta deja de sobreescribirlo. La ardilla, que heredaba la versión de contadores de cliente, gana la
  misma que la civeta.

## Verificación

- Gametest: un sifaka adulto y uno bebé; `getByName` de cada nombre de la tabla no lanza.
- Build completo y gametests existentes en verde.
- En juego (usuario): cada estado del sifaka, uno por uno.
