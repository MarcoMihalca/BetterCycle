package ro.utcn.bettercycle.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Marco",
    val age: Int = 22,
    val weightKg: Float = 72f,
    val restingHeartRate: Int = 60,
    val vo2MaxEstimate: Float = 48.5f,
    val emergencyContactPhone: String = "112"
)

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val rideId: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val elevationGainMeters: Float,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val avgCadenceRpm: Int,
    val caloriesBurned: Int,
    val avgFatigueScore: Float // Calculat de modelul TFLite (0.0 - 1.0)
)

@Entity(
    tableName = "telemetry_points",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["rideId"],
            childColumns = ["rideOwnerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rideOwnerId")]
)
data class TelemetryPointEntity(
    @PrimaryKey(autoGenerate = true) val pointId: Long = 0,
    val rideOwnerId: Long,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedKmh: Float,
    val slopePercent: Float,
    val heartRateBpm: Int,
    val cadenceRpm: Int,
    val predictedFatigue: Float
)
