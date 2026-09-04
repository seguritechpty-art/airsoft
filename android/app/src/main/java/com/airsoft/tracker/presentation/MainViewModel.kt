package com.airsoft.tracker.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airsoft.tracker.AirsoftApp
import com.airsoft.tracker.data.model.AreaDto
import com.airsoft.tracker.data.model.ObjectiveDto
import com.airsoft.tracker.data.model.UserDto
import com.airsoft.tracker.location.LocationTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la UI de la pantalla de login */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val squadCode: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/** ViewModel principal: autenticación + estado del escuadrón en tiempo real */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AirsoftApp
    private val repo get() = app.repository
    private val socket get() = app.socketManager

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _users = MutableStateFlow<List<UserDto>>(emptyList())
    val users: StateFlow<List<UserDto>> = _users.asStateFlow()

    private val _objectives = MutableStateFlow<List<ObjectiveDto>>(emptyList())
    val objectives: StateFlow<List<ObjectiveDto>> = _objectives.asStateFlow()

    private val _areas = MutableStateFlow<List<AreaDto>>(emptyList())
    val areas: StateFlow<List<AreaDto>> = _areas.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _myLocation = MutableStateFlow<Location?>(null)
    val myLocation: StateFlow<Location?> = _myLocation.asStateFlow()

    private val _myNick = MutableStateFlow("")
    val myNick: StateFlow<String> = _myNick.asStateFlow()

    private val _myColor = MutableStateFlow("#4CAF50")
    val myColor: StateFlow<String> = _myColor.asStateFlow()

    private val _trackingActive = MutableStateFlow(false)
    val trackingActive: StateFlow<Boolean> = _trackingActive.asStateFlow()

    init {
        // Copiar flujos del socket al ViewModel
        viewModelScope.launch {
            socket.users.collect { _users.value = it }
        }
        viewModelScope.launch {
            socket.objectives.collect { _objectives.value = it }
        }
        viewModelScope.launch {
            socket.areas.collect { _areas.value = it }
        }
        viewModelScope.launch {
            socket.connected.collect { _connected.value = it }
        }

        // Si ya hay sesión guardada, restaurarla
        if (repo.hasSession()) {
            _myNick.value = repo.savedNick()
            _authState.value = AuthState.Success(repo.savedSquad())
        }
    }

    fun createSquad(nick: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            repo.createSquad(nick).fold(
                onSuccess = { resp ->
                    _myNick.value = nick
                    _myColor.value = resp.user?.color ?: _myColor.value
                    _authState.value = AuthState.Success(resp.squadCode)
                },
                onFailure = { e ->
                    _authState.value = AuthState.Error(e.message ?: "Error al crear partida")
                }
            )
        }
    }

    fun joinSquad(nick: String, squadCode: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            repo.joinSquad(nick, squadCode).fold(
                onSuccess = { resp ->
                    _myNick.value = nick
                    _myColor.value = resp.user?.color ?: _myColor.value
                    _authState.value = AuthState.Success(resp.squadCode)
                },
                onFailure = { e ->
                    _authState.value = AuthState.Error(e.message ?: "Error al unirse a la partida")
                }
            )
        }
    }

    fun startRealtime() {
        val squad = (authState.value as? AuthState.Success)?.squadCode ?: return
        repo.startRealtime(_myNick.value, squad) { ok ->
            if (ok) startTracking()
        }
    }

    fun stopRealtime() {
        repo.stopRealtime()
    }

    fun updateMyLocation(location: Location) {
        _myLocation.value = location
        socket.sendLocation(
            location.latitude,
            location.longitude,
            if (location.hasBearing()) location.bearing else null,
            if (location.hasSpeed()) location.speed else null,
            if (location.hasAccuracy()) location.accuracy else null,
        )
    }

    fun startTracking() {
        val ctx = getApplication<Application>()
        LocationTrackingService.start(ctx)
        _trackingActive.value = true
    }

    fun stopTracking() {
        val ctx = getApplication<Application>()
        LocationTrackingService.stop(ctx)
        _trackingActive.value = false
    }

    fun hasLocationPermission(): Boolean {
        val ctx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun myUserId(): String? = _users.value.firstOrNull { it.nick == _myNick.value }?.id

    // Acciones del mapa
    fun addObjective(name: String, lat: Double, lng: Double, color: String, radius: Int = 100) {
        socket.addObjective(name, lat, lng, color, radius)
    }

    fun completeObjective(id: String, completed: Boolean) {
        socket.completeObjective(id, completed)
    }

    fun addArea(name: String, color: String, opacity: Double, coords: List<Pair<Double, Double>>, type: String) {
        socket.addArea(name, color, opacity, coords, type)
    }
}