package com.vaso.bichos.ui.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cómo brilla el recuadro del jugador al que le toca. Es solo estético: lo elige cada jugador en
 * la sala y viaja con la sala, no con la partida (el motor no lo conoce).
 */
sealed interface Aura {
    /** Un color de [AuraPalette] que late. */
    data class Solid(val color: Int) : Aura
    /** Dorada, con un destello que da vueltas. */
    data object Gold : Aura
    /** Los colores de las piedras dando vueltas. */
    data object Rainbow : Aura

    fun encode(): String = when (this) {
        is Solid -> "c$color"
        Gold -> "gold"
        Rainbow -> "rainbow"
    }

    companion object {
        /** Por defecto, cada asiento tiene su color. */
        fun default(seat: Int): Aura = Solid(seat % AuraPalette.colors.size)

        fun decode(text: String?): Aura? = when {
            text == null -> null
            text == "gold" -> Gold
            text == "rainbow" -> Rainbow
            text.startsWith("c") -> text.drop(1).toIntOrNull()?.let { Solid(it.mod(AuraPalette.colors.size)) }
            else -> null
        }
    }
}

object AuraPalette {
    val colors = listOf(
        Color(0xFFFF6B6B), // coral
        Color(0xFF4DD8E6), // cian
        Color(0xFFA6E34B), // lima
        Color(0xFFB388FF), // violeta
        Color(0xFFFFA24C), // naranja
        Color(0xFFFF7FC8), // rosa
        Color(0xFF5C8DFF), // azul
        Color(0xFFF2F2F2), // blanco
    )
    val gold = Color(0xFFFFC857)
    val rainbow get() = StonePalette.colors

    /** Un color representativo (para muestras pequeñas). */
    fun swatch(aura: Aura): Color = when (aura) {
        is Aura.Solid -> colors[aura.color]
        Aura.Gold -> gold
        Aura.Rainbow -> StonePalette.colors.first()
    }
}

/** Brillo alrededor del recuadro (con esquinas de [corner]) mientras [aura] no sea null. */
fun Modifier.turnAura(aura: Aura?, corner: Dp): Modifier = if (aura == null) this else composed {
    val transition = rememberInfiniteTransition(label = "aura")
    val pulse by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(if (aura is Aura.Rainbow) 2200 else 3200, easing = LinearEasing)),
        label = "spin",
    )
    drawWithContent {
        val brush: Brush = when (aura) {
            is Aura.Solid -> SolidColor(AuraPalette.colors[aura.color])
            Aura.Gold -> spinning(listOf(AuraPalette.gold, Color(0xFFFFF4D6), AuraPalette.gold, Color(0xFFB8862B), AuraPalette.gold), spin)
            Aura.Rainbow -> spinning(AuraPalette.rainbow + AuraPalette.rainbow.first(), spin)
        }
        val r = corner.toPx()
        // Halo: varios trazos cada vez más anchos y tenues.
        val layers = 9
        for (i in 1..layers) {
            val spread = i * 1.3.dp.toPx() * (0.75f + 0.25f * pulse)
            drawRoundRect(
                brush,
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + 2 * spread, size.height + 2 * spread),
                cornerRadius = CornerRadius(r + spread),
                style = Stroke(2.5.dp.toPx()),
                alpha = 0.22f * (1f - (i - 1f) / layers) * (0.55f + 0.45f * pulse),
            )
        }
        drawContent()
        drawRoundRect(brush, cornerRadius = CornerRadius(r), style = Stroke(2.dp.toPx()), alpha = 0.95f)
    }
}

/**
 * Degradado circular girado [degrees] grados. En vez de rotar el shader (no se puede igual en todas
 * las plataformas), se recalculan los colores en 48 puntos del círculo ya desplazados.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.spinning(colors: List<Color>, degrees: Float): Brush {
    val steps = 48
    val shift = ((degrees / 360f) % 1f + 1f) % 1f
    val rotated = List(steps + 1) { i ->
        val t = ((i.toFloat() / steps - shift) % 1f + 1f) % 1f
        val pos = t * (colors.size - 1)
        val k = pos.toInt().coerceAtMost(colors.size - 2)
        lerp(colors[k], colors[k + 1], pos - k)
    }
    return Brush.sweepGradient(rotated, center)
}
