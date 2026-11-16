# Mecánicas de ecosistema (presupuesto nov, $145) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Las siete mecánicas del presupuesto del 4-nov-2026 (sin el okapi): despertar, sed por comida,
waterhole, buscar confort, ganado vanilla, caza por categorías y cadáveres.

**Architecture:** Cada mecánica es una tarea independiente con su gametest y su prueba en juego. Las
piezas compartidas son pocas y se crean en la primera tarea que las necesita: `DiverseCritter.hasUrgentNeed()`
(T1), `DiverseCritter.onAteFood()` (T2), `DiverseCritter.climateScoreAt()` (T4), las etiquetas de
categoría y `preyCategories()` (T6), que T7 usa para el cadáver.

**Tech Stack:** Forge 47.4.10 (MC 1.20.1), parchment, GameTest de Forge, datagen (`DoC*TagGenerator`).

**Spec:** `docs/quotes/Okapi-and-Mechanics-Quote.pdf`, página 2 (sección "2. New mechanics").

## Global Constraints

- Paquete base `com.evirapo.diversityofcritters`. Rutas de Java relativas a
  `src/main/java/com/evirapo/diversityofcritters/`.
- Afecta a todos los `DiverseCritter` (civeta, ardilla, sifaka). `LionEntity` extiende `Animal` y queda fuera.
  El okapi se aparca, pero nada debe impedir añadirlo (p. ej. `isLargeAnimal()` existe aunque hoy ninguna especie lo use).
- Fuera de alcance: que las presas huyan de los depredadores. Siguen huyendo solo de jugadores.
- Todo valor ajustable va en `DOCServerConfig` (sección nueva `ecosystem`), con el valor por defecto
  indicado en cada tarea.
- Con `doCritterNeeds` apagado no hay sed ni hambre que despierte; con `doCritterClimate` apagado no hay
  peligro climático ni búsqueda de confort (las zonas ya devuelven 0).
- Nunca `gradlew clean`. No añadir `build.gradle`, `mods.toml`, `gradle.properties` ni `run-data/` a los commits.
- Los gametests corren a la vez en el mismo mundo: cada uno limpia con `helper.killAllEntities()`.
- Verificación por tarea: Claude ejecuta `gradlew build` y `gradlew runGameTestServer` y reporta el
  resultado real; el usuario hace la prueba en juego de la tarea. No se pasa a la siguiente sin su OK.
- Al cerrar cada tarea, añadir su sección a la guía de pruebas para el cliente (artifact en inglés).

## Review Focus

1. **Mascotas y órdenes:** un animal domesticado sentado o siguiendo al dueño no se mueve por confort ni
   caza; una mascota nunca es presa de un depredador (ni salvaje ni de otro dueño). Tests en T4 y T6.
2. **Animal sin salida:** un critter en peligro climático sin ningún sitio mejor al alcance no debe
   quedarse oscilando ni bloquear comer/beber para siempre. Test en T4.
3. **Rendimiento con muchos animales:** el escaneo de confort, el conteo del tope de ganado y la
   búsqueda de presas a 32 bloques corren con enfriamiento, nunca cada tick. Revisado en T4, T5 y T6.
4. **Cadáver en sitio imposible:** presa muerta en el agua, en el aire o sobre un bloque no sólido;
   el cadáver busca hueco cerca y, si no hay, cae el botín normal. Test en T7.
5. **Datos guardados:** el cadáver conserva porciones y edad al recargar el mundo, y el waterhole no
   desaparece ni se seca con estaciones o temperatura. Tests en T3 y T7.

---

### Task 1: Despertar por sed o peligro climático ($8)

**Files:**
- Modify: `common/entity/custom/base/DiverseCritter.java` (junto a `isHungry()`, ~1203)
- Modify: `common/entity/ai/SleepBehaviorGoal.java` (~198 en `canUse`, ~272 en `canContinueToUse`)
- Modify: `gametest/AwakeWhileHungryGameTests.java` (mismos helpers `floor`, `NOON`, `ARENA`)

**Interfaces:**
- Produces: `public boolean hasUrgentNeed()` en `DiverseCritter` = `isHungry() || isThirsty() || getTemperatureZone() >= 2 || getHumidityZone() >= 2`.

- [ ] **Step 1:** Añadir a `AwakeWhileHungryGameTests` los tests `thirstyCivetDoesNotSleep`,
  `thirstWakesSleepingCivet` (sed a `maxThirst()/4`, hambre llena; aserción: `getSleepState()` acaba
  `AWAKE`) y `climateDangerWakesSleepingCivet` (forzar peligro con un aire acondicionado al extremo
  frío junto al animal; misma aserción).
- [ ] **Step 2:** `gradlew runGameTestServer` → los tres nuevos FALLAN (el animal sigue dormido).
- [ ] **Step 3:** Añadir `hasUrgentNeed()` y sustituir `isHungry()` por `hasUrgentNeed()` en las dos líneas de `SleepBehaviorGoal`.
- [ ] **Step 4:** `gradlew runGameTestServer` → PASAN todos, incluidos los de hambre previos.
- [ ] **Step 5:** Commit `feat: critters wake up when thirsty or in climate danger`.

**Cómo comprobarlo en juego**
1. `/time set noon`, ponte junto a una civeta (duerme de día). Espera a que se duerma.
2. `/doc_thirst 20` a menos de 5 bloques → se despierta (animación de despertar) y va a beber.
3. Repite con `/doc_thirst 100` y llévala (correa o Carry On) a un bioma nevado sin abrigo: cuando el
   panel de stats marque temperatura en peligro (rojo), se despierta. Con `/gamerule doCritterClimate false` ya no.

---

### Task 2: Sed al comer ($12)

**Files:**
- Modify: `common/entity/custom/base/DiverseCritter.java`
- Modify: todos los sitios que comen (lista con `grep -rn "setHunger(.*maxHunger())"`):
  `CritterEatItemChainGoal`, `CritterEatItemGoal`, `SifakaEatLeavesChainGoal`, `SifakaEatPlantGoal`,
  `SquirrelEatLeavesChainGoal`, `SquirrelEatLeavesGoal`, `BowlFeedingHelper.consumeFoodFor`, y los de
  `CivetEntity`, `SquirrelEntity`, `SifakaEntity`. **No** tocar: lectura NBT (`DiverseCritter` ~649),
  necesidades apagadas (~996), biberón (~1229), `FindCryingBabyGoal` (amamantar no es comer).
- Modify: `common/config/DOCServerConfig.java`
- Create: `gametest/ThirstFromFoodGameTests.java`

**Interfaces:**
- Produces: `public boolean isLargeAnimal()` (default `false`, constante por especie);
  `public void onAteFood()` = hambre al máximo + sed `+ maxThirst() * pct / 100`, con
  `pct = isLargeAnimal() ? LARGE_THIRST_FROM_FOOD : SMALL_THIRST_FROM_FOOD`.
- Config: `ecosystem.smallThirstFromFood` (int %, def. 50, 0-100), `ecosystem.largeThirstFromFood` (def. 15).

- [ ] **Step 1:** Test `eatingFromBowlQuenchesThirst`: civeta con sed 10 y hambre 10, cuenco de carne con
  un filete al lado; aserción: al vaciarse el cuenco `getThirst() == 60` y `getHunger() == maxHunger()`.
  Test `fullThirstIsCapped`: sed 90 → tras comer `getThirst() == 100`.
- [ ] **Step 2:** Ejecutar → FALLA (sed sigue en 10).
- [ ] **Step 3:** Añadir `isLargeAnimal()`, `onAteFood()` y la config; sustituir cada `setHunger(maxHunger())` de comer por `onAteFood()`.
- [ ] **Step 4:** Ejecutar → PASA.
- [ ] **Step 5:** Commit `feat: eating quenches thirst (small animals more, large less)`.

**Cómo comprobarlo en juego**
1. Junto a una civeta: `/doc_hunger 10` y `/doc_thirst 10`. Abre su panel de stats: ambas barras bajas.
2. Tírale un filete crudo al suelo (o ponlo en un cuenco de carne). Al comérselo: hambre llena, sed a la mitad.
3. Lo mismo con una ardilla comiendo hojas o bellotas.

---

### Task 3: Waterhole ($25)

**Files:**
- Create: `common/block/WaterholeBlock.java`, `common/entity/util/DrinkSources.java`
- Modify: `common/block/DOCBlocks.java`, `common/item/DOCItems.java` (BlockItem), `client/event/DOCClientEvents.java`
  (`RegisterColorHandlersEvent.Block` con `BiomeColors.getAverageWaterColor`, render type `translucent` en el JSON del modelo)
- Modify: `common/entity/ai/CritterDrinkChainGoal.java` (`findWater`, `landNextTo` ~142), `common/entity/ai/CritterDrinkGoal.java` (~167, ~180)
- Modify: `misc/tags/DoCTags.java`, `datagen/providers/DoCBlockTagGenerator.java`, `DoCBiomeTagGenerator.java`, `DoCBlockLootTables.java`
- Create: `src/main/resources/data/diversityofcritters/worldgen/configured_feature/waterhole.json`,
  `.../worldgen/placed_feature/waterhole.json`, `.../forge/biome_modifier/waterhole.json`
- Create: blockstate, modelo (texturas `minecraft:block/water_still` con `tintindex: 0`), lang
- Create: `gametest/WaterholeGameTests.java`

**Interfaces:**
- Produces: bloque `diversityofcritters:waterhole` (forma 16×1×16, sin colisión, `canSurvive` pide cara
  superior sólida debajo, se rompe soltándose a sí mismo, no tiene estado de secado ni fluido real);
  etiqueta de bloque `diversityofcritters:drinkable` (contiene el waterhole);
  `DrinkSources.isDrinkable(Level level, BlockPos pos): boolean` = fluido `FluidTags.WATER` **o** bloque en `drinkable`.
- Worldgen: `random_patch` de 1-3 bloques (`tries 6`, `xz_spread 2`), `rarity_filter` 1/24,
  `in_square`, `heightmap MOTION_BLOCKING_NO_LEAVES`, `would_survive`. Paso `vegetal_decoration`.
  Biomas: etiqueta `diversityofcritters:has_waterhole` = `#minecraft:is_overworld` menos océanos, ríos,
  playas y `mushroom_fields`.

- [ ] **Step 1:** Tests `thirstyCivetDrinksFromWaterhole` (suelo de piedra sin agua, waterhole a 6
  bloques, sed 10; aserción: `getThirst()` sube por encima de 50) y `waterholeNeverDries`
  (aire acondicionado al máximo calor al lado; aserción: `helper.assertBlockPresent` del waterhole tras 600 ticks).
- [ ] **Step 2:** Ejecutar → FALLA (el bloque no existe).
- [ ] **Step 3:** Bloque, registro, color, `DrinkSources`, y usar `DrinkSources.isDrinkable` en los dos goals de beber.
- [ ] **Step 4:** Ejecutar → PASA. Correr también `gradlew runData` y comprobar los JSON generados.
- [ ] **Step 5:** Worldgen (tres JSON + etiqueta de biomas). `gradlew build`.
- [ ] **Step 6:** Commit `feat: waterhole block that generates in the world and critters drink from`.

**Cómo comprobarlo en juego**
1. En un mundo existente: `/place feature diversityofcritters:waterhole ~ ~ ~` → aparecen 1-3 charcos.
2. `/doc_thirst 10` junto a una civeta, sin agua cerca salvo el charco → camina al charco y bebe; la barra de sed sube.
3. Crea un mundo nuevo y explora llanuras, bosque y sabana: deberías ver charcos sueltos cada pocos chunks; en el océano no.
4. Déjalo un día entero en el desierto: sigue ahí (no se seca).

---

### Task 4: Buscar confort ($30)

**Files:**
- Create: `common/entity/ai/SeekComfortGoal.java`
- Modify: `common/entity/custom/base/DiverseCritter.java` (`climateScoreAt`, registro del goal en `registerGoals()` ~373, priority 5)
- Modify: `common/config/DOCServerConfig.java`
- Create: `gametest/SeekComfortGameTests.java`

**Interfaces:**
- Consumes: `TemperatureSystem.getEffectiveTier(Level, BlockPos)`, `HumiditySystem.getEffectiveTier(Level, BlockPos)`
  (ya incluyen aire acondicionado y humidificador), `TemperatureTier/HumidityTier.distanceOutside(min, max)`,
  `Reachability.nearestReachable(...)` y `Reachability.repath(...)`.
- Produces: `public int climateScoreAt(BlockPos pos)` = `w(tempDist) + w(humDist)` con
  `w(0)=0, w(1)=1, w(≥2)=4` (el peligro pesa más que dos estrés juntos).
  `SeekComfortGoal(DiverseCritter critter, double speed)`, flags `MOVE`.
- Config: `ecosystem.comfortSearchRadius` (def. 24), `ecosystem.comfortScanCooldownTicks` (def. 100).

**Reglas del goal (decididas):**
- `canUse`: servidor, clima activo, `climateScoreAt(blockPosition()) > 0`, no recién nacido, no
  sedado, no dormido; domesticado solo si `isWandering()`. Si solo hay estrés (sin peligro) y el animal
  tiene hambre o sed, cede (comer y beber primero); en peligro va primero el confort.
- Escaneo con enfriamiento: 16 posiciones aleatorias de suelo en el radio; destino = la de menor
  puntuación, y solo si es **estrictamente mejor** que la actual. Sin destino mejor → enfriamiento ×3
  (no oscila, no bloquea comer/beber).
- Evitar el peligro: un paseo que entra en peligro dispara el goal (priority 5) y el animal sale; el goal
  nunca elige un destino peor que donde está.

- [ ] **Step 1:** Tests: `uncomfortableCivetWalksToAirConditioner` (aire acondicionado templado a 12
  bloques, el resto del área forzado a frío por otro aparato; aserción: termina con `climateScoreAt == 0`
  y dentro del radio del aparato); `sittingTamedCivetStays` (domesticada + `setOrderedToSit(true)`; aserción:
  no se mueve más de 1 bloque en 300 ticks); `noBetterSpotDoesNotOscillate` (todo el área igual de fría;
  aserción: puede seguir comiendo de un cuenco cercano).
- [ ] **Step 2:** Ejecutar → FALLAN.
- [ ] **Step 3:** `climateScoreAt`, config y `SeekComfortGoal`; registrarlo en `DiverseCritter.registerGoals()`.
- [ ] **Step 4:** Ejecutar → PASAN.
- [ ] **Step 5:** Commit `feat: critters seek comfortable climate and leave dangerous spots`.

**Cómo comprobarlo en juego**
1. Bioma nevado. Pon un aire acondicionado en modo calor y aléjate 15 bloques con una civeta salvaje.
2. En unos segundos camina hacia el aparato y se queda en su radio; el panel de stats pasa de rojo/amarillo a verde.
3. Domestica otra, ponla en **wandering**: hace lo mismo. Ponla **sentada**: se queda quieta aunque pase frío. En **seguir**: te sigue a ti.
4. Humedad: lo mismo con un humidificador en un desierto (la ardilla tiene rango húmedo).

---

### Task 5: Ganado vanilla ($25)

**Files:**
- Create: `common/spawn/LivestockRules.java` (conteo y tope), `common/entity/ai/LivestockSelfBreedGoal.java`
- Create: `common/command/LivestockDebugCommand.java` (`/doc_livestock`)
- Modify: `common/event/DOCForgeEvents.java` (registrar comando; `MobSpawnEvent.FinalizeSpawn`; `EntityJoinLevelEvent` para añadir el goal)
- Modify: `common/config/DOCServerConfig.java`, `misc/tags/DoCTags.java`, `datagen/providers/DoCEntityTagGenerator.java`, `DoCBiomeTagGenerator.java`
- Create: `src/main/resources/data/diversityofcritters/forge/biome_modifier/livestock_spawns.json` (`forge:add_spawns`, vaca/cerdo/oveja/gallina, peso 8, grupo 2-4)
- Create: `gametest/LivestockGameTests.java`

**Interfaces:**
- Produces: etiqueta de entidad `diversityofcritters:livestock` (cow, pig, sheep, chicken);
  etiqueta de bioma `diversityofcritters:livestock_biomes` = `#minecraft:is_overworld` menos océanos, ríos, playas y `mushroom_fields`;
  `LivestockRules.countNearby(ServerLevel level, EntityType<?> type, BlockPos center): int` (radio de config, incluye crías);
  `LivestockRules.isAtCap(ServerLevel level, EntityType<?> type, BlockPos center): boolean`.
- `FinalizeSpawn`: solo `MobSpawnType.NATURAL` y `CHUNK_GENERATION`, solo `livestock`; si `isAtCap` → `setSpawnCancelled(true)`.
- `LivestockSelfBreedGoal(Animal animal)`: cada `livestockBreedIntervalSeconds` con un 50 % de
  probabilidad, si es adulto, `getAge()==0`, no está enamorado, hay pareja adulta disponible a ≤ 8 bloques
  y `!isAtCap` → `setInLove(null)` en ambos; el `BreedGoal` vanilla hace el resto. La cría por el jugador no cambia.
- Config: `ecosystem.livestockEnabled` (true), `livestockCap` (def. 6, rango 1-64), `livestockCapRadius` (def. 32), `livestockBreedIntervalSeconds` (def. 600).

- [ ] **Step 1:** Tests `twoCowsBreedOnTheirOwn` (intervalo forzado a 1 s vía config en el test; aserción: aparece una cría)
  y `cowsStopAtCap` (6 vacas adultas; aserción: tras 600 ticks siguen siendo 6).
- [ ] **Step 2:** Ejecutar → FALLAN.
- [ ] **Step 3:** Implementar reglas, goal, eventos, etiquetas, biome modifier y comando
  (`/doc_livestock` imprime, por tipo, `contados / tope` en el radio del jugador).
- [ ] **Step 4:** Ejecutar → PASAN.
- [ ] **Step 5:** Commit `feat: vanilla livestock spawns widely and self-breeds under an area cap`.

**Cómo comprobarlo en juego**
1. En `serverconfig/diversityofcritters-server.toml` pon `livestockBreedIntervalSeconds = 10` (solo para probar).
2. Valla un corral y mete 2 vacas: `/summon cow ~ ~ ~` dos veces. En 10-30 s se enamoran (corazones) y nace un ternero.
3. Repite hasta que haya 6: `/doc_livestock` muestra `cow 6 / 6` y ya no nacen más.
4. Mundo nuevo: en taiga, sabana, jungla o desierto aparecen vacas, cerdos, ovejas o gallinas, y nunca más de 6 de un tipo en la misma zona.
5. Devuelve el intervalo a 600.

---

### Task 6: Caza por categorías ($20)

**Files:**
- Create: `common/entity/ai/CritterHuntTargetGoal.java`
- Modify: `misc/tags/DoCTags.java` (`DoCTags.EntityTypes`), `datagen/providers/DoCEntityTagGenerator.java`
- Modify: `common/entity/custom/base/DiverseCritter.java` (`preyCategories`, `huntRange`, `canHunt`)
- Modify: `common/entity/custom/CivetEntity.java` (quitar los dos `NearestAttackableTargetGoal` ~213-214 e `isPrey` ~915; `FOLLOW_RANGE` 16 → 32)
- Create: `gametest/HuntingGameTests.java`

**Interfaces:**
- Produces: etiquetas de entidad `diversityofcritters:category/small_rodent` (squirrel),
  `category/small_carnivora` (civet), `category/small_primate` (sifaka), `category/lagomorph` (rabbit),
  `category/small_bird` (chicken, parrot). Editables por datapack.
  `DoCTags.EntityTypes.CATEGORIES: List<TagKey<EntityType<?>>>` (todas, en ese orden).
  `public List<TagKey<EntityType<?>>> preyCategories()` (default vacía); civeta: `small_rodent, lagomorph, small_bird`.
  `public double huntRange()` (default 32).
  `public static boolean isPreyFor(DiverseCritter hunter, LivingEntity target)`: tipo en alguna categoría
  del cazador, distinta especie, vivo, **no** domesticado (`TamableAnimal#isTame`).
- `CritterHuntTargetGoal(DiverseCritter hunter)` extiende `TargetGoal`: solo si `isHungry()`, adulto, no
  sedado, y salvaje o domesticado en wandering; busca con `TargetingConditions.forCombat().range(huntRange())`
  (con línea de visión) cada 20 ticks y fija el objetivo más cercano. El ataque lo sigue haciendo `AnimatableMeleeAttackGoal`.

- [ ] **Step 1:** Tests `hungryCivetTargetsSquirrelAt25Blocks` (aserción: `getTarget()` es la ardilla),
  `fedCivetIgnoresPrey` (hambre llena → `getTarget() == null`), `civetIgnoresTamedSquirrel` y `civetIgnoresSifaka`.
- [ ] **Step 2:** Ejecutar → FALLAN.
- [ ] **Step 3:** Etiquetas (+ `gradlew runData`), métodos en `DiverseCritter`, goal y cambios en la civeta.
- [ ] **Step 4:** Ejecutar → PASAN.
- [ ] **Step 5:** Commit `feat: predators hunt by prey category from farther away`.

**Cómo comprobarlo en juego**
1. Llano abierto. Civeta salvaje en un punto, `/doc_hunger 10` a su lado.
2. A unos 25 bloques: `/summon diversityofcritters:squirrel`. La civeta la ve, corre y la caza (antes solo cazaba conejos y gallinas, y a 16 bloques).
3. `/doc_hunger 100` y otra ardilla: la ignora.
4. Una ardilla domesticada tuya: nunca la ataca. Una sifaka: tampoco (no es su presa).
5. (Hasta T7 la presa suelta su botín normal.)

---

### Task 7: Cadáveres ($25)

> **Pendiente del cliente** (no bloquea empezar; los valores por defecto se cambian con una línea):
> modelo genérico o uno por categoría; si las muertes por jugador dejan cadáver; si el jugador puede
> abrirlo. **Por defecto:** un modelo provisional para todos, solo las presas de depredadores dejan
> cadáver, el jugador no lo abre.

**Files:**
- Create: `common/block/CorpseBlock.java`, `common/block/entity/CorpseBlockEntity.java`, `common/event/CorpseEvents.java` (o en `DOCForgeEvents`)
- Modify: `common/block/DOCBlocks.java`, `common/block/DOCBlockEntities.java`, `common/item/DOCItems.java` (carnes)
- Modify: `common/entity/util/BowlFeedingHelper.java` (`hasFoodFor`, `consumeFoodFor`), `common/block/entity/BowlBlockEntity.java` (`BowlLogic.isMeat` → `stack.is(DoCTags.Items.MEATS)`)
- Modify: `datagen/providers/DoCItemTagGenerator.java` (carnes nuevas y las vanilla de la lista actual en `meats`), `DoCItemModelProvider.java`
- Modify: `common/command/SpawnDebugCommand.java` o nuevo `CorpseDebugCommand.java` (`/doc_corpse <category>`)
- Create: blockstate, modelo y texturas provisionales; lang
- Create: `gametest/CorpseGameTests.java`

**Interfaces:**
- Consumes: `DoCTags.EntityTypes.CATEGORIES`, `DiverseCritter.preyCategories()`, `isPreyFor(...)` (T6), `onAteFood()` (T2).
- Produces: items `small_rodent_meat`, `small_carnivora_meat`, `small_primate_meat`, `lagomorph_meat`, `small_bird_meat` (comestibles, en `diversityofcritters:meats`).
  `CorpseBlockEntity`: `ResourceLocation category`, `int portions`, `long bornGameTime`;
  `ItemStack takePortion()` (devuelve una carne de su categoría; con 0 porciones quita el bloque);
  tick de servidor que quita el bloque (con partículas) cuando `gameTime - bornGameTime ≥ corpseRotSeconds * 20`. Todo se guarda en NBT.
- Colocación: `LivingDropsEvent`; si `source.getEntity()` es un `DiverseCritter` y `isPreyFor(hunter, victim)` →
  cancelar botín y poner el cadáver en la posición de la víctima, o en la casilla de aire con suelo sólido
  más cercana a ≤ 2 bloques; si no hay ninguna, botín normal.
- Comer: `hasFoodFor` acepta un `CorpseBlock` con porciones si `getDietConfig().acceptsMeat`;
  `consumeFoodFor` llama a `takePortion()`, `onAteFood()` y cura con la nutrición de la carne.
  `FindFoodBowlGoal` ya pregunta a `hasFoodFor` por cada bloque, así que encuentra cadáveres sin más cambios.
- Config: `ecosystem.corpsePortions` (def. 3), `ecosystem.corpseRotSeconds` (def. 600).

- [ ] **Step 1:** Tests: `civetKillLeavesCorpse` (civeta con hambre mata un conejo; aserción: bloque
  cadáver con categoría `lagomorph` y ningún `ItemEntity` de conejo); `anyCarnivoreEatsFromCorpse`
  (otra civeta hambrienta come del cadáver; porciones 3 → 2); `corpseRotsAway` (rot a 1 s; aserción: bloque desaparece);
  `corpseInWaterFallsBackToDrops` (presa muerta en agua profunda; aserción: hay botín normal);
  `corpseKeepsDataOnReload` (escribir/leer NBT del block entity; mismas porciones y edad).
- [ ] **Step 2:** Ejecutar → FALLAN.
- [ ] **Step 3:** Items, bloque, block entity, evento, cambios en `BowlFeedingHelper` y `isMeat` por etiqueta, comando.
- [ ] **Step 4:** `gradlew runData` y `gradlew runGameTestServer` → PASAN.
- [ ] **Step 5:** Commit `feat: predator kills leave a corpse that feeds any carnivore and rots`.

**Cómo comprobarlo en juego**
1. Repite la prueba de T6 con un conejo (`/summon rabbit`). Al morir no suelta carne: aparece un cadáver.
2. Pon otra civeta cerca con `/doc_hunger 10`: va al cadáver y come aunque no lo cazó ella. Tres comidas y desaparece.
3. Para ver que se pudre: `corpseRotSeconds = 20` en el serverconfig, `/doc_corpse small_rodent` y espera 20 s.
4. Mata tú un conejo con la espada: botín normal (salvo que el cliente pida lo contrario).
5. Sal y vuelve a entrar al mundo con un cadáver a medio comer: conserva las porciones.
