package ro.utcn.bettercycle.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class HistoricalAggregates(
    val totalRides: Int,
    val totalDistanceKm: Float,
    val overallAvgSpeedKmh: Float,
    val overallAvgHr: Float,
    val peakHrRecorded: Int,
    val overallAvgCadence: Float
)

@Dao
interface CyclingDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity)

    @Insert
    suspend fun insertRide(ride: RideEntity): Long

    @Update
    suspend fun updateRide(ride: RideEntity)

    @Insert
    suspend fun insertTelemetryPoint(point: TelemetryPointEntity)

    @Query("SELECT * FROM rides ORDER BY startTimeMillis DESC")
    fun getAllRidesFlow(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides ORDER BY startTimeMillis DESC LIMIT 10")
    suspend fun getRecentRidesOnce(): List<RideEntity>

    @Query("SELECT * FROM telemetry_points WHERE rideOwnerId = :rideId ORDER BY timestampMillis ASC")
    suspend fun getTelemetryForRide(rideId: Long): List<TelemetryPointEntity>

    @Query("""
        SELECT 
            COUNT(*) as totalRides,
            COALESCE(SUM(distanceMeters) / 1000.0, 0) as totalDistanceKm,
            COALESCE(AVG(avgSpeedKmh), 0) as overallAvgSpeedKmh,
            COALESCE(AVG(avgHeartRate), 0) as overallAvgHr,
            COALESCE(MAX(maxHeartRate), 0) as peakHrRecorded,
            COALESCE(AVG(avgCadenceRpm), 0) as overallAvgCadence
        FROM rides
    """)
    suspend fun getHistoricalAggregates(): HistoricalAggregates
}
