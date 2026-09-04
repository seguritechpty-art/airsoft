package com.airsoft.tracker.data.prefs

import android.content.Context
import android.content.SharedPreferences

/** Guarda la sesión (nick y código de partida) entre reinicios. */
class SessionPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("airsoft_session", Context.MODE_PRIVATE)

    var nick: String
        get() = prefs.getString(KEY_NICK, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NICK, value).apply()

    var squadCode: String
        get() = prefs.getString(KEY_SQUAD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SQUAD, value).apply()

    var lastServerUrl: String
        get() = prefs.getString(KEY_SERVER, "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000"
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    fun hasSession(): Boolean = nick.isNotBlank() && squadCode.isNotBlank()

    fun clear() {
        prefs.edit().remove(KEY_NICK).remove(KEY_SQUAD).apply()
    }

    private companion object {
        const val KEY_NICK = "nick"
        const val KEY_SQUAD = "squad_code"
        const val KEY_SERVER = "server_url"
    }
}