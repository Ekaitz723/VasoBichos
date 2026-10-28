package com.vaso.bichos.net

import kotlinx.coroutines.flow.Flow

/**
 * Lo poco de Firebase Realtime Database que usan las salas. Cada plataforma lo implementa con su
 * SDK (Android: el SDK nativo; web: el SDK de JavaScript).
 *
 * Las rutas van separadas por "/" (`rooms/ABCD/meta`). Los valores son como en Firebase:
 * `Map<String, Any?>`, `String`, `Long`, `Double`, `Boolean` o `null` (no existe).
 */
interface RealtimeDb {
    /** Entra de forma anónima (o reutiliza la sesión) y devuelve el uid. */
    suspend fun signIn(): String

    suspend fun get(path: String): Any?

    /** `null` borra. */
    suspend fun set(path: String, value: Any?)

    suspend fun update(path: String, values: Map<String, Any?>)

    /** El valor de [path] cada vez que cambia. */
    fun observe(path: String): Flow<Any?>

    /** Los hijos de [path] con clave >= [startKey], en orden de clave, y los que lleguen después. */
    fun observeChildren(path: String, startKey: String): Flow<Pair<String, Any?>>

    /** Escribe [value] en [path] solo si no había nada. Devuelve si lo ha escrito. */
    suspend fun setIfEmpty(path: String, value: String): Boolean

    companion object {
        /** Lo sustituye el servidor por su hora al guardar (igual en todos los SDK). */
        val SERVER_TIME: Map<String, String> = mapOf(".sv" to "timestamp")
    }
}
