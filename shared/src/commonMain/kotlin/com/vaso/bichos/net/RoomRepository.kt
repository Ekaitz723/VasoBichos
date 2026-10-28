package com.vaso.bichos.net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlin.random.Random

data class ChampionChoice(val definitionId: String, val color: Int)

data class RoomPlayer(
    val uid: String,
    val name: String,
    val joinedAt: Long,
    val picks: List<ChampionChoice>,
    val ready: Boolean,
    /** Bot de pruebas: lo mueve el móvil del anfitrión. */
    val bot: Boolean = false,
    /** Brillo de su turno (ver `Aura`); null = el de su asiento. */
    val aura: String? = null,
)

data class RoomMeta(
    val hostUid: String,
    val modeId: String,
    val status: String,
    val seed: Long,
    /** uids en orden de turno; solo existe al empezar. */
    val order: List<String>,
    /** Colores extra elegidos por el anfitrión; null = todos. */
    val extraColors: Set<Int>?,
)

data class Room(val code: String, val meta: RoomMeta, val players: List<RoomPlayer>)

/**
 * Salas en Realtime Database:
 * /rooms/{code}/meta, /rooms/{code}/players/{uid}, /rooms/{code}/commands/{000000}
 * Los comandos son la única verdad de la partida: cada móvil los aplica en orden.
 */
class RoomRepository(private val db: RealtimeDb) {
    private fun room(code: String) = "rooms/$code"

    /** Al entrar, los bichos por defecto ya cuentan (para que se vean sus colores en la sala). */
    var defaultPicks: String = ""

    suspend fun signIn(): String = db.signIn()

    suspend fun createRoom(uid: String, name: String, modeId: String, aura: String? = null): String {
        var code: String
        do {
            code = (1..4).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("")
        } while (db.get("${room(code)}/meta") != null)
        db.set(
            "${room(code)}/meta",
            mapOf("hostUid" to uid, "modeId" to modeId, "status" to STATUS_LOBBY, "seed" to 0L),
        )
        joinRoom(code, uid, name, aura)
        return code
    }

    /** Devuelve null si ha ido bien, o el motivo del fallo. */
    suspend fun joinRoom(code: String, uid: String, name: String, aura: String? = null): String? {
        val meta = db.get("${room(code)}/meta") as? Map<*, *> ?: return "No existe la sala $code"
        val me = "${room(code)}/players/$uid"
        if (db.get(me) != null) return null // ya estaba: reconexión
        if (meta["status"] != STATUS_LOBBY) return "La partida ya ha empezado"
        val data = mutableMapOf<String, Any?>(
            "name" to name, "joinedAt" to RealtimeDb.SERVER_TIME, "picks" to defaultPicks, "ready" to false,
        )
        if (aura != null) data["aura"] = aura
        db.set(me, data)
        return null
    }

    suspend fun setAura(code: String, uid: String, aura: String) {
        db.set("${room(code)}/players/$uid/aura", aura)
    }

    suspend fun leaveLobby(code: String, uid: String) {
        db.set("${room(code)}/players/$uid", null)
    }

    suspend fun addBot(code: String, name: String, picks: List<ChampionChoice>) {
        val id = "bot-" + (1..6).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("")
        db.set(
            "${room(code)}/players/$id",
            mapOf(
                "name" to name,
                "joinedAt" to RealtimeDb.SERVER_TIME,
                "picks" to picks.joinToString(",") { "${it.definitionId}:${it.color}" },
                "ready" to true,
                "bot" to true,
            ),
        )
    }

    suspend fun setPicks(code: String, uid: String, picks: List<ChampionChoice>, ready: Boolean) {
        db.update(
            "${room(code)}/players/$uid",
            mapOf("picks" to picks.joinToString(",") { "${it.definitionId}:${it.color}" }, "ready" to ready),
        )
    }

    suspend fun setExtraColors(code: String, colors: Set<Int>) {
        db.set("${room(code)}/meta/extraColors", colors.sorted().joinToString(","))
    }

    suspend fun startGame(code: String, order: List<String>, seed: Long) {
        db.update(
            "${room(code)}/meta",
            mapOf("order" to order.joinToString(","), "seed" to seed, "status" to STATUS_PLAYING),
        )
    }

    suspend fun deleteRoom(code: String) {
        db.set(room(code), null)
    }

    /** Solo publica cuando han llegado las dos partes, para no ver una sala a medias. */
    fun observeRoom(code: String): Flow<Room?> =
        combine(db.observe("${room(code)}/meta"), db.observe("${room(code)}/players")) { meta, players ->
            (meta as? Map<*, *>)?.let { m ->
                val list = (players as? Map<*, *>).orEmpty()
                    .map { (uid, p) -> parsePlayer(uid.toString(), p as? Map<*, *> ?: emptyMap<String, Any?>()) }
                    .sortedWith(compareBy({ it.joinedAt }, { it.uid }))
                Room(code, parseMeta(m), list)
            }
        }

    /** Todos los comandos ya guardados, en orden. */
    suspend fun loadCommands(code: String): List<String> =
        when (val all = db.get("${room(code)}/commands")) {
            is Map<*, *> -> all.map { (k, v) -> k.toString().toInt() to v }
            // Firebase devuelve una lista si las claves parecen índices 0, 1, 2...
            is List<*> -> all.mapIndexed { i, v -> i to v }
            else -> emptyList()
        }.sortedBy { it.first }.mapNotNull { it.second as? String }

    /** Comandos nuevos a partir del índice [from] (incluido). */
    fun observeCommands(code: String, from: Int): Flow<Pair<Int, String>> =
        db.observeChildren("${room(code)}/commands", key(from)).mapNotNull { (k, v) -> (v as? String)?.let { k.toInt() to it } }

    /** Escribe el comando [index] solo si nadie lo ha escrito antes. */
    suspend fun pushCommand(code: String, index: Int, text: String): Boolean =
        db.setIfEmpty("${room(code)}/commands/${key(index)}", text)

    private fun parseMeta(m: Map<*, *>) = RoomMeta(
        hostUid = m["hostUid"] as? String ?: "",
        modeId = m["modeId"] as? String ?: "",
        status = m["status"] as? String ?: "",
        seed = (m["seed"] as? Number)?.toLong() ?: 0L,
        order = (m["order"] as? String).orEmpty().split(",").filter { it.isNotEmpty() },
        extraColors = (m["extraColors"] as? String)
            ?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toSet(),
    )

    private fun parsePlayer(uid: String, p: Map<*, *>) = RoomPlayer(
        uid = uid,
        name = p["name"] as? String ?: "",
        joinedAt = (p["joinedAt"] as? Number)?.toLong() ?: Long.MAX_VALUE,
        picks = (p["picks"] as? String).orEmpty().split(",").filter { it.isNotEmpty() }.map {
            val (def, color) = it.split(":")
            ChampionChoice(def, color.toInt())
        },
        ready = p["ready"] as? Boolean ?: false,
        bot = p["bot"] as? Boolean ?: false,
        aura = p["aura"] as? String,
    )

    private fun key(index: Int) = index.toString().padStart(6, '0')

    companion object {
        const val STATUS_LOBBY = "lobby"
        const val STATUS_PLAYING = "playing"
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
