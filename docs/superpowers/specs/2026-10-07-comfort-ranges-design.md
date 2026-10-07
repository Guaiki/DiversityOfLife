# Rangos de confort (módulo 5) — Spec

**Qué:** ajustar los rangos de confort de temperatura y humedad de cada especie a los valores pedidos.
Sin cambios en los sistemas.

**Encargo:** módulo 5, *Temperature & Humidity Comfort Ranges* (sin coste).

## Valores

| Especie | Temperatura: antes → ahora | Humedad: antes → ahora |
|---|---|---|
| Ardilla (`SquirrelEntity`) | COOL–WARM → **FREEZING**–WARM | MODERATE–HUMID → **ARID–SATURATED** |
| Civeta (`CivetEntity`) | WARM–HOT → **COOL**–HOT | HUMID–WET → **MODERATE**–WET |
| Sifaka (`SifakaEntity`) | WARM–HOT → **MODERATE**–HOT | LOW–MODERATE → **ARID**–MODERATE |

Se cambian solo los `return` de `comfortTierMin()`, `comfortTierMax()`, `comfortHumidityMin()` y
`comfortHumidityMax()` de cada entidad.

## Efectos

- La ardilla cubre los 7 niveles de humedad: nunca entra en estrés ni recibe daño por humedad, y el
  humidificador no le afecta.
- Las tres especies pasan menos tiempo en estrés (menos multiplicadores de hambre y sed) y pueden criar
  en más biomas, lo que acelera algo la recuperación de la población salvaje (módulo 2b).
- La guía del cliente sigue valiendo: la civeta sigue en peligro en la llanura nevada (FREEZING queda a
  2 niveles de COOL) y en el desierto (ARID queda a 3 de MODERATE).

Se descartó mover los rangos a la config: el encargo pide no cambiar los sistemas.

## Verificación

- Gametest: invoca las tres especies y comprueba sus cuatro límites contra esta tabla.
- Build completo y gametests existentes en verde.
