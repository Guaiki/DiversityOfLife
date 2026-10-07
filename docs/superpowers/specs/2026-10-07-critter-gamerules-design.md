# Gamerules de necesidades y clima (módulo 3) — Spec

**Qué:** dos gamerules propios, con la sintaxis estándar `/gamerule <nombre> true|false`, que apagan
los sistemas de hambre/sed y de temperatura/humedad de los critters.

**Encargo:** módulo 3, *Gamerules for Disabling Hunger & Thirst / Humidity & Temperature* ($20).
Incluye: el drenaje de hambre y sed, los sistemas de temperatura y humedad, su daño ambiental y sus
multiplicadores de stats.

---

## Los dos gamerules

| Gamerule | Por defecto | Apaga |
|---|---|---|
| `doCritterNeeds` | `true` | hambre y sed |
| `doCritterClimate` | `true` | temperatura y humedad |

- Categoría **Mobs** del menú "Edit Game Rules", con nombre y descripción en `en_us.json`
  (`gamerule.<nombre>` y `gamerule.<nombre>.description`).
- Se registran en una clase `DOCGameRules` (`common/gamerule/`), que se inicializa desde el
  constructor del mod: `GameRules.register` tiene que correr antes de cargar cualquier mundo.
- Viven en el mundo, como cualquier gamerule, así que se guardan con él.

Se descartó un gamerule por sistema (cuatro): el presupuesto los agrupa por parejas, y son la mitad
de reglas que documentar y probar.

---

## `doCritterNeeds = false`

- Hambre y sed se **mantienen llenas**: cada tick de servidor, si están por debajo del máximo, se
  ponen al máximo. Solo se escribe cuando no está lleno, para no sincronizar en cada tick.
- El drenaje no corre.
- Como nunca llegan a 0, desaparecen la muerte por inanición y por sed (y su resta en la población
  salvaje del módulo 2b).
- Enriquecimiento e higiene **no cambian**: no están en el encargo.
- Al volver a `true`, el drenaje sigue desde lleno.

Se descartó congelar las barras donde estén: un animal congelado al 10 % sería "hambriento" para
siempre, buscaría comida sin parar y, con el módulo 4, nunca dormiría.

## `doCritterClimate = false`

- `getTemperatureZone()` y `getHumidityZone()` devuelven 0 (confort). Todo lo demás sale de ahí sin
  tocarlo:
  - sin daño ambiental (necesita zona ≥ 2);
  - multiplicadores de hambre y sed a ×1;
  - `canMate` deja de bloquear la cría por clima;
  - sin muertes por clima, así que tampoco restan población.
- Las barras de confort de temperatura y humedad se ponen a 100 y se quedan ahí.
- Las pantallas de stats muestran `Temp: Off` y `Humidity: Off`.
- El aire acondicionado y el humidificador siguen funcionando como bloques (su nieve y su hielo son
  efectos sobre el mundo), pero no afectan a los animales mientras el gamerule esté apagado.

---

## Sincronización al cliente

Minecraft no envía los gamerules al cliente, y las pantallas calculan la zona en el cliente.

**Solución:** un `byte` sincronizado en `DiverseCritter` (`RULES_OFF`): bit 1 = necesidades apagadas,
bit 2 = clima apagado. El servidor lo recalcula cada 20 ticks a partir de los gamerules y solo lo
escribe si cambió. `needsEnabled()` y `climateEnabled()` lo leen en los dos lados, así que la zona sale
igual en servidor y cliente sin más código.

Por defecto vale 0 (todo activo), así que los animales de mundos existentes no cambian nada.

Se descartó un paquete que envíe los gamerules a cada cliente: hay que escuchar los cambios de
gamerule y el login para el mismo resultado.

El retraso de hasta un segundo entre `/gamerule` y el efecto no se nota en juego.

---

## Pantallas de stats

Las tres pantallas (civeta, sifaka, ardilla) repiten el mismo bloque que construye las líneas de
temperatura y humedad. Se extrae a un helper `ClimateLine` en `client/screen/` que devuelve texto y
color, y que escribe `Off` (en el color normal del texto) cuando el clima está apagado.

---

## Relación con otros módulos

- **Módulo 4** (no dormir con hambre): con `doCritterNeeds = false` las barras están llenas, así que
  nunca hay hambre que le impida dormir.
- **Módulo 2b**: no hay que tocarlo; las muertes que apagan los gamerules simplemente dejan de ocurrir.

---

## Verificación

- **Gametests**, uno por gamerule. Cada uno apaga su regla, comprueba el efecto y la vuelve a
  encender:
  - `doCritterNeeds = false`: una civeta con hambre y sed a 0 termina con las dos llenas y viva.
  - `doCritterClimate = false`: la civeta tiene zona 0 en temperatura y humedad y las dos barras de
    confort a 100.
- **Build completo** y gametests existentes en verde.
- **En juego** (usuario): sección nueva en la guía del cliente.

## Fuera de alcance

- Gamerules para enriquecimiento o higiene.
- Desactivar los bloques de aire acondicionado o humidificador.
- Cambiar los rangos de confort (es el módulo 5).
