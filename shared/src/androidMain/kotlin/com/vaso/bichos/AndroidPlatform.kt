package com.vaso.bichos

import android.content.Context
import com.vaso.bichos.net.FirebaseRealtimeDb
import com.vaso.bichos.net.RealtimeDb

class AndroidPlatform(context: Context) : Platform {
    override val version = APP_VERSION
    override val db: RealtimeDb by lazy { FirebaseRealtimeDb() }
    override val prefs: KeyValueStore = SharedPrefsStore(context)
}

private class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("vasobichos", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
