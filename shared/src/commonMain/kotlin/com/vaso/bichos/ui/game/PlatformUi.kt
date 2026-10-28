package com.vaso.bichos.ui.game

import androidx.compose.runtime.Composable

/** Vibración de cada plataforma (en la web de iPhone no hay: no hace nada). */
@Composable
expect fun rememberHaptics(): (HapticKind) -> Unit

/** Botón/gesto de "atrás" del sistema, si la plataforma lo tiene. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
