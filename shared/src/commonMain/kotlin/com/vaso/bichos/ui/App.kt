package com.vaso.bichos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaso.bichos.ui.game.GameScreen
import com.vaso.bichos.ui.theme.VasoBichosTheme
import kotlinx.coroutines.delay

/** La app entera, igual en Android y en la web. */
@Composable
fun App(vm: AppViewModel) {
    VasoBichosTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (val screen = vm.screen) {
                    Screen.Home -> Box(Modifier.statusBarsPadding()) { HomeScreen(vm) }
                    is Screen.Lobby -> Box(Modifier.statusBarsPadding()) { LobbyScreen(vm, screen.code) }
                    Screen.Game -> GameScreen(vm)
                }
                vm.message?.let { text ->
                    LaunchedEffect(text) {
                        delay(2500)
                        vm.message = null
                    }
                    Snackbar(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                    ) { Text(text) }
                }
            }
        }
    }
}
