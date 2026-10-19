package com.vaso.bichos.net

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale
import kotlin.random.Random

data class ChampionChoice(val definitionId: String, val color: Int)

data class RoomPlayer(
    val uid: String,
    val name: String,
    val joinedAt: Long,
    val picks: List<ChampionChoice>,
    val ready: Boolean,
)

data class RoomMeta(
    val hostUid: String,
    val modeId: String,
    val status: String,
    val seed: Long,
    /** uids en orden de turno; solo existe al empezar. */
    val order: List<String>,
)

data class Room(val code: String, val meta: RoomMeta, val players: List<RoomPlayer>)

/**
 * Salas en Realtime Database:
 * /rooms/{code}/meta, /rooms/{code}/players/{uid}, /rooms/{code}/commands/{000000}
 * Los comandos son la única verdad de la partida: cada móvil los aplica en orden.
 */
class RoomRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private fun room(code: String): DatabaseReference = db.getReference("rooms").child(code)

    suspend fun signIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid

    suspend fun createRoom(uid: String, name: String, modeId: String): String {
        var code: String
        do {
            code = (1..4).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("")
        } while (room(code).child("meta").get().await().exists())
        room(code).child("meta").setValue(
            mapOf("hostUid" to uid, "modeId" to modeId, "status" to STATUS_LOBBY, "seed" to 0L),
        ).await()
        joinRoom(code, uid, name)
        return code
    }

    /** Devuelve null si ha ido bien, o el motivo del fallo. */
    suspend fun joinRoom(code: String, uid: String, name: String): String? {
        val metaSnap = room(code).child("meta").get().await()
        if (!metaSnap.exists()) return "No existe la sala $code"
        val me = room(code).child("players").child(uid)
        if (me.get().await().exists()) return null // ya estaba: reconexión
        if (metaSnap.child("status").getValue(String::class.java) != STATUS_LOBBY) return "La partida ya ha empezado"
        me.setValue(
            mapOf("name" to name, "joinedAt" to ServerValue.TIMESTAMP, "picks" to "", "ready" to false),
        ).await()
        return null
    }

    suspend fun leaveLobby(code: String, uid: String) {
        room(code).child("players").child(uid).removeValue().await()
    }

    suspend fun setPicks(code: String, uid: String, picks: List<ChampionChoice>, ready: Boolean) {
        room(code).child("players").child(uid).updateChildren(
            mapOf("picks" to picks.joinToString(",") { "${it.definitionId}:${it.color}" }, "ready" to ready),
        ).await()
    }

    suspend fun startGame(code: String, order: List<String>, seed: Long) {
        room(code).child("meta").updateChildren(
            mapOf("order" to order.joinToString(","), "seed" to seed, "status" to STATUS_PLAYING),
        ).await()
    }

    suspend fun deleteRoom(code: String) {
        room(code).removeValue().await()
    }

    fun observeRoom(code: String): Flow<Room?> = callbackFlow {
        val ref = room(code)
        var meta: RoomMeta? = null
        var metaLoaded = false
        var players: List<RoomPlayer>? = null
        // Solo se publica cuando han llegado las dos partes, para no ver una sala a medias.
        fun publish() {
            val loadedPlayers = players ?: return
            if (!metaLoaded) return
            trySend(meta?.let { Room(code, it, loadedPlayers) })
        }
        val metaListener = ref.child("meta").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                meta = if (s.exists()) parseMeta(s) else null
                metaLoaded = true
                publish()
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        val playersListener = ref.child("players").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                players = s.children.map(::parsePlayer).sortedBy { it.joinedAt }
                publish()
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose {
            ref.child("meta").removeEventListener(metaListener)
            ref.child("players").removeEventListener(playersListener)
        }
    }

    /** Todos los comandos ya guardados, en orden. */
    suspend fun loadCommands(code: String): List<String> =
        room(code).child("commands").orderByKey().get().await().children.mapNotNull { it.getValue(String::class.java) }

    /** Comandos nuevos a partir del índice [from] (incluido). */
    fun observeCommands(code: String, from: Int): Flow<Pair<Int, String>> = callbackFlow {
        val query = room(code).child("commands").orderByKey().startAt(key(from))
        val listener = query.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, previous: String?) {
                val text = s.getValue(String::class.java) ?: return
                trySend(s.key!!.toInt() to text)
            }

            override fun onChildChanged(s: DataSnapshot, previous: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, previous: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { query.removeEventListener(listener) }
    }

    /** Escribe el comando [index] solo si nadie lo ha escrito antes. */
    suspend fun pushCommand(code: String, index: Int, text: String): Boolean =
        suspendCancellableCoroutine { cont ->
            room(code).child("commands").child(key(index)).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    if (data.value != null) return Transaction.abort()
                    data.value = text
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException()) else cont.resume(committed)
                }
            })
        }

    private fun parseMeta(s: DataSnapshot) = RoomMeta(
        hostUid = s.child("hostUid").getValue(String::class.java).orEmpty(),
        modeId = s.child("modeId").getValue(String::class.java).orEmpty(),
        status = s.child("status").getValue(String::class.java).orEmpty(),
        seed = s.child("seed").getValue(Long::class.java) ?: 0L,
        order = s.child("order").getValue(String::class.java).orEmpty().split(",").filter { it.isNotEmpty() },
    )

    private fun parsePlayer(s: DataSnapshot) = RoomPlayer(
        uid = s.key!!,
        name = s.child("name").getValue(String::class.java).orEmpty(),
        joinedAt = s.child("joinedAt").getValue(Long::class.java) ?: Long.MAX_VALUE,
        picks = s.child("picks").getValue(String::class.java).orEmpty().split(",").filter { it.isNotEmpty() }.map {
            val (def, color) = it.split(":")
            ChampionChoice(def, color.toInt())
        },
        ready = s.child("ready").getValue(Boolean::class.java) ?: false,
    )

    private fun key(index: Int) = String.format(Locale.ROOT, "%06d", index)

    companion object {
        const val STATUS_LOBBY = "lobby"
        const val STATUS_PLAYING = "playing"
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
