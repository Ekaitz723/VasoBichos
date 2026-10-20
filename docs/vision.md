# Visión del juego

Clon sencillo, no comercial, de un match-3 con héroes, para jugar entre amigos a distancia.

## Idea base

- Hay un **tablero** rectangular de piedras de colores (y otras fichas, como orbes).
- Al juntar 3 o más iguales en línea (o en formas: L, T, cruz...) se recogen.
- Cada jugador elige al empezar **N bichos** (por defecto 2; N no está fijado en el código) y no
  puede cambiarlos durante la partida. Cada bicho tiene un **color** y un jugador no puede tener dos
  bichos del mismo color.
- Las piedras recogidas de un color van al **contador** del bicho de ese color del jugador. Al
  llegar al coste de su habilidad, la habilidad se puede lanzar (cómo y cuándo lo decide el bicho).
- Las habilidades y pasivas pueden tocar **cualquier cosa**: el tablero, los jugadores, otros
  bichos, los turnos, las reglas...
- Los bichos tienen **fases** (2.ª, 3.ª...) que se compran con **orbes** u otras cosas.
- Los **orbes** son fichas del tablero que no cargan bichos, sino que se guardan en el jugador hasta
  una capacidad. Sirven como moneda: subir de fase, ataques especiales, cambiarse por piedras...
- Por **turnos**. Cada jugador tiene movimientos por turno y contenedores de movimientos (son dos
  cosas distintas, ver abajo).
- Partidas de 2 o más jugadores, cada uno en su móvil, online.

## Conceptos que hay que mantener separados

- **Movimientos**: los que te dan al empezar el turno (base del tablero base: 2).
- **Contenedores**: cuántos movimientos puedes tener a la vez como máximo (base: 2). Los
  movimientos extra (por ejemplo, por hacer 4+) solo se ganan si hay contenedor libre. Una pasiva
  de "+1 contenedor" no da un movimiento: da hueco para acumular uno.
- Al empezar el turno se repone la base de movimientos, no se llenan todos los contenedores.

## Reglas transversales que pidió el usuario

- **Todo lo encadenado cuenta para el jugador del turno**: piedras, orbes, movimientos extra,
  efectos. Venga la combinación de un intercambio, de una habilidad, de un redraw o de lo que sea.
- **La comprobación de combinaciones y premios se hace después de cualquier acción**, no solo tras
  mover. Una conversión de color que forma un 4 da su movimiento extra en ese mismo turno.
- **Piedras de un color sin bicho**: se pierden, pero los efectos de la combinación (premios del
  tablero, etc.) se aplican igual.
- **Premios acumulativos**: un grupo de 5+ aplica los efectos de 5+, de 4+ y de 3+.
- **Cómo se cuentan los grupos (L, T, cruz)**: las líneas de 3+ que se cruzan forman un único grupo.
  Las piedras se recogen una sola vez. El grupo puntúa con su tamaño total y, además, con cada
  línea de 4+ que contenga salvo la más larga. Ejemplos:
  - 3+3 en L = un 5.
  - 4+3 cruzados = un 5 (6 casillas).
  - 4+4 en cruz = un 5 y un 4.
  - 5+5 en L = un 5 y un 5.
- **Fin de partida (tablero base)**: cuando al acabar un turno queda un solo jugador con bichos
  vivos (o ninguno), se juegan turnos fantasma (sin mover, solo para que salten efectos como
  "revive en 3 turnos"). Luego: 1 vivo gana, 0 vivos es empate, 2+ vivos la partida sigue con el
  turno que tocaba de verdad (los fantasmas no avanzan el turno real).

## Ejemplos que el usuario quiere poder hacer algún día

No están implementados, pero la arquitectura tiene que soportarlos sin refactorizar:

- Quitar piedras guardadas a otro bicho.
- Reorganizar todo el tablero; recoger un cuadrado entero; recoger casillas al azar.
- Convertir piedras a otro color (y que lo que combine cuente en ese turno).
- Cambiar la dirección de la gravedad (las piedras pueden caer hacia arriba o hacia los lados).
- Hacer algo con la carga sobrante: guardarla, convertirla en daño...
- Elegir si lanzar la habilidad ya o guardarla; gastar orbes para lanzar la versión fuerte.
- Pasiva de +1 contenedor toda la partida; habilidad de "1 tiro extra durante 2 turnos".
- Tableros con propiedades: "el último color movido se congela", "al final de cada turno se
  reordena todo", "cada turno un color aleatorio hace daño al enemigo que elijas"...
- Ampliar o reducir la capacidad de orbes con pasivas.
- Revivir en X turnos.
- Componentes nuevos de juego, por ejemplo que cada jugador lleve 3 amuletos (como las runas del LoL).
- Otras formas de mover (aunque de momento siempre es intercambiar dos fichas vecinas).
- Nuevos patrones de premio (5 en distintas formas, 6+...).

El usuario irá mandando prototipos de bichos. Cada uno "es de su padre y de su madre": no
comparten estructura más allá de lo mínimo.
