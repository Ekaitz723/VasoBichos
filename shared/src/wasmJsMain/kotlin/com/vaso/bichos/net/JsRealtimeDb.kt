@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vaso.bichos.net

import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.js.Promise

/** [RealtimeDb] con el SDK de Firebase para JavaScript (ver `firebase-bridge.js`). */
class JsRealtimeDb : RealtimeDb {
    override suspend fun signIn(): String = jsSignIn().awaitDb<JsString>().toString()

    override suspend fun get(path: String): Any? = fromJson(jsGet(path).awaitDb<JsString>().toString())

    override suspend fun set(path: String, value: Any?) {
        jsSet(path, toJson(value).toString()).awaitDb<JsAny?>()
    }

    override suspend fun update(path: String, values: Map<String, Any?>) {
        jsUpdate(path, toJson(values).toString()).awaitDb<JsAny?>()
    }

    override fun observe(path: String): Flow<Any?> = callbackFlow {
        val handle = jsObserve(
            path,
            { json -> trySend(fromJson(json.toString())) },
            { error -> close(IllegalStateException(error.toString())) },
        )
        awaitClose { jsStop(handle) }
    }

    override fun observeChildren(path: String, startKey: String): Flow<Pair<String, Any?>> = callbackFlow {
        val handle = jsObserveChildren(
            path,
            startKey,
            { key, json -> trySend(key.toString() to fromJson(json.toString())) },
            { error -> close(IllegalStateException(error.toString())) },
        )
        awaitClose { jsStop(handle) }
    }

    override suspend fun setIfEmpty(path: String, value: String): Boolean =
        jsSetIfEmpty(path, value).awaitDb<JsBoolean>().toBoolean()
}

private fun fromJson(text: String): Any? = Json.parseToJsonElement(text).toValue()

private fun JsonElement.toValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> entries.associate { (k, v) -> k to v.toValue() }
    is JsonArray -> map { it.toValue() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> doubleOrNull
    }
}

private fun toJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
    is List<*> -> JsonArray(value.map(::toJson))
    else -> error("No se puede guardar en Firebase: $value")
}

private fun jsSignIn(): Promise<JsString> = js("window.vbDb.signIn()")

private fun jsGet(path: String): Promise<JsString> = js("window.vbDb.get(path)")

private fun jsSet(path: String, json: String): Promise<JsAny?> = js("window.vbDb.set(path, json)")

private fun jsUpdate(path: String, json: String): Promise<JsAny?> = js("window.vbDb.update(path, json)")

private fun jsObserve(path: String, onData: (JsString) -> Unit, onError: (JsString) -> Unit): Int =
    js("window.vbDb.observe(path, onData, onError)")

private fun jsObserveChildren(
    path: String,
    startKey: String,
    onChild: (JsString, JsString) -> Unit,
    onError: (JsString) -> Unit,
): Int = js("window.vbDb.observeChildren(path, startKey, onChild, onError)")

private fun jsStop(handle: Int): Unit = js("window.vbDb.stop(handle)")

private fun jsSetIfEmpty(path: String, value: String): Promise<JsBoolean> = js("window.vbDb.setIfEmpty(path, value)")

/**
 * Espera la promesa y convierte el error de JavaScript (que en Wasm no es una `Exception`) en una
 * normal, para que el código común lo capture igual que en Android.
 */
private suspend fun <T : JsAny?> Promise<T>.awaitDb(): T =
    try {
        await<T>()
    } catch (e: JsException) {
        throw IllegalStateException(e.message, e)
    }
