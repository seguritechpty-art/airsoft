package com.airsoft.tracker.data.repository

import com.airsoft.tracker.data.model.JoinResponseDto
import com.airsoft.tracker.data.model.SquadStateDto
import com.airsoft.tracker.data.network.ApiService
import com.airsoft.tracker.data.network.CreateSquadRequest
import com.airsoft.tracker.data.network.JoinSquadRequest
import com.airsoft.tracker.data.prefs.SessionPrefs
import com.airsoft.tracker.data.socket.SocketManager

/** Repositorio único: combina REST (crear/unirse) + WebSocket (tiempo real). */
class TrackerRepository(
    private val api: ApiService,
    val socket: SocketManager,
    private val prefs: SessionPrefs,
) {

    suspend fun createSquad(nick: String, name: String = ""): Result<JoinResponseDto> = runCatching {
        val resp = api.createSquad(CreateSquadRequest(nick, name))
        prefs.nick = nick
        prefs.squadCode = resp.squadCode
        resp
    }

    suspend fun joinSquad(nick: String, squadCode: String): Result<JoinResponseDto> = runCatching {
        val resp = api.joinSquad(JoinSquadRequest(nick, squadCode))
        prefs.nick = nick
        prefs.squadCode = resp.squadCode
        resp
    }

    suspend fun getState(squadCode: String): Result<SquadStateDto> = runCatching {
        api.getSquadState(squadCode)
    }

    /** Conecta el WebSocket a la sala y arranca la sincronización en tiempo real */
    fun startRealtime(nick: String, squadCode: String, onReady: (Boolean) -> Unit = {}) {
        socket.connect(nick, squadCode, onReady)
    }

    fun stopRealtime() {
        socket.disconnect()
    }

    fun hasSession(): Boolean = prefs.hasSession()
    fun savedNick(): String = prefs.nick
    fun savedSquad(): String = prefs.squadCode
    fun clearSession() = prefs.clear()
}