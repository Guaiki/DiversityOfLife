# Curado pasivo y adopción de crías — Spec

Dos funcionalidades independientes que comparten sitio en `DiverseCritter`. Se especifican juntas
porque ninguna es grande, pero no dependen la una de la otra y pueden implementarse en cualquier
orden.

---

## 1. Curado pasivo (heal-self)

**Qué:** un critter recupera medio corazón por minuto si está bien alimentado.

**Regla:** cada 1200 ticks, si `getHealth() < getMaxHealth()` y hambre y sed están **por encima de la
mitad** de su máximo, `heal(1.0F)`.

En Minecraft un corazón son 2 HP, así que medio corazón es `heal(1.0F)`, no `0.5F`.

**Dónde:** en el bloque de servidor de `DiverseCritter.aiStep()`. Las cuatro especies lo heredan y las
futuras también.

**Umbrales relativos** (`maxHunger() / 2`, `maxThirst() / 2`) y no absolutos, porque cada especie tiene
su propia escala: la civeta tiene 4000 de hambre máxima.

**Escalonado por entidad** con `(tickCount + getId()) % 1200`, para que veinte critters no se curen
todos en el mismo tick.

### Lo que NO hace, a propósito

- No trata distinto a los recién nacidos.
- No se apaga durante el sueño.
- No consulta el sistema de wariness.

Cada condición extra es un sitio más donde el curado puede quedarse mudo sin que se entienda por qué.
El vínculo con la comida y el agua ya cumple el objetivo de diseño: cuidar al animal lo cura,
descuidarlo no.

---

## 2. Adopción de crías

**Qué:** al nacer, un bebé queda a cargo de su madre real. Si la madre muere o se la llevan, otra
hembra adulta libre puede adoptarlo.

### Estado del problema

Hoy **no existe el concepto de madre**. `FindCryingBabyGoal` deja que cualquier hembra adulta de la
misma especie a 24 bloques alimente a cualquier recién nacido que llore, y no limita cuántos atiende
cada una. La adopción no es un caso más del sistema actual: hay que introducir el vínculo.

Un recién nacido con hambre por debajo de 3000 llora (`IS_CRYING`). Con hambre o sed a 0 recibe daño
por `starve()`. O sea que un huérfano desatendido **acaba muriendo**, y eso es intencionado: es lo que
da peso a la mecánica.

### Arquitectura: el vínculo vive solo en el bebé

```java
private @Nullable UUID caregiverUUID;   // solo significativo en recién nacidos
private int lastCaregiverSeenTick;      // persistido, ver "Recarga del mundo"
```

Ambos en `DiverseCritter`, **solo servidor**, persistidos en NBT. No se sincronizan al cliente porque
nada se dibuja a partir de ellos.

Se descartó el enlace bidireccional (bebé → cuidadora y cuidadora → cría). Sería O(1) para comprobar
el límite, pero son dos copias del mismo hecho y se desincronizan en cuanto uno de los dos se
descarga, muere o se serializa a medias.

**Una sola regla cubre los dos casos del encargo.** Para el bebé, "mi madre murió" y "se la llevaron"
son lo mismo: dejó de estar cerca. No hace falta distinguirlos.

### Ciclo de vida

**Al nacer.** `DiverseCritter.giveBirth()` corre sobre la madre, así que es una línea en un único
sitio que vale para las cuatro especies:

```java
if (baby instanceof DiverseCritter cria) {
    cria.setCaregiver(this.getUUID());
}
```

Un bebé invocado con `/summon` o con huevo no tiene cuidadora: nace huérfano y es adoptable de
inmediato. Es el comportamiento correcto por defecto.

**Presencia.** En el tick del bebé, cada 20 ticks:

```java
Entity madre = serverLevel.getEntity(caregiverUUID);   // lookup O(1), sin escanear
if (madre instanceof DiverseCritter m && m.isAlive() && distanceTo(m) <= CAREGIVER_RANGE) {
    lastCaregiverSeenTick = tickCount;
}
if (tickCount - lastCaregiverSeenTick > ORPHAN_TIMEOUT) {
    caregiverUUID = null;   // huérfano
}
```

`ServerLevel.getEntity(UUID)` es una búsqueda en mapa, no un escaneo por área. Si la madre está en un
chunk descargado devuelve null, que cuenta como ausencia — correcto, porque desde el punto de vista
del bebé lo es.

**Muerte de la madre.** El encargo pide que la muerte libere al bebé **al instante**, no a los 60 s.
Como al morir la entidad desaparece y no se puede distinguir de "chunk descargado", se resuelve desde
el otro lado: al morir, la madre busca en un AABB generoso los recién nacidos que le apunten y les
corta el vínculo. El temporizador de 60 s queda como red para todo lo demás — que se la lleven, que
muera lejos de la cría, que cambie de dimensión.

**Adopción.** Cuando una hembra libre termina de amamantar a un huérfano:
`cria.setCaregiver(getUUID())`. El vínculo es permanente y sustituye al anterior.

### Cambio en `FindCryingBabyGoal`

El filtro de `canUse()` pasa de "cualquier recién nacido que llore" a:

| Caso | Acción |
|---|---|
| Es mi cría | La atiendo siempre |
| Es huérfano y yo no tengo cría | Lo adopto |
| Cualquier otro | Lo ignoro |

`tengoCria()` busca en un AABB de **`CAREGIVER_RANGE`, no en la caja de búsqueda del goal**, y sin
filtrar por `isCrying` — mi propia cría puede estar bien alimentada, y en ese caso no llora.

Que use el mismo radio que la comprobación de presencia **no es un detalle**: es lo que sostiene la
propiedad de auto-reparación de la sección siguiente. Con la caja de 24 del goal habría una banda entre
24 y 32 bloques en la que la madre se cree libre mientras la cría la sigue considerando suya, y la
madre acabaría con dos crías. Los dos radios tienen que ser el mismo número.

Se mantiene el resto de guardas actuales: no bebé, no dormido, no sentado, no pasajero, hembra
(`!getIsMale()`), misma especie.

### Constantes

| Nombre | Valor | Por qué |
|---|---|---|
| `CAREGIVER_RANGE` | 32 bloques | Generoso: una madre pastando cerca no debe perder a su cría |
| `ORPHAN_TIMEOUT` | 1200 ticks (60 s) | Lo pedido |
| Intervalo de presencia | 20 ticks | Un lookup por bebé por segundo; los bebés son pocos |

### Dos propiedades no obvias

**El sistema se auto-repara.** El punto flojo de guardar el vínculo solo en el bebé es que si mi cría
está fuera de `CAREGIVER_RANGE`, no la veo y me creo libre para adoptar otra. Pero las dos
comprobaciones usan ese mismo radio, así que si estoy tan lejos que no la veo, **ella tampoco me ve a
mí**, y en 60 s se declara huérfana. Los dos estados convergen solos, y el desacuerdo dura como mucho
los 60 s del temporizador.

Es exactamente la coherencia que el enlace bidireccional prometía mantener a mano, salvo que aquí sale
gratis — siempre que los radios coincidan. Si alguien los separa, la propiedad desaparece sin que nada
falle de forma visible.

**No hay reserva sobre el huérfano.** Dos hembras pueden caminar hacia el mismo bebé, pero la primera
que lo amamanta lo vincula y la segunda, en su siguiente `canUse()`, ve que ya no es huérfano y lo
suelta. La ventana es de segundos y el peor síntoma es un paseo inútil. No merece la maquinaria de
`CritterClaims`.

### Recarga del mundo

`lastCaregiverSeenTick` se persiste, pero `tickCount` **se reinicia a 0** cuando la entidad vuelve del
disco. La resta `tickCount - lastCaregiverSeenTick` daría un número negativo enorme y el bebé no se
declararía huérfano nunca.

Al cargar desde NBT, `lastCaregiverSeenTick` se pone a `tickCount` (es decir, 0): el bebé arranca con
el contador a cero y tiene sus 60 s para reencontrar a su madre antes de considerarse huérfano. Eso es
lo deseable — recargar un mundo no debe huerfanar a nadie, ni tampoco perpetuar un vínculo con una
madre que ya no existe.

### Si nadie adopta

No se añade nada. El bebé sigue llorando, el hambre baja y muere de inanición. El jugador puede
salvarlo con el biberón (`FILLED_NURSER_BOTTLE`), que ya existe y ya alimenta bebés.

Alimentarlo con el biberón **no** crea vínculo: rellena hambre y sed como hasta ahora. Que el jugador
pueda convertirse en cuidador es una mecánica aparte y queda fuera.

---

## Fuera de alcance

- **Que el bebé siga a su cuidadora.** Hoy usa `FollowParentGoal` de vanilla, que va al adulto más
  cercano de su especie. Cambiarlo para que siga a su cuidadora concreta es una mejora natural, pero es
  otra funcionalidad.
- **Adopción por el jugador** vía biberón.
- **Machos como cuidadores**, y hembras con más de una cría.
- **Aviso al jugador** de que un bebé ha quedado huérfano.

## Verificación

No hay tests automatizados en este repo: `src/test/java` está vacío, no hay JUnit y la config de
GameTest no registra ningún test. El ciclo es **compilar → comprobación estática → comprobación en
juego → commit**.

Comandos: `./gradlew compileJava --console=plain` y `./gradlew runClient`. **Nunca `gradlew clean`**:
rompe el `fg_cache` de ForgeGradle.

### Casos a comprobar en juego

**Curado**

1. Hiere a un critter bien alimentado y espera un minuto: recupera medio corazón.
2. Baja su hambre por debajo de la mitad: deja de curarse.

**Adopción**

3. Cría un bebé y déjalo con hambre: solo su madre acude, aunque haya otras hembras más cerca.
4. Mata a la madre: otra hembra libre acude en cuanto el bebé vuelva a llorar.
5. Llévate a la madre lejos y espera 60 s: el bebé queda libre y lo adopta otra hembra.
6. Devuelve a la madre antes de los 60 s: sigue siendo suya y ninguna otra lo toca.
7. Con una sola hembra libre y dos huérfanos: adopta uno y el otro se queda sin atender.
8. Ese segundo huérfano acaba muriendo de hambre si no lo salvas con el biberón.
9. Guarda y recarga el mundo con un bebé vinculado: sigue siendo de su madre y no se huerfana al
   entrar.
10. `/summon` de un bebé: nace huérfano y cualquier hembra libre lo adopta.
11. Aleja a una madre hasta el borde de `CAREGIVER_RANGE` y suelta ahí un huérfano: **no** debe
    adoptarlo. Es el caso que rompe si los dos radios dejan de coincidir, y desde fuera se ve como una
    hembra con dos crías sin explicación.
