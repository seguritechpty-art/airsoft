package com.airsoft.tracker

import android.app.Application
import com.airsoft.tracker.data.network.ApiClient
import com.airsoft.tracker.data.network.ApiService
import com.airsoft.tracker.data.prefs.SessionPrefs
import com.airsoft.tracker.data.socket.SocketManager
import com.airsoft.tracker.data.repository.TrackerRepository

/** Punto de entrada de la app. Contenedor de dependencias simple (sin Hilt). */
class AirsoftApp : Application() {

    val apiService: ApiService by lazy { ApiClient.api }
    val sessionPrefs: SessionPrefs by lazy { SessionPrefs(this) }
    val socketManager: SocketManager by lazy { SocketManager() }
    val repository: TrackerRepository by lazy { TrackerRepository(apiService, socketManager, sessionPrefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AirsoftApp
            private set
    }
}