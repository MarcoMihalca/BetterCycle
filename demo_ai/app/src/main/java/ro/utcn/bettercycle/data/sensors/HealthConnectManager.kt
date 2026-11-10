package ro.utcn.bettercycle.data.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    private val _simulatedHealthConnectData = MutableStateFlow<Pair<Int, Int>?>(null)
    val syncStatus = MutableStateFlow("Nesincronizat (Foloseste valoarea din profil sau sincronizeaza)")

    fun isAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getRecentRestingAndAvgHeartRate(): Pair<Int, Int>? {
        _simulatedHealthConnectData.value?.let { return it }
        if (!isAvailable()) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(permissions)) return null

            val end = Instant.now()
            val start = end.minus(7, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val allSamples = response.records.flatMap { it.samples }.map { it.beatsPerMinute.toInt() }
            if (allSamples.isEmpty()) return null
            val minResting = allSamples.minOrNull() ?: 60
            val avg = allSamples.average().toInt()
            syncStatus.value = "Sincronizat din Google Health Connect (Repaus: $minResting BPM, Medie 7 zile: $avg BPM)"
            Pair(minResting, avg)
        } catch (e: Exception) {
            null
        }
    }

    fun simulateHealthConnectSync(restingBpm: Int = 56, weeklyAvgBpm: Int = 74) {
        _simulatedHealthConnectData.value = Pair(restingBpm, weeklyAvgBpm)
        syncStatus.value = "Google Health Connect Sincronizat: Puls repaus $restingBpm BPM | Medie 7 zile $weeklyAvgBpm BPM"
    }
}
