package ro.utcn.bettercycle.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ro.utcn.bettercycle.domain.ml.CyclingMlEngine
import kotlin.math.sqrt

class CrashDetectionManager(
    context: Context,
    private val mlEngine: CyclingMlEngine
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _crashDetected = MutableStateFlow(false)
    val crashDetected: StateFlow<Boolean> = _crashDetected

    private val _lastPeakGForce = MutableStateFlow(1.0f)
    val lastPeakGForce: StateFlow<Float> = _lastPeakGForce

    private var latestGyroMagnitude = 0f
    private val windowSvm = FloatArray(10) { 1.0f }
    private var windowIndex = 0

    fun startMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                latestGyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                // Conversie în multiplii de g (9.81 m/s^2)
                val gForce = sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
                windowSvm[windowIndex] = gForce
                windowIndex = (windowIndex + 1) % windowSvm.size

                if (gForce > _lastPeakGForce.value) {
                    _lastPeakGForce.value = gForce
                }

                // Dacă apare un spike de accelerație mai mare de 2.8G, interogăm modelul TFLite
                if (gForce > 2.8f && !_crashDetected.value) {
                    val variance = calculateVariance(windowSvm)
                    val crashProbability = mlEngine.predictCrashProbability(
                        peakGForce = gForce,
                        gyroRadPerSec = latestGyroMagnitude,
                        postImpactVariance = variance
                    )
                    if (crashProbability > 0.72f) {
                        _crashDetected.value = true
                    }
                }
            }
        }
    }

    fun triggerSimulatedCrash() {
        _lastPeakGForce.value = 4.6f
        _crashDetected.value = true
    }

    fun dismissCrashAlert() {
        _crashDetected.value = false
        _lastPeakGForce.value = 1.0f
    }

    private fun calculateVariance(values: FloatArray): Float {
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
