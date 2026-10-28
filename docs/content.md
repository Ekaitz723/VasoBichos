# Contenido actual

Todo esto es **contenido**, no reglas del motor: otro bicho u otro tablero puede hacerlo distinto.

## Bicho: Vainilla (`content/vanilla/Vanilla.kt`)

- Vida 50. Carga 3. Puede ser de cualquier color.
- Al llegar a 3 de carga, la habilidad **se lanza sola** y no gasta movimiento:
  - 5 de daño a todos los bichos enemigos (de todos los rivales).
  - En **fase 2**, 10 de daño.
- Lo que sobra de carga **se pierde**. Si gana 5 de golpe, dispara una vez y pierde 2. Cada grupo
  combinado carga por separado: dos tríos rojos a la vez son dos disparos, porque se procesan uno
  detrás de otro y así dejan sitio a sinergias.
- **Muerto (0 de vida)**: no carga. Las piedras que le lleguen se pierden.
- **Curación**: si ya está a tope de vida, en vez de curarse gana **1 piedra** de carga, que puede
  disparar su habilidad. Si le falta vida, se cura hasta el máximo.
- **Opciones del menú** (cada una gasta 1 movimiento y solo se pueden usar en tu turno):
  - *Fase 2 (4 orbes)*: permanente.
  - *Revivir (3 orbes)*: vuelve con 20 de vida. *Revivir (4 orbes)*: vuelve con 30.
  - *Orbes → piedras*: pregunta cuántos (o cancelar); cada orbe es 1 piedra de su color.

## Tablero base (`content/basic/BasicMode.kt`)

- Tablero de 7 de ancho × 5 de alto. 7 colores, y el anfitrión puede quitar los extra (mínimo 4
  colores en total).
- De 2 a 6 jugadores (por defecto se juega con 3). 2 bichos por jugador, sin repetir color dentro
  de un jugador.
- 2 movimientos por turno, 2 contenedores, capacidad de 4 orbes.
- Fichas: cada color del tablero pesa 1 y el orbe pesa 1 (con 7 colores, 1 de cada 8 fichas es un
  orbe; con 4 colores, 1 de cada 5).
- **Mover**: intercambiar dos fichas vecinas. Siempre cuesta 1 movimiento, combine o no.
- Gravedad hacia abajo; las fichas nuevas entran por arriba.
- Reglas (cada una es un `GameElement`):
  - `ColorCollection`: las piedras recogidas van al bicho de ese color del jugador del turno y los
    orbes al jugador (hasta su capacidad; lo que sobra se pierde).
  - `MatchRewards`: 4+ da +1 movimiento por grupo (si hay contenedor libre); 5+ cura 5 a cada bicho
    vivo del jugador.
  - `RedrawRule`: si pasa una ronda entera sin ninguna combinación, al jugador al que le vuelve el
    turno se le ofrece un botón de redraw gratis. Lo que combine el redraw cuenta para él.
  - `GraceEndRule(rounds = 2, extraTurns = 0)`: 2 rondas fantasma antes de decidir el final.

## Plantilla de bicho nuevo

```kotlin
class MiBicho(setup: ChampionSetup) : Champion(setup, definition, baseMaxHp = 40, baseAbilityCost = 4) {

    override fun receiveCharge(amount: Int, ctx: GameContext) {
        super.receiveCharge(amount, ctx)
        if (charge >= abilityCost(ctx)) {
            spendCharge(abilityCost(ctx), ctx)
            ctx.enqueue("mibicho.ability") { useAbility(it) }
        }
    }

    private fun useAbility(ctx: GameContext) {
        ctx.emit(Events.AbilityActivated(id, "Nombre de la habilidad"))
        // Encola acciones: DealDamage, CollectCells, ChangeOrbs, Act { ... }
        // Para elegir objetivo: ctx.ask(Decision(owner, "¿A quién?", choices) { choice, c -> ... })
    }

    // Pasivas: onEvent / modify / intercept. Botones: options / useOption.

    companion object {
        val definition = ChampionDefinition(
            id = "mibicho",
            name = "Mi Bicho",
            description = "Qué hace, en una frase.",
            create = ::MiBicho,
        )
    }
}
```

Después: añadirlo a `Content.default` y escribir tests en `engine/src/commonTest` (ver `GameTest` y
`TestSupport`: tablero de relleno `Junk` para controlar exactamente qué combina).
