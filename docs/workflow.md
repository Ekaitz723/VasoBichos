# Flujo de trabajo

## Git

- Rama principal: `main`. Para cambios grandes o que el usuario quiera poder deshacer, crea una
  rama (por ejemplo `ui-rivales`) y haz commits pequeños con mensajes en español que digan qué
  cambia y por qué.
- Los mensajes de commit llevan las líneas de atribución que indique el sistema.
- Al empezar, mira `git status` y `git log --oneline -5`. **Ya pasó una vez** que un cuelgue de
  Windows llenó de bytes nulos `.git/HEAD` y una rama. Se arregló reescribiendo
  `ref: refs/heads/<rama>` en HEAD y el hash del último commit en la rama, y comprobándolo con
  `git fsck`.

## Versiones y APK

- Cada vez que le pases un APK al usuario, sube `versionCode` y `versionName` en
  `app/build.gradle.kts` (0.5 → 0.6...). La versión se ve en la pantalla de inicio.
- `./gradlew :app:assembleDebug`, copia `app/build/outputs/apk/debug/app-debug.apk` al scratchpad
  como `VasoBichos-vX.Y.apk` y envíalo con SendUserFile.
- Recuérdale que lo instale **en todos los móviles**: si tienen versiones distintas, las partidas
  se desincronizan.

## Tests

- `./gradlew :engine:allTests` (`kotlin.test`; corre en JVM y en Wasm con Node). Solo JVM, más
  rápido: `:engine:jvmTest`. `TestSupport.kt` tiene `newGame()` (3 jugadores; colores
  0-1, 2-3 y 4-5; el 6 no es de nadie), un tablero de relleno `Junk` que nunca combina, `put()`
  para colocar fichas y helpers como `swapOk`, `junkSwap` y `useOk`.
- Hay un test de partida aleatoria larga (2000 jugadas) y otro de determinismo. Mantenlos pasando.

## Emulador

- SDK en `%LOCALAPPDATA%/Android/Sdk`. Hay un AVD: `Pixel_7`. (El `Pixel_Tablet` se
  borró para liberar disco: el disco C: del portátil va justo, no instales imágenes de más.)
- El usuario lo abre desde **Device Manager de Android Studio**, para que salga en
  *Running Devices*. Si lo lanzas tú por línea de comandos sale en una ventana aparte y él no lo ve
  dentro de Studio.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` y
  `adb shell am start -n com.vaso.bichos/.MainActivity`.
- Capturas: `adb exec-out screencap -p > captura.png`. La pantalla es de 1080×2400; si la imagen
  se muestra reducida a 900×2000, multiplica las coordenadas por 1,2.
- Casilla del tablero (col, fila) en píxeles reales, aproximadamente:
  `x = (85 + col·122)·1,2`, `y = (1377 + fila·122)·1,2`. Comprueba con una captura, porque la
  disposición puede cambiar.
- Errores: `adb logcat -d -b crash | grep -A40 com.vaso.bichos`.
- Trampas: tras instalar, la pantalla de arranque tarda unos segundos (espera antes de tocar). No
  uses la tecla atrás para cerrar el teclado, porque si no está abierto te saca de la app. Usa
  `adb shell input keyevent 111`.

## Probar el online sin un segundo móvil

- Lo más fácil: el botón **"Añadir bot (pruebas)"** de la sala.
- Para controlar a mano al otro jugador, usa la API REST con un usuario anónimo:
  1. `POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=<api_key de google-services.json>`
     con `{"returnSecureToken":true}`. Devuelve `idToken` (caduca en 1 hora) y `localId`.
  2. Unirse: `PUT /rooms/<CÓDIGO>/players/<localId>.json?auth=<idToken>` con
     `{"name":"Amigo","joinedAt":{".sv":"timestamp"},"picks":"vanilla:2,vanilla:3","ready":true}`.
  3. Mover: `PUT /rooms/<CÓDIGO>/commands/00000N.json?auth=<idToken>` con `"s|1|0,4|1,4"`.
- **Borra siempre las salas de prueba** al terminar (`DELETE /rooms/<CÓDIGO>.json`). Nunca toques
  salas que no hayas creado tú: pueden ser partidas del usuario.
