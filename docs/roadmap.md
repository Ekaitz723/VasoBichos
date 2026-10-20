# Hoja de ruta

## Hecho (v0.6)

- Motor completo con el ciclo de resolución, turnos fantasma, Vainilla y Tablero base. 30 tests.
- Pantallas: inicio, sala y partida, con menú RPG, preguntas, resultado y confeti.
- Game feel: muelles, aplastamiento, partículas, sacudida, micropausa, números flotantes, vibración
  y carambolas cada vez más rápidas.
- Online con Firebase: salas por código, comandos en orden y reconexión.
- Colores extra del tablero elegibles en la sala (v0.4).
- Bots de prueba (v0.5, temporales).
- Parte de arriba de la partida rediseñada (v0.6, rama `ui-rivales`): rivales en columnas
  pequeñas que se abren en grande encima, y aura de turno elegible por jugador en la sala.
- Arreglados: cierre al sacudir el tablero (animación fuera de la composición) y cierre al
  reconectar (la sala llegaba a medias).

## Pendiente o por decidir

- **Prototipos de bichos**: el usuario mandará los suyos. Cada uno en su archivo, con tests.
- **Quitar los bots de prueba** cuando el usuario lo diga (commit `c86621c`).
- **Proporción de orbes**: con menos colores salen más orbes (el orbe pesa como un color). ¿Dejar
  fija la proporción?
- **Sonidos.**
- Revisar los tiempos de animación jugando.
- En el modo prueba de un solo móvil, los números flotantes pueden salir en el panel equivocado
  si el turno cambia a mitad de la animación.
