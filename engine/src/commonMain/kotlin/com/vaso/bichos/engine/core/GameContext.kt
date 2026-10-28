package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.champion.Champion

/** Lo que reciben los elementos y las acciones para leer y cambiar la partida. */
class GameContext internal constructor(private val engine: Engine) {
    val state: GameState get() = engine.state
    val rng: Rng get() = state.rng

    fun emit(event: GameEvent) = engine.emit(event)

    /**
     * Encola una acción. Si se llama reaccionando a un evento, va delante de lo que ya estaba
     * esperando; si se llama ejecutando una acción, va justo después de las reacciones a esa acción.
     */
    fun enqueue(action: Action) = engine.enqueue(action)

    fun enqueue(name: String, block: (GameContext) -> Unit) = enqueue(Act(name, block))

    fun <T> query(query: Query<T>, base: T): T = engine.query(query, base)

    fun ask(decision: Decision) = enqueue(AskDecision(decision))

    /** ¿Es el turno normal de [player] y puede hacer cosas ahora mismo? */
    fun canAct(player: PlayerId): Boolean = engine.canAct(player)

    fun addElement(element: GameElement) {
        state.elements += element
    }

    fun removeElement(element: GameElement) {
        state.elements -= element
    }

    fun champion(id: ElementId): Champion? = state.champion(id)

    fun movesPerTurn(player: PlayerId) = query(Q.MovesPerTurn(player), state.mode.baseMovesPerTurn)
    fun moveContainers(player: PlayerId) = query(Q.MoveContainers(player), state.mode.baseMoveContainers)
    fun orbCapacity(player: PlayerId) = query(Q.OrbCapacity(player), state.mode.baseOrbCapacity)
    fun boardColors(): List<StoneColor> = query(Q.BoardColors, state.boardColors)
}
