# Arquitectura

## Módulos

- **`:engine`**: Kotlin Multiplatform puro (destinos JVM 11 y Wasm para la web), sin Android.
  Contiene todas las reglas y el contenido (bichos, tableros). Todo va en `commonMain`: nada de
  `java.*` ni APIs de una sola plataforma. Tests con `kotlin.test` en `engine/src/commonTest`,
  que corren en JVM y en Wasm (así se comprueba que la partida sale igual en los dos).
- **`:shared`**: Kotlin Multiplatform + Compose Multiplatform (Android y web/Wasm). Pantallas,
  animación y red. Casi todo está en `commonMain`; lo que solo existe en una plataforma va en
  `androidMain` / `wasmJsMain` (con `expect`/`actual` o detrás de una interfaz):
  - `Platform`: lo que pone cada plataforma (base de datos, ajustes guardados, versión).
  - `RealtimeDb`: lo mínimo de Firebase que usan las salas. Android usa el SDK nativo
    (`FirebaseRealtimeDb`); la web, el SDK de JavaScript a través de `firebase-bridge.js`
    (`JsRealtimeDb`). `RoomRepository` es común y solo habla con `RealtimeDb`.
  - `PlatformUi.kt`: vibración (`rememberHaptics`) y botón atrás (`PlatformBackHandler`).
  - La web arranca en `wasmJsMain/.../Main.kt`; su HTML, iconos y configuración de Firebase están
    en `wasmJsMain/resources`.
- **`:app`**: solo `MainActivity`, que arranca `App()` de `:shared` en Android.
- No uses librerías solo de Android en `commonMain` (por eso el confeti es propio, `Confetti.kt`).
- La versión (`vasobichos.versionName`/`versionCode`) está en `gradle.properties`; el código común
  la ve como `APP_VERSION`.

```
engine/src/commonMain/kotlin/com/vaso/bichos/engine/
  core/       Engine, GameState, GameContext, Element, Query, Action(s), Events, Decision,
              Commands, GameMode/GameConfig, Rng, Ids
  board/      Board, Pos, Direction, Tile/Stone/Orb, MatchFinder, TileSpawner
  champion/   Champion (clase base mínima), ChampionDefinition
  content/    Content (catálogo) + un paquete por bicho o tablero
              vanilla/Vanilla.kt, basic/BasicMode.kt
shared/src/commonMain/kotlin/com/vaso/bichos/
  Platform.kt KeyValueStore
  net/        RoomRepository, RealtimeDb, CommandCodec
  ui/         App, AppViewModel (+ GameSession), HomeScreen, LobbyScreen, theme/
  ui/game/    GameScreen, BoardView, GameDisplay (director de animaciones), TileGlyph, Aura,
              Confetti, PlatformUi (expect)
shared/src/androidMain/   AndroidPlatform, FirebaseRealtimeDb, PlatformUi.android
shared/src/wasmJsMain/    Main (WebPlatform), JsRealtimeDb, PlatformUi.wasmJs, resources/
app/src/main/java/com/vaso/bichos/MainActivity.kt
```

## Conceptos del motor

| Concepto | Qué es |
|---|---|
| `GameElement` | Cualquier cosa que influye en la partida: bichos, reglas del tablero, efectos temporales, futuros amuletos. Puede **escuchar eventos** (`onEvent`), **modificar consultas** (`modify`), **interceptar acciones** (`intercept`, devolver otra o `null` para anularla) y **ofrecer opciones** (`options`/`useOption`). El motor no sabe qué tipo de elemento es cada uno. |
| `Query<T>` (`Q.*`) | Un valor que cualquier elemento puede modificar: movimientos por turno, contenedores, capacidad de orbes, vida máxima, coste de habilidad, si se puede intercambiar, coste de un intercambio, colores del tablero, quién genera fichas, cómo se buscan combinaciones... Se calcula con `ctx.query(q, base)`: la base pasa por todos los elementos en orden. Se pueden crear consultas nuevas en cualquier sitio. |
| `Action` | Algo que cambia la partida: `SwapTiles`, `CollectCells`, `Settle` (gravedad y relleno), `Redraw`, `AddCharge`, `DealDamage`, `Heal`, `Revive`, `GrantMoves`, `SpendMoves`, `ChangeOrbs`, `BeginTurn`/`EndTurn`/`AdvanceTurn`, `StartGrace`, `EndGame`... Para lógica puntual se usa `Act("nombre") { ctx -> ... }`. |
| `GameEvent` (`Events.*`) | Lo que ha pasado. Los elementos reaccionan a ellos y la pantalla los anima. Es abierto: el contenido puede crear sus propios eventos. |
| `Decision` | Pregunta a un jugador (elegir objetivo, cuántos orbes, ¿revivir con 3 o con 4?...). La resolución **se para** y sigue donde estaba cuando el jugador contesta, aunque sea en mitad de una carambola. `Choice.target` permite a la pantalla resaltar lo elegible. |
| `GameOption` | Un botón que un elemento ofrece a un jugador ahora mismo (con `enabled`). La pantalla los pinta todos como un menú, sin conocerlos. |
| `Command` | Lo que pide un jugador: `Swap`, `UseOption`, `Answer`. Es lo único que viaja por la red. |
| `GameMode` | Un tablero/modo: tamaño, colores, jugadores, valores base, generador de fichas, buscador de combinaciones, regla base de intercambio y sus reglas (que son elementos). |
| `Content` | Catálogo de modos y bichos (`Content.default`). Ahí se registra cada bicho o tablero nuevo. |

## Ciclo de resolución (`Engine.run`)

```
comando → acciones en cola
repetir:
  si hay una pregunta pendiente o la partida terminó → parar
  si hay acciones en la cola → sacar una, pasarla por intercept() de cada elemento, ejecutarla,
     repartir sus eventos a los elementos; lo que encolen va DELANTE de lo que ya esperaba
     (primero las reacciones a los eventos, luego lo que encoló la propia acción)
  si la cola está vacía → "checkpoint", en este orden:
     1. ¿huecos en el tablero?      → Settle (gravedad + relleno)
     2. ¿combinaciones?             → ResolveMatches
     3. ¿el turno terminó?          → AdvanceTurn
     4. ¿turno fantasma?            → EndTurn
     5. ¿sin movimientos? (Q.AutoEndTurn) → EndTurn
     6. si no → esperar al jugador
```

Así **cualquier combinación**, la provoque quien la provoque, se resuelve y puntúa en el momento.
Hay un tope de 100 000 pasos para detectar bucles infinitos entre elementos.

`ResolveMatches` emite, por cada grupo: `GroupMatched`, un `GroupScored` por cada tamaño con el que
puntúa (ver `MatchGroup.scoringSizes`) y luego `CollectCells`, que quita las fichas y emite
`TilesCollected(player, tiles)`. Las reglas del tablero deciden qué hacer con lo recogido.

## Turnos y turnos fantasma

`TurnState` guarda `current`, `ghost`, `ghostTurnsLeft`, `resumeWith` y `advancePending`.
`StartGrace(n)` programa n turnos fantasma y apunta a quién le toca de verdad después. Los turnos
fantasma emiten `TurnStarted/TurnEnded(ghost = true)` (las duraciones avanzan), pero no tocan el
turno real. Al acabar se emite `GraceEnded` y, si nadie termina la partida, sigue `resumeWith`.

## Determinismo (imprescindible para el online)

- Todo el azar sale de `state.rng` (SplitMix64 con semilla). Nunca uses `Random` en el motor.
- Usa listas y `linkedMapOf`/`mutableMapOf` (que conservan el orden), no iteres sobre `HashMap`/`HashSet`.
- Los elementos se consultan en el orden de `state.elements`: primero las reglas del modo, luego
  los bichos por jugador.
- Un cambio que altere cómo se juega una partida ya empezada rompe las partidas en curso guardadas
  en Firebase. Mantén la compatibilidad cuando sea razonable. Ejemplo: `extraColors = null`
  significa "todos los colores", que es exactamente el comportamiento anterior.

## Cómo añadir cosas

**Un bicho nuevo:** un archivo en `content/<nombre>/`, una clase que extienda `Champion` con su
`ChampionDefinition`, y registrarlo en `Content.default`. Sobrescribe solo lo que necesite:
`receiveCharge`, `receiveDamage`, `receiveHeal`, `revive`, `onEvent`, `modify`, `intercept`,
`options`/`useOption`. Reutiliza las acciones que ya hay; si falta una, créala en `core/Actions.kt`
si es genérica o dentro del bicho si es solo suya. Plantilla en `docs/content.md`.

**Un tablero/modo nuevo:** un `GameMode` en `content/<nombre>/` con sus reglas como
`GameElement`s, y registrarlo en `Content.default`.

**Un componente nuevo (por ejemplo amuletos):** un nuevo tipo de `GameElement` que se añade a
`state.elements` al crear la partida, más su configuración en `GameConfig`/sala y su pantalla de
elección. El núcleo del motor no debería necesitar cambios.

**Una ficha nueva:** implementa `Tile` (con su `matchKey`; `null` significa que no combina) y
dibújala en `TileGlyph`.

**Un patrón nuevo:** otro `MatchFinder`, o reglas que escuchen `GroupMatched`/`GroupScored`.

## Estado vs comportamiento

Los bichos y elementos son objetos con estado mutable y se crean de forma determinista a partir de
la configuración. Para el online no se serializa el estado: se reenvían los comandos.
