package com.vaso.bichos.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vaso.bichos.engine.board.Orb
import com.vaso.bichos.engine.board.Stone
import com.vaso.bichos.engine.board.Tile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Cada color tiene también su forma, para que se distingan sin depender solo del color. */
object StonePalette {
    val colors = listOf(
        Color(0xFFFF4D5E), // rojo
        Color(0xFF3D8BFF), // azul
        Color(0xFF3DDC84), // verde
        Color(0xFFFFD23F), // amarillo
        Color(0xFFB45CFF), // morado
        Color(0xFFFF9532), // naranja
        Color(0xFFFF7EC8), // rosa
    )
    val names = listOf("Rojo", "Azul", "Verde", "Amarillo", "Morado", "Naranja", "Rosa")
    val orbColor = Color(0xFFFFF1B8)

    fun color(index: Int) = colors[index % colors.size]
    fun name(index: Int) = names[index % names.size]
}

@Composable
fun TileGlyph(tile: Tile, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (tile) {
            is Stone -> drawStone(tile.color.index)
            Orb -> drawOrb()
        }
    }
}

@Composable
fun ColorGlyph(colorIndex: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawStone(colorIndex) }
}

private fun DrawScope.drawStone(colorIndex: Int) {
    val base = StonePalette.color(colorIndex)
    val r = min(size.width, size.height) * 0.42f
    val c = center
    val path = when (colorIndex % 7) {
        0 -> polygon(c, r, 64, 0f) // círculo
        1 -> polygon(c, r * 1.12f, 4, PI.toFloat() / 4) // cuadrado
        2 -> polygon(c, r * 1.1f, 4, 0f) // rombo
        3 -> polygon(Offset(c.x, c.y + r * 0.15f), r * 1.12f, 3, -PI.toFloat() / 2) // triángulo
        4 -> polygon(c, r * 1.05f, 6, 0f) // hexágono
        5 -> star(c, r * 1.12f, r * 0.55f) // estrella
        else -> polygon(c, r * 1.05f, 5, -PI.toFloat() / 2) // pentágono
    }
    drawPath(path, Brush.radialGradient(listOf(base.lighten(0.45f), base, base.darken(0.35f)), Offset(c.x - r * 0.35f, c.y - r * 0.4f), r * 1.6f))
    drawPath(path, base.darken(0.5f), style = Stroke(width = r * 0.1f))
    // Brillo
    drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x - r * 0.45f, c.y - r * 0.6f), Size(r * 0.55f, r * 0.3f))
}

private fun DrawScope.drawOrb() {
    val r = min(size.width, size.height) * 0.38f
    val c = center
    drawCircle(Brush.radialGradient(listOf(StonePalette.orbColor.copy(alpha = 0.55f), Color.Transparent), c, r * 1.4f), r * 1.4f, c)
    drawCircle(Brush.radialGradient(listOf(Color.White, StonePalette.orbColor, Color(0xFFE0A526)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.3f), r, c)
    drawCircle(Color(0xFF9C6A00), r, c, style = Stroke(width = r * 0.12f))
    drawPath(polygon(c, r * 0.45f, 4, 0f), Color.White.copy(alpha = 0.85f))
}

private fun polygon(c: Offset, r: Float, sides: Int, rotation: Float) = Path().apply {
    for (i in 0 until sides) {
        val a = rotation + 2 * PI.toFloat() * i / sides
        val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun star(c: Offset, outer: Float, inner: Float) = Path().apply {
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2 + PI.toFloat() * i / 5
        val r = if (i % 2 == 0) outer else inner
        val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
