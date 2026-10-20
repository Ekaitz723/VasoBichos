# Online (Firebase)

- Proyecto de Firebase: `vasobichos`. Realtime Database en
  `https://vasobichos-default-rtdb.europe-west1.firebasedatabase.app`.
- Inicio de sesión **anónimo**. El uid se conserva entre reinicios de la app.
- `app/google-services.json` **no está en git** (`.gitignore`).
- Reglas de la base de datos: exigen estar identificado (`auth != null`).
- Gratis (plan Spark) de sobra: una partida ocupa unos KB y se borra al terminar.

## Estructura

```
/rooms/{CÓDIGO}                    4 letras
  meta:
    hostUid, modeId, status ("lobby" | "playing"), seed (Long)
    order: "uid1,uid2,..."         orden de turnos (se escribe al empezar)
    extraColors: "2,5"             colores extra; si no existe = todos
  players/{uid}:
    name, joinedAt (hora del servidor), picks: "vanilla:0,vanilla:1", ready, bot?
  commands/{000000}: "texto del comando"
```

Formato de los comandos (`CommandCodec`):

- `s|jugador|c,f|c,f`: intercambio.
- `o|jugador|elementId|clave`: usar una opción.
- `a|jugador|elección`: responder una pregunta.

## Cómo funciona una partida

1. El anfitrión crea la sala y los demás se unen con el código. Cada uno elige bichos y colores y
   pulsa Listo. El anfitrión decide los colores extra y empieza.
2. Al empezar se escriben `order`, `seed` y `status = playing`. Cada móvil construye la misma
   `GameConfig` y su propio `Engine`.
3. Para jugar, el móvil comprueba el comando con `engine.check()` y lo escribe en
   `commands/{índice}` con una **transacción** (si ese índice ya existe, no se escribe). **No se
   aplica en local**: todos, incluido quien lo envía, lo aplican cuando llega por el listener,
   siempre en orden. Si el motor rechaza un comando, todos lo ignoran igual.
4. **Reconexión**: al entrar a una sala en juego se cargan todos los comandos, se aplican sin
   animar y se escuchan los nuevos. `observeRoom` no publica la sala hasta tener a la vez `meta` y
   `players`; antes, si llegaba primero `meta`, la app se cerraba.
5. Al terminar, el anfitrión borra la sala al salir. Si el anfitrión cierra la sala de espera,
   también se borra.

**Todos los móviles deben tener la misma versión de la app.** Si un móvil calcula distinto, las
partidas se desincronizan. Por eso la versión se ve en la pantalla de inicio.

## Bots de prueba (temporales)

El anfitrión puede añadirlos en la sala: `players/bot-XXXXXX` con `bot = true`, colores libres y
ya listos. En partida, **el móvil del anfitrión** les juega: un intercambio al azar cada ~0,9 s
cuando les toca, y si les preguntan algo eligen la primera opción. Código en
`AppViewModel` (sección "Bots de prueba") y `RoomRepository.addBot`. Están en el commit `c86621c`
para poder quitarlos fácil.
