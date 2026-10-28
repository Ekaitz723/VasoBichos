package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.board.Board
import com.vaso.bichos.engine.champion.Champion

class PlayerState(val id: PlayerId, val name: String) {
    var moves = 0
    var orbs = 0
}

class TurnState(var current: PlayerId) {
    /** El turno actual es fantasma: nadie mueve, solo saltan los efectos. */
    var ghost = false
    var ghostTurnsLeft = 0

    /** A quién le toca de verdad cuando acaben los turnos fantasma. */
    var resumeWith: PlayerId? = null

    /** El turno ya terminó y falta pasar al siguiente cuando todo se estabilice. */
    var advancePending = false
    var number = 0

    val inGrace: Boolean get() = ghost || ghostTurnsLeft > 0
}

sealed interface GameResult {
    data class Winner(val player: PlayerId) : GameResult
    data object Draw : GameResult
}

class GameState(
    val mode: GameMode,
    val rng: Rng,
    val board: Board,
    val players: List<PlayerState>,
    /** Colores del tablero al empezar (los elementos pueden cambiarlos con [Q.BoardColors]). */
    val boardColors: List<StoneColor>,
) {
    /** En el orden en que se les consulta y avisa. */
    val elements = mutableListOf<GameElement>()
    val turn = TurnState(players.first().id)
    var pendingDecision: Decision? = null
    var result: GameResult? = null

    private var nextElementId = 1
    fun newElementId() = ElementId(nextElementId++)

    val isOver: Boolean get() = result != null

    fun player(id: PlayerId) = players[id.index]

    fun element(id: ElementId) = elements.firstOrNull { it.id == id }

    fun champions(): List<Champion> = elements.filterIsInstance<Champion>()

    fun champion(id: ElementId) = champions().firstOrNull { it.id == id }

    fun championsOf(player: PlayerId) = champions().filter { it.owner == player }

    fun nextPlayer(after: PlayerId) = PlayerId((after.index + 1) % players.size)

    /** Jugadores con algún bicho vivo. */
    fun livingPlayers() = players.filter { p -> championsOf(p.id).any { it.isAlive() } }
}
