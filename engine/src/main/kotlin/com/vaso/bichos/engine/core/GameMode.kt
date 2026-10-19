package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.board.MatchFinder
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.board.TileSpawner

/**
 * Un modo de juego / tablero: sus números base y sus reglas propias (que son elementos más).
 * Los valores base pasan siempre por [Query], así que cualquier elemento puede cambiarlos.
 */
data class GameMode(
    val id: String,
    val name: String,
    val boardWidth: Int,
    val boardHeight: Int,
    val colorCount: Int,
    val playerCount: IntRange,
    val championsPerPlayer: Int,
    val baseMovesPerTurn: Int,
    val baseMoveContainers: Int,
    val baseOrbCapacity: Int,
    val spawner: TileSpawner,
    val matchFinder: MatchFinder,
    val baseCanSwap: (a: Pos, b: Pos, state: GameState) -> Boolean,
    val createRules: (state: GameState) -> List<GameElement>,
)

data class ChampionPick(val definitionId: String, val color: StoneColor)

data class PlayerSetup(val name: String, val champions: List<ChampionPick>)

data class GameConfig(val modeId: String, val seed: Long, val players: List<PlayerSetup>)
