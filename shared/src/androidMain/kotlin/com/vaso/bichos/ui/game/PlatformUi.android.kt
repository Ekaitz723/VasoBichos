package com.vaso.bichos.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberHaptics(): (HapticKind) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { kind ->
            view.performHapticFeedback(
                when (kind) {
                    HapticKind.TICK -> HapticFeedbackConstants.CLOCK_TICK
                    HapticKind.POP -> HapticFeedbackConstants.KEYBOARD_TAP
                    HapticKind.HEAVY -> HapticFeedbackConstants.LONG_PRESS
                },
            )
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
