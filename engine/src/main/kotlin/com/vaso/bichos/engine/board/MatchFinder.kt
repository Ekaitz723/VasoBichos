package com.vaso.bichos.engine.board

/** Una fila o columna de 3 o más fichas seguidas que combinan. */
data class Line(val cells: List<Pos>) {
    val size: Int get() = cells.size
}

/**
 * Un grupo combinado: una línea suelta o varias que se cruzan (L, T, cruz...).
 * [scoringSizes] son los "grupos" con los que puntúa para los premios (3+, 4+, 5+...).
 */
class MatchGroup(
    val key: Any,
    val cells: Set<Pos>,
    val lines: List<Line>,
    val scoringSizes: List<Int>,
) {
    val size: Int get() = cells.size
}

fun interface MatchFinder {
    fun find(board: Board): List<MatchGroup>
}

/**
 * Busca líneas de [minLength]+ y une las que comparten casilla. Cada grupo puntúa con su
 * tamaño total y, además, con cada línea de 4+ que contenga salvo la más larga
 * (dos de 4 en cruz = un 5+ y un 4+; dos de 5 en L = un 5+ y un 5+).
 */
class StandardMatchFinder(private val minLength: Int = 3) : MatchFinder {

    override fun find(board: Board): List<MatchGroup> {
        val lines = findLines(board)
        if (lines.isEmpty()) return emptyList()

        // Unión de líneas que comparten alguna casilla.
        val parent = IntArray(lines.size) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x]
            return x
        }
        val owner = HashMap<Pos, Int>()
        lines.forEachIndexed { i, line ->
            for (p in line.cells) {
                val other = owner[p]
                if (other == null) owner[p] = i else parent[root(i)] = root(other)
            }
        }

        return lines.indices
            .groupBy { root(it) }
            .values
            .map { idx -> buildGroup(board, idx.map { lines[it] }) }
            .sortedBy { g -> g.cells.minOf { it.row * board.width + it.col } }
    }

    private fun buildGroup(board: Board, lines: List<Line>): MatchGroup {
        val cells = linkedSetOf<Pos>()
        lines.forEach { cells.addAll(it.cells) }
        val extraLines = lines.map { it.size }.filter { it >= 4 }.sortedDescending().drop(1)
        return MatchGroup(
            key = board[lines.first().cells.first()]!!.matchKey!!,
            cells = cells,
            lines = lines,
            scoringSizes = listOf(cells.size) + extraLines,
        )
    }

    private fun findLines(board: Board): List<Line> {
        val result = mutableListOf<Line>()
        val rows = (0 until board.height).map { r -> (0 until board.width).map { c -> Pos(c, r) } }
        val cols = (0 until board.width).map { c -> (0 until board.height).map { r -> Pos(c, r) } }
        for (lane in rows + cols) {
            var start = 0
            while (start < lane.size) {
                val key = board[lane[start]]?.matchKey
                var end = start + 1
                if (key != null) {
                    while (end < lane.size && board[lane[end]]?.matchKey == key) end++
                    if (end - start >= minLength) result += Line(lane.subList(start, end).toList())
                }
                start = end
            }
        }
        return result
    }
}
