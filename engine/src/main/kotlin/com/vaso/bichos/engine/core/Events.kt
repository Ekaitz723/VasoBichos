package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.board.Direction
import com.vaso.bichos.engine.board.MatchGroup
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.board.Tile

/**
 * Lo que ha pasado. Los elementos los escuchan para reaccionar y la pantalla los usa para animar.
 * Es abierto: el contenido puede emitir sus propios eventos.
 */
interface GameEvent

object Events {
    data object GameStarted : GameEvent
    data class TurnStarted(val player: PlayerId, val ghost: Boolean) : GameEvent
    data class TurnEnded(val player: PlayerId, val ghost: Boolean) : GameEvent
    data class GraceStarted(val turns: Int) : GameEvent
    data object GraceEnded : GameEvent
    data class GameOver(val result: GameResult) : GameEvent

    data class TilesSwapped(val a: Pos, val b: Pos) : GameEvent
    data class TilesCleared(val cells: List<Pos>) : GameEvent
    data class TilesFell(val moves: List<Pair<Pos, Pos>>) : GameEvent
    data class TilesSpawned(val tiles: Map<Pos, Tile>, val from: Direction) : GameEvent
    data class BoardRedrawn(val tiles: Map<Pos, Tile>) : GameEvent

    /** Un grupo combinado (una vez por grupo, aunque puntúe varias veces). */
    data class GroupMatched(val player: PlayerId, val group: MatchGroup) : GameEvent

    /** Un tamaño con el que puntúa un grupo; los premios 3+/4+/5+ escuchan esto. */
    data class GroupScored(val player: PlayerId, val group: MatchGroup, val size: Int) : GameEvent

    /** Fichas recogidas para [player], vengan de una combinación o de una habilidad. */
    data class TilesCollected(val player: PlayerId, val tiles: List<Tile>) : GameEvent

    data class MovesChanged(val player: PlayerId, val moves: Int, val lost: Int = 0) : GameEvent
    data class OrbsChanged(val player: PlayerId, val orbs: Int, val lost: Int = 0) : GameEvent

    data class ChargeAdded(val champion: ElementId, val amount: Int, val total: Int) : GameEvent
    data class ChargeLost(val champion: ElementId, val amount: Int) : GameEvent
    data class ChargeSpent(val champion: ElementId, val amount: Int, val total: Int) : GameEvent
    data class AbilityActivated(val champion: ElementId, val name: String) : GameEvent
    data class Damaged(val target: ElementId, val amount: Int, val hp: Int, val source: ElementId?) : GameEvent
    data class Healed(val target: ElementId, val amount: Int, val hp: Int, val source: ElementId?) : GameEvent
    data class Died(val champion: ElementId) : GameEvent
    data class Revived(val champion: ElementId, val hp: Int) : GameEvent
    data class PhaseChanged(val champion: ElementId, val phase: Int) : GameEvent

    data class DecisionRequested(val decision: Decision) : GameEvent
    data class DecisionAnswered(val player: PlayerId, val choice: String) : GameEvent
}
