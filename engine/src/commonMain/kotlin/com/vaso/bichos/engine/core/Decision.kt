package com.vaso.bichos.engine.core

/**
 * Una pregunta a un jugador. La resolución se para hasta que conteste, aunque sea
 * en mitad de una carambola, y luego sigue donde estaba.
 */
class Decision(
    val player: PlayerId,
    val prompt: String,
    val choices: List<Choice>,
    val onChoose: (choice: Choice, ctx: GameContext) -> Unit,
)

/** [target] permite a la pantalla resaltar lo elegible (un bicho, una casilla...). */
data class Choice(val key: String, val label: String, val target: Any? = null)
