package com.vaso.bichos.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Explosión de confeti desde ([originX], [originY]), relativos al tamaño. Hecho a mano para que
 * funcione igual en Android y en la web. El azar es solo visual (no toca la partida).
 */
@Composable
fun ConfettiBurst(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    originX: Float = 0.5f,
    originY: Float = 0.3f,
    count: Int = 160,
    /** Velocidad máxima de salida, en dp por fotograma (60 fps). */
    maxSpeed: Float = 30f,
) {
    val density = LocalDensity.current.density
    val pieces = remember {
        List(count) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * maxSpeed
            Piece(
                delay = Random.nextFloat() * 0.2f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                color = colors[it % colors.size],
                width = 6f + Random.nextFloat() * 6f,
                height = 4f + Random.nextFloat() * 4f,
                spin = (Random.nextFloat() - 0.5f) * 20f,
            )
        }
    }
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (elapsed.longValue < LIFE_NANOS) {
            withFrameNanos { elapsed.longValue = it - start }
        }
    }
    Canvas(modifier) {
        val seconds = elapsed.longValue / 1e9f
        val origin = Offset(size.width * originX, size.height * originY)
        val alpha = (1f - (seconds - FADE_FROM) / (LIFE - FADE_FROM)).coerceIn(0f, 1f)
        if (alpha <= 0f) return@Canvas
        for (p in pieces) {
            val t = seconds - p.delay
            if (t <= 0f) continue
            // En fotogramas de 60 fps: frena un 10 % por fotograma y cae con algo de gravedad.
            val frames = t * 60f
            val travel = (1f - DAMPING.pow(frames)) / (1f - DAMPING)
            val x = origin.x + p.vx * travel * density
            val y = origin.y + p.vy * travel * density + GRAVITY * frames * frames * density
            rotate(p.spin * frames, Offset(x, y)) {
                drawRect(
                    p.color,
                    topLeft = Offset(x - p.width * density / 2, y - p.height * density / 2),
                    size = Size(p.width * density, p.height * density),
                    alpha = alpha,
                )
            }
        }
    }
}

private class Piece(
    val delay: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    val spin: Float,
)

private const val DAMPING = 0.9f
private const val GRAVITY = 0.01f
private const val LIFE = 4f
private const val FADE_FROM = 2.5f
private const val LIFE_NANOS = (LIFE * 1_000_000_000).toLong()
