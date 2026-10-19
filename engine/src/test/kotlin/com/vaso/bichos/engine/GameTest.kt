package com.vaso.bichos.engine

import com.vaso.bichos.engine.board.Direction
import com.vaso.bichos.engine.board.Orb
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.content.vanilla.Vanilla
import com.vaso.bichos.engine.core.Command
import com.vaso.bichos.engine.core.CommandResult
import com.vaso.bichos.engine.core.ElementId
import com.vaso.bichos.engine.core.Engine
import com.vaso.bichos.engine.core.PlayerId
import com.vaso.bichos.engine.core.Events
import com.vaso.bichos.engine.core.GameContext
import com.vaso.bichos.engine.core.GameElement
import com.vaso.bichos.engine.core.GameEvent
import com.vaso.bichos.engine.core.GameResult
import com.vaso.bichos.engine.core.Revive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {

    /** Deja [color] en la fila de abajo de forma que al bajar (2,3)->(2,4) se haga una línea de [length]. */
    private fun Engine.lineSetup(color: Int, length: Int) {
        val cols = (0 until length).filter { it != 2 }
        cols.forEach { put(Pos(it, 4) to stone(color)) }
        put(Pos(2, 3) to stone(color))
    }

    private fun Engine.triggerLine(player: PlayerId = P0) =
        swapOk(player, Pos(2, 3), Pos(2, 4))

    @Test fun threeInLineChargesAndFiresTheVanillaAbility() {
        val g = newGame()
        g.lineSetup(color = 0, length = 3)
        g.triggerLine()
        assertEquals(0, g.vanilla(0).charge)
        for (enemy in listOf(2, 3, 4, 5)) assertEquals(45, g.vanilla(enemy).hp)
        assertEquals(50, g.vanilla(1).hp)
        assertEquals(1, g.state.player(P0).moves)
    }

    @Test fun fourInLineGivesAnExtraMoveAndExcessIsLost() {
        val g = newGame()
        g.lineSetup(color = 0, length = 4)
        g.triggerLine()
        assertEquals(2, g.state.player(P0).moves)
        assertEquals(0, g.vanilla(0).charge)
        assertEquals(45, g.vanilla(2).hp)
    }

    @Test fun stonesOfAColorWithoutChampionAreLostButRewardsStillApply() {
        val g = newGame()
        g.lineSetup(color = 6, length = 4)
        g.triggerLine()
        assertEquals(2, g.state.player(P0).moves)
        assertTrue(g.state.champions().all { it.charge == 0 })
    }

    @Test fun fiveHealsAndFullHpVanillaGetsAStoneInstead() {
        val g = newGame()
        g.vanilla(1).hp = 40
        // L de color 6: (0,4)(1,4)(2,4) + (0,3)(0,2)
        g.put(Pos(0, 4) to stone(6), Pos(1, 4) to stone(6), Pos(0, 3) to stone(6), Pos(0, 2) to stone(6), Pos(2, 3) to stone(6))
        g.triggerLine()
        assertEquals(1, g.vanilla(0).charge) // estaba a tope: 1 piedra
        assertEquals(45, g.vanilla(1).hp) // se cura
        assertEquals(0, g.vanilla(1).charge)
        assertEquals(2, g.state.player(P0).moves) // 5+ también da el movimiento del 4+
    }

    @Test fun movesNeverExceedContainers() {
        val g = newGame()
        // Cruz de dos líneas de 4 (fila 3 y columna 2) con un solo intercambio: puntúa 7 y 4,
        // o sea dos movimientos extra, pero solo cabe uno.
        g.put(Pos(0, 3) to stone(6), Pos(1, 3) to stone(6), Pos(3, 3) to stone(6))
        g.put(Pos(2, 0) to stone(6), Pos(2, 1) to stone(6), Pos(2, 2) to stone(6), Pos(2, 4) to stone(6))
        val result = g.submit(Command.Swap(P0, Pos(2, 3), Pos(2, 4))) as CommandResult.Accepted
        assertEquals(2, g.state.player(P0).moves)
        assertTrue(result.events.any { it is Events.MovesChanged && it.lost == 1 })
    }

    @Test fun turnPassesAfterTwoMoves() {
        val g = newGame()
        g.junkSwap(P0)
        assertEquals(P0, g.state.turn.current)
        g.junkSwap(P0)
        assertEquals(P1, g.state.turn.current)
        assertEquals(2, g.state.player(P1).moves)
        assertTrue(g.submit(Command.Swap(P0, Pos(5, 0), Pos(6, 0))) is CommandResult.Rejected)
    }

    @Test fun lastPlayerStandingWinsAfterTwoGhostRounds() {
        val g = newGame()
        listOf(2, 3, 4, 5).forEach { g.vanilla(it).hp = 5 }
        g.lineSetup(color = 0, length = 3)
        g.triggerLine()
        assertNull(g.state.result)
        g.junkSwap(P0)
        assertEquals(GameResult.Winner(P0), g.state.result)
    }

    @Test fun everybodyDeadIsADraw() {
        val g = newGame()
        g.state.champions().forEach { it.hp = 0 }
        g.junkSwap(P0)
        g.junkSwap(P0)
        assertEquals(GameResult.Draw, g.state.result)
    }

    @Test fun reviveDuringGraceContinuesTheGameWithTheRightPlayer() {
        val g = newGame()
        listOf(2, 3, 4, 5).forEach { g.vanilla(it).hp = 0 }
        val reviveTarget = g.vanilla(4).id
        var ghostTurns = 0
        g.state.elements += object : GameElement {
            override val id = ElementId(999)
            override fun onEvent(event: GameEvent, ctx: GameContext) {
                if (event is Events.TurnStarted && event.ghost && ++ghostTurns == 2) ctx.enqueue(Revive(reviveTarget, 20))
            }
        }
        g.junkSwap(P0)
        g.junkSwap(P0)
        assertEquals(6, ghostTurns)
        assertNull(g.state.result)
        assertEquals(P1, g.state.turn.current)
        assertFalse(g.state.turn.ghost)
        assertEquals(2, g.state.player(P1).moves)
    }

    @Test fun phase2CostsFourOrbsAndAMoveAndDoublesDamage() {
        val g = newGame()
        g.state.player(P0).orbs = 4
        g.useOk(P0, 0, Vanilla.PHASE_2)
        assertEquals(2, g.vanilla(0).phase)
        assertEquals(0, g.state.player(P0).orbs)
        assertEquals(1, g.state.player(P0).moves)
        g.lineSetup(color = 0, length = 3)
        g.triggerLine()
        assertEquals(40, g.vanilla(2).hp)
    }

    @Test fun reviveWithThreeOrFourOrbs() {
        val g = newGame()
        g.vanilla(0).hp = 0
        g.state.player(P0).orbs = 4
        val options = g.options(P0).filter { it.source == g.vanilla(0).id }.associateBy { it.key }
        assertTrue(options.getValue(Vanilla.REVIVE_3).enabled)
        assertTrue(options.getValue(Vanilla.REVIVE_4).enabled)
        g.useOk(P0, 0, Vanilla.REVIVE_4)
        assertEquals(30, g.vanilla(0).hp)
        assertEquals(0, g.state.player(P0).orbs)
        assertEquals(1, g.state.player(P0).moves)
    }

    @Test fun deadVanillaDoesNotCharge() {
        val g = newGame()
        g.vanilla(0).hp = 0
        g.lineSetup(color = 0, length = 3)
        g.triggerLine()
        assertEquals(0, g.vanilla(0).charge)
        assertEquals(50, g.vanilla(2).hp)
    }

    @Test fun convertOrbsAsksHowManyAndCanFireTheAbility() {
        val g = newGame()
        g.state.player(P0).orbs = 3
        g.useOk(P0, 0, Vanilla.CONVERT)
        val decision = g.state.pendingDecision
        assertNotNull(decision)
        assertTrue(g.submit(Command.Swap(P0, Pos(5, 0), Pos(6, 0))) is CommandResult.Rejected)
        assertTrue(g.submit(Command.Answer(P0, "3")) is CommandResult.Accepted)
        assertEquals(0, g.state.player(P0).orbs)
        assertEquals(1, g.state.player(P0).moves)
        assertEquals(45, g.vanilla(2).hp)
    }

    @Test fun cancellingTheConversionCostsNothing() {
        val g = newGame()
        g.state.player(P0).orbs = 2
        g.useOk(P0, 0, Vanilla.CONVERT)
        g.submit(Command.Answer(P0, "cancel"))
        assertEquals(2, g.state.player(P0).orbs)
        assertEquals(2, g.state.player(P0).moves)
    }

    @Test fun orbsGoToThePlayerUpToCapacity() {
        val g = newGame()
        g.state.player(P0).orbs = 2
        g.put(Pos(0, 4) to Orb, Pos(1, 4) to Orb, Pos(3, 4) to Orb, Pos(2, 3) to Orb)
        g.triggerLine()
        assertEquals(4, g.state.player(P0).orbs)
        assertEquals(2, g.state.player(P0).moves) // 4 orbes también dan movimiento
    }

    @Test fun redrawIsOfferedAfterAFullRoundWithoutMatchesAndIsFree() {
        val g = newGame()
        repeat(3) { g.junkSwap(g.state.turn.current); g.junkSwap(g.state.turn.current) }
        assertEquals(P0, g.state.turn.current)
        val redraw = g.options(P0).single { it.key == "redraw" }
        assertTrue(g.submit(Command.UseOption(P0, redraw.source, redraw.key)) is CommandResult.Accepted)
        assertEquals(2, g.state.player(P0).moves)
        assertTrue(g.options(P0).none { it.key == "redraw" })
    }

    @Test fun gravityCanPointSideways() {
        val g = newGame()
        g.state.board.gravity = Direction.RIGHT
        g.put(Pos(5, 0) to stone(1), Pos(6, 0) to stone(6), Pos(6, 1) to stone(6), Pos(6, 2) to stone(6))
        g.swapOk(P0, Pos(0, 4), Pos(1, 4))
        assertEquals(stone(1), g.state.board[Pos(6, 0)])
    }

    @Test fun onlyChampionColorsAndChosenExtrasFallOnTheBoard() {
        // Bichos de colores 0-5; de extra solo el 6.
        val g = newGame(junkBoard = false, seed = 9, extraColors = emptySet())
        val allowed = (0..5).map { stone(it) }.toSet() + Orb
        val rng = java.util.Random(3)
        repeat(300) {
            val p = g.state.turn.current
            val a = Pos(rng.nextInt(6), rng.nextInt(5))
            g.submit(Command.Swap(p, a, Pos(a.col + 1, a.row)))
            assertTrue(g.state.board.positions.all { g.state.board[it] in allowed })
        }
    }

    @Test fun sameSeedAndCommandsGiveTheSameGame() {
        fun play(): List<Any?> {
            val g = newGame(junkBoard = false, seed = 7)
            repeat(40) { i ->
                val p = g.state.turn.current
                g.submit(Command.Swap(p, Pos(i % 6, i % 5), Pos(i % 6 + 1, i % 5)))
            }
            return g.state.board.positions.map { g.state.board[it] } + g.state.champions().map { it.hp }
        }
        assertEquals(play(), play())
    }

    @Test fun longRandomGameNeverBreaks() {
        val g = newGame(junkBoard = false, seed = 123)
        val rng = java.util.Random(1)
        repeat(2000) {
            if (g.state.isOver) return
            val p = g.state.turn.current
            g.state.pendingDecision?.let { d -> g.submit(Command.Answer(d.player, d.choices.first().key)); return@repeat }
            val usable = g.options(p).filter { it.enabled }
            if (usable.isNotEmpty() && rng.nextInt(4) == 0) {
                val o = usable[rng.nextInt(usable.size)]
                g.submit(Command.UseOption(p, o.source, o.key))
            } else {
                val a = Pos(rng.nextInt(6), rng.nextInt(5))
                g.submit(Command.Swap(p, a, Pos(a.col + 1, a.row)))
            }
        }
    }
}
