package com.vaso.bichos.engine.content.basic

import com.vaso.bichos.engine.board.Orb
import com.vaso.bichos.engine.board.StandardMatchFinder
import com.vaso.bichos.engine.board.Stone
import com.vaso.bichos.engine.board.WeightedSpawner
import com.vaso.bichos.engine.core.AddCharge
import com.vaso.bichos.engine.core.ChangeOrbs
import com.vaso.bichos.engine.core.ElementId
import com.vaso.bichos.engine.core.EndGame
import com.vaso.bichos.engine.core.Events
import com.vaso.bichos.engine.core.GameContext
import com.vaso.bichos.engine.core.GameElement
import com.vaso.bichos.engine.core.GameEvent
import com.vaso.bichos.engine.core.GameMode
import com.vaso.bichos.engine.core.GameOption
import com.vaso.bichos.engine.core.GameResult
import com.vaso.bichos.engine.core.GrantMoves
import com.vaso.bichos.engine.core.Heal
import com.vaso.bichos.engine.core.PlayerId
import com.vaso.bichos.engine.core.Redraw
import com.vaso.bichos.engine.core.StartGrace

/** El primer tablero: 7×5, 7 colores, 3 jugadores por defecto con 2 bichos cada uno. */
object BasicMode {
    const val ID = "basic"
    const val COLORS = 7

    val mode = GameMode(
        id = ID,
        name = "Tablero base",
        boardWidth = 7,
        boardHeight = 5,
        colorCount = COLORS,
        playerCount = 2..6,
        championsPerPlayer = 2,
        baseMovesPerTurn = 2,
        baseMoveContainers = 2,
        baseOrbCapacity = 4,
        spawner = WeightedSpawner(COLORS, orbWeight = 1),
        matchFinder = StandardMatchFinder(),
        baseCanSwap = { a, b, _ -> a.isAdjacentTo(b) },
        createRules = { state ->
            listOf(
                ColorCollection(state.newElementId()),
                MatchRewards(state.newElementId()),
                RedrawRule(state.newElementId()),
                GraceEndRule(state.newElementId(), rounds = 2, extraTurns = 0),
            )
        },
    )
}

/** Las piedras van al bicho de ese color del jugador (si no tiene, se pierden). Los orbes, al jugador. */
class ColorCollection(override val id: ElementId) : GameElement {
    override fun onEvent(event: GameEvent, ctx: GameContext) {
        if (event !is Events.TilesCollected) return
        val stones = event.tiles.filterIsInstance<Stone>().groupingBy { it.color }.eachCount()
        for ((color, count) in stones) {
            val champion = ctx.state.championsOf(event.player).firstOrNull { it.color == color } ?: continue
            ctx.enqueue(AddCharge(champion.id, count))
        }
        val orbs = event.tiles.count { it == Orb }
        if (orbs > 0) ctx.enqueue(ChangeOrbs(event.player, orbs))
    }
}

/** Premios acumulativos: 4+ da un movimiento (si cabe), 5+ cura 5 a cada bicho vivo del jugador. */
class MatchRewards(override val id: ElementId) : GameElement {
    override fun onEvent(event: GameEvent, ctx: GameContext) {
        if (event !is Events.GroupScored) return
        if (event.size >= 4) ctx.enqueue(GrantMoves(event.player, 1))
        if (event.size >= 5) {
            for (champion in ctx.state.championsOf(event.player).filter { it.isAlive() }) {
                ctx.enqueue(Heal(id, champion.id, HEAL))
            }
        }
    }

    private companion object {
        const val HEAL = 5
    }
}

/**
 * Si pasa una ronda entera sin ninguna combinación, al jugador al que le vuelve el turno
 * se le ofrece un redraw gratis. Lo que combine el redraw cuenta para él.
 */
class RedrawRule(override val id: ElementId) : GameElement {
    private var turnsWithoutMatch = 0
    private var matchedThisTurn = false
    private var offeredTo: PlayerId? = null

    override fun onEvent(event: GameEvent, ctx: GameContext) {
        when (event) {
            is Events.TurnStarted -> if (!event.ghost) {
                matchedThisTurn = false
                offeredTo = if (turnsWithoutMatch >= ctx.state.players.size) event.player else null
            }
            is Events.GroupMatched -> {
                matchedThisTurn = true
                turnsWithoutMatch = 0
            }
            is Events.TurnEnded -> if (!event.ghost) {
                if (!matchedThisTurn) turnsWithoutMatch++
                offeredTo = null
            }
        }
    }

    override fun options(player: PlayerId, ctx: GameContext): List<GameOption> =
        if (offeredTo == player && ctx.canAct(player)) {
            listOf(GameOption(id, KEY, "¿Redraw?", "Nadie ha combinado en toda la ronda. Vacía y rellena el tablero. No gasta movimiento."))
        } else {
            emptyList()
        }

    override fun useOption(player: PlayerId, key: String, ctx: GameContext) {
        offeredTo = null
        turnsWithoutMatch = 0
        ctx.enqueue(Redraw())
    }

    private companion object {
        const val KEY = "redraw"
    }
}

/**
 * Cuando al acabar un turno queda uno o ningún jugador con bichos vivos, se juegan
 * [rounds] rondas + [extraTurns] turnos fantasma para que salten revivires y demás.
 * Después: uno vivo gana, ninguno es empate, y si hay varios la partida sigue.
 */
class GraceEndRule(override val id: ElementId, private val rounds: Int, private val extraTurns: Int) : GameElement {
    override fun onEvent(event: GameEvent, ctx: GameContext) {
        val state = ctx.state
        when (event) {
            is Events.TurnEnded -> if (!event.ghost && !state.turn.inGrace && state.livingPlayers().size <= 1) {
                ctx.enqueue(StartGrace(rounds * state.players.size + extraTurns))
            }
            is Events.GraceEnded -> {
                val living = state.livingPlayers()
                when (living.size) {
                    0 -> ctx.enqueue(EndGame(GameResult.Draw))
                    1 -> ctx.enqueue(EndGame(GameResult.Winner(living.single().id)))
                }
            }
        }
    }
}
