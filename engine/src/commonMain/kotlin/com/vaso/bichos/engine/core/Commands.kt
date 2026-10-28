package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.board.Pos

/** Lo que pide un jugador. Es lo único que viaja por la red. */
sealed interface Command {
    val player: PlayerId

    data class Swap(override val player: PlayerId, val a: Pos, val b: Pos) : Command
    data class UseOption(override val player: PlayerId, val source: ElementId, val key: String) : Command
    data class Answer(override val player: PlayerId, val choice: String) : Command
}

sealed interface CommandResult {
    data class Accepted(val events: List<GameEvent>) : CommandResult
    data class Rejected(val reason: String) : CommandResult
}
