package com.vaso.bichos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vaso.bichos.ui.game.ColorGlyph

@Composable
fun HomeScreen(vm: AppViewModel) {
    var code by remember { mutableStateOf("") }
    val hasName = vm.playerName.isNotBlank()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(7) { ColorGlyph(it, Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Text("VasoBichos", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = vm.playerName,
            onValueChange = vm::updateName,
            label = { Text("Tu nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        vm.lastRoom?.let { last ->
            Button(onClick = { vm.joinRoom(last) }, enabled = hasName && !vm.working, modifier = Modifier.fillMaxWidth()) {
                Text("Volver a la sala $last")
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(onClick = vm::createRoom, enabled = hasName && !vm.working, modifier = Modifier.fillMaxWidth()) {
            Text("Crear sala")
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(4) },
                label = { Text("Código") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.joinRoom(code) }, enabled = hasName && code.length == 4 && !vm.working) {
                Text("Unirse")
            }
        }
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = vm::startLocal) {
            Text("Partida de prueba en este móvil (3 jugadores)")
        }
        if (vm.working) Text("Conectando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
