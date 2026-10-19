package com.vaso.bichos.engine.content

import com.vaso.bichos.engine.champion.ChampionDefinition
import com.vaso.bichos.engine.content.basic.BasicMode
import com.vaso.bichos.engine.content.vanilla.Vanilla
import com.vaso.bichos.engine.core.GameMode

/** Catálogo de todo lo que se puede usar en una partida. */
class Content(modes: List<GameMode>, champions: List<ChampionDefinition>) {
    val modes: Map<String, GameMode> = modes.associateBy { it.id }
    val champions: Map<String, ChampionDefinition> = champions.associateBy { it.id }

    fun mode(id: String) = requireNotNull(modes[id]) { "Modo desconocido: $id" }
    fun champion(id: String) = requireNotNull(champions[id]) { "Bicho desconocido: $id" }

    companion object {
        /** Aquí se registra cada bicho y cada tablero nuevo. */
        val default = Content(
            modes = listOf(BasicMode.mode),
            champions = listOf(Vanilla.definition),
        )
    }
}
