package com.vaso.bichos.engine.board

import com.vaso.bichos.engine.core.StoneColor

/** Fila 0 = arriba, columna 0 = izquierda. */
data class Pos(val col: Int, val row: Int) {
    operator fun plus(d: Direction) = Pos(col + d.dCol, row + d.dRow)

    fun isAdjacentTo(other: Pos): Boolean =
        Math.abs(col - other.col) + Math.abs(row - other.row) == 1
}

enum class Direction(val dCol: Int, val dRow: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

/**
 * Lo que ocupa una casilla. Es abierto: se pueden crear fichas nuevas (bombas, comodines...)
 * sin tocar el motor.
 */
interface Tile {
    /** Fichas con la misma clave combinan entre sí; null = no combina nunca. */
    val matchKey: Any?
}

data class Stone(val color: StoneColor) : Tile {
    override val matchKey: Any get() = this
}

data object Orb : Tile {
    override val matchKey: Any get() = Orb
}

class Board(val width: Int, val height: Int) {
    private val tiles = arrayOfNulls<Tile>(width * height)

    /** Hacia dónde caen las fichas. Las nuevas entran por el lado contrario. */
    var gravity: Direction = Direction.DOWN

    /** Todas las posiciones, por filas de arriba abajo y de izquierda a derecha. */
    val positions: List<Pos> = (0 until height).flatMap { r -> (0 until width).map { c -> Pos(c, r) } }

    operator fun contains(p: Pos) = p.col in 0 until width && p.row in 0 until height

    operator fun get(p: Pos): Tile? = if (p in this) tiles[p.row * width + p.col] else null

    operator fun set(p: Pos, tile: Tile?) {
        require(p in this) { "$p is outside the board" }
        tiles[p.row * width + p.col] = tile
    }

    fun emptyPositions(): List<Pos> = positions.filter { this[it] == null }

    /**
     * Carriles en el sentido de la gravedad: cada lista va desde el "suelo" (donde se apilan
     * las fichas) hasta el borde por donde entran las nuevas.
     */
    fun gravityLanes(): List<List<Pos>> = when (gravity) {
        Direction.DOWN -> (0 until width).map { c -> (height - 1 downTo 0).map { r -> Pos(c, r) } }
        Direction.UP -> (0 until width).map { c -> (0 until height).map { r -> Pos(c, r) } }
        Direction.RIGHT -> (0 until height).map { r -> (width - 1 downTo 0).map { c -> Pos(c, r) } }
        Direction.LEFT -> (0 until height).map { r -> (0 until width).map { c -> Pos(c, r) } }
    }
}
