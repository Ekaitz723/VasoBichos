package com.vaso.bichos

import androidx.compose.ui.ExperimentalComposeUiApi
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
        App(viewModel { AppViewModel(WebPlatform) })
    }
}
