package com.vaso.bichos.engine

import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.board.Stone
import com.vaso.bichos.engine.board.Tile
import com.vaso.bichos.engine.content.Content
import com.vaso.bichos.engine.content.basic.BasicMode
import com.vaso.bichos.engine.content.vanilla.Vanilla
import com.vaso.bichos.engine.core.ChampionPick
import com.vaso.bichos.engine.core.Command
import com.vaso.bichos.engine.core.CommandResult
import com.vaso.bichos.engine.core.Engine
import com.vaso.bichos.engine.core.GameConfig
import com.vaso.bichos.engine.core.PlayerId
import com.vaso.bichos.engine.core.PlayerSetup
import com.vaso.bichos.engine.core.StoneColor
import org.junit.Assert.assertTrue

/** Ficha de relleno que nunca combina: así los tests controlan exactamente qué pasa. */
object Junk : Tile {
    override val matchKey: Any? = null
}

fun stone(color: Int) = Stone(StoneColor(color))

val P0 = PlayerId(0)
val P1 = PlayerId(1)
val P2 = PlayerId(2)

/** Jugador 0: colores 0 y 1. Jugador 1: 2 y 3. Jugador 2: 4 y 5. El color 6 no es de nadie. */
fun newGame(junkBoard: Boolean = true, seed: Long = 42): Engine {
    val mode = if (junkBoard) BasicMode.mode.copy(spawner = { _, _ -> Junk }) else BasicMode.mode
    val content = Content(listOf(mode), listOf(Vanilla.definition))
    val players = listOf(0 to 1, 2 to 3, 4 to 5).mapIndexed { i, (a, b) ->
        PlayerSetup("J$i", listOf(ChampionPick("vanilla", StoneColor(a)), ChampionPick("vanilla", StoneColor(b))))
    }
    return Engine(GameConfig(BasicMode.ID, seed, players), content)
}

fun Engine.put(vararg tiles: Pair<Pos, Tile>) {
    for ((p, t) in tiles) state.board[p] = t
}

fun Engine.vanilla(color: Int): Vanilla = state.champions().first { it.color.index == color } as Vanilla

fun Engine.swapOk(player: PlayerId, a: Pos, b: Pos) {
    val result = submit(Command.Swap(player, a, b))
    assertTrue("Swap rechazado: $result", result is CommandResult.Accepted)
}

/** Un intercambio de relleno que no combina nada (fila superior). */
fun Engine.junkSwap(player: PlayerId) = swapOk(player, Pos(5, 0), Pos(6, 0))

fun Engine.useOk(player: PlayerId, color: Int, key: String) {
    val result = submit(Command.UseOption(player, vanilla(color).id, key))
    assertTrue("Opción rechazada: $result", result is CommandResult.Accepted)
}
