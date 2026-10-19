package com.vaso.bichos.engine.board

import com.vaso.bichos.engine.core.GameContext

/** Decide qué ficha nueva aparece en una casilla. */
fun interface TileSpawner {
    fun spawn(ctx: GameContext, pos: Pos): Tile
}

/** Cada color del tablero pesa 1 y el orbe pesa [orbWeight]. */
class WeightedSpawner(private val orbWeight: Int = 1) : TileSpawner {
    override fun spawn(ctx: GameContext, pos: Pos): Tile {
        val colors = ctx.boardColors()
        val roll = ctx.rng.nextInt(colors.size + orbWeight)
        return if (roll < colors.size) Stone(colors[roll]) else Orb
    }
}
