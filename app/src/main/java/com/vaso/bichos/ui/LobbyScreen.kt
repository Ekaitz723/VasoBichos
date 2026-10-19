package com.vaso.bichos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaso.bichos.engine.content.Content
import com.vaso.bichos.net.ChampionChoice
import com.vaso.bichos.ui.game.ColorGlyph
import com.vaso.bichos.ui.game.StonePalette

@Composable
fun LobbyScreen(vm: AppViewModel, code: String) {
    val room = vm.room
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Sala", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(code, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text("Pásales este código a tus amigos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        if (room == null) {
            Text("Cargando…")
            return@Column
        }
        val mode = Content.default.mode(room.meta.modeId)
        val me = room.players.firstOrNull { it.uid == vm.uid }
        val isHost = room.meta.hostUid == vm.uid

        Text("Jugadores (${room.players.size}/${mode.playerCount.last})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        for (p in room.players) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    (if (p.uid == room.meta.hostUid) "★ " else "") + p.name + if (p.uid == vm.uid) " (tú)" else "",
                    modifier = Modifier.weight(1f),
                )
                for (pick in p.picks) ColorGlyph(pick.color, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (p.ready) "Listo" else "Eligiendo…",
                    color = if (p.ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (me != null) {
            Spacer(Modifier.height(20.dp))
            Text("Tus bichos", style = MaterialTheme.typography.titleMedium)
            Text("Cada uno de un color distinto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val picks = me.picks.takeIf { it.size == mode.championsPerPlayer }
                ?: List(mode.championsPerPlayer) { ChampionChoice("vanilla", it) }
            picks.forEachIndexed { slot, pick ->
                Spacer(Modifier.height(10.dp))
                ChampionSlot(
                    pick = pick,
                    takenColors = picks.filterIndexed { i, _ -> i != slot }.map { it.color }.toSet(),
                    colorCount = mode.colorCount,
                    enabled = !me.ready,
                    onChange = { newPick -> vm.setPicks(picks.toMutableList().also { it[slot] = newPick }, ready = false) },
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.setPicks(picks, ready = !me.ready) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.working,
            ) { Text(if (me.ready) "Cambiar bichos" else "¡Listo!") }
        }

        Spacer(Modifier.height(12.dp))
        if (isHost) {
            Button(
                onClick = vm::startOnline,
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.working && room.players.size >= mode.playerCount.first && room.players.all { it.ready },
            ) { Text("Empezar partida") }
        } else {
            Text("Esperando a que el anfitrión empiece…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::leaveLobby, modifier = Modifier.fillMaxWidth()) {
            Text(if (isHost) "Cerrar sala" else "Salir de la sala")
        }
    }
}

@Composable
private fun ChampionSlot(
    pick: ChampionChoice,
    takenColors: Set<Int>,
    colorCount: Int,
    enabled: Boolean,
    onChange: (ChampionChoice) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
            .alpha(if (enabled) 1f else 0.6f),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (def in Content.default.champions.values) {
                FilterChip(
                    selected = def.id == pick.definitionId,
                    onClick = { if (enabled) onChange(pick.copy(definitionId = def.id)) },
                    label = { Text(def.name) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (color in 0 until colorCount) {
                val taken = color in takenColors
                val selected = color == pick.color
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                        .clickable(enabled = enabled && !taken) { onChange(pick.copy(color = color)) }
                        .alpha(if (taken) 0.25f else 1f)
                        .padding(4.dp),
                ) { ColorGlyph(color, Modifier.fillMaxSize()) }
            }
        }
        Text(
            StonePalette.name(pick.color),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
