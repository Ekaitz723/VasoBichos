package com.vaso.bichos.engine

import com.vaso.bichos.engine.board.Board
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.board.StandardMatchFinder
import kotlin.test.assertEquals
import kotlin.test.Test

class MatchFinderTest {
    private fun board(vararg rows: String): Board {
        val b = Board(rows.first().length, rows.size)
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch -> b[Pos(c, r)] = if (ch == '.') Junk else stone(ch - '0') }
        }
        return b
    }

    private fun scoring(vararg rows: String) =
        StandardMatchFinder().find(board(*rows)).map { it.scoringSizes }

    @Test fun lineOf3() = assertEquals(listOf(listOf(3)), scoring("111...."))

    @Test fun lineOf4() = assertEquals(listOf(listOf(4)), scoring("1111..."))

    @Test fun lineOf5() = assertEquals(listOf(listOf(5)), scoring("11111.."))

    @Test fun twoSeparateGroups() = assertEquals(listOf(listOf(3), listOf(3)), scoring("111.222"))

    @Test fun lShape3and3IsAFive() = assertEquals(
        listOf(listOf(5)),
        scoring(
            "1......",
            "1......",
            "111....",
        ),
    )

    @Test fun tShape() = assertEquals(
        listOf(listOf(5)),
        scoring(
            "111....",
            ".1.....",
            ".1.....",
        ),
    )

    @Test fun fourCrossedWithThreeIsJustAFive() = assertEquals(
        listOf(listOf(6)),
        scoring(
            ".1.....",
            "1111...",
            ".1.....",
        ),
    )

    @Test fun twoFoursCrossedAreAFiveAndAFour() = assertEquals(
        listOf(listOf(7, 4)),
        scoring(
            ".1.....",
            "1111...",
            ".1.....",
            ".1.....",
        ),
    )

    @Test fun twoFivesInLAreTwoFives() = assertEquals(
        listOf(listOf(9, 5)),
        scoring(
            "1......",
            "1......",
            "1......",
            "1......",
            "11111..",
        ),
    )

    @Test fun differentColorsDoNotJoin() = assertEquals(
        listOf(listOf(3), listOf(3)),
        scoring(
            "2......",
            "2......",
            "2111...",
        ),
    )
}
