package com.vaso.bichos.engine.champion

import com.vaso.bichos.engine.core.ElementId
import com.vaso.bichos.engine.core.Events
import com.vaso.bichos.engine.core.GameContext
import com.vaso.bichos.engine.core.GameElement
import com.vaso.bichos.engine.core.PlayerId
import com.vaso.bichos.engine.core.PlayerState
import com.vaso.bichos.engine.core.Q
import com.vaso.bichos.engine.core.StoneColor

data class ChampionSetup(val id: ElementId, val owner: PlayerId, val color: StoneColor)

/** Una entrada del catálogo de bichos. Para añadir uno: un archivo nuevo y registrarlo en el contenido. */
class ChampionDefinition(
    val id: String,
    val name: String,
    val description: String,
    /** null = puede ser de cualquier color. */
    val allowedColors: Set<StoneColor>? = null,
    val create: (ChampionSetup) -> Champion,
)

/**
 * Lo mínimo que tiene cualquier bicho (vida, carga, fase, color). Todo lo demás lo decide
 * cada bicho sobrescribiendo lo que necesite: cómo recibe carga, daño, curación, qué
 * opciones ofrece, a qué eventos reacciona...
 */
abstract class Champion(
    setup: ChampionSetup,
    val definition: ChampionDefinition,
    val baseMaxHp: Int,
    val baseAbilityCost: Int,
) : GameElement {
    override val id: ElementId = setup.id
    val owner: PlayerId = setup.owner
    val color: StoneColor = setup.color

    var hp = baseMaxHp
    var charge = 0
    var phase = 1

    open fun isAlive() = hp > 0

    fun maxHp(ctx: GameContext) = ctx.query(Q.MaxHp(id), baseMaxHp)
    fun abilityCost(ctx: GameContext) = ctx.query(Q.AbilityCost(id), baseAbilityCost)

    fun ownerState(ctx: GameContext): PlayerState = ctx.state.player(owner)
    fun enemies(ctx: GameContext) = ctx.state.champions().filter { it.owner != owner }
    fun allies(ctx: GameContext) = ctx.state.championsOf(owner).filter { it.id != id }

    open fun receiveCharge(amount: Int, ctx: GameContext) {
        charge += amount
        ctx.emit(Events.ChargeAdded(id, amount, charge))
    }

    /** Gasta [amount] de carga (o toda si no llega). */
    fun spendCharge(amount: Int, ctx: GameContext) {
        val spent = minOf(amount, charge)
        charge -= spent
        ctx.emit(Events.ChargeSpent(id, spent, charge))
    }

    open fun receiveDamage(amount: Int, source: ElementId?, ctx: GameContext) {
        if (!isAlive() || amount <= 0) return
        hp = (hp - amount).coerceAtLeast(0)
        ctx.emit(Events.Damaged(id, amount, hp, source))
        if (hp == 0) ctx.emit(Events.Died(id))
    }

    open fun receiveHeal(amount: Int, source: ElementId?, ctx: GameContext) {
        if (!isAlive() || amount <= 0) return
        val healed = minOf(amount, (maxHp(ctx) - hp).coerceAtLeast(0))
        if (healed == 0) return
        hp += healed
        ctx.emit(Events.Healed(id, healed, hp, source))
    }

    open fun revive(hp: Int, ctx: GameContext) {
        if (isAlive()) return
        this.hp = minOf(hp, maxHp(ctx))
        ctx.emit(Events.Revived(id, this.hp))
    }

    fun changePhase(phase: Int, ctx: GameContext) {
        this.phase = phase
        ctx.emit(Events.PhaseChanged(id, phase))
    }
}
