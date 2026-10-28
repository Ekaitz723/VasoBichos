package com.vaso.bichos.ui.game

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vaso.bichos.engine.champion.Champion
import com.vaso.bichos.engine.core.Command
import com.vaso.bichos.engine.core.ElementId
import com.vaso.bichos.engine.core.GameOption
import com.vaso.bichos.engine.core.GameResult
import com.vaso.bichos.engine.core.PlayerId
import com.vaso.bichos.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: AppViewModel) {
    val session = vm.session ?: return
    val display = vm.display ?: return
    val engine = session.engine
    val state = engine.state
    vm.version // recomponer cuando cambia el motor

    display.haptic = rememberHaptics()

    val viewer = session.viewer()
    val current = state.turn.current
    val enemies = state.players.map { it.id }.filter { it != viewer }
    // Rival abierto en grande; null = todos en columnas pequeñas.
    var expandedEnemy by remember { mutableStateOf<PlayerId?>(null) }
    val openEnemy = expandedEnemy?.takeIf { it in enemies }
    PlatformBackHandler(enabled = openEnemy != null) { expandedEnemy = null }

    var sheetFor by remember { mutableStateOf<ElementId?>(null) }
    val options = if (vm.queued == 0) engine.options(viewer) else emptyList()
    val championIds = state.champions().map { it.id }.toSet()
    val globalOptions = options.filter { it.source !in championIds }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            TopStrip(vm, viewer, current)
            RivalColumns(vm, enemies, openEnemy) { expandedEnemy = if (it == openEnemy) null else it }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                        BannerText(display.banner)
                    }
                    PlayerSide(vm, viewer, isEnemy = false, onChampion = { sheetFor = it })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        for (option in globalOptions) {
                            Button(onClick = { vm.submit(Command.UseOption(viewer, option.source, option.key)) }, enabled = option.enabled && vm.canPlay()) {
                                Text(option.label)
                            }
                        }
                        if (globalOptions.isEmpty()) HintText(vm, viewer)
                    }
                    BoardView(
                        display = display,
                        columns = state.board.width,
                        rows = state.board.height,
                        enabled = vm.canPlay(),
                        onSwap = { a, b -> vm.submit(Command.Swap(viewer, a, b)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    )
                }
                // El rival en grande se despliega encima, pegado a las columnas. Tocar fuera lo cierra.
                if (openEnemy != null) {
                    Box(Modifier.fillMaxSize().background(Color(0x99000000)).noRippleClickable { expandedEnemy = null })
                }
                var lastOpen by remember { mutableStateOf(openEnemy) }
                if (openEnemy != null) lastOpen = openEnemy
                androidx.compose.animation.AnimatedVisibility(
                    visible = openEnemy != null,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    lastOpen?.let { enemy ->
                        PlayerSide(
                            vm,
                            enemy,
                            isEnemy = true,
                            onChampion = { sheetFor = it },
                            onClose = { expandedEnemy = null },
                            modifier = Modifier.padding(top = 2.dp).noRippleClickable {},
                        )
                    }
                }
            }
        }

        sheetFor?.let { id ->
            val champion = state.champion(id)
            if (champion != null) {
                ModalBottomSheet(onDismissRequest = { sheetFor = null }, containerColor = MaterialTheme.colorScheme.surface) {
                    ChampionMenu(vm, champion, options.filter { it.source == id }) { option ->
                        sheetFor = null
                        vm.submit(Command.UseOption(viewer, option.source, option.key))
                    }
                }
            }
        }

        val decision = state.pendingDecision
        if (decision != null && decision.player == viewer && vm.queued == 0) {
            Dialog(onDismissRequest = {}) {
                RpgPanel {
                    Text(decision.prompt, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    for (choice in decision.choices) {
                        RpgMenuItem(choice.label, enabled = true) { vm.submit(Command.Answer(viewer, choice.key)) }
                    }
                }
            }
        }

        val result = state.result
        if (result != null && vm.queued == 0) ResultOverlay(vm, result)
    }
}

@Composable
private fun TopStrip(vm: AppViewModel, viewer: PlayerId, current: PlayerId) {
    val session = vm.session ?: return
    val state = session.engine.state
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (session.me == null) "MODO PRUEBA · controlas a todos" else "Sala ${session.roomCode}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (current == viewer) "Tu turno" else "Turno de ${state.player(current).name}",
            style = MaterialTheme.typography.labelLarge,
            color = if (current == viewer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = vm::leaveGame) { Text("Salir") }
    }
}

/** Una columna pequeña por rival, todos a la vez. Al tocar una se abre en grande. */
@Composable
private fun RivalColumns(vm: AppViewModel, enemies: List<PlayerId>, open: PlayerId?, onPick: (PlayerId) -> Unit) {
    val session = vm.session ?: return
    val display = vm.display ?: return
    val engine = session.engine
    val state = engine.state
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (enemy in enemies) {
            val selected = enemy == open
            val pd = display.players[enemy.index]
            Column(
                Modifier
                    .weight(1f)
                    .turnAura(session.auras[enemy.index].takeIf { enemy == state.turn.current }, 14.dp)
                    .clip14()
                    .background(if (selected) Color(0xFF3A2A44) else Color(0xFF2A1E2E))
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onPick(enemy) }
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    state.player(enemy).name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                for (champion in state.championsOf(enemy)) MiniChampion(vm, champion)
                MiniPips(pd.moves, engine.moveContainers(enemy), MaterialTheme.colorScheme.secondary, square = true)
                MiniPips(pd.orbs, engine.orbCapacity(enemy), StonePalette.orbColor, square = false)
            }
        }
    }
}

/** Un bicho en la columna pequeña: su forma, vida y carga. */
@Composable
private fun MiniChampion(vm: AppViewModel, champion: Champion) {
    val display = vm.display ?: return
    val engine = vm.session?.engine ?: return
    val cd = display.champions.getValue(champion.id)
    val maxHp = engine.maxHp(champion.id).coerceAtLeast(1)
    val cost = engine.abilityCost(champion.id).coerceAtLeast(1)
    val shake = rememberHitShake(cd.hits)
    val hpFraction by animateFloatAsState(cd.hp.toFloat() / maxHp, tween(350), label = "hp")
    // Altura fija: el número flotante sobresale sin estirar la columna.
    Box(Modifier.height(16.dp)) {
        Row(
            Modifier.graphicsLayer { translationX = shake.value * 0.6f }.alpha(if (cd.alive) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorGlyph(champion.color.index, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Bar(hpFraction, if (hpFraction > 0.3f) HpGood else HpLow, Color(0xFF3A1F27), 5.dp)
                Bar(cd.charge.toFloat() / cost, StonePalette.color(champion.color.index), Color(0xFF34304A), 3.dp)
            }
        }
        for (popup in display.popups.filter { it.target == champion.id }) {
            androidx.compose.runtime.key(popup.id) { FloatingText(popup, Modifier.align(Alignment.Center), small = true) }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, track: Color, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
private fun MiniPips(filled: Int, total: Int, color: Color, square: Boolean) {
    Row {
        repeat(maxOf(total, filled)) { i ->
            val shape = RoundedCornerShape(if (square) 1.5.dp else 1.dp)
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .size(6.dp)
                    .graphicsLayer { rotationZ = if (square) 0f else 45f }
                    .clip(shape)
                    .background(if (i < filled) color else Color.Transparent)
                    .border(0.75.dp, color.copy(alpha = 0.6f), shape),
            )
        }
    }
}

@Composable
private fun PlayerSide(
    vm: AppViewModel,
    player: PlayerId,
    isEnemy: Boolean,
    onChampion: (ElementId) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val session = vm.session ?: return
    val display = vm.display ?: return
    val engine = session.engine
    val p = engine.state.player(player)
    val pd = display.players[player.index]
    Column(
        modifier
            .fillMaxWidth()
            .turnAura(session.auras[player.index].takeIf { player == engine.state.turn.current }, 14.dp)
            .clip14()
            .background(if (isEnemy) Color(0xFF2A1E2E) else Color(0xFF1C2238))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isEnemy) p.name else "${p.name} (tú)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Pips("Mov", pd.moves, engine.moveContainers(player), MaterialTheme.colorScheme.secondary, square = true)
            Spacer(Modifier.width(10.dp))
            Pips("Orbes", pd.orbs, engine.orbCapacity(player), StonePalette.orbColor, square = false)
            if (onClose != null) {
                Text(
                    "✕",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClose).padding(horizontal = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (champion in engine.state.championsOf(player)) {
                ChampionCard(vm, champion, Modifier.weight(1f)) { onChampion(champion.id) }
            }
        }
    }
}

@Composable
private fun Pips(label: String, filled: Int, total: Int, color: Color, square: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        repeat(maxOf(total, filled)) { i ->
            val on = i < filled
            Box(
                Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(10.dp)
                    .graphicsLayer { rotationZ = if (square) 0f else 45f }
                    .clip(RoundedCornerShape(if (square) 3.dp else 2.dp))
                    .background(if (on) color else Color.Transparent)
                    .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(if (square) 3.dp else 2.dp)),
            )
        }
    }
}

@Composable
private fun ChampionCard(vm: AppViewModel, champion: Champion, modifier: Modifier, onClick: () -> Unit) {
    val display = vm.display ?: return
    val engine = vm.session?.engine ?: return
    val cd = display.champions.getValue(champion.id)
    val maxHp = engine.maxHp(champion.id).coerceAtLeast(1)
    val cost = engine.abilityCost(champion.id)

    val shake = rememberHitShake(cd.hits)
    val hpFraction by animateFloatAsState(cd.hp.toFloat() / maxHp, tween(350), label = "hp")

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value }
                .clip14()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, StonePalette.color(champion.color.index).copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(8.dp)
                .alpha(if (cd.alive) 1f else 0.45f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorGlyph(champion.color.index, Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
                Text(champion.definition.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                if (cd.phase > 1) {
                    Text(
                        "F${cd.phase}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 4.dp),
                    )
                }
                if (!cd.alive) Text(" KO", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF3A1F27))) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(hpFraction.coerceIn(0f, 1f))
                        .background(if (hpFraction > 0.3f) HpGood else HpLow),
                )
            }
            Text("${cd.hp}/$maxHp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                repeat(maxOf(cost, cd.charge)) { i ->
                    Box(
                        Modifier
                            .padding(end = 3.dp)
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < cd.charge) StonePalette.color(champion.color.index) else Color(0xFF34304A)),
                    )
                }
            }
        }
        for (popup in display.popups.filter { it.target == champion.id }) {
            androidx.compose.runtime.key(popup.id) { FloatingText(popup, Modifier.align(Alignment.Center)) }
        }
    }
}

@Composable
private fun FloatingText(popup: Popup, modifier: Modifier, small: Boolean = false) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900)) }
    val color = when (popup.kind) {
        PopupKind.DAMAGE -> Color(0xFFFF5A5A)
        PopupKind.HEAL -> Color(0xFF4CD964)
        PopupKind.CHARGE -> Color(0xFFFFE27A)
        PopupKind.INFO -> Color.White
    }
    Text(
        popup.text,
        color = color,
        fontSize = if (small) 15.sp else 22.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier.graphicsLayer {
            val p = progress.value
            translationY = -60f * p
            val pop = if (p < 0.15f) 0.6f + p / 0.15f * 0.6f else 1.2f - (p - 0.15f) * 0.25f
            scaleX = pop
            scaleY = pop
            alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
        },
    )
}

@Composable
private fun BannerText(banner: String?) {
    var last by remember { mutableStateOf("") }
    if (banner != null) last = banner
    AnimatedVisibility(
        visible = banner != null,
        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = fadeOut(tween(150)),
    ) {
        Text(
            last,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
    if (banner == null) Text("VS", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Black)
}

@Composable
private fun HintText(vm: AppViewModel, viewer: PlayerId) {
    val state = vm.session?.engine?.state ?: return
    val decision = state.pendingDecision
    val text = when {
        decision != null && decision.player != viewer -> "Esperando a ${state.player(decision.player).name}…"
        state.turn.current != viewer -> "Esperando a ${state.player(state.turn.current).name}…"
        else -> "Desliza o toca dos fichas vecinas · toca un bicho para su menú"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun ChampionMenu(vm: AppViewModel, champion: Champion, options: List<GameOption>, onUse: (GameOption) -> Unit) {
    val engine = vm.session?.engine ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorGlyph(champion.color.index, Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(champion.definition.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${StonePalette.name(champion.color.index)} · de ${engine.state.player(champion.owner).name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(champion.definition.description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Vida ${champion.hp}/${engine.maxHp(champion.id)} · Carga ${champion.charge}/${engine.abilityCost(champion.id)} · Fase ${champion.phase}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("ACCIONES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            RpgPanel {
                for (option in options) {
                    RpgMenuItem(option.label, option.description, enabled = option.enabled && vm.canPlay()) { onUse(option) }
                }
            }
        }
    }
}

@Composable
fun RpgPanel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF15122A))
            .border(2.dp, Color(0xFFE9E2FF), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) { content() }
}

@Composable
fun RpgMenuItem(label: String, description: String = "", enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text("▶", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (description.isNotEmpty()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResultOverlay(vm: AppViewModel, result: GameResult) {
    val state = vm.session?.engine?.state ?: return
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        if (result is GameResult.Winner) {
            ConfettiBurst(StonePalette.colors, Modifier.fillMaxSize())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                when (result) {
                    is GameResult.Winner -> "¡Gana ${state.player(result.player).name}!"
                    GameResult.Draw -> "Empate"
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = vm::leaveGame) { Text("Volver al inicio") }
        }
    }
}

/** La carta tiembla cada vez que [hits] cambia. */
@Composable
private fun rememberHitShake(hits: Int): Animatable<Float, *> {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(hits) {
        if (hits > 0) {
            shake.snapTo(10f)
            shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = Spring.StiffnessHigh))
        }
    }
    return shake
}

private val HpGood = Color(0xFF4CD964)
private val HpLow = Color(0xFFFF5A5A)

private fun Modifier.clip14() = this.clip(RoundedCornerShape(14.dp))

private fun Modifier.noRippleClickable(onClick: () -> Unit) = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
