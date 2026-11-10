package ro.utcn.bettercycle.domain.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.utcn.bettercycle.data.local.CyclingDao
import ro.utcn.bettercycle.data.local.UserProfileEntity
import ro.utcn.bettercycle.data.sensors.HealthConnectManager
import kotlin.math.roundToInt

data class AiChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class LocalCyclingAiAgent(
    private val context: Context,
    private val cyclingDao: CyclingDao,
    private val healthConnectManager: HealthConnectManager
) {

    suspend fun askAgent(
        userQuestion: String,
        liveHr: Int,
        liveSpeedKmh: Float,
        liveCadence: Int,
        liveFatigue: Float
    ): String = withContext(Dispatchers.IO) {
        val profile = cyclingDao.getUserProfileOnce() ?: UserProfileEntity()
        val aggregates = cyclingDao.getHistoricalAggregates()
        val recentRides = cyclingDao.getRecentRidesOnce()
        val healthConnectHr = healthConnectManager.getRecentRestingAndAvgHeartRate()

        val effectiveRestingHr = healthConnectHr?.first ?: profile.restingHeartRate
        // Formula Tanaka (2001): HRmax = 208 - 0.7 * varsta
        val theoreticalMaxHr = (208 - (0.7 * profile.age)).roundToInt()
        val zone2Min = (effectiveRestingHr + 0.60 * (theoreticalMaxHr - effectiveRestingHr)).roundToInt()
        val zone2Max = (effectiveRestingHr + 0.70 * (theoreticalMaxHr - effectiveRestingHr)).roundToInt()
        val zone4Threshold = (effectiveRestingHr + 0.85 * (theoreticalMaxHr - effectiveRestingHr)).roundToInt()

        val activeHrToAnalyze = if (liveHr > 0) liveHr else aggregates.overallAvgHr.roundToInt()

        // Motorul Conversational On-Device (RAG Local peste Room DB + BLE + Health Connect)
        val q = userQuestion.lowercase()
        val sb = StringBuilder()

        when {
            q.contains("puls") || q.contains("cardiac") || q.contains("bpm") || q.contains("varsta") || q.contains("normal") || q.contains("elevat") -> {
                sb.appendLine("📊 **Analiză Puls & Context Fiziologic (${profile.age} ani):**")
                sb.appendLine("• **Pulsul tău de repaus:** $effectiveRestingHr BPM (${evaluateRestingHrForAge(effectiveRestingHr, profile.age)}).")
                healthConnectHr?.let {
                    sb.appendLine("• **Date Google Health Connect (7 zile):** Repaus ${it.first} BPM | Medie zilnică ${it.second} BPM.")
                }
                sb.appendLine("• **Pulsul maxim estimat pentru ${profile.age} ani (Tanaka):** $theoreticalMaxHr BPM.")
                sb.appendLine("• **Zona ta optimă de anduranță (Zona 2 Karvonen):** $zone2Min – $zone2Max BPM.")

                if (activeHrToAnalyze > 0) {
                    val pctOfMax = ((activeHrToAnalyze.toFloat() / theoreticalMaxHr) * 100).roundToInt()
                    val evaluation = when {
                        activeHrToAnalyze > zone4Threshold -> "**ELEVAT ($activeHrToAnalyze BPM, $pctOfMax% din HRmax)**. Te afli în zona anaerobă / prag lactat. Pentru vârsta ta de ${profile.age} ani, menținerea acestui puls pe durate lungi accelerează epuizarea glicogenului."
                        activeHrToAnalyze in zone2Min..zone2Max -> "**OPTIM ($activeHrToAnalyze BPM, $pctOfMax% din HRmax)**. Este exact în Zona 2 aerobă pentru ${profile.age} ani — ideal pentru arderea lipidelor și creșterea densității mitocondriale."
                        activeHrToAnalyze < zone2Min -> "**SCĂZUT / RECUPERARE ($activeHrToAnalyze BPM, $pctOfMax% din HRmax)**. Efortul este lejer pentru grupa ta de vârstă; poți crește ritmul dacă urmărești progresul cardio."
                        else -> "**MODERAT-INTENS ($activeHrToAnalyze BPM, $pctOfMax% din HRmax, Zona 3 Tempo)**. Valoare normală pentru efort susținut pe bicicletă la ${profile.age} ani."
                    }
                    sb.appendLine("• **Evaluare valoare analizată:** $evaluation")
                } else {
                    sb.appendLine("• Nu ai încă date de puls din cursă. Conectează centura BLE sau apasă pe butonul de simulare telemetrie.")
                }

                sb.appendLine("\n💡 **Cum poți îmbunătăți aceste valori:**")
                sb.appendLine("1. Petrece 75-80% din timpul săptămânal pe bicicletă în **Zona 2 ($zone2Min–$zone2Max BPM)** pentru a scădea pulsul la aceeași viteză.")
                sb.appendLine("2. Menține o cadență de **85–90 RPM** în loc să apeși greu în pedale la turații joase.")
            }

            q.contains("vitez") || q.contains("cadent") || q.contains("performant") || q.contains("imbunatat") -> {
                val avgSpd = if (aggregates.totalRides > 0) aggregates.overallAvgSpeedKmh else liveSpeedKmh
                val avgCad = if (aggregates.totalRides > 0) aggregates.overallAvgCadence.roundToInt() else liveCadence
                sb.appendLine("🚴 **Analiză Performanță, Viteză & Cadență:**")
                sb.appendLine("• **Istoric local Room DB:** ${aggregates.totalRides} curse (${"%.1f".format(aggregates.totalDistanceKm)} km total).")
                sb.appendLine("• **Viteză medie analizată:** ${"%.1f".format(avgSpd)} km/h | **Cadență medie:** $avgCad RPM.")

                val cadenceEval = when {
                    avgCad in 85..95 -> "excelentă (reduce stresul articular pe genunchi și transferă efortul spre sistemul cardiovascular)"
                    avgCad in 1..84 -> "ușor scăzută (*grinding* — forțezi musculatura cvadricepsului și crești oboseala prematură)"
                    avgCad == 0 -> "inactivă momentan (pornește cursa sau simularea BLE)"
                    else -> "ridicată (bună pe plat, dar ai grijă la stabilitatea în șa)"
                }
                sb.appendLine("• **Evaluare cadență:** Valoarea de $avgCad RPM este $cadenceEval.")
                sb.appendLine("\n💡 **Plan de optimizare:**")
                sb.appendLine("1. **Antrenament de Cadență (Spin-ups):** Include 4 serii a câte 1 minut la 95-100 RPM pe teren plat.")
                sb.appendLine("2. **Pacing pe cățărare:** Când panta trece de 5%, folosește indicatorul ML din modul Cockpit HUD pentru a schimba din timp pe un pinion ușor.")
            }

            q.contains("oboseal") || q.contains("eta") || q.contains("recuperar") -> {
                val fatiguePct = (liveFatigue * 100).roundToInt()
                sb.appendLine("🧠 **Analiză Oboseală Neuromusculară & Recuperare (TFLite + NNAPI):**")
                sb.appendLine("• **Scor curent de oboseală:** $fatiguePct%.")
                val fatigueEval = when {
                    fatiguePct >= 75 -> "RIDICAT — risc de scădere a puterii și acumulare rapidă de lactat. Redu intensitatea în Zona 2 ($zone2Min–$zone2Max BPM) și hidratează-te (500–700 ml/oră cu electroliți)."
                    fatiguePct in 40..74 -> "MODERAT — ritm sustenabil. Păstrează o cadență constantă și consumă 30-45g carbohidrați/oră dacă tura depășește 60 minute."
                    else -> "SCĂZUT — rezerve energetice optime pentru intervale sau urcări susținute."
                }
                sb.appendLine("• **Recomandare tactică:** $fatigueEval")
            }

            else -> {
                sb.appendLine("🤖 **Raport Sintetic BetterCycle (100% On-Device):**")
                sb.appendLine("• **Profil:** ${profile.name}, ${profile.age} ani (HRmax estimat: $theoreticalMaxHr BPM, Puls repaus: $effectiveRestingHr BPM).")
                sb.appendLine("• **Baza de date locală:** ${aggregates.totalRides} curse salvate, distanță totală ${"%.1f".format(aggregates.totalDistanceKm)} km, viteză medie ${"%.1f".format(aggregates.overallAvgSpeedKmh)} km/h.")
                if (recentRides.isNotEmpty()) {
                    val last = recentRides.first()
                    sb.appendLine("• **Ultima cursă:** ${"%.2f".format(last.distanceMeters / 1000f)} km cu ${"%.1f".format(last.avgSpeedKmh)} km/h și puls mediu ${last.avgHeartRate} BPM.")
                }
                sb.appendLine("• **Oboseală curentă estimată (TFLite + NNAPI):** ${(liveFatigue * 100).roundToInt()}%.")
                sb.appendLine("\nÎntreabă-mă specific despre *pulsul pentru vârsta ta*, *cum să îți îmbunătățești viteza și cadența* sau *ce indică nivelul de oboseală*!")
            }
        }

        sb.appendLine("\n---")
        sb.append("⚠️ *Notă: Aceste interpretări sunt generate local pe dispozitiv cu rol orientativ și nu înlocuiesc evaluarea sau recomandările unui medic.*")
        sb.toString()
    }

    private fun evaluateRestingHrForAge(rhr: Int, age: Int): String {
        return when {
            rhr < 55 -> "nivel atletic excelent pentru $age ani"
            rhr in 55..68 -> "normal-optim pentru $age ani"
            rhr in 69..80 -> "în limite normale pentru $age ani, cu loc de optimizare aerobă"
            else -> "elevat pentru repaus la $age ani — verifică hidratarea și nivelul de recuperare"
        }
    }
}
