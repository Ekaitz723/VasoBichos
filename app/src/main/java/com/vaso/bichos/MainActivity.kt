package com.vaso.bichos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaso.bichos.ui.AppViewModel
import com.vaso.bichos.ui.HomeScreen
import com.vaso.bichos.ui.LobbyScreen
import com.vaso.bichos.ui.Screen
import com.vaso.bichos.ui.game.GameScreen
import com.vaso.bichos.ui.theme.VasoBichosTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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
    }
}
