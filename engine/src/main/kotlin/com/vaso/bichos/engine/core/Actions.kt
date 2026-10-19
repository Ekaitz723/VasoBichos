package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.board.MatchGroup
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.board.Tile

/**
 * Algo que cambia la partida. Las habilidades se montan juntando acciones; si falta alguna,
 * se crea otra clase o se usa [Act] para lógica puntual.
 */
interface Action {
    fun execute(ctx: GameContext)
}

class Act(val name: String, private val block: (GameContext) -> Unit) : Action {
    override fun execute(ctx: GameContext) = block(ctx)
    override fun toString() = "Act($name)"
}

// ---------- Tablero ----------

data class SwapTiles(val a: Pos, val b: Pos) : Action {
    override fun execute(ctx: GameContext) {
        val board = ctx.state.board
        val tmp = board[a]
        board[a] = board[b]
        board[b] = tmp
        ctx.emit(Events.TilesSwapped(a, b))
    }
}

/** Busca qué ha combinado: avisa de cada grupo, de cada tamaño con el que puntúa, y lo recoge. */
class ResolveMatches(val groups: List<MatchGroup>) : Action {
    override fun execute(ctx: GameContext) {
        val player = ctx.state.turn.current
        for (group in groups) {
            ctx.emit(Events.GroupMatched(player, group))
            for (size in group.scoringSizes) ctx.emit(Events.GroupScored(player, group, size))
            ctx.enqueue(CollectCells(player, group.cells.toList()))
        }
    }
}

/** Quita fichas del tablero y se las da a [player] (las reglas deciden qué hacer con ellas). */
data class CollectCells(val player: PlayerId, val cells: List<Pos>) : Action {
    override fun execute(ctx: GameContext) {
        val board = ctx.state.board
        val taken = cells.filter { board[it] != null }
        if (taken.isEmpty()) return
        val tiles = taken.map { board[it]!! }
        taken.forEach { board[it] = null }
        ctx.emit(Events.TilesCleared(taken))
        ctx.emit(Events.TilesCollected(player, tiles))
    }
}

/** Aplica la gravedad y rellena los huecos desde el lado contrario. */
class Settle : Action {
    override fun execute(ctx: GameContext) {
        val state = ctx.state
        val board = state.board
        val spawner = ctx.query(Q.Spawner, state.mode.spawner)
        val moves = mutableListOf<Pair<Pos, Pos>>()
        val spawned = linkedMapOf<Pos, Tile>()
        for (lane in board.gravityLanes()) {
            var write = 0
            for ((read, p) in lane.withIndex()) {
                val tile = board[p] ?: continue
                if (read != write) {
                    board[lane[write]] = tile
                    board[p] = null
                    moves += p to lane[write]
                }
                write++
            }
            for (i in write until lane.size) {
                val tile = spawner.spawn(state, lane[i])
                board[lane[i]] = tile
                spawned[lane[i]] = tile
            }
        }
        if (moves.isNotEmpty()) ctx.emit(Events.TilesFell(moves))
        if (spawned.isNotEmpty()) ctx.emit(Events.TilesSpawned(spawned, board.gravity.opposite))
    }
}

/** Vacía el tablero entero (sin recoger nada) y lo vuelve a llenar. */
class Redraw : Action {
    override fun execute(ctx: GameContext) {
        val state = ctx.state
        val board = state.board
        val spawner = ctx.query(Q.Spawner, state.mode.spawner)
        ctx.emit(Events.TilesCleared(board.positions.filter { board[it] != null }))
        val tiles = linkedMapOf<Pos, Tile>()
        for (p in board.positions) {
            val tile = spawner.spawn(state, p)
            board[p] = tile
            tiles[p] = tile
        }
        ctx.emit(Events.BoardRedrawn(tiles))
    }
}

// ---------- Jugadores ----------

data class SpendMoves(val player: PlayerId, val amount: Int) : Action {
    override fun execute(ctx: GameContext) {
        val p = ctx.state.player(player)
        p.moves = (p.moves - amount).coerceAtLeast(0)
        ctx.emit(Events.MovesChanged(player, p.moves))
    }
}

/** Da movimientos solo hasta llenar los contenedores; lo que no cabe se pierde. */
data class GrantMoves(val player: PlayerId, val amount: Int) : Action {
    override fun execute(ctx: GameContext) {
        val p = ctx.state.player(player)
        val room = (ctx.moveContainers(player) - p.moves).coerceAtLeast(0)
        val gained = minOf(room, amount)
        p.moves += gained
        ctx.emit(Events.MovesChanged(player, p.moves, lost = amount - gained))
    }
}

/** Suma o resta orbes, con tope en la capacidad del jugador. */
data class ChangeOrbs(val player: PlayerId, val delta: Int) : Action {
    override fun execute(ctx: GameContext) {
        val p = ctx.state.player(player)
        val raw = p.orbs + delta
        val capped = raw.coerceIn(0, maxOf(ctx.orbCapacity(player), 0))
        p.orbs = capped
        ctx.emit(Events.OrbsChanged(player, capped, lost = (raw - capped).coerceAtLeast(0)))
    }
}

// ---------- Bichos (cada bicho decide cómo le afecta) ----------

data class AddCharge(val champion: ElementId, val amount: Int) : Action {
    override fun execute(ctx: GameContext) {
        ctx.champion(champion)?.receiveCharge(amount, ctx)
    }
}

data class DealDamage(val source: ElementId?, val target: ElementId, val amount: Int) : Action {
    override fun execute(ctx: GameContext) {
        ctx.champion(target)?.receiveDamage(amount, source, ctx)
    }
}

data class Heal(val source: ElementId?, val target: ElementId, val amount: Int) : Action {
    override fun execute(ctx: GameContext) {
        ctx.champion(target)?.receiveHeal(amount, source, ctx)
    }
}

data class Revive(val target: ElementId, val hp: Int) : Action {
    override fun execute(ctx: GameContext) {
        ctx.champion(target)?.revive(hp, ctx)
    }
}

// ---------- Flujo de partida ----------

class AskDecision(val decision: Decision) : Action {
    override fun execute(ctx: GameContext) {
        ctx.state.pendingDecision = decision
        ctx.emit(Events.DecisionRequested(decision))
    }
}

class StartGame : Action {
    override fun execute(ctx: GameContext) = ctx.emit(Events.GameStarted)
}

data class BeginTurn(val player: PlayerId) : Action {
    override fun execute(ctx: GameContext) {
        val state = ctx.state
        if (state.isOver) return
        val turn = state.turn
        turn.current = player
        turn.ghost = false
        turn.number++
        val p = state.player(player)
        p.moves = minOf(ctx.movesPerTurn(player), ctx.moveContainers(player)).coerceAtLeast(0)
        ctx.emit(Events.TurnStarted(player, ghost = false))
        ctx.emit(Events.MovesChanged(player, p.moves))
    }
}

class EndTurn : Action {
    override fun execute(ctx: GameContext) {
        val turn = ctx.state.turn
        if (turn.advancePending) return
        turn.advancePending = true
        ctx.emit(Events.TurnEnded(turn.current, turn.ghost))
    }
}

/**
 * Pasa al siguiente turno. Los turnos fantasma avanzan su propio contador y no tocan
 * a quién le toca de verdad: al acabar, se retoma con [TurnState.resumeWith].
 */
class AdvanceTurn : Action {
    override fun execute(ctx: GameContext) {
        val state = ctx.state
        val turn = state.turn
        turn.advancePending = false
        when {
            turn.ghostTurnsLeft > 0 -> {
                turn.ghostTurnsLeft--
                turn.current = state.nextPlayer(turn.current)
                turn.ghost = true
                ctx.emit(Events.TurnStarted(turn.current, ghost = true))
            }
            turn.ghost -> {
                turn.ghost = false
                val resume = turn.resumeWith ?: state.nextPlayer(turn.current)
                turn.resumeWith = null
                ctx.emit(Events.GraceEnded)
                ctx.enqueue(BeginTurn(resume))
            }
            else -> ctx.enqueue(BeginTurn(state.nextPlayer(turn.current)))
        }
    }
}

/** Empieza [turns] turnos fantasma justo después del turno actual. */
data class StartGrace(val turns: Int) : Action {
    override fun execute(ctx: GameContext) {
        require(turns > 0) { "grace needs at least one turn" }
        val turn = ctx.state.turn
        turn.ghostTurnsLeft = turns
        turn.resumeWith = ctx.state.nextPlayer(turn.current)
        ctx.emit(Events.GraceStarted(turns))
    }
}

data class EndGame(val result: GameResult) : Action {
    override fun execute(ctx: GameContext) {
        ctx.state.result = result
        ctx.emit(Events.GameOver(result))
    }
}
