package com.airsoft.tracker.data.socket

import com.airsoft.tracker.BuildConfig
import com.airsoft.tracker.data.model.AreaDto
import com.airsoft.tracker.data.model.ChatMessageDto
import com.airsoft.tracker.data.model.ObjectiveDto
import com.airsoft.tracker.data.model.UserDto
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestor de comunicación en tiempo real con el backend.
 * Usa Socket.IO (WebSocket + fallback polling) para máxima compatibilidad
 * con redes móviles inestables en el campo.
 */
class SocketManager {

    private var socket: Socket? = null

    // --- Flujos de estado (consumidos por la UI) ---
    private val _users = MutableStateFlow<List<UserDto>>(emptyList())
    val users: StateFlow<List<UserDto>> = _users.asStateFlow()

    private val _objectives = MutableStateFlow<List<ObjectiveDto>>(emptyList())
    val objectives: StateFlow<List<ObjectiveDto>> = _objectives.asStateFlow()

    private val _areas = MutableStateFlow<List<AreaDto>>(emptyList())
    val areas: StateFlow<List<AreaDto>> = _areas.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val messages: StateFlow<List<ChatMessageDto>> = _messages.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    var myNick: String = ""
        private set

    /** Conecta y autentica en la sala */
    fun connect(nick: String, squadCode: String, onReady: (Boolean) -> Unit = {}) {
        myNick = nick
        disconnect()

        val opts = IO.Options.builder()
            .setTransports(arrayOf("websocket", "polling"))
            .setReconnection(true)
            .setReconnectionAttempts(Int.MAX_VALUE)
            .setReconnectionDelay(1000)
            .setReconnectionDelayMax(5000)
            .setTimeout(20_000)
            .build()

        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        val newSocket = IO.socket(baseUrl, opts)
        socket = newSocket

        newSocket.on(Socket.EVENT_CONNECT) {
            _connected.value = true
            _lastError.value = null
            // Autenticar en la sala vía WebSocket
            newSocket.emit("squad:auth", arrayOf(JSONObject().apply {
                put("nick", nick)
                put("squadCode", squadCode)
            }), Ack { ackArgs ->
                val ok = (ackArgs.firstOrNull() as? JSONObject)?.optBoolean("ok") ?: false
                onReady(ok)
            })
        }

        newSocket.on(Socket.EVENT_DISCONNECT) {
            _connected.value = false
        }

        newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val err = args.firstOrNull()?.toString() ?: "Error de conexión"
            _lastError.value = err
        }

        newSocket.on("squad:state") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            _users.value = payload.optJSONArray("users")?.let(::parseUsers) ?: emptyList()
            _objectives.value = payload.optJSONArray("objectives")?.let(::parseObjectives) ?: emptyList()
            _areas.value = payload.optJSONArray("areas")?.let(::parseAreas) ?: emptyList()
        }

        newSocket.on("location:update") { args ->
            val user = parseUser(args.firstOrNull() as? JSONObject ?: return@on)
            _users.value = _users.value.map { if (it.id == user.id) user else it }
        }

        newSocket.on("chat:message") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            _messages.value = _messages.value + ChatMessageDto(
                id = payload.optString("id"),
                nick = payload.optString("nick"),
                text = payload.optString("text"),
                created_at = payload.optLong("created_at"),
            )
        }

        newSocket.on("objective:add") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            _objectives.value = _objectives.value + payload.toObjective()
        }

        newSocket.on("objective:complete") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            val id = payload.optString("id")
            val completed = payload.optBoolean("completed")
            _objectives.value = _objectives.value.map {
                if (it.id == id) it.copy(completed = if (completed) 1 else 0) else it
            }
        }

        newSocket.on("area:add") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            _areas.value = _areas.value + payload.toArea()
        }

        newSocket.connect()
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _connected.value = false
    }

    // --- Emisores ---

    fun sendLocation(lat: Double, lng: Double, heading: Float?, speed: Float?, accuracy: Float?) {
        socket?.emit("location:update", JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("heading", heading ?: JSONObject.NULL)
            put("speed", speed ?: JSONObject.NULL)
            put("accuracy", accuracy ?: JSONObject.NULL)
        })
    }

    fun sendChat(text: String) {
        socket?.emit("chat:message", JSONObject().apply { put("text", text) })
    }

    fun addObjective(name: String, lat: Double, lng: Double, color: String, radius: Int, description: String = "") {
        socket?.emit("objective:add", JSONObject().apply {
            put("name", name)
            put("lat", lat)
            put("lng", lng)
            put("color", color)
            put("radius", radius)
            put("description", description)
        })
    }

    fun completeObjective(id: String, completed: Boolean) {
        socket?.emit("objective:complete", JSONObject().apply {
            put("id", id)
            put("completed", completed)
        })
    }

    fun addArea(name: String, color: String, opacity: Double, coordinates: List<Pair<Double, Double>>, type: String) {
        val coordsArr = JSONArray()
        coordinates.forEach { (lat, lng) ->
            coordsArr.put(JSONArray().put(lat).put(lng))
        }
        socket?.emit("area:add", JSONObject().apply {
            put("name", name)
            put("color", color)
            put("opacity", opacity)
            put("type", type)
            put("coordinates", coordsArr)
        })
    }

    // --- Parsers ---

    private fun parseUser(json: JSONObject): UserDto = UserDto(
        id = json.optString("id"),
        nick = json.optString("nick"),
        color = json.optString("color", "#4CAF50"),
        online = json.optBoolean("online"),
        lat = json.optDoubleOrNull("lat"),
        lng = json.optDoubleOrNull("lng"),
        heading = json.optDoubleOrNull("heading"),
        speed = json.optDoubleOrNull("speed"),
        accuracy = json.optDoubleOrNull("accuracy"),
        updated_at = json.optLongOrNull("updated_at"),
    )

    private fun parseUsers(arr: JSONArray): List<UserDto> =
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let(::parseUser)
        }

    private fun parseObjectives(arr: JSONArray): List<ObjectiveDto> =
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toObjective() }

    private fun parseAreas(arr: JSONArray): List<AreaDto> =
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toArea() }

    private fun JSONObject.toObjective() = ObjectiveDto(
        id = optString("id"),
        squad_code = optString("squad_code"),
        name = optString("name"),
        description = optString("description"),
        lat = optDouble("lat"),
        lng = optDouble("lng"),
        color = optString("color", "#FF0000"),
        radius = optInt("radius", 100),
        completed = optInt("completed", 0),
        created_by = optString("created_by"),
        created_at = optLong("created_at"),
    )

    private fun JSONObject.toArea() = AreaDto(
        id = optString("id"),
        squad_code = optString("squad_code"),
        name = optString("name"),
        color = optString("color", "#00FF00"),
        opacity = optDouble("opacity", 0.5),
        coordinates = optString("coordinates", "[]"),
        type = optString("type", "circle"),
        created_by = optString("created_by"),
        created_at = optLong("created_at"),
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else (opt(key) as? Number)?.toDouble()

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else (opt(key) as? Number)?.toLong()
}