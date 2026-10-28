package com.vaso.bichos.engine.content.vanilla

import com.vaso.bichos.engine.champion.Champion
import com.vaso.bichos.engine.champion.ChampionDefinition
import com.vaso.bichos.engine.champion.ChampionSetup
import com.vaso.bichos.engine.core.AddCharge
import com.vaso.bichos.engine.core.ChangeOrbs
import com.vaso.bichos.engine.core.Choice
import com.vaso.bichos.engine.core.DealDamage
import com.vaso.bichos.engine.core.Decision
import com.vaso.bichos.engine.core.ElementId
import com.vaso.bichos.engine.core.Events
import com.vaso.bichos.engine.core.GameContext
import com.vaso.bichos.engine.core.GameOption
import com.vaso.bichos.engine.core.PlayerId
import com.vaso.bichos.engine.core.Revive
import com.vaso.bichos.engine.core.SpendMoves

/**
 * El primer bicho. 50 de vida, carga 3 y al llenarse hace 5 de daño a todos los enemigos
 * (10 en fase 2). Lo que sobra al cargar se pierde. Si le curan estando a tope, gana 1 piedra.
 */
class Vanilla(setup: ChampionSetup) : Champion(setup, definition, baseMaxHp = 50, baseAbilityCost = 3) {

    override fun receiveCharge(amount: Int, ctx: GameContext) {
        if (!isAlive()) {
            ctx.emit(Events.ChargeLost(id, amount))
            return
        }
        super.receiveCharge(amount, ctx)
        val cost = abilityCost(ctx)
        if (charge >= cost) {
            val excess = charge - cost
            spendCharge(charge, ctx)
            if (excess > 0) ctx.emit(Events.ChargeLost(id, excess))
            ctx.enqueue("vanilla.ability") { useAbility(it) }
        }
    }

    private fun useAbility(ctx: GameContext) {
        ctx.emit(Events.AbilityActivated(id, ABILITY_NAME))
        val damage = if (phase >= 2) PHASE_2_DAMAGE else BASE_DAMAGE
        for (enemy in enemies(ctx)) ctx.enqueue(DealDamage(id, enemy.id, damage))
    }

    override fun receiveHeal(amount: Int, source: ElementId?, ctx: GameContext) {
        if (!isAlive() || amount <= 0) return
        if (hp >= maxHp(ctx)) ctx.enqueue(AddCharge(id, 1)) else super.receiveHeal(amount, source, ctx)
    }

    override fun options(player: PlayerId, ctx: GameContext): List<GameOption> {
        if (player != owner) return emptyList()
        val canAct = ctx.canAct(owner) && ownerState(ctx).moves >= 1
        val orbs = ownerState(ctx).orbs
        if (!isAlive()) {
            return listOf(
                GameOption(id, REVIVE_3, "Revivir (3 orbes)", "Vuelve con 20 de vida. Gasta 1 movimiento.", canAct && orbs >= 3),
                GameOption(id, REVIVE_4, "Revivir (4 orbes)", "Vuelve con 30 de vida. Gasta 1 movimiento.", canAct && orbs >= 4),
            )
        }
        val result = mutableListOf<GameOption>()
        if (phase == 1) {
            result += GameOption(
                id, PHASE_2, "Fase 2 (4 orbes)",
                "Su habilidad pasa a hacer $PHASE_2_DAMAGE de daño para siempre. Gasta 1 movimiento.",
                canAct && orbs >= 4,
            )
        }
        result += GameOption(
            id, CONVERT, "Orbes → piedras",
            "Cada orbe se convierte en 1 piedra de su color. Gasta 1 movimiento.",
            canAct && orbs >= 1,
        )
        return result
    }

    override fun useOption(player: PlayerId, key: String, ctx: GameContext) {
        when (key) {
            REVIVE_3 -> payAndThen(3, ctx) { it.enqueue(Revive(id, 20)) }
            REVIVE_4 -> payAndThen(4, ctx) { it.enqueue(Revive(id, 30)) }
            PHASE_2 -> payAndThen(4, ctx) { changePhase(2, it) }
            CONVERT -> {
                val orbs = ownerState(ctx).orbs
                ctx.ask(
                    Decision(
                        player = owner,
                        prompt = "¿Cuántos orbes conviertes en piedras?",
                        choices = (1..orbs).map { Choice("$it", "$it") } + Choice(CANCEL, "Cancelar"),
                    ) { choice, c ->
                        if (choice.key != CANCEL) {
                            val n = choice.key.toInt()
                            payAndThen(n, c) { it.enqueue(AddCharge(id, n)) }
                        }
                    },
                )
            }
        }
    }

    /** Gasta 1 movimiento y [orbs] orbes, y luego hace [then]. */
    private fun payAndThen(orbs: Int, ctx: GameContext, then: (GameContext) -> Unit) {
        ctx.enqueue(SpendMoves(owner, 1))
        ctx.enqueue(ChangeOrbs(owner, -orbs))
        ctx.enqueue("vanilla.paid", then)
    }

    companion object {
        const val ABILITY_NAME = "Golpe"
        const val BASE_DAMAGE = 5
        const val PHASE_2_DAMAGE = 10

        const val REVIVE_3 = "revive3"
        const val REVIVE_4 = "revive4"
        const val PHASE_2 = "phase2"
        const val CONVERT = "convert"
        private const val CANCEL = "cancel"

        val definition = ChampionDefinition(
            id = "vanilla",
            name = "Vainilla",
            description = "Al cargar 3 piedras golpea a todos los bichos enemigos (5 de daño, 10 en fase 2).",
            create = ::Vanilla,
        )
    }
}
