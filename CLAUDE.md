# VasoBichos

Juego no comercial para jugar entre amigos a distancia: un match-3 (juntar piedras de colores)
donde cada jugador lleva N bichos ("campeones"). Las piedras de un color cargan al bicho de ese
color, y al llenarse hace su habilidad. Android, Kotlin + Jetpack Compose, **siempre en vertical**,
online con Firebase Realtime Database.

El usuario habla español: responde en español. Identificadores de código en inglés, comentarios
y textos de la app en español.

## Reglas de oro

1. **Nada es una ley universal.** Todo lo que el usuario describe (cuánto cuesta una habilidad,
   qué pasa con lo que sobra, curar, premios por 4+/5+, redraw, rondas de gracia, mover
   intercambiando vecinas...) es contenido de un bicho o de un tablero concreto, nunca del motor.
   Cada bicho, tablero o componente futuro (amuletos, runas...) debe poder cambiar *cualquier* cosa.
   El motor solo ofrece mecanismos genéricos. Ver `docs/architecture.md`.
2. **Todo deshacible con git.** Cada cambio en su rama o en commits pequeños y bien descritos, para
   poder volver atrás con facilidad. No mezcles cambios distintos en un commit.
3. **El motor (`:engine`) es Kotlin puro y determinista.** Sin Android, sin azar fuera de `Rng`,
   sin depender del orden de un `HashMap`. Todos los móviles reproducen la misma partida a partir de
   la semilla y la lista de comandos.
4. **La pantalla nunca decide reglas.** Solo pinta el estado y anima los eventos que devuelve el motor.
5. **Antes de dar algo por hecho, pruébalo**: tests del motor y, si toca la app, en el emulador.
   Sube la versión y pásale el APK al usuario (ver `docs/workflow.md`).

## Documentación (léela según lo que vayas a tocar)

Lee solo el documento del tema que vas a tocar, y solo si lo necesitas. No hace falta leerlos
todos en cada conversación ni antes de cada cambio pequeño.

**Mantenlos al día:** cuando un cambio deje desactualizado algo que dicen (una regla nueva, otra
forma de hacer algo, una decisión del usuario, una tarea terminada en `docs/roadmap.md`), actualiza
ese documento en el mismo commit. Solo lo que haya cambiado de verdad; no los reescribas por rutina.

| Documento | Contenido |
|---|---|
| `docs/vision.md` | La idea del juego y TODAS las reglas e ideas del usuario, con ejemplos de lo que tiene que caber |
| `docs/architecture.md` | Módulos, conceptos del motor, ciclo de resolución, turnos, cómo añadir bichos/tableros/componentes |
| `docs/content.md` | El bicho Vainilla y el Tablero base al detalle; plantilla para un bicho nuevo |
| `docs/ui-ux.md` | Diseño de pantallas, menús tipo RPG, game feel, animaciones, paleta, trampas de Compose |
| `docs/online.md` | Firebase: salas, comandos, reconexión, bots de prueba |
| `docs/workflow.md` | Compilar, tests, emulador, probar online sin segundo móvil, versiones, APK |
| `docs/roadmap.md` | Qué está hecho, qué es lo siguiente y dudas abiertas |

## Comandos rápidos

```
./gradlew :engine:test            # tests del motor
./gradlew :app:assembleDebug      # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug       # instalar en el dispositivo/emulador conectado
```
