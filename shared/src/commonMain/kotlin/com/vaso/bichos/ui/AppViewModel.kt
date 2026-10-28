package com.vaso.bichos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaso.bichos.Platform
import com.vaso.bichos.engine.board.Direction
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.content.Content
import com.vaso.bichos.engine.content.basic.BasicMode
import com.vaso.bichos.engine.core.ChampionPick
import com.vaso.bichos.engine.core.Command
import com.vaso.bichos.engine.core.CommandResult
import com.vaso.bichos.engine.core.Engine
import com.vaso.bichos.engine.core.GameConfig
import com.vaso.bichos.engine.core.GameEvent
import com.vaso.bichos.engine.core.PlayerId
import com.vaso.bichos.engine.core.PlayerSetup
import com.vaso.bichos.engine.core.StoneColor
import com.vaso.bichos.net.ChampionChoice
import com.vaso.bichos.net.CommandCodec
import com.vaso.bichos.net.Room
import com.vaso.bichos.net.RoomRepository
import com.vaso.bichos.ui.game.Aura
import com.vaso.bichos.ui.game.GameDisplay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface Screen {
    data object Home : Screen
    data class Lobby(val code: String) : Screen
    data object Game : Screen
}

/** Una partida en marcha. [me] null = modo prueba: este móvil controla a todos. */
class GameSession(
    val config: GameConfig,
    val me: PlayerId?,
    val roomCode: String?,
    /** Bots que mueve este móvil (solo el anfitrión). */
    val bots: Set<PlayerId> = emptySet(),
    /** Brillo de turno de cada jugador, por índice. */
    val auras: List<Aura> = List(config.players.size) { Aura.default(it) },
) {
    val engine = Engine(config, Content.default)
    var applied = 0

    /** Desde qué jugador se mira y se toca la partida ahora mismo. */
    fun viewer(): PlayerId = me ?: (engine.state.pendingDecision?.player ?: engine.state.turn.current)
}

class AppViewModel(platform: Platform) : ViewModel() {
    private val prefs = platform.prefs
    val appVersion = platform.version
    private val repo by lazy {
        RoomRepository(platform.db).apply {
            val mode = Content.default.mode(BasicMode.ID)
            defaultPicks = (0 until mode.championsPerPlayer).joinToString(",") { "vanilla:$it" }
        }
    }

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var playerName by mutableStateOf(prefs.get(KEY_NAME).orEmpty())
        private set
    var lastRoom by mutableStateOf(prefs.get(KEY_ROOM))
        private set

    /** Brillo de turno elegido en este móvil; null = el de su asiento. */
    var aura by mutableStateOf(Aura.decode(prefs.get(KEY_AURA)))
        private set
    var message by mutableStateOf<String?>(null)
    var working by mutableStateOf(false)
        private set
    var uid by mutableStateOf<String?>(null)
        private set
    var room by mutableStateOf<Room?>(null)
        private set
    var session by mutableStateOf<GameSession?>(null)
        private set
    var display by mutableStateOf<GameDisplay?>(null)
        private set

    /** Sube cada vez que cambia el motor, para recalcular opciones y textos. */
    var version by mutableIntStateOf(0)
        private set

    /** Lotes de eventos pendientes de animar. */
    var queued by mutableIntStateOf(0)
        private set

    /** Mandamos un comando y esperamos a que vuelva de la base de datos. */
    var awaitingEcho by mutableStateOf(false)
        private set

    private val batches = Channel<List<GameEvent>>(Channel.UNLIMITED)
    private var roomJob: Job? = null
    private var commandsJob: Job? = null
    private var botJob: Job? = null

    init {
        viewModelScope.launch {
            for (batch in batches) {
                display?.play(batch)
                queued--
                version++
            }
        }
    }

    fun updateName(name: String) {
        playerName = name.take(16)
        prefs.put(KEY_NAME, playerName)
    }

    // ---------- Partida ----------

    fun canPlay(): Boolean {
        val s = session ?: return false
        return queued == 0 && !awaitingEcho && s.engine.isTurnOf(s.viewer())
    }

    fun submit(command: Command) {
        val s = session ?: return
        s.engine.check(command)?.let {
            message = it
            return
        }
        val code = s.roomCode
        if (code == null) {
            apply(s, s.applied, command)
            return
        }
        awaitingEcho = true
        viewModelScope.launch {
            try {
                if (!repo.pushCommand(code, s.applied, CommandCodec.encode(command))) awaitingEcho = false
            } catch (e: Exception) {
                awaitingEcho = false
                message = "No se pudo enviar: ${e.message}"
            }
        }
    }

    private fun apply(s: GameSession, index: Int, command: Command?) {
        if (index != s.applied) return
        s.applied++
        val result = command?.let { s.engine.submit(it) }
        awaitingEcho = false
        if (result is CommandResult.Accepted) enqueue(result.events)
        version++
    }

    private fun enqueue(events: List<GameEvent>) {
        if (events.isEmpty()) return
        queued++
        batches.trySend(events)
    }

    private fun open(s: GameSession) {
        session = s
        display = GameDisplay(s.engine, viewModelScope)
        enqueue(s.engine.drainEvents())
        screen = Screen.Game
        version++
    }

    fun startLocal() {
        val players = (0 until 3).map { i ->
            PlayerSetup(
                "Jugador ${i + 1}",
                listOf(ChampionPick("vanilla", StoneColor(i * 2)), ChampionPick("vanilla", StoneColor(i * 2 + 1))),
            )
        }
        open(GameSession(GameConfig(BasicMode.ID, Random.nextLong(), players), me = null, roomCode = null))
    }

    fun leaveGame() {
        val s = session
        commandsJob?.cancel()
        roomJob?.cancel()
        botJob?.cancel()
        val code = s?.roomCode
        if (code != null && s.engine.state.isOver) {
            if (room?.meta?.hostUid == uid) viewModelScope.launch { runCatching { repo.deleteRoom(code) } }
            forgetRoom()
        }
        session = null
        display = null
        room = null
        screen = Screen.Home
    }

    // ---------- Salas ----------

    fun createRoom() = online {
        val me = repo.signIn().also { uid = it }
        val code = repo.createRoom(me, playerName, BasicMode.ID, aura?.encode())
        enterLobby(code)
    }

    fun joinRoom(code: String) = online {
        val me = repo.signIn().also { uid = it }
        val clean = code.trim().uppercase()
        val error = repo.joinRoom(clean, me, playerName, aura?.encode())
        if (error != null) {
            message = error
            if (clean == lastRoom) forgetRoom()
        } else {
            enterLobby(clean)
        }
    }

    fun setPicks(picks: List<ChampionChoice>, ready: Boolean) {
        val code = room?.code ?: return
        val me = uid ?: return
        online { repo.setPicks(code, me, picks, ready) }
    }

    /** Se recuerda en el móvil para las próximas salas. */
    fun chooseAura(choice: Aura) {
        aura = choice
        prefs.put(KEY_AURA, choice.encode())
        val code = room?.code ?: return
        val me = uid ?: return
        online { repo.setAura(code, me, choice.encode()) }
    }

    fun startOnline() {
        val r = room ?: return
        val mode = Content.default.mode(r.meta.modeId)
        val problem = when {
            r.players.size !in mode.playerCount -> "Hacen falta entre ${mode.playerCount.first} y ${mode.playerCount.last} jugadores"
            r.players.any { !it.ready } -> "Falta gente por darle a Listo"
            boardColors(r).size < mode.minBoardColors -> "Hacen falta al menos ${mode.minBoardColors} colores en el tablero"
            else -> null
        }
        if (problem != null) {
            message = problem
            return
        }
        online { repo.startGame(r.code, r.players.map { it.uid }, Random.nextLong()) }
    }

    /** Colores que tendrá el tablero: los de los bichos elegidos más los extra. */
    fun boardColors(r: Room): Set<Int> {
        val mode = Content.default.mode(r.meta.modeId)
        val used = usedColors(r)
        val extras = r.meta.extraColors ?: (0 until mode.colorCount).toSet()
        return used + extras
    }

    fun usedColors(r: Room): Set<Int> = r.players.flatMap { p -> p.picks.map { it.color } }.toSet()

    fun toggleExtraColor(color: Int) {
        val r = room ?: return
        if (r.meta.hostUid != uid) return
        val mode = Content.default.mode(r.meta.modeId)
        val current = r.meta.extraColors ?: (0 until mode.colorCount).toSet()
        val next = if (color in current) current - color else current + color
        online { repo.setExtraColors(r.code, next) }
    }

    fun leaveLobby() {
        val r = room
        val me = uid
        roomJob?.cancel()
        room = null
        screen = Screen.Home
        forgetRoom()
        if (r != null && me != null) {
            viewModelScope.launch {
                runCatching { if (r.meta.hostUid == me) repo.deleteRoom(r.code) else repo.leaveLobby(r.code, me) }
            }
        }
    }

    private fun enterLobby(code: String) {
        lastRoom = code
        prefs.put(KEY_ROOM, code)
        screen = Screen.Lobby(code)
        roomJob?.cancel()
        roomJob = viewModelScope.launch {
            repo.observeRoom(code).collect { r ->
                room = r
                when {
                    r == null -> {
                        if (session?.engine?.state?.isOver != true) message = "La sala $code ya no existe"
                        if (screen is Screen.Lobby) {
                            screen = Screen.Home
                            forgetRoom()
                        }
                    }
                    r.meta.status == RoomRepository.STATUS_PLAYING && session == null -> startOnlineSession(r)
                }
            }
        }
    }

    private suspend fun startOnlineSession(r: Room) {
        val me = uid ?: return
        val index = r.meta.order.indexOf(me)
        if (index < 0) {
            message = "Esta partida empezó sin ti"
            return
        }
        val byUid = r.players.associateBy { it.uid }
        if (r.meta.order.any { it !in byUid }) return // aún no han llegado todos los jugadores
        val setups = r.meta.order.map { u ->
            val p = byUid.getValue(u)
            PlayerSetup(p.name, p.picks.map { ChampionPick(it.definitionId, StoneColor(it.color)) })
        }
        val extras = r.meta.extraColors?.map { StoneColor(it) }?.toSet()
        val bots = if (r.meta.hostUid == me) {
            r.meta.order.withIndex().filter { byUid.getValue(it.value).bot }.map { PlayerId(it.index) }.toSet()
        } else {
            emptySet()
        }
        val auras = r.meta.order.mapIndexed { seat, u -> Aura.decode(byUid.getValue(u).aura) ?: Aura.default(seat) }
        val s = GameSession(GameConfig(r.meta.modeId, r.meta.seed, setups, extras), PlayerId(index), r.code, bots, auras)
        // Reconexión: se aplica todo lo que ya pasó sin animarlo.
        for (text in repo.loadCommands(r.code)) {
            s.applied++
            CommandCodec.decode(text)?.let { s.engine.submit(it) }
        }
        s.engine.drainEvents()
        session = s
        display = GameDisplay(s.engine, viewModelScope)
        screen = Screen.Game
        version++
        commandsJob?.cancel()
        commandsJob = viewModelScope.launch {
            repo.observeCommands(r.code, s.applied).collect { (i, text) -> apply(s, i, CommandCodec.decode(text)) }
        }
        startBots(s)
    }

    // ---------- Bots de prueba (temporal) ----------

    fun addBot() {
        val r = room ?: return
        val mode = Content.default.mode(r.meta.modeId)
        val used = usedColors(r)
        val free = (0 until mode.colorCount).filter { it !in used }
        val colors = (free + (0 until mode.colorCount).filter { it !in free }).take(mode.championsPerPlayer)
        val name = "Bot ${r.players.count { it.bot } + 1}"
        online { repo.addBot(r.code, name, colors.map { ChampionChoice("vanilla", it) }) }
    }

    fun removeBot(botUid: String) {
        val code = room?.code ?: return
        online { repo.leaveLobby(code, botUid) }
    }

    /** Cuando le toca a un bot, hace un intercambio al azar (o elige la primera opción si le preguntan). */
    private fun startBots(s: GameSession) {
        if (s.bots.isEmpty()) return
        botJob?.cancel()
        botJob = viewModelScope.launch {
            while (session === s && !s.engine.state.isOver) {
                delay(BOT_THINK_MS)
                if (queued > 0 || awaitingEcho) continue
                botCommand(s)?.let { submit(it) }
            }
        }
    }

    private fun botCommand(s: GameSession): Command? {
        val state = s.engine.state
        state.pendingDecision?.let { d ->
            return if (d.player in s.bots) Command.Answer(d.player, d.choices.first().key) else null
        }
        val player = state.turn.current
        if (player !in s.bots || !s.engine.isTurnOf(player)) return null
        repeat(100) {
            val a = Pos(Random.nextInt(state.board.width), Random.nextInt(state.board.height))
            val swap = Command.Swap(player, a, a + Direction.entries.random())
            if (s.engine.check(swap) == null) return swap
        }
        return null
    }

    private fun forgetRoom() {
        lastRoom = null
        prefs.put(KEY_ROOM, null)
    }

    private fun online(block: suspend () -> Unit) {
        if (working) return
        working = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                message = "Error de conexión: ${e.message}"
            } finally {
                working = false
            }
        }
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_ROOM = "room"
        const val KEY_AURA = "aura"
        const val BOT_THINK_MS = 900L
    }
}
