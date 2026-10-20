# Hoja de ruta

## Hecho (v0.5)

- Motor completo con el ciclo de resolución, turnos fantasma, Vainilla y Tablero base. 30 tests.
- Pantallas: inicio, sala y partida, con menú RPG, preguntas, resultado y confeti.
- Game feel: muelles, aplastamiento, partículas, sacudida, micropausa, números flotantes, vibración
  y carambolas cada vez más rápidas.
- Online con Firebase: salas por código, comandos en orden y reconexión.
- Colores extra del tablero elegibles en la sala (v0.4).
- Bots de prueba (v0.5, temporales).
- Arreglados: cierre al sacudir el tablero (animación fuera de la composición) y cierre al
  reconectar (la sala llegaba a medias).

## Siguiente: rediseño de la parte superior de la partida

Ya existe la rama `ui-rivales`, creada desde `main`. Hazlo ahí, en commits pequeños, para poder
deshacerlo con git. Lo que pidió el usuario:

- Se ven **todos los rivales a la vez**, aunque sea en pequeño: **una columna por jugador**
  minimizada.
- Al tocar uno, **se maximiza**, pero sigue en el lado de los enemigos.
- Al tocar fuera o pulsar atrás, se vuelve a minimizar.
- Dentro del rival maximizado se pueden ver sus cosas, tocar sus bichos, etc. (por lo que pueda
  hacer falta en el futuro).
- **Tus bichos siempre visibles.**
- **El jugador al que le toca tiene su recuadro rodeado de un aura brillante.**

Pensadlo juntos antes de programar: propón opciones y espera a que el usuario elija.

## Pendiente o por decidir

- **Prototipos de bichos**: el usuario mandará los suyos. Cada uno en su archivo, con tests.
- **Quitar los bots de prueba** cuando el usuario lo diga (commit `c86621c`).
- **Proporción de orbes**: con menos colores salen más orbes (el orbe pesa como un color). ¿Dejar
  fija la proporción?
- **Sonidos.**
- Revisar los tiempos de animación jugando.
- En el modo prueba de un solo móvil, los números flotantes pueden salir en el panel equivocado
  si el turno cambia a mitad de la animación.
