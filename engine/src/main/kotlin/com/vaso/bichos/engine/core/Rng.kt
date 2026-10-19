package com.vaso.bichos.engine.core

/**
 * Azar determinista (SplitMix64). Con la misma semilla y los mismos comandos,
 * todos los móviles calculan exactamente la misma partida.
 */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += 0x9E3779B97F4A7C15uL.toLong()
        var z = state
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    fun <T> shuffle(items: MutableList<T>) {
        for (i in items.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = items[i]
            items[i] = items[j]
            items[j] = tmp
        }
    }
}
