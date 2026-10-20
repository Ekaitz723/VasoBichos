# UI/UX y game feel

## Principios

- **Solo móvil y en vertical** (`screenOrientation="portrait"`). Pantallas de unos 1080×2400.
- **Tema oscuro tipo menú de RPG** (`ui/theme/Theme.kt`): fondo `#14121F`, superficies moradas
  oscuras, primario dorado `#FFC857`, secundario celeste. La ventana también es oscura
  (`themes.xml`) para que no haya fogonazo blanco al abrir.
- **La pantalla no conoce los botones de cada bicho.** Pinta lo que devuelve
  `engine.options(viewer)`. Un bicho con mil acciones y otro sin ninguna usan el mismo menú.
- **Las preguntas del motor** (`Decision`) se muestran como un diálogo RPG con la lista de opciones.
- **Cada color tiene también una forma** (círculo, cuadrado, rombo, triángulo, hexágono, estrella,
  pentágono), para que se distingan aunque alguien no vea bien los colores. Paleta en
  `TileGlyph.kt` → `StonePalette`. El orbe es una esfera dorada brillante.
- Los textos de la app van en español, cortos y directos.

## Pantallas

- **Inicio** (`HomeScreen`): nombre, "Volver a la sala X" (reconexión), crear sala, unirse con
  código, partida de prueba en un solo móvil y la versión (`vX.Y`) abajo.
- **Sala** (`LobbyScreen`): código grande; jugadores con su aura, sus colores y si están listos;
  elegir bichos y colores; tu brillo de turno; colores del tablero (los de bichos van siempre, los extra los decide el
  anfitrión); "Añadir bot (pruebas)"; empezar (solo el anfitrión) o salir.
- **Partida** (`GameScreen`), de arriba abajo:
  1. Franja superior: sala o "MODO PRUEBA", de quién es el turno, salir.
  2. **Una columna pequeña por rival**, todos a la vez: nombre, cada bicho (forma, barra de vida y
     de carga) y movimientos y orbes en puntitos. Al tocar una, su panel grande (como el tuyo, con
     sus cartas tocables) se despliega **encima**, pegado a las columnas y con el resto oscurecido:
     el tablero no se mueve. Tocar otra columna cambia de rival; tocar fuera, la ✕ o atrás lo
     cierra.
  3. Hueco central para avisos grandes (banner) o "VS".
  4. Tu panel: nombre, movimientos (cuadrados = contenedores, rellenos = movimientos), orbes
     (rombos = capacidad) y tus cartas de bicho.
  5. Botones globales (por ejemplo "¿Redraw?") o una pista de qué hacer.
  6. El tablero, abajo, al alcance del pulgar.
- **Aura de turno** (`Aura.kt`, `Modifier.turnAura`): el recuadro de quien mueve (tu panel o la
  columna del rival) se rodea de un halo animado. Cada jugador elige el suyo en la sala ("Tu brillo
  de turno"): un color que late (por defecto, el de su sitio en la sala), dorado con destello o
  arcoíris que gira. Viaja con la sala, no con la partida, y se recuerda en el móvil.
- **Carta de bicho**: forma y color, nombre, fase (F2), barra de vida, barra de carga en
  segmentos (uno por punto de coste) y KO si está muerto. Al tocarla se abre un panel inferior con
  su ficha y sus ACCIONES en estilo RPG ("▶ Nombre" y una descripción; en gris si no se pueden usar).
- **Resultado**: capa oscura, "¡Gana X!" o "Empate", confeti (Konfetti) y volver al inicio.

## Controles

- Tocar una ficha y luego una vecina, o deslizar desde una ficha hacia una vecina (umbral de 0,35
  casillas).
- No se acepta ningún toque mientras se anima (`vm.queued > 0`) ni mientras se espera la
  confirmación de la red (`awaitingEcho`).

## Animación dirigida por eventos (`GameDisplay`)

El motor lo resuelve todo al instante y devuelve la lista de eventos de cada comando. `GameDisplay`
mantiene **su propia copia de lo que se ve** y reproduce los eventos uno a uno con su ritmo. Al
terminar cada lote compara con el motor y, si algo no cuadra, el motor manda.

- Cada ficha es un `TileSprite` con **identidad propia** (`id`), para poder animar sus movimientos.
- Las fichas nuevas entran apiladas desde el lado contrario a la gravedad.

Tiempos actuales (puntos de partida, se ajustan jugando):

| Momento | Tiempo |
|---|---|
| Intercambio | 150 ms |
| Recoger | 200 ms (sube a 125 % y desaparece, con partículas) |
| Caída y relleno | 260 ms; muelle con rebote y aplastamiento al aterrizar |
| Habilidad | banner de 550 ms y sacudida |
| Golpe | micropausa de 70 ms y la carta tiembla |
| Banner de turno | 650 ms |
| Ronda de gracia | 1100 ms |
| Números flotantes | 1 s (suben y se desvanecen) |

**Carambolas**: cada eslabón va más rápido (`speed = max(0,55; 1 − 0,12·cascada)`) y sale "Combo xN".

**Sacudida del tablero**: con 5+ fichas a la vez, con carambolas de 2 o más eslabones y con
habilidades. Pequeña y corta.

**Vibración**: tic al seleccionar o intercambiar, toque al recoger y fuerte en golpes grandes,
habilidades y KO.

Confeti con Konfetti (`nl.dionsegijn:konfetti-compose`).

## Trampas conocidas (ya nos han roto la app)

- **Nunca animes con `Animatable.animateTo` fuera de la composición** (por ejemplo desde
  `viewModelScope`): no hay reloj de fotogramas y la app se cierra. `GameDisplay` solo cambia
  estado (por ejemplo `shakes++`) y las animaciones viven en composables con `LaunchedEffect`.
- `Text` fuera de un `Surface` sale negro: la raíz está envuelta en un `Surface` del tema.
- En el modo prueba de un solo móvil, si el turno cambia mientras aún se anima, los números
  flotantes pueden salir en el panel del jugador siguiente (es solo estético; en online no pasa).

## Ideas de game feel que todavía faltan

- Sonidos (`SoundPool`), con el tono subiendo en cada eslabón de la carambola.
- Animación propia para cada habilidad.
