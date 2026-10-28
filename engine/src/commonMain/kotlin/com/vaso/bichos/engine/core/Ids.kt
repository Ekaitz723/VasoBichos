package com.vaso.bichos.engine.core

import kotlin.jvm.JvmInline

@JvmInline
value class PlayerId(val index: Int)

/** Identifica cualquier elemento de la partida: bichos, reglas del tablero, efectos, amuletos... */
@JvmInline
value class ElementId(val value: Int)

@JvmInline
value class StoneColor(val index: Int)
