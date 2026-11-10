package ro.utcn.bettercycle.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ro.utcn.bettercycle.data.gpx.GpxManager
import ro.utcn.bettercycle.data.gpx.ImportedGpxSummary
import ro.utcn.bettercycle.data.local.*
import ro.utcn.bettercycle.data.sensors.BleHeartRateManager
import ro.utcn.bettercycle.data.sensors.CrashDetectionManager
import ro.utcn.bettercycle.data.sensors.HealthConnectManager
import ro.utcn.bettercycle.domain.agent.AiChatMessage
import ro.utcn.bettercycle.domain.agent.LocalCyclingAiAgent
import ro.utcn.bettercycle.domain.ml.CyclingMlEngine
import ro.utcn.bettercycle.domain.ml.FatigueAndPacingPrediction
import ro.utcn.bettercycle.service.CyclingForegroundService
import ro.utcn.bettercycle.service.LiveGpsMetrics
import kotlin.math.roundToInt

class CyclingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.cyclingDao()

    val bleManager = BleHeartRateManager(application)
    val healthConnectManager = HealthConnectManager(application)
    val mlEngine = CyclingMlEngine(application)
    val crashManager = CrashDetectionManager(application, mlEngine)
    private val aiAgent = LocalCyclingAiAgent(application, dao, healthConnectManager)

    val userProfile: StateFlow<UserProfileEntity> = dao.getUserProfileFlow()
        .map { it ?: UserProfileEntity() }
        .stateIn(viewModelScope, SharingStarted.Lazily, UserProfileEntity())

    val allRides: StateFlow<List<RideEntity>> = dao.getAllRidesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _gpsMetrics = MutableStateFlow(LiveGpsMetrics())
    val gpsMetrics: StateFlow<LiveGpsMetrics> = _gpsMetrics

    private val _importedGpx = MutableStateFlow<ImportedGpxSummary?>(null)
    val importedGpx: StateFlow<ImportedGpxSummary?> = _importedGpx

    private val _mlPrediction = MutableStateFlow(
        FatigueAndPacingPrediction(0.12f, 88, 42, "Pregatit de cursa. NNAPI activ.", true)
    )
    val mlPrediction: StateFlow<FatigueAndPacingPrediction> = _mlPrediction

    private val _chatMessages = MutableStateFlow(
        listOf(
            AiChatMessage(
                text = "Salut! Sunt agentul tău AI local din BetterCycle. Toate datele tale din Room DB, senzorii BLE și Google Health Connect rămân 100% pe telefon. Întreabă-mă dacă valorile tale de puls sau viteză sunt normale pentru vârsta ta sau cum le poți îmbunătăți!",
                isFromUser = false
            )
        )
    )
    val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus

    private var activeRideId: Long? = null
    private var foregroundService: CyclingForegroundService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? CyclingForegroundService.LocalBinder ?: return
            foregroundService = localBinder.getService()
            viewModelScope.launch {
                foregroundService?.gpsMetrics?.collect { metrics ->
                    _gpsMetrics.value = metrics
                    if (metrics.isTracking) {
                        runRealtimeMlAndPersistTelemetry(metrics)
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService = null
        }
    }

    init {
        viewModelScope.launch {
            if (dao.getUserProfileOnce() == null) {
                dao.saveUserProfile(UserProfileEntity())
            }
        }
        Intent(application, CyclingForegroundService::class.java).also { intent ->
            application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun startRide() {
        val ctx = getApplication<Application>()
        val serviceIntent = Intent(ctx, CyclingForegroundService::class.java)
        try {
            ctx.startForegroundService(serviceIntent)
        } catch (_: Exception) {
        }
        crashManager.startMonitoring()

        viewModelScope.launch {
            activeRideId = dao.insertRide(
                RideEntity(
                    startTimeMillis = System.currentTimeMillis(),
                    endTimeMillis = 0L,
                    durationSeconds = 0L,
                    distanceMeters = 0f,
                    avgSpeedKmh = 0f,
                    maxSpeedKmh = 0f,
                    elevationGainMeters = 0f,
                    avgHeartRate = 0,
                    maxHeartRate = 0,
                    avgCadenceRpm = 0,
                    caloriesBurned = 0,
                    avgFatigueScore = 0f
                )
            )
            foregroundService?.startRideTracking()
            _gpsMetrics.value = _gpsMetrics.value.copy(isTracking = true)
        }
    }

    fun stopRide() {
        val metrics = _gpsMetrics.value
        val hr = bleManager.heartRateBpm.value
        val cad = bleManager.cadenceRpm.value
        val fatigue = _mlPrediction.value.fatigueIndex

        foregroundService?.stopRideTracking()
        crashManager.stopMonitoring()
        _gpsMetrics.value = metrics.copy(isTracking = false)

        viewModelScope.launch {
            val idToUpdate = activeRideId ?: dao.insertRide(
                RideEntity(
                    startTimeMillis = System.currentTimeMillis() - (metrics.elapsedSeconds * 1000L),
                    endTimeMillis = System.currentTimeMillis(),
                    durationSeconds = metrics.elapsedSeconds,
                    distanceMeters = metrics.distanceMeters,
                    avgSpeedKmh = metrics.avgSpeedKmh,
                    maxSpeedKmh = metrics.maxSpeedKmh,
                    elevationGainMeters = metrics.elevationGainMeters,
                    avgHeartRate = if (hr > 0) hr else 142,
                    maxHeartRate = if (hr > 0) hr + 14 else 168,
                    avgCadenceRpm = if (cad > 0) cad else 84,
                    caloriesBurned = ((metrics.elapsedSeconds / 60f) * 9.5f).roundToInt(),
                    avgFatigueScore = fatigue
                )
            )

            val calories = ((metrics.elapsedSeconds / 60f) * 9.5f).roundToInt()
            dao.updateRide(
                RideEntity(
                    rideId = idToUpdate,
                    startTimeMillis = System.currentTimeMillis() - (metrics.elapsedSeconds * 1000L),
                    endTimeMillis = System.currentTimeMillis(),
                    durationSeconds = metrics.elapsedSeconds,
                    distanceMeters = metrics.distanceMeters,
                    avgSpeedKmh = metrics.avgSpeedKmh,
                    maxSpeedKmh = metrics.maxSpeedKmh,
                    elevationGainMeters = metrics.elevationGainMeters,
                    avgHeartRate = if (hr > 0) hr else 142,
                    maxHeartRate = if (hr > 0) hr + 14 else 168,
                    avgCadenceRpm = if (cad > 0) cad else 84,
                    caloriesBurned = calories,
                    avgFatigueScore = fatigue
                )
            )
            activeRideId = null
        }
    }

    private suspend fun runRealtimeMlAndPersistTelemetry(metrics: LiveGpsMetrics) {
        val profile = userProfile.value
        val maxHr = (208 - (0.7 * profile.age)).roundToInt()
        val currentHr = if (bleManager.heartRateBpm.value > 0) bleManager.heartRateBpm.value else 145
        val currentCad = if (bleManager.cadenceRpm.value > 0) bleManager.cadenceRpm.value else 85
        val targetDistKm = _importedGpx.value?.totalDistanceKm ?: 15f
        val remainingKm = (targetDistKm - (metrics.distanceMeters / 1000f)).coerceAtLeast(1f)

        val prediction = mlEngine.predictFatigueAndPacing(
            slopePercent = metrics.currentSlopePercent,
            durationMinutes = metrics.elapsedSeconds / 60f,
            currentHr = currentHr,
            restingHr = profile.restingHeartRate,
            maxHrForAge = maxHr,
            currentCadence = currentCad,
            currentSpeedKmh = metrics.currentSpeedKmh,
            remainingDistanceKm = remainingKm
        )
        _mlPrediction.value = prediction

        val rideId = activeRideId ?: dao.insertRide(
            RideEntity(
                startTimeMillis = System.currentTimeMillis(),
                endTimeMillis = 0L,
                durationSeconds = 0L,
                distanceMeters = 0f,
                avgSpeedKmh = 0f,
                maxSpeedKmh = 0f,
                elevationGainMeters = 0f,
                avgHeartRate = currentHr,
                maxHeartRate = currentHr,
                avgCadenceRpm = currentCad,
                caloriesBurned = 0,
                avgFatigueScore = prediction.fatigueIndex
            )
        ).also { activeRideId = it }

        dao.insertTelemetryPoint(
            TelemetryPointEntity(
                rideOwnerId = rideId,
                timestampMillis = System.currentTimeMillis(),
                latitude = metrics.latitude,
                longitude = metrics.longitude,
                altitudeMeters = metrics.altitudeMeters,
                speedKmh = metrics.currentSpeedKmh,
                slopePercent = metrics.currentSlopePercent,
                heartRateBpm = currentHr,
                cadenceRpm = currentCad,
                predictedFatigue = prediction.fatigueIndex
            )
        )
    }

    // Simulare telemetrie completă pentru demonstrarea la laborator/prezentare
    fun simulateDemoTelemetryStep() {
        val randomHr = (138..176).random()
        val randomCad = (72..94).random()
        val randomSlope = listOf(-2.0f, 0.5f, 3.8f, 7.2f, 9.5f).random()
        val randomSpeed = (18..36).random().toFloat()

        bleManager.simulateTelemetry(randomHr, randomCad)

        val current = _gpsMetrics.value
        val newLat = current.latitude + 0.00045
        val newLon = current.longitude + 0.00060
        _gpsMetrics.value = current.copy(
            isTracking = true,
            latitude = newLat,
            longitude = newLon,
            currentSpeedKmh = randomSpeed,
            avgSpeedKmh = if (current.avgSpeedKmh > 0f) (current.avgSpeedKmh + randomSpeed) / 2f else randomSpeed,
            maxSpeedKmh = maxOf(current.maxSpeedKmh, randomSpeed),
            distanceMeters = current.distanceMeters + 450f,
            elevationGainMeters = current.elevationGainMeters + if (randomSlope > 0) 14.5f else 0f,
            currentSlopePercent = randomSlope,
            elapsedSeconds = current.elapsedSeconds + 90L,
            routeCoordinates = current.routeCoordinates + Pair(newLat, newLon)
        )

        viewModelScope.launch {
            runRealtimeMlAndPersistTelemetry(_gpsMetrics.value)
        }
    }

    fun sendQuestionToAi(question: String) {
        if (question.isBlank()) return
        _chatMessages.value = _chatMessages.value + AiChatMessage(question, isFromUser = true)

        viewModelScope.launch {
            val response = aiAgent.askAgent(
                userQuestion = question,
                liveHr = bleManager.heartRateBpm.value,
                liveSpeedKmh = _gpsMetrics.value.currentSpeedKmh,
                liveCadence = bleManager.cadenceRpm.value,
                liveFatigue = _mlPrediction.value.fatigueIndex
            )
            _chatMessages.value = _chatMessages.value + AiChatMessage(response, isFromUser = false)
        }
    }

    fun updateUserProfile(name: String, age: Int, weightKg: Float, restingHr: Int) {
        viewModelScope.launch {
            val current = userProfile.value
            dao.saveUserProfile(
                current.copy(
                    name = name.ifBlank { "Marco" },
                    age = age,
                    weightKg = weightKg,
                    restingHeartRate = restingHr
                )
            )
        }
    }

    fun exportLastRideToGpx() {
        viewModelScope.launch {
            val latestRide = allRides.value.firstOrNull()
            if (latestRide == null) {
                _exportStatus.value = "Nu exista inca o cursa finalizata pentru export .gpx. Simuleaza si opreste o cursa mai intai!"
                return@launch
            }
            val points = dao.getTelemetryForRide(latestRide.rideId)
            val file = GpxManager.exportRideToGpx(getApplication(), latestRide.rideId, points)
            _exportStatus.value = "Exportat GPX 1.1: ${file.absolutePath}"
            delay(6000)
            _exportStatus.value = null
        }
    }

    fun importDemoMountainGpxRoute() {
        viewModelScope.launch {
            val summary = GpxManager.loadSampleMountainGpxRoute()
            _importedGpx.value = summary
            val coords = summary.points.map { Pair(it.lat, it.lon) }
            _gpsMetrics.value = _gpsMetrics.value.copy(
                currentSlopePercent = summary.avgSlopePercent,
                routeCoordinates = coords
            )
            runRealtimeMlAndPersistTelemetry(_gpsMetrics.value)
            _exportStatus.value = "Traseu GPX importat: ${summary.routeName} (${summary.totalDistanceKm} km, +${summary.totalElevationGainMeters.toInt()}m, ETA ML recalculat)"
        }
    }

    override fun onCleared() {
        super.onCleared()
        mlEngine.close()
    }
}
