package com.vaso.bichos.net

import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.core.Command
import com.vaso.bichos.engine.core.ElementId
import com.vaso.bichos.engine.core.PlayerId

/** Comandos como texto corto para guardarlos en la base de datos. */
object CommandCodec {
    fun encode(command: Command): String = when (command) {
        is Command.Swap -> "s|${command.player.index}|${command.a.col},${command.a.row}|${command.b.col},${command.b.row}"
        is Command.UseOption -> "o|${command.player.index}|${command.source.value}|${command.key}"
        is Command.Answer -> "a|${command.player.index}|${command.choice}"
    }

    fun decode(text: String): Command? = runCatching {
        val parts = text.split("|", limit = 4)
        val player = PlayerId(parts[1].toInt())
        when (parts[0]) {
            "s" -> Command.Swap(player, pos(parts[2]), pos(parts[3]))
            "o" -> Command.UseOption(player, ElementId(parts[2].toInt()), parts[3])
            "a" -> Command.Answer(player, text.split("|", limit = 3)[2])
            else -> null
        }
    }.getOrNull()

    private fun pos(text: String): Pos {
        val (c, r) = text.split(",").map { it.toInt() }
        return Pos(c, r)
    }
}
