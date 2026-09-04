package com.airsoft.tracker.presentation.screens

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoft.tracker.data.model.AreaDto
import com.airsoft.tracker.data.model.ObjectiveDto
import com.airsoft.tracker.data.model.UserDto
import com.airsoft.tracker.presentation.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pantalla principal: mapa táctico en tiempo real.
 * Muestra:
 *  - Marcadores de compañeros con nombre y color
 *  - Mi propia posición
 *  - Objetivos/waypoints
 *  - Áreas de colores (círculos/polígonos)
 *  - Panel de control + chat + lista de escuadrón
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMapsComposeApi::class)
@Composable
fun MapScreen(viewModel: MainViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val users by viewModel.users.collectAsStateWithLifecycle()
    val objectives by viewModel.objectives.collectAsStateWithLifecycle()
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val trackingActive by viewModel.trackingActive.collectAsStateWithLifecycle()
    val myNick by viewModel.myNick.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            LatLng(8.9824, -79.5199), 16f // Panamá por defecto
        )
    }

    var showPanel by remember { mutableStateOf(false) }
    var showAddObjective by remember { mutableStateOf(false) }
    var myLocation by remember { mutableStateOf<LatLng?>(null) }

    // Permiso de ubicación
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.startRealtime()
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) {
            viewModel.startRealtime()
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ))
        }
    }

    // Polígono: permitir toque en mapa para crear objetivo
    var pendingObjective by remember { mutableStateOf<LatLng?>(null) }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng ->
                myLocation = latLng
                cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng))
            },
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapType = com.google.android.gms.maps.model.MapType.HYBRID,
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = true),
        ) {
            // Compañeros
            users.forEach { user ->
                val pos = user.lat?.let { lat -> user.lng?.let { lng -> LatLng(lat, lng) } }
                if (pos != null && user.nick != myNick) {
                    Marker(
                        state = rememberMarkerState(position = pos),
                        title = user.nick,
                        snippet = "${user.speed?.let { "%.1f m/s".format(it) } ?: ""} ${if (!user.online) "(offline)" else ""}",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            colorToHue(user.color)
                        ),
                    )
                }
            }
            // Mi propio marcador mas grande
            users.filter { it.nick == myNick }.forEach { user ->
                val pos = user.lat?.let { lat -> user.lng?.let { lng -> LatLng(lat, lng) } }
                if (pos != null) {
                    Marker(
                        state = rememberMarkerState(position = pos),
                        title = "${user.nick} (YO)",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    )
                }
            }
            // Objetivos
            objectives.forEach { obj ->
                Marker(
                    state = rememberMarkerState(position = LatLng(obj.lat, obj.lng)),
                    title = if (obj.completed == 1) "✅ ${obj.name}" else obj.name,
                    snippet = obj.description,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (obj.completed == 1) BitmapDescriptorFactory.HUE_GREEN
                        else colorToHue(obj.color)
                    ),
                )
            }
            // Áreas
            areas.forEach { area -> AreaOverlay(area) }
        }

        // Top bar: estado de conexión + squad code
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (connected) Color(0xFF4CAF50) else Color(0xFFF44336),
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connected) "Conectado" else "Conectando...",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "SALA ${viewModel.authState.value.let { if (it is com.airsoft.tracker.presentation.AuthState.Success) it.squadCode else "" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Panel de control flotante
        if (trackingActive) {
            FloatingActionButton(
                onClick = { viewModel.stopTracking() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFFF44336),
            ) {
                Icon(Icons.Default.LocationOff, contentDescription = "Detener tracking")
            }
        }

        // Botón panel inferior
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FloatingActionButton(
                onClick = { showAddObjective = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.AddLocation, contentDescription = "Añadir objetivo", tint = Color.White)
            }
            FloatingActionButton(
                onClick = { showPanel = !showPanel },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.Group, contentDescription = "Escuadrón", tint = Color.Black)
            }
            FloatingActionButton(
                onClick = {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(myLocation ?: LatLng(8.9824, -79.5199), 16f)
                    )
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Centrar en mí", tint = Color.Black)
            }
        }

        // Panel lateral derecha: escuadrón
        if (showPanel) {
            SquadPanel(
                users = users,
                objectives = objectives,
                onClose = { showPanel = false },
                onComplete = { viewModel.completeObjective(it, true) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(280.dp)
                    .fillMaxHeight()
                    .padding(vertical = 56.dp),
            )
        }

        // Dialog añadir objetivo
        if (showAddObjective) {
            AddObjectiveDialog(
                myLocation = myLocation,
                onDismiss = { showAddObjective = false },
                onAdd = { name, color, radius ->
                    val pos = myLocation ?: LatLng(8.9824, -79.5199)
                    viewModel.addObjective(name, pos.latitude, pos.longitude, color, radius)
                    showAddObjective = false
                },
            )
        }
    }
}

/** Dibuja un área de color (círculo o polígono) en el mapa */
@Composable
private fun AreaOverlay(area: AreaDto) {
    val colorArgb = try {
        android.graphics.Color.parseColor(area.color)
    } catch (e: Exception) {
        android.graphics.Color.GREEN
    }
    val fill = Color(colorArgb).copy(alpha = area.opacity.toFloat().coerceIn(0.1f, 0.9f)).toArgb()

    if (area.type == "circle") {
        // El círculo se representa con 2+ puntos [lat,lng] - usamos el primero como centro y el segundo como radio aproximado
        val coords = parseCoords(area.coordinates)
        if (coords.size >= 2) {
            val center = LatLng(coords[0].first, coords[0].second)
            val edge = LatLng(coords[1].first, coords[1].second)
            val radiusMeters = distanceMeters(center, edge).toDouble().coerceAtLeast(10.0)
            Circle(
                center = center,
                radius = radiusMeters,
                fillColor = fill,
                strokeColor = Color(colorArgb).toArgb(),
                strokeWidth = 2f,
            )
        }
    } else {
        val coords = parseCoords(area.coordinates)
        if (coords.size >= 3) {
            Polygon(
                points = coords.map { LatLng(it.first, it.second) },
                fillColor = fill,
                strokeColor = Color(colorArgb).toArgb(),
                strokeWidth = 2f,
            )
        }
    }
}

/** Parsear coordenadas JSON: [[lat,lng],...] */
private fun parseCoords(jsonStr: String): List<Pair<Double, Double>> {
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val arr = json.parseToJsonElement(jsonStr).jsonArray
        arr.mapNotNull { elem ->
            val pair = elem.jsonArray
            if (pair.size >= 2) {
                Pair(pair[0].jsonPrimitive.content.toDouble(), pair[1].jsonPrimitive.content.toDouble())
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/** Haversine: distancia en metros entre dos LatLng */
private fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 2 * r * Math.asin(Math.sqrt(h))
}

/** Convertir color hex a hue para defaultMarker */
private fun colorToHue(hex: String): Float {
    return try {
        val color = android.graphics.Color.parseColor(hex)
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        var h = when (max) {
            r -> ((g - b) / d * 60 + 360) % 360
            g -> ((b - r) / d * 60 + 120) % 360
            else -> ((r - g) / d * 60 + 240) % 360
        }
        if (d == 0f) h = 0f
        h / 360f * 360f
    } catch (e: Exception) {
        BitmapDescriptorFactory.HUE_RED
    }
}

/** Panel del escuadrón (lista de usuarios + objetivos) */
@Composable
private fun SquadPanel(
    users: List<UserDto>,
    objectives: List<ObjectiveDto>,
    onClose: () -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(startStart = 16.dp, bottomStart = 16.dp),
        tonalElevation = 5.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ESCUADRÓN", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
            }
            Divider()
            Text("Miembros (${users.count { it.online }}/${users.size})", style = MaterialTheme.typography.labelMedium)
            LazyColumn {
                items(users) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(
                                    try {
                                        Color(android.graphics.Color.parseColor(user.color))
                                    } catch (e: Exception) { Color.Gray },
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(user.nick, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        if (user.online) {
                            Text(
                                "${user.speed?.let { "%.0f m/s".format(it) } ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("offline", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            Text("Objetivos", style = MaterialTheme.typography.titleSmall)
            LazyColumn {
                items(objectives) { obj ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (obj.completed == 0) onComplete(obj.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (obj.completed == 1) "✅" else "⭕",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(obj.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Dialog para crear objetivo */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddObjectiveDialog(
    myLocation: LatLng?,
    onDismiss: () -> Unit,
    onAdd: (name: String, color: String, radius: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo objetivo") },
        text = {
            Column {
                Text(
                    "Posición: ${myLocation?.latitude?.let { "%.5f".format(it) } ?: "?"}, " +
                    "${myLocation?.longitude?.let { "%.5f".format(it) } ?: "?"}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nombre del objetivo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Radio de alerta (metros)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, "#E53935", radius.toIntOrNull() ?: 100) },
                enabled = name.isNotBlank(),
            ) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}