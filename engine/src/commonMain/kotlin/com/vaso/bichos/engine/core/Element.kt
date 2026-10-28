package com.vaso.bichos.engine.core

/**
 * Todo lo que puede influir en la partida: bichos, reglas del tablero, efectos temporales,
 * y en el futuro amuletos o lo que sea. El motor no sabe qué es cada uno; solo les pregunta.
 */
interface GameElement {
    val id: ElementId

    /** Reacciona a lo que ha pasado. Para cambiar cosas, encola acciones con [GameContext.enqueue]. */
    fun onEvent(event: GameEvent, ctx: GameContext) {}

    /** Cambia cualquier valor consultado (movimientos, contenedores, costes, reglas...). */
    fun modify(query: Query<*>, value: Any?, ctx: GameContext): Any? = value

    /** Ve una acción antes de que ocurra: puede devolverla igual, sustituirla o anularla (null). */
    fun intercept(action: Action, ctx: GameContext): Action? = action

    /** Botones que este elemento ofrece ahora mismo a [player]. */
    fun options(player: PlayerId, ctx: GameContext): List<GameOption> = emptyList()

    /** El jugador ha pulsado una de las opciones que ofrecimos (ya validada y activa). */
    fun useOption(player: PlayerId, key: String, ctx: GameContext) {}
}

data class GameOption(
    val source: ElementId,
    val key: String,
    val label: String,
    val description: String = "",
    val enabled: Boolean = true,
)
