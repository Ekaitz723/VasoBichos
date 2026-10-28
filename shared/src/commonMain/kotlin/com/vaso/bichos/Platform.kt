package com.vaso.bichos

import com.vaso.bichos.net.RealtimeDb

/** Lo que pone cada plataforma (Android, web) para que el juego común funcione. */
interface Platform {
    val db: RealtimeDb
    val prefs: KeyValueStore

    /** Versión que se enseña en la portada. */
    val version: String
}

/** Ajustes pequeños que se recuerdan en el aparato (nombre, última sala...). */
interface KeyValueStore {
    fun get(key: String): String?

    /** `null` borra. */
    fun put(key: String, value: String?)
}
