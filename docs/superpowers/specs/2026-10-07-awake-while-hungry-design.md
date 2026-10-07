# Despierto mientras tenga hambre (módulo 4) — Spec

**Qué:** un critter con hambre no se duerme, y si le entra hambre durmiendo se despierta. Vuelve a
poder dormir cuando ha comido.

**Encargo:** módulo 4, *Animals Stay Awake While Hungry* ($10): se integra con la lógica de despertar
forzado que ya existe para el daño y el agua.

---

## Umbral

Se usa `DiverseCritter.isHungry()` tal cual: hambre ≤ 50 % del máximo. Es el mismo umbral con el que
los goals de comida (comedero, items, presas, hojas) se ponen en marcha, así que "tiene hambre"
significa lo mismo en todo el mod: busca comida y no duerme. Con dos umbrales distintos podría quedar
despierto sin buscar comida.

## Dónde se engancha

Todo en `SleepBehaviorGoal`:

1. **`canUse()`** — si `isHungry()`, devuelve `false`. Va después de la adopción de estado huérfano y
   del tranquilizante, y después de registrar la orden del jugador (`consumeScheduleChanged()` →
   `scheduleForced`) pero antes de cumplirla: la orden no se pierde, se cumple cuando haya comido.
2. **`canContinueToUse()`** — el hambre se suma a la condición de despertar forzado:
   `hurtTime > 0 || isInWater() || hasThreat() || isHungry()` → `startWaking(true)`. Despierta con su
   clip y deja la vigilia de despertar forzado (`FORCED_WAKE_COOLDOWN`, 600 ticks); después
   `canUse()` lo mantiene despierto mientras siga con hambre.

## Lo que no cambia

- **El tranquilizante gana al hambre.** La tanda sedada se resuelve antes en los dos métodos, así que
  un animal sedado duerme aunque tenga hambre.
- **La sed no cuenta.** El encargo dice *hungry*; el "agua" de la lógica de despertar es estar dentro
  del agua.
- **Con `doCritterNeeds = false`** (módulo 3) las barras están llenas: nunca hay hambre y el sueño es el
  de siempre.

Efecto visible: una cría huérfana con hambre se queda despierta hasta que le dan el biberón.

## Verificación

Gametests con la hora del mundo dentro de la ventana de sueño de la civeta (nocturna, duerme de día:
`setDayTime(6000)`):

1. Una civeta alimentada y otra al 25 % de hambre (hambrienta pero lejos de morir de inanición). La
   alimentada llega a `SLEEPING`; la hambrienta está `AWAKE` en todos los ticks.
2. Una civeta alimentada se duerme; al ponerle el hambre al 25 % pasa a `AWAKENING` y luego a `AWAKE`,
   y sigue despierta. Su tanda dura 2-5 minutos, así que despertar en segundos solo puede ser por el
   hambre.

Build completo, gametests existentes en verde y sección nueva en la guía del cliente.
