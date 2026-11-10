package ro.utcn.bettercycle.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

data class LiveGpsMetrics(
    val isTracking: Boolean = false,
    val latitude: Double = 46.7712, // Coordonate implicite Cluj-Napoca
    val longitude: Double = 23.6236,
    val altitudeMeters: Double = 340.0,
    val currentSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val distanceMeters: Float = 0f,
    val elevationGainMeters: Float = 0f,
    val currentSlopePercent: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val routeCoordinates: List<Pair<Double, Double>> = emptyList()
)

class CyclingForegroundService : Service() {

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private var startTimestampMillis: Long = 0L

    private val _gpsMetrics = MutableStateFlow(LiveGpsMetrics())
    val gpsMetrics: StateFlow<LiveGpsMetrics> = _gpsMetrics

    inner class LocalBinder : Binder() {
        fun getService(): CyclingForegroundService = this@CyclingForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @SuppressLint("MissingPermission")
    fun startRideTracking() {
        startTimestampMillis = System.currentTimeMillis()
        lastLocation = null
        _gpsMetrics.value = LiveGpsMetrics(isTracking = true)

        val notification = buildNotification("Cursa activa: 0.0 km | 0.0 km/h")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(2f)
                .build()

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Daca permisiunea GPS nu a fost inca acordata, cursa poate continua in modul Simulare
        }
    }

    fun stopRideTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {
        }
        _gpsMetrics.value = _gpsMetrics.value.copy(isTracking = false)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val prev = lastLocation
            val currentState = _gpsMetrics.value

            val deltaDist = if (prev != null) prev.distanceTo(location) else 0f
            val newTotalDistance = currentState.distanceMeters + deltaDist

            val deltaAlt = if (prev != null && location.hasAltitude() && prev.hasAltitude()) {
                (location.altitude - prev.altitude).toFloat()
            } else 0f

            val newElevationGain = if (deltaAlt > 0.5f) {
                currentState.elevationGainMeters + deltaAlt
            } else {
                currentState.elevationGainMeters
            }

            val slopePercent = if (deltaDist > 3f) {
                ((deltaAlt / deltaDist) * 100f).coerceIn(-25f, 25f)
            } else currentState.currentSlopePercent

            val speedKmh = if (location.hasSpeed()) {
                location.speed * 3.6f
            } else if (prev != null) {
                val dtSec = (location.time - prev.time) / 1000f
                if (dtSec > 0) (deltaDist / dtSec) * 3.6f else 0f
            } else 0f

            val elapsedSec = max(1L, (System.currentTimeMillis() - startTimestampMillis) / 1000L)
            val avgSpeed = ((newTotalDistance / 1000f) / (elapsedSec / 3600f))
            val maxSpeed = max(currentState.maxSpeedKmh, speedKmh)

            val updatedRoute = currentState.routeCoordinates + Pair(location.latitude, location.longitude)

            _gpsMetrics.value = currentState.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = location.altitude,
                currentSpeedKmh = speedKmh,
                avgSpeedKmh = avgSpeed,
                maxSpeedKmh = maxSpeed,
                distanceMeters = newTotalDistance,
                elevationGainMeters = newElevationGain,
                currentSlopePercent = slopePercent,
                elapsedSeconds = elapsedSec,
                routeCoordinates = updatedRoute
            )

            lastLocation = location
            updateNotification(
                "Distanta: %.2f km | Viteza: %.1f km/h".format(
                    newTotalDistance / 1000f,
                    speedKmh
                )
            )
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BetterCycle Tracking GPS",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BetterCycle — Monitorizare Cursa")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    companion object {
        private const val CHANNEL_ID = "better_cycle_tracking_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
