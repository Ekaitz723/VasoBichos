package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.board.MatchFinder
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.board.TileSpawner

/**
 * Un valor que cualquier elemento puede modificar. Se calcula a partir de una base que pasa
 * por [GameElement.modify] de todos los elementos, en orden.
 */
interface Query<T>

/** Consultas que usa el motor o el contenido actual. Se pueden crear más en cualquier sitio. */
object Q {
    data class MovesPerTurn(val player: PlayerId) : Query<Int>
    data class MoveContainers(val player: PlayerId) : Query<Int>
    data class OrbCapacity(val player: PlayerId) : Query<Int>
    data class MaxHp(val champion: ElementId) : Query<Int>
    data class AbilityCost(val champion: ElementId) : Query<Int>

    /** ¿Puede [player] intercambiar estas dos casillas? */
    data class CanSwap(val player: PlayerId, val a: Pos, val b: Pos) : Query<Boolean>

    /** Cuántos movimientos cuesta un intercambio. */
    data class SwapCost(val player: PlayerId) : Query<Int>

    /** ¿Se pasa el turno solo al quedarse sin movimientos? */
    data class AutoEndTurn(val player: PlayerId) : Query<Boolean>

    object Spawner : Query<TileSpawner>
    object Finder : Query<MatchFinder>
}
