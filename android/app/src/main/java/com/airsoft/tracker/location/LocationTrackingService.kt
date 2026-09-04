package com.airsoft.tracker.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.airsoft.tracker.MainActivity
import com.airsoft.tracker.R
import com.airsoft.tracker.data.socket.SocketManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Servicio foreground que captura GPS en tiempo real y lo envía
 * al backend vía WebSocket.
 *
 * Compatibilidad:
 *  - Android 8 (O): ForegroundService obligatorio para background
 *  - Android 10 (Q): requiere ACCESS_BACKGROUND_LOCATION para segundo plano estricto
 *  - Android 13 (T): requiere POST_NOTIFICATIONS
 *  - Android 14 (U): FOREGROUND_SERVICE_LOCATION + tipo en manifest
 */
class LocationTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val socketManager: SocketManager
        get() = (application as com.airsoft.tracker.AirsoftApp).socketManager

    private var fusedClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var lastSent = 0L
    private var lastLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lastLocation = location
            sendIfReady(location)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        startForegroundWithNotification()
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_UPDATE_INTERVAL) {
            // El servicio ya está corriendo; los ajustes de intervalo se aplican en el próximo reinicio
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedClient?.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun sendIfReady(location: Location) {
        val now = System.currentTimeMillis()
        if (now - lastSent < UPDATE_INTERVAL_MS) return
        lastSent = now

        scope.launch {
            socketManager.sendLocation(
                lat = location.latitude,
                lng = location.longitude,
                heading = if (location.hasBearing()) location.bearing else null,
                speed = if (location.hasSpeed()) location.speed else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
            )
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LocationTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Airsoft Tracker")
            .setContentText("Tracking activo - enviando posición al escuadrón")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "Detener tracking", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tracking de ubicación",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Indicador de tracking GPS activo en la partida"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        fusedClient?.removeLocationUpdates(locationCallback)
        socketManager.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 5_000L      // 5 segundos
        private const val FASTEST_INTERVAL_MS = 2_000L     // mínimo 2s

        const val ACTION_START = "com.airsoft.tracker.START_TRACKING"
        const val ACTION_STOP = "com.airsoft.tracker.STOP_TRACKING"
        const val ACTION_UPDATE_INTERVAL = "com.airsoft.tracker.UPDATE_INTERVAL"

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}