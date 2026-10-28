package com.vaso.bichos.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.vaso.bichos.engine.board.Direction
import com.vaso.bichos.engine.board.Orb
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.board.Stone
import com.vaso.bichos.engine.board.Tile
import com.vaso.bichos.engine.core.ElementId
import com.vaso.bichos.engine.core.Engine
import com.vaso.bichos.engine.core.Events
import com.vaso.bichos.engine.core.GameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/** Una ficha en pantalla. Tiene identidad propia para poder animar su movimiento. */
class TileSprite(val id: Long, tile: Tile, pos: Pos, val spawnOffset: Pair<Int, Int>? = null) {
    var tile by mutableStateOf(tile)
    var pos by mutableStateOf(pos)
    var dying by mutableStateOf(false)
}

class ChampionDisplay(hp: Int, charge: Int, phase: Int, alive: Boolean) {
    var hp by mutableIntStateOf(hp)
    var charge by mutableIntStateOf(charge)
    var phase by mutableIntStateOf(phase)
    var alive by mutableStateOf(alive)
    /** Cambia cada vez que recibe un golpe (dispara la sacudida de su carta). */
    var hits by mutableIntStateOf(0)
}

class PlayerDisplay(moves: Int, orbs: Int) {
    var moves by mutableIntStateOf(moves)
    var orbs by mutableIntStateOf(orbs)
}

enum class PopupKind { DAMAGE, HEAL, CHARGE, INFO }

class Popup(val id: Long, val target: ElementId, val text: String, val kind: PopupKind)

class Burst(val pos: Pos, val color: Color, val big: Boolean)

/**
 * Lo que se ve, que va por detrás del motor: el motor lo resuelve todo al instante y esto
 * reproduce sus eventos uno a uno con su ritmo (intercambio, explosión, caída, golpes...).
 */
class GameDisplay(private val engine: Engine, private val scope: CoroutineScope) {
    val sprites = mutableStateListOf<TileSprite>()
    val champions = engine.state.champions().associate { it.id to ChampionDisplay(it.hp, it.charge, it.phase, it.isAlive()) }
    val players = engine.state.players.map { PlayerDisplay(it.moves, it.orbs) }
    val popups = mutableStateListOf<Popup>()
    val bursts = Channel<Burst>(Channel.UNLIMITED)
    /** Cada sacudida pedida sube [shakes]; la pantalla la anima con [shakeStrength]. */
    var shakes by mutableIntStateOf(0)
        private set
    var shakeStrength = 0f
        private set
    var banner by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    /** Lo pone la pantalla para vibrar. */
    var haptic: (HapticKind) -> Unit = {}

    private var nextId = 1L
    private var cascade = 0

    init {
        rebuildBoard()
    }

    /** Reproduce un lote de eventos (los de un comando). */
    suspend fun play(events: List<GameEvent>) {
        busy = true
        cascade = 0
        try {
            for (event in events) handle(event)
        } finally {
            reconcile()
            busy = false
        }
    }

    /** Pone todo como está en el motor, sin animar (al entrar o reconectar). */
    fun snapToEngine() {
        rebuildBoard()
        reconcile()
    }

    private val speed: Float get() = max(0.55f, 1f - 0.12f * cascade)

    private suspend fun pause(ms: Long) = delay((ms * speed).toLong())

    private suspend fun handle(event: GameEvent) {
        when (event) {
            is Events.TilesSwapped -> {
                val a = spriteAt(event.a)
                val b = spriteAt(event.b)
                a?.pos = event.b
                b?.pos = event.a
                haptic(HapticKind.TICK)
                delay(SWAP_MS)
            }
            is Events.GroupMatched -> if (cascade >= 1) {
                engine.state.championsOf(event.player).firstOrNull()?.let {
                    popup(it.id, "Combo x${cascade + 1}", PopupKind.INFO)
                }
            }
            is Events.TilesCleared -> {
                val dying = event.cells.mapNotNull { spriteAt(it) }
                dying.forEach { s ->
                    s.dying = true
                    bursts.trySend(Burst(s.pos, tileColor(s.tile), big = event.cells.size >= 5))
                }
                if (event.cells.size >= 5 || cascade >= 2) kick(if (event.cells.size >= 5) 10f else 6f)
                haptic(if (event.cells.size >= 5) HapticKind.HEAVY else HapticKind.POP)
                pause(CLEAR_MS)
                sprites.removeAll(dying)
            }
            is Events.TilesFell -> {
                val moving = event.moves.mapNotNull { (from, to) -> spriteAt(from)?.let { it to to } }
                moving.forEach { (s, to) -> s.pos = to }
            }
            is Events.TilesSpawned -> {
                spawn(event.tiles, event.from)
                cascade++
                pause(FALL_MS)
            }
            is Events.BoardRedrawn -> {
                sprites.clear()
                spawn(event.tiles, engine.state.board.gravity.opposite)
                pause(FALL_MS * 2)
            }
            is Events.AbilityActivated -> {
                val champion = engine.state.champion(event.champion)
                banner = "${champion?.definition?.name ?: ""} · ${event.name}!"
                haptic(HapticKind.HEAVY)
                kick(8f)
                delay(ABILITY_MS)
                banner = null
            }
            is Events.Damaged -> champions[event.target]?.let {
                it.hp = event.hp
                it.hits++
                popup(event.target, "-${event.amount}", PopupKind.DAMAGE)
                delay(HIT_STOP_MS)
            }
            is Events.Healed -> champions[event.target]?.let {
                it.hp = event.hp
                popup(event.target, "+${event.amount}", PopupKind.HEAL)
            }
            is Events.ChargeAdded -> champions[event.champion]?.let {
                it.charge = event.total
                popup(event.champion, "+${event.amount}⚡", PopupKind.CHARGE)
            }
            is Events.ChargeSpent -> champions[event.champion]?.charge = event.total
            is Events.Died -> champions[event.champion]?.let {
                it.alive = false
                popup(event.champion, "KO", PopupKind.DAMAGE)
                haptic(HapticKind.HEAVY)
            }
            is Events.Revived -> champions[event.champion]?.let {
                it.alive = true
                it.hp = event.hp
                popup(event.champion, "¡Revive!", PopupKind.HEAL)
            }
            is Events.PhaseChanged -> champions[event.champion]?.let {
                it.phase = event.phase
                popup(event.champion, "¡Fase ${event.phase}!", PopupKind.INFO)
                delay(ABILITY_MS)
            }
            is Events.MovesChanged -> players[event.player.index].moves = event.moves
            is Events.OrbsChanged -> players[event.player.index].orbs = event.orbs
            is Events.TurnStarted -> if (!event.ghost) {
                banner = "Turno de ${engine.state.player(event.player).name}"
                delay(TURN_MS)
                banner = null
            }
            is Events.GraceStarted -> {
                banner = "Ronda de gracia…"
                delay(GRACE_MS)
                banner = null
            }
        }
    }

    private fun spawn(tiles: Map<Pos, Tile>, from: Direction) {
        // Las que entran por el mismo carril se apilan fuera del tablero en orden.
        val perLane = tiles.keys.groupBy { if (from.dCol == 0) it.col else it.row }.mapValues { it.value.size }
        for ((pos, tile) in tiles) {
            val k = perLane.getValue(if (from.dCol == 0) pos.col else pos.row)
            sprites += TileSprite(nextId++, tile, pos, spawnOffset = from.dCol * k to from.dRow * k)
        }
    }

    private fun popup(target: ElementId, text: String, kind: PopupKind) {
        val p = Popup(nextId++, target, text, kind)
        popups += p
        scope.launch {
            delay(POPUP_MS)
            popups.remove(p)
        }
    }

    private fun kick(strength: Float) {
        shakeStrength = strength
        shakes++
    }

    private fun spriteAt(p: Pos) = sprites.firstOrNull { it.pos == p && !it.dying }

    /** Si algo no cuadra con el motor, el motor manda. */
    private fun reconcile() {
        val board = engine.state.board
        val mismatch = board.positions.any { p -> spriteAt(p)?.tile != board[p] } || sprites.size != board.positions.size
        if (mismatch) rebuildBoard()
        for (c in engine.state.champions()) champions[c.id]?.let {
            it.hp = c.hp
            it.charge = c.charge
            it.phase = c.phase
            it.alive = c.isAlive()
        }
        engine.state.players.forEach { p ->
            players[p.id.index].moves = p.moves
            players[p.id.index].orbs = p.orbs
        }
    }

    private fun rebuildBoard() {
        sprites.clear()
        val board = engine.state.board
        for (p in board.positions) board[p]?.let { sprites += TileSprite(nextId++, it, p) }
    }

    private fun tileColor(tile: Tile) = when (tile) {
        is Stone -> StonePalette.color(tile.color.index)
        Orb -> StonePalette.orbColor
        else -> Color.White
    }

    private companion object {
        const val SWAP_MS = 150L
        const val CLEAR_MS = 200L
        const val FALL_MS = 260L
        const val ABILITY_MS = 550L
        const val HIT_STOP_MS = 70L
        const val TURN_MS = 650L
        const val GRACE_MS = 1100L
        const val POPUP_MS = 1000L
    }
}

enum class HapticKind { TICK, POP, HEAVY }
