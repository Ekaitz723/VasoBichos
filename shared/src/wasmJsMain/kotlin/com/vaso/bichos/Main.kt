package com.vaso.bichos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaso.bichos.net.JsRealtimeDb
import com.vaso.bichos.net.RealtimeDb
import com.vaso.bichos.ui.App
import com.vaso.bichos.ui.AppViewModel
import kotlinx.browser.document
import kotlinx.browser.localStorage

private object WebPlatform : Platform {
    override val db: RealtimeDb by lazy { JsRealtimeDb() }
    override val version = "$APP_VERSION web"
    override val prefs = object : KeyValueStore {
        override fun get(key: String): String? = runCatching { localStorage.getItem("vb.$key") }.getOrNull()

        override fun put(key: String, value: String?) {
            runCatching {
                if (value == null) localStorage.removeItem("vb.$key") else localStorage.setItem("vb.$key", value)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        PhoneFrame {
            App(viewModel { AppViewModel(WebPlatform) })
        }
    }
}

/**
 * En pantallas anchas (ordenador) la app va en una columna con forma de móvil, centrada. En un
 * móvil (menos de 600 de ancho) ocupa toda la pantalla, igual que sin esto.
 */
@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF14121F)), contentAlignment = Alignment.Center) {
        val width = if (maxWidth < 600.dp) maxWidth else minOf(maxWidth, maxHeight * PHONE_ASPECT)
        Box(Modifier.width(width).fillMaxHeight()) { content() }
    }
}

/** Ancho / alto de un móvil normal en vertical. */
private const val PHONE_ASPECT = 0.5f
