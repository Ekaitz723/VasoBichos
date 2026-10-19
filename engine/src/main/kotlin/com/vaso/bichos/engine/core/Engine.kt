package com.vaso.bichos.engine.core

import com.vaso.bichos.engine.board.Board
import com.vaso.bichos.engine.board.Pos
import com.vaso.bichos.engine.champion.ChampionSetup
import com.vaso.bichos.engine.content.Content

/**
 * El motor. Recibe comandos, los convierte en acciones y lo resuelve todo hasta que la partida
 * queda estable: después de CADA acción se reacciona a sus eventos, y cada vez que la cola se
 * vacía se vuelve a mirar el tablero (huecos, combinaciones) y el turno. Así ninguna carambola
 * se escapa, la provoque quien la provoque.
 */
class Engine(config: GameConfig, content: Content) {
    val state: GameState
    private val ctx = GameContext(this)

    private val queue = ArrayDeque<Action>()
    private val pendingEvents = ArrayDeque<GameEvent>()
    private val log = mutableListOf<GameEvent>()
    private var reactions = mutableListOf<Action>()
    private var followUps = mutableListOf<Action>()
    private var dispatching = false

    init {
        val mode = content.mode(config.modeId)
        validate(config, mode, content)
        state = GameState(
            mode = mode,
            rng = Rng(config.seed),
            board = Board(mode.boardWidth, mode.boardHeight),
            players = config.players.mapIndexed { i, p -> PlayerState(PlayerId(i), p.name) },
        )
        state.elements += mode.createRules(state)
        config.players.forEachIndexed { i, setup ->
            for (pick in setup.champions) {
                val definition = content.champion(pick.definitionId)
                state.elements += definition.create(ChampionSetup(state.newElementId(), PlayerId(i), pick.color))
            }
        }
        fillWithoutMatches()
        queue += StartGame()
        queue += BeginTurn(state.players.first().id)
        run()
    }

    /** Eventos ocurridos desde la última llamada (para animar). */
    fun drainEvents(): List<GameEvent> = log.toList().also { log.clear() }

    /** Todos los botones que [player] puede ver ahora mismo, de todos los elementos. */
    fun options(player: PlayerId): List<GameOption> =
        if (state.isOver) emptyList() else state.elements.flatMap { it.options(player, ctx) }

    fun submit(command: Command): CommandResult {
        reject(command)?.let { return CommandResult.Rejected(it) }
        when (command) {
            is Command.Swap -> {
                queue.addLast(SpendMoves(command.player, swapCost(command.player)))
                queue.addLast(SwapTiles(command.a, command.b))
            }
            is Command.UseOption -> {
                val element = state.element(command.source)!!
                queue.addLast(Act("option:${command.key}") { element.useOption(command.player, command.key, it) })
            }
            is Command.Answer -> {
                val decision = state.pendingDecision!!
                val choice = decision.choices.first { it.key == command.choice }
                state.pendingDecision = null
                queue.addFirst(Act("answer:${choice.key}") {
                    it.emit(Events.DecisionAnswered(command.player, choice.key))
                    decision.onChoose(choice, it)
                })
            }
        }
        run()
        return CommandResult.Accepted(drainEvents())
    }

    private fun reject(command: Command): String? {
        if (state.isOver) return "La partida ha terminado"
        val pending = state.pendingDecision
        if (command is Command.Answer) {
            if (pending == null) return "No hay ninguna pregunta pendiente"
            if (pending.player != command.player) return "La pregunta no es para este jugador"
            if (pending.choices.none { it.key == command.choice }) return "Opción no válida"
            return null
        }
        if (pending != null) return "Hay una pregunta pendiente"
        return when (command) {
            is Command.Swap -> rejectSwap(command)
            is Command.UseOption -> {
                val element = state.element(command.source) ?: return "Ese elemento no existe"
                val option = element.options(command.player, ctx).firstOrNull { it.key == command.key }
                when {
                    option == null -> "Esa opción no está disponible"
                    !option.enabled -> "Esa opción no se puede usar ahora"
                    else -> null
                }
            }
            is Command.Answer -> null
        }
    }

    private fun rejectSwap(command: Command.Swap): String? {
        val board = state.board
        return when {
            !canAct(command.player) -> "No es tu turno"
            command.a !in board || command.b !in board -> "Fuera del tablero"
            board[command.a] == null || board[command.b] == null -> "Casilla vacía"
            state.player(command.player).moves < swapCost(command.player) -> "No te quedan movimientos"
            !query(Q.CanSwap(command.player, command.a, command.b), state.mode.baseCanSwap(command.a, command.b, state)) ->
                "Ese intercambio no está permitido"
            else -> null
        }
    }

    private fun swapCost(player: PlayerId) = query(Q.SwapCost(player), 1)

    // ---------- Resolución ----------

    private fun run() {
        var steps = 0
        while (!state.isOver && state.pendingDecision == null) {
            check(++steps < MAX_STEPS) { "La resolución no termina: posible bucle infinito entre elementos" }
            val next = queue.removeFirstOrNull() ?: checkpoint() ?: break
            execute(next)
        }
    }

    /** Con la cola vacía: ¿hay que hacer algo más antes de esperar al jugador? */
    private fun checkpoint(): Action? {
        val board = state.board
        if (board.emptyPositions().isNotEmpty()) return Settle()
        val groups = query(Q.Finder, state.mode.matchFinder).find(board)
        if (groups.isNotEmpty()) return ResolveMatches(groups)
        val turn = state.turn
        if (turn.advancePending) return AdvanceTurn()
        if (turn.ghost) return EndTurn()
        val player = turn.current
        if (state.player(player).moves <= 0 && query(Q.AutoEndTurn(player), true)) return EndTurn()
        return null
    }

    private fun execute(action: Action) {
        var current: Action = action
        for (element in state.elements.toList()) {
            if (element !in state.elements) continue
            current = element.intercept(current, ctx) ?: return
        }
        reactions = mutableListOf()
        followUps = mutableListOf()
        current.execute(ctx)
        dispatchEvents()
        val next = reactions + followUps
        for (a in next.asReversed()) queue.addFirst(a)
    }

    private fun dispatchEvents() {
        dispatching = true
        try {
            while (pendingEvents.isNotEmpty()) {
                val event = pendingEvents.removeFirst()
                for (element in state.elements.toList()) {
                    if (element in state.elements) element.onEvent(event, ctx)
                }
            }
        } finally {
            dispatching = false
        }
    }

    internal fun emit(event: GameEvent) {
        log += event
        pendingEvents += event
    }

    internal fun enqueue(action: Action) {
        if (dispatching) reactions += action else followUps += action
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> query(query: Query<T>, base: T): T {
        var value: Any? = base
        for (element in state.elements.toList()) value = element.modify(query, value, ctx)
        return value as T
    }

    internal fun canAct(player: PlayerId): Boolean {
        val turn = state.turn
        return !state.isOver && state.pendingDecision == null && !turn.ghost &&
            !turn.advancePending && turn.current == player
    }

    // ---------- Preparación ----------

    /** Llena el tablero inicial sin combinaciones hechas. */
    private fun fillWithoutMatches() {
        val board = state.board
        val spawner = query(Q.Spawner, state.mode.spawner)
        for (p in board.positions) {
            var tries = 0
            do {
                board[p] = spawner.spawn(state, p)
            } while (closesLine(board, p) && ++tries < MAX_FILL_TRIES)
        }
    }

    private fun closesLine(board: Board, p: Pos): Boolean {
        val key = board[p]?.matchKey ?: return false
        fun same(dc: Int, dr: Int) = (1..2).all { board[Pos(p.col - dc * it, p.row - dr * it)]?.matchKey == key }
        return same(1, 0) || same(0, 1)
    }

    private fun validate(config: GameConfig, mode: GameMode, content: Content) {
        require(config.players.size in mode.playerCount) {
            "${mode.name} admite ${mode.playerCount} jugadores, no ${config.players.size}"
        }
        for (player in config.players) {
            require(player.champions.size == mode.championsPerPlayer) {
                "${player.name} debe llevar ${mode.championsPerPlayer} bichos"
            }
            val colors = player.champions.map { it.color }
            require(colors.toSet().size == colors.size) { "${player.name} repite color" }
            for (pick in player.champions) {
                require(pick.color.index in 0 until mode.colorCount) { "Color ${pick.color.index} no existe" }
                val allowed = content.champion(pick.definitionId).allowedColors
                require(allowed == null || pick.color in allowed) {
                    "${pick.definitionId} no puede ser del color ${pick.color.index}"
                }
            }
        }
    }

    private companion object {
        const val MAX_STEPS = 100_000
        const val MAX_FILL_TRIES = 50
    }
}
