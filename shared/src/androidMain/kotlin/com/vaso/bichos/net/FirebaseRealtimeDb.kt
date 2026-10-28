package com.vaso.bichos.net

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [RealtimeDb] con el SDK nativo de Firebase para Android. */
class FirebaseRealtimeDb(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : RealtimeDb {
    private fun ref(path: String) = db.getReference(path)

    override suspend fun signIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid

    override suspend fun get(path: String): Any? = ref(path).get().await().value

    override suspend fun set(path: String, value: Any?) {
        if (value == null) ref(path).removeValue().await() else ref(path).setValue(value).await()
    }

    override suspend fun update(path: String, values: Map<String, Any?>) {
        ref(path).updateChildren(values).await()
    }

    override fun observe(path: String): Flow<Any?> = callbackFlow {
        val listener = ref(path).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                trySend(s.value)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref(path).removeEventListener(listener) }
    }

    override fun observeChildren(path: String, startKey: String): Flow<Pair<String, Any?>> = callbackFlow {
        val query = ref(path).orderByKey().startAt(startKey)
        val listener = query.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, previous: String?) {
                trySend(s.key!! to s.value)
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

    override suspend fun setIfEmpty(path: String, value: String): Boolean =
        suspendCancellableCoroutine { cont ->
            ref(path).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    if (data.value != null) return Transaction.abort()
                    data.value = value
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException()) else cont.resume(committed)
                }
            })
        }
}
