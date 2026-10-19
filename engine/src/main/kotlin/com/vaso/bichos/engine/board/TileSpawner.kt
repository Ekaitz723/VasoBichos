package com.vaso.bichos.engine.board

import com.vaso.bichos.engine.core.GameState
import com.vaso.bichos.engine.core.StoneColor

/** Decide qué ficha nueva aparece en una casilla. */
fun interface TileSpawner {
    fun spawn(state: GameState, pos: Pos): Tile
}

/** Cada color pesa 1 y el orbe pesa [orbWeight]. */
class WeightedSpawner(private val colorCount: Int, private val orbWeight: Int = 1) : TileSpawner {
    override fun spawn(state: GameState, pos: Pos): Tile {
        val roll = state.rng.nextInt(colorCount + orbWeight)
        return if (roll < colorCount) Stone(StoneColor(roll)) else Orb
    }
}
