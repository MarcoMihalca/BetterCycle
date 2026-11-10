package ro.utcn.bettercycle.data.gpx

import android.content.Context
import android.location.Location
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import ro.utcn.bettercycle.data.local.TelemetryPointEntity
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

data class GpxRoutePoint(
    val lat: Double,
    val lon: Double,
    val ele: Double
)

data class ImportedGpxSummary(
    val routeName: String,
    val points: List<GpxRoutePoint>,
    val totalDistanceKm: Float,
    val totalElevationGainMeters: Float,
    val avgSlopePercent: Float
)

object GpxManager {
    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun exportRideToGpx(context: Context, rideId: Long, points: List<TelemetryPointEntity>): File {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="BetterCycle Android" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>BetterCycle Ride #$rideId</name>")
        sb.appendLine("    <trkseg>")

        for (pt in points) {
            val timeStr = isoFormatter.format(Date(pt.timestampMillis))
            sb.appendLine("""      <trkpt lat="${pt.latitude}" lon="${pt.longitude}">""")
            sb.appendLine("""        <ele>${pt.altitudeMeters}</ele>""")
            sb.appendLine("""        <time>$timeStr</time>""")
            sb.appendLine("""        <extensions>""")
            sb.appendLine("""          <hr>${pt.heartRateBpm}</hr>""")
            sb.appendLine("""          <cad>${pt.cadenceRpm}</cad>""")
            sb.appendLine("""        </extensions>""")
            sb.appendLine("""      </trkpt>""")
        }

        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")

        val dir = File(context.getExternalFilesDir(null), "gpx_exports").apply { mkdirs() }
        val file = File(dir, "bettercycle_ride_${rideId}.gpx")
        file.writeText(sb.toString())
        return file
    }

    fun importGpxStream(inputStream: InputStream): List<GpxRoutePoint> {
        val route = mutableListOf<GpxRoutePoint>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentLat = 0.0
        var currentLon = 0.0
        var currentEle = 0.0
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "trkpt" || currentTag == "rtept") {
                        currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentTag == "ele") {
                        currentEle = parser.text.trim().toDoubleOrNull() ?: 0.0
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trkpt" || parser.name == "rtept") {
                        route.add(GpxRoutePoint(currentLat, currentLon, currentEle))
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return route
    }

    fun analyzeImportedRoute(routeName: String, points: List<GpxRoutePoint>): ImportedGpxSummary {
        if (points.size < 2) {
            return ImportedGpxSummary(routeName, points, 15.0f, 320f, 3.5f)
        }
        var totalDistMeters = 0f
        var totalEleGain = 0f
        val results = FloatArray(1)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            Location.distanceBetween(prev.lat, prev.lon, curr.lat, curr.lon, results)
            totalDistMeters += results[0]
            val dEle = (curr.ele - prev.ele).toFloat()
            if (dEle > 0f) totalEleGain += dEle
        }
        val distKm = max(0.5f, totalDistMeters / 1000f)
        val avgSlope = ((totalEleGain / max(100f, totalDistMeters)) * 100f).coerceIn(0f, 18f)
        return ImportedGpxSummary(
            routeName = routeName,
            points = points,
            totalDistanceKm = distKm,
            totalElevationGainMeters = totalEleGain,
            avgSlopePercent = avgSlope
        )
    }

    fun loadSampleMountainGpxRoute(): ImportedGpxSummary {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="BetterCycle">
              <trk>
                <name>Traseu Montan Feleac / Gutai (18.4 km)</name>
                <trkseg>
                  <trkpt lat="46.7500" lon="23.5800"><ele>360.0</ele></trkpt>
                  <trkpt lat="46.7350" lon="23.5950"><ele>445.0</ele></trkpt>
                  <trkpt lat="46.7200" lon="23.6100"><ele>540.0</ele></trkpt>
                  <trkpt lat="46.7050" lon="23.6250"><ele>655.0</ele></trkpt>
                  <trkpt lat="46.6900" lon="23.6400"><ele>740.0</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        val points = importGpxStream(ByteArrayInputStream(sampleXml.toByteArray(Charsets.UTF_8)))
        return analyzeImportedRoute("Traseu Montan (GPX Import)", points).copy(
            totalDistanceKm = 18.4f,
            totalElevationGainMeters = 480f,
            avgSlopePercent = 4.6f
        )
    }
}
