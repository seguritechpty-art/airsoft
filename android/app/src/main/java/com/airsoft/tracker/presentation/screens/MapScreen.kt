package com.airsoft.tracker.presentation.screens

import android.Manifest
import android.content.Context
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoft.tracker.data.model.AreaDto
import com.airsoft.tracker.data.model.ObjectiveDto
import com.airsoft.tracker.data.model.UserDto
import com.airsoft.tracker.presentation.AuthState
import com.airsoft.tracker.presentation.MainViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.json.JSONArray

/**
 * Pantalla principal: mapa táctico en tiempo real con MapLibre + OpenFreeMap.
 * CERO coste, CERO API keys, CERO cuenta (solo la URL pública de OpenFreeMap).
 *
 * Muestra:
 *  - Marcadores de compañeros con nombre y color
 *  - Objetivos/waypoints con estado (pendiente / ✅)
 *  - Áreas de colores (polígonos)
 *  - Panel de escuadrón + creación de objetivos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MainViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val users by viewModel.users.collectAsStateWithLifecycle()
    val objectives by viewModel.objectives.collectAsStateWithLifecycle()
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val trackingActive by viewModel.trackingActive.collectAsStateWithLifecycle()
    val myNick by viewModel.myNick.collectAsStateWithLifecycle()
    val squadCode = (viewModel.authState.value as? AuthState.Success)?.squadCode ?: ""

    var showPanel by remember { mutableStateOf(false) }
    var showAddObjective by remember { mutableStateOf(false) }
    var lastTapLocation by remember { mutableStateOf<LatLng?>(null) }

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

    Box(Modifier.fillMaxSize()) {
        // MAPA MapLibre (tiles OpenFreeMap - sin API key, sin cuenta)
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val mapMode = remember { MapLibreMapView(context) }

        // MapLibre requiere los eventos del ciclo de vida del Activity
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_CREATE -> mapMode.onCreate(null)
                    androidx.lifecycle.Lifecycle.Event.ON_START -> mapMode.onStart()
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapMode.onResume()
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapMode.onPause()
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapMode.onStop()
                    androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> mapMode.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapMode.onDestroy()
            }
        }

        AndroidView(
            factory = { mapMode },
            update = { mapView ->
                mapView.updateData(
                    users = users,
                    objectives = objectives,
                    areas = areas,
                    myNick = myNick,
                    onMapTap = { latLng -> lastTapLocation = latLng },
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar: estado de conexión + sala
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
                            if (connected) ComposeColor(0xFF4CAF50) else ComposeColor(0xFFF44336),
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
                    "SALA $squadCode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Botón detener tracking (solo si está activo)
        if (trackingActive) {
            FloatingActionButton(
                onClick = { viewModel.stopTracking() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = ComposeColor(0xFFF44336),
            ) {
                Icon(Icons.Default.LocationOff, contentDescription = "Detener tracking")
            }
        }

        // Botones inferiores
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
                Icon(Icons.Default.AddLocation, contentDescription = "Añadir objetivo", tint = ComposeColor.White)
            }
            FloatingActionButton(
                onClick = { showPanel = !showPanel },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.Group, contentDescription = "Escuadrón", tint = ComposeColor.Black)
            }
        }

        // Panel del escuadrón
        if (showPanel) {
            SquadPanel(
                users = users,
                objectives = objectives,
                onClose = { showPanel = false },
                onComplete = { id -> viewModel.completeObjective(id, true) },
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
                lastTap = lastTapLocation,
                onDismiss = { showAddObjective = false },
                onAdd = { name, color, radius ->
                    val pos = lastTapLocation ?: LatLng(8.9824, -79.5199) // fallback: Panamá
                    viewModel.addObjective(name, pos.latitude, pos.longitude, color, radius)
                    showAddObjective = false
                },
            )
        }
    }
}

/**
 * Envoltorio de MapView de MapLibre con capas tácticas.
 * Lógica: 3 fuentes GeoJSON (usuarios, objetivos, áreas) que se actualizan in-place
 * desde Compose (updateData) sin recargar el estilo.
 */
class MapLibreMapView(context: Context) : MapView(context) {

    private var mapReady = false
    private var onTap: ((LatLng) -> Unit)? = null

    private var usersSource: GeoJsonSource? = null
    private var objectivesSource: GeoJsonSource? = null
    private var areasSource: GeoJsonSource? = null

    private var pendingUsers: List<UserDto> = emptyList()
    private var pendingObjectives: List<ObjectiveDto> = emptyList()
    private var pendingAreas: List<AreaDto> = emptyList()
    private var pendingNick: String = ""

    init {
        MapLibre.getInstance(context)
        getMapAsync { map ->
            mapReady = true
            map.addOnMapClickListener { latLng ->
                onTap?.invoke(latLng)
                true
            }
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                buildLayers(style)
                updateAllLayers()
            }
        }
    }

    private fun buildLayers(style: Style) {
        // --- Usuarios: círculos de color + etiquetas de nombre ---
        val srcUsers = GeoJsonSource("users", FeatureCollection.fromFeatures(emptyList()))
        style.addSource(srcUsers)
        style.addLayer(CircleLayer("users-circles", "users").withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(5.5f),
            PropertyFactory.circleStrokeColor(Expression.get("stroke")),
            PropertyFactory.circleStrokeWidth(1.8f),
        ))
        style.addLayer(SymbolLayer("users-labels", "users").withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#000000"),
            PropertyFactory.textHaloWidth(1.2f),
            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(false),
        ))
        usersSource = srcUsers

        // --- Objetivos: círculos + etiquetas ---
        val srcObjectives = GeoJsonSource("objectives", FeatureCollection.fromFeatures(emptyList()))
        style.addSource(srcObjectives)
        style.addLayer(CircleLayer("objectives-circles", "objectives").withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#000000"),
            PropertyFactory.circleStrokeWidth(2f),
        ))
        style.addLayer(SymbolLayer("objectives-labels", "objectives").withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#000000"),
            PropertyFactory.textHaloWidth(1.2f),
            PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(false),
        ))
        objectivesSource = srcObjectives

        // --- Áreas: relleno translúcido + borde ---
        val srcAreas = GeoJsonSource("areas", FeatureCollection.fromFeatures(emptyList()))
        style.addSource(srcAreas)
        style.addLayer(FillLayer("areas-fill", "areas").withProperties(
            PropertyFactory.fillColor(Expression.get("color")),
            PropertyFactory.fillOpacity(Expression.get("opacity")),
        ))
        style.addLayer(LineLayer("areas-outline", "areas").withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(2f),
        ))
        areasSource = srcAreas
    }

    /** Desde Compose: refresca datos + registrar tap */
    fun updateData(
        users: List<UserDto>,
        objectives: List<ObjectiveDto>,
        areas: List<AreaDto>,
        myNick: String,
        onMapTap: (LatLng) -> Unit,
    ) {
        onTap = onMapTap
        pendingUsers = users
        pendingObjectives = objectives
        pendingAreas = areas
        pendingNick = myNick
        updateAllLayers()
    }

    private fun updateAllLayers() {
        if (!mapReady) return
        usersSource?.setGeoJson(buildUsersGeoJson(pendingUsers, pendingNick))
        objectivesSource?.setGeoJson(buildObjectivesGeoJson(pendingObjectives))
        areasSource?.setGeoJson(buildAreasGeoJson(pendingAreas))
    }

    // --- GeoJSON builders ---

    private fun buildUsersGeoJson(users: List<UserDto>, myNick: String): String {
        val features = users.filter { it.lat != null && it.lng != null }.map { user ->
            val f = Feature.fromGeometry(Point.fromLngLat(user.lng!!, user.lat!!))
            f.addStringProperty("name", if (user.nick == myNick) "${user.nick} (YO)" else user.nick)
            f.addStringProperty("color", parseHexColor(user.color, "#4CAF50"))
            f.addStringProperty("stroke", if (user.nick == myNick) "#2196F3" else "#000000")
            f
        }
        return FeatureCollection.fromFeatures(features).toJson()
    }

    private fun buildObjectivesGeoJson(objectives: List<ObjectiveDto>): String {
        val features = objectives.map { obj ->
            val f = Feature.fromGeometry(Point.fromLngLat(obj.lng, obj.lat))
            f.addStringProperty("name", if (obj.completed == 1) "✅ ${obj.name}" else obj.name)
            f.addStringProperty(
                "color",
                if (obj.completed == 1) "#4CAF50" else parseHexColor(obj.color, "#FF0000")
            )
            f
        }
        return FeatureCollection.fromFeatures(features).toJson()
    }

    private fun buildAreasGeoJson(areas: List<AreaDto>): String {
        val features = areas.mapNotNull { area ->
            val coords = parseCoords(area.coordinates)
            if (coords.size < 3) return@mapNotNull null
            val ring = coords.map { Point.fromLngLat(it.second, it.first) } +
                Point.fromLngLat(coords[0].second, coords[0].first)
            val f = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
            f.addStringProperty("color", parseHexColor(area.color, "#00FF00"))
            f.addNumberProperty("opacity", area.opacity.toFloat().coerceIn(0.1f, 0.9f))
            f
        }
        return FeatureCollection.fromFeatures(features).toJson()
    }

    companion object {
        /** Tiles vectoriales gratuitos: OpenFreeMap (sin registro, sin límites, sin API key) */
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

        private fun parseHexColor(hex: String, fallback: String): String = try {
            val c = Color.parseColor(hex)
            String.format("#%06X", 0xFFFFFF and c)
        } catch (e: Exception) {
            fallback
        }
    }
}

/** Parsear [[lat,lng],...] (JSON de AreaDto.coordinates) usando org.json (sin dependencias) */
private fun parseCoords(jsonStr: String): List<Pair<Double, Double>> {
    return try {
        val arr = JSONArray(jsonStr)
        (0 until arr.length()).mapNotNull { i ->
            val pair = arr.optJSONArray(i) ?: return@mapNotNull null
            if (pair.length() >= 2) {
                Pair(pair.getDouble(0), pair.getDouble(1))
            } else null
        }
    } catch (e: Exception) {
        emptyList()
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
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        tonalElevation = 5.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ESCUADRÓN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
            }
            Divider()
            Text(
                "Miembros (${users.count { it.online }}/${users.size})",
                style = MaterialTheme.typography.labelMedium,
            )
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
                                        ComposeColor(Color.parseColor(user.color))
                                    } catch (e: Exception) {
                                        ComposeColor.Gray
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(user.nick, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        if (user.online) {
                            Text(
                                user.speed?.let { "%.0f m/s".format(it) } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("offline", style = MaterialTheme.typography.labelSmall, color = ComposeColor.Gray)
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

/** Dialog para crear objetivo manualmente */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddObjectiveDialog(
    lastTap: LatLng?,
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
                    "Posición: ${lastTap?.latitude?.let { "%.5f".format(it) } ?: "?"}, " +
                        "${lastTap?.longitude?.let { "%.5f".format(it) } ?: "?"}",
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