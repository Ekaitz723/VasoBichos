package com.vaso.bichos.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vaso.bichos.engine.board.Direction
import com.vaso.bichos.engine.board.Pos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun BoardView(
    display: GameDisplay,
    columns: Int,
    rows: Int,
    enabled: Boolean,
    onSwap: (Pos, Pos) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<Pos?>(null) }
    val swap by rememberUpdatedState(onSwap)
    if (!enabled && selected != null) selected = null

    BoxWithConstraints(
        modifier
            .aspectRatio(columns.toFloat() / rows)
            .graphicsLayer { translationX = display.shake.value * sin(display.shake.value * 3f) }
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E0C18))
            .border(2.dp, Color(0xFF3A3360), RoundedCornerShape(14.dp)),
    ) {
        val density = LocalDensity.current
        val cellPx = with(density) { maxWidth.toPx() } / columns
        val cellDp = maxWidth / columns

        // Fondo a cuadros
        Canvas(Modifier.fillMaxSize()) {
            for (r in 0 until rows) for (c in 0 until columns) {
                val tint = if ((r + c) % 2 == 0) Color(0xFF1B1830) else Color(0xFF16142A)
                drawRoundRect(
                    tint,
                    Offset(c * cellPx + 2f, r * cellPx + 2f),
                    Size(cellPx - 4f, cellPx - 4f),
                    CornerRadius(cellPx * 0.18f),
                )
            }
            selected?.let {
                drawRoundRect(
                    Color(0x55FFC857),
                    Offset(it.col * cellPx + 2f, it.row * cellPx + 2f),
                    Size(cellPx - 4f, cellPx - 4f),
                    CornerRadius(cellPx * 0.18f),
                )
            }
        }

        Box(Modifier.fillMaxSize().clipToBounds()) {
            for (sprite in display.sprites) {
                key(sprite.id) {
                    TileSpriteView(sprite, cellPx, cellDp, sprite.pos == selected)
                }
            }
        }

        ParticleLayer(display, cellPx)

        // Entrada: tocar una y luego otra vecina, o deslizar desde una ficha.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled, cellPx) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = Pos((down.position.x / cellPx).toInt(), (down.position.y / cellPx).toInt())
                        if (start.col !in 0 until columns || start.row !in 0 until rows) return@awaitEachGesture
                        var swiped = false
                        while (true) {
                            val change = awaitPointerEvent().changes.first()
                            val delta = change.position - down.position
                            if (!swiped && delta.getDistance() > cellPx * 0.35f) {
                                val dir = if (abs(delta.x) > abs(delta.y)) {
                                    if (delta.x > 0) Direction.RIGHT else Direction.LEFT
                                } else {
                                    if (delta.y > 0) Direction.DOWN else Direction.UP
                                }
                                val target = start + dir
                                if (target.col in 0 until columns && target.row in 0 until rows) swap(start, target)
                                selected = null
                                swiped = true
                            }
                            if (!change.pressed) break
                        }
                        if (!swiped) {
                            val current = selected
                            selected = when {
                                current == null -> start
                                current == start -> null
                                current.isAdjacentTo(start) -> {
                                    swap(current, start)
                                    null
                                }
                                else -> start
                            }
                            if (selected != null) display.haptic(HapticKind.TICK)
                        }
                    }
                },
        )
    }
}

@Composable
private fun TileSpriteView(sprite: TileSprite, cellPx: Float, cellDp: androidx.compose.ui.unit.Dp, selected: Boolean) {
    val target = IntOffset((sprite.pos.col * cellPx).roundToInt(), (sprite.pos.row * cellPx).roundToInt())
    val offset = remember(sprite.id) {
        val start = sprite.spawnOffset?.let { (dc, dr) ->
            IntOffset(target.x + (dc * cellPx).roundToInt(), target.y + (dr * cellPx).roundToInt())
        } ?: target
        Animatable(start, IntOffset.VectorConverter)
    }
    val squash = remember(sprite.id) { Animatable(1f) }

    LaunchedEffect(target) {
        val distance = abs(offset.value.x - target.x) + abs(offset.value.y - target.y)
        offset.animateTo(target, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
        if (distance > cellPx * 0.6f) {
            // Al aterrizar se aplasta un poco y rebota: da sensación de peso.
            squash.snapTo(0.82f)
            squash.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }

    val death by animateFloatAsState(
        targetValue = if (sprite.dying) 0f else 1f,
        animationSpec = if (sprite.dying) {
            keyframes {
                durationMillis = 190
                1.25f at 70
                0f at 190
            }
        } else {
            tween(0)
        },
        label = "death",
    )
    val pulse = if (selected) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "pulse").value
    } else {
        1f
    }

    TileGlyph(
        sprite.tile,
        Modifier
            .offset { offset.value }
            .size(cellDp)
            .graphicsLayer {
                val s = death * pulse
                scaleX = s * (2f - squash.value)
                scaleY = s * squash.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.85f)
                alpha = death.coerceIn(0f, 1f)
            },
    )
}

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val color: Color, val size: Float,
)

/** Chispas al recoger fichas. Solo dibuja fotogramas mientras hay partículas vivas. */
@Composable
private fun ParticleLayer(display: GameDisplay, cellPx: Float) {
    val particles = remember { ArrayList<Particle>() }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cellPx) {
        var last = 0L
        while (true) {
            if (particles.isEmpty()) {
                addBurst(particles, display.bursts.receive(), cellPx)
                last = 0L
            }
            while (true) addBurst(particles, display.bursts.tryReceive().getOrNull() ?: break, cellPx)
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else (now - last) / 1e9f
                last = now
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    p.life -= dt
                    if (p.life <= 0f) {
                        it.remove()
                        continue
                    }
                    p.vy += 900f * dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                }
                frame = now
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        frame // leer el estado para redibujar cada fotograma
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawCircle(p.color.copy(alpha = a), p.size * (0.5f + a * 0.5f), Offset(p.x, p.y))
        }
    }
}

private fun addBurst(into: MutableList<Particle>, burst: Burst, cellPx: Float) {
    val cx = (burst.pos.col + 0.5f) * cellPx
    val cy = (burst.pos.row + 0.5f) * cellPx
    val count = if (burst.big) 14 else 8
    repeat(count) {
        val angle = Random.nextFloat() * 6.283f
        val speed = cellPx * (2.5f + Random.nextFloat() * (if (burst.big) 5f else 3f))
        val life = 0.35f + Random.nextFloat() * 0.3f
        into += Particle(
            cx, cy, cos(angle) * speed, sin(angle) * speed - cellPx * 2f,
            life, life, if (Random.nextInt(4) == 0) Color.White else burst.color,
            cellPx * (0.05f + Random.nextFloat() * 0.06f),
        )
    }
}
