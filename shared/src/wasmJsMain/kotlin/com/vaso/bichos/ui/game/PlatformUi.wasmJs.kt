@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vaso.bichos.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/** Vibra donde el navegador lo permite (Chrome en Android); Safari de iPhone lo ignora. */
private fun vibrate(ms: Int): Unit = js("{ if (navigator.vibrate) navigator.vibrate(ms); }")

@Composable
actual fun rememberHaptics(): (HapticKind) -> Unit = { kind ->
    vibrate(
        when (kind) {
            HapticKind.TICK -> 5
            HapticKind.POP -> 12
            HapticKind.HEAVY -> 35
        },
    )
}

/** En la web no hay botón atrás del sistema: usamos la tecla Escape. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val listener: (Event) -> Unit = { e -> if ((e as? KeyboardEvent)?.key == "Escape") current.value() }
        if (enabled) window.addEventListener("keydown", listener)
        onDispose { window.removeEventListener("keydown", listener) }
    }
}
