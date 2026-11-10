package ro.utcn.bettercycle.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ro.utcn.bettercycle.domain.ml.FatigueAndPacingPrediction
import ro.utcn.bettercycle.service.LiveGpsMetrics

class MainActivity : ComponentActivity() {

    private val viewModel: CyclingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF00B0FF),
                    background = Color(0xFF101418),
                    surface = Color(0xFF1B2228)
                )
            ) {
                BetterCycleApp(viewModel)
            }
        }
    }
}

@Composable
fun BetterCycleApp(viewModel: CyclingViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var highContrastHudMode by remember { mutableStateOf(false) }

    val gpsMetrics by viewModel.gpsMetrics.collectAsState()
    val mlPrediction by viewModel.mlPrediction.collectAsState()
    val heartRate by viewModel.bleManager.heartRateBpm.collectAsState()
    val cadence by viewModel.bleManager.cadenceRpm.collectAsState()
    val bleStatus by viewModel.bleManager.connectionState.collectAsState()
    val crashDetected by viewModel.crashManager.crashDetected.collectAsState()
    val peakGForce by viewModel.crashManager.lastPeakGForce.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Dialog SOS automat la detectarea unui accident
    if (crashDetected) {
        CrashSosDialog(
            peakG = peakGForce,
            onDismiss = { viewModel.crashManager.dismissCrashAlert() }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (highContrastHudMode) Color.Black else Color(0xFF1B2228))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🚴 BetterCycle",
                        color = if (highContrastHudMode) Color.Yellow else Color(0xFF00E676),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "On-Device TFLite (NNAPI) • 100% Privacy",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
                FilterChip(
                    selected = highContrastHudMode,
                    onClick = { highContrastHudMode = !highContrastHudMode },
                    label = { Text(if (highContrastHudMode) "SUN HUD: ON" else "Mod Ghidon HUD") },
                    leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null) }
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF161C22)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    label = { Text("Cockpit HUD") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                    label = { Text("Agent AI") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Route, contentDescription = null) },
                    label = { Text("Curse & GPX") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                    label = { Text("Senzori & Profil") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (highContrastHudMode) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> BikeCockpitHudScreen(
                    metrics = gpsMetrics,
                    mlPrediction = mlPrediction,
                    heartRate = heartRate,
                    cadence = cadence,
                    highContrast = highContrastHudMode,
                    onStartRide = { viewModel.startRide() },
                    onStopRide = { viewModel.stopRide() },
                    onSimulateStep = { viewModel.simulateDemoTelemetryStep() }
                )
                1 -> AiAssistantScreen(viewModel)
                2 -> RidesAndGpxScreen(viewModel)
                3 -> SensorsAndProfileScreen(
                    viewModel = viewModel,
                    bleStatus = bleStatus
                )
            }
        }
    }
}

@Composable
fun BikeCockpitHudScreen(
    metrics: LiveGpsMetrics,
    mlPrediction: FatigueAndPacingPrediction,
    heartRate: Int,
    cadence: Int,
    highContrast: Boolean,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit,
    onSimulateStep: () -> Unit
) {
    val primaryTextColor = if (highContrast) Color(0xFFFFFF00) else Color.White
    val cardBg = if (highContrast) Color.Black else Color(0xFF1B2228)
    val borderMod = if (highContrast) Modifier.border(2.dp, Color.Yellow, RoundedCornerShape(14.dp)) else Modifier

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Viteza Gigant (Lizibilitate maxima pe ghidon in lumina solara)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(borderMod),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "VITEZA CURENTA (KM/H)",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "%.1f".format(metrics.currentSpeedKmh),
                        color = primaryTextColor,
                        fontSize = if (highContrast) 76.sp else 64.sp,
                        fontWeight = FontWeight.Black
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("Medie: ${"%.1f".format(metrics.avgSpeedKmh)} km/h", color = Color.LightGray)
                        Text("Max: ${"%.1f".format(metrics.maxSpeedKmh)} km/h", color = Color.LightGray)
                        Text("Panta: ${"%.1f".format(metrics.currentSlopePercent)}%", color = Color(0xFF00E676))
                    }
                }
            }
        }

        // Grila 2x2: Puls BLE, Cadenta, Distanta, Elevatie
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HudMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "PULS (BLE)",
                    value = if (heartRate > 0) "$heartRate" else "--",
                    unit = "BPM",
                    accentColor = Color(0xFFFF5252),
                    highContrast = highContrast
                )
                HudMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "CADENTA / TINTA ML",
                    value = "${if (cadence > 0) cadence else "--"} / ${mlPrediction.optimalCadenceRpm}",
                    unit = "RPM",
                    accentColor = Color(0xFF00B0FF),
                    highContrast = highContrast
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HudMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "DISTANTA",
                    value = "%.2f".format(metrics.distanceMeters / 1000f),
                    unit = "KM",
                    accentColor = Color(0xFF00E676),
                    highContrast = highContrast
                )
                HudMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "ELEVATIE + / ETA ML",
                    value = "+${metrics.elevationGainMeters.toInt()}m",
                    unit = "ETA Traseu: ${mlPrediction.dynamicEtaMinutes} min",
                    accentColor = Color(0xFFFFD740),
                    highContrast = highContrast
                )
            }
        }

        // Card Predictii TensorFlow Lite (NNAPI) in Timp Real
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(borderMod),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🧠 TFLite Fatigue & Pacing (NNAPI)",
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Oboseala: ${(mlPrediction.fatigueIndex * 100).toInt()}%",
                            color = if (mlPrediction.fatigueIndex > 0.7f) Color(0xFFFF5252) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { mlPrediction.fatigueIndex },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = if (mlPrediction.fatigueIndex > 0.7f) Color(0xFFFF5252) else Color(0xFF00E676)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mlPrediction.tacticalAdvice,
                        color = primaryTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Harta Vectoriala Live GPS Trace (Functioneaza 100% offline fara cheie Google Maps)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12181F))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LiveRouteCanvas(route = metrics.routeCoordinates)
                    Text(
                        text = "Traseu GPS Live (${metrics.routeCoordinates.size} pct) • Lat ${"%.4f".format(metrics.latitude)}, Lon ${"%.4f".format(metrics.longitude)}",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            }
        }

        // Controale Start / Stop Cursa & Simulare Telemetrie
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!metrics.isTracking) {
                    Button(
                        onClick = onStartRide,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text("START CURSA (GPS)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStopRide,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("OPRESTE & SALVEAZA", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onSimulateStep,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Simuleaza Date BLE/GPS")
                }
            }
        }
    }
}

@Composable
fun HudMetricTile(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    highContrast: Boolean
) {
    Card(
        modifier = modifier.then(
            if (highContrast) Modifier.border(2.dp, accentColor, RoundedCornerShape(12.dp)) else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = if (highContrast) Color.Black else Color(0xFF1B2228))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text = value,
                color = if (highContrast) Color.White else accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(unit, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun LiveRouteCanvas(route: List<Pair<Double, Double>>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (route.size < 2) {
            drawCircle(color = Color(0xFF00E676), radius = 10f, center = center)
            return@Canvas
        }
        val minLat = route.minOf { it.first }
        val maxLat = route.maxOf { it.first }
        val minLon = route.minOf { it.second }
        val maxLon = route.maxOf { it.second }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)

        val path = Path()
        route.forEachIndexed { idx, (lat, lon) ->
            val x = (((lon - minLon) / lonSpan) * size.width).toFloat()
            val y = (size.height - (((lat - minLat) / latSpan) * size.height)).toFloat()
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color(0xFF00E676),
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AiAssistantScreen(viewModel: CyclingViewModel) {
    val messages by viewModel.chatMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val quickPrompts = listOf(
        "Valorile mele de puls sunt normale sau elevate pentru varsta mea?",
        "Cum imi pot imbunatati viteza si cadenta?",
        "Ce indica nivelul meu curent de oboseala?",
        "Fa-mi un rezumat al curselor mele din baza de date"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "🤖 Agent AI Personal (100% On-Device Privacy)",
            color = Color(0xFF00E676),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "Analizează datele din Room DB, BLE și Google Health Connect fără a le trimite pe niciun server extern.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickPrompts) { prompt ->
                AssistChip(
                    onClick = { viewModel.sendQuestionToAi(prompt) },
                    label = { Text(prompt, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isFromUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isFromUser) Color(0xFF005B4F) else Color(0xFF1B2228)
                        ),
                        modifier = Modifier.widthIn(max = 330.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Întreabă agentul despre puls, vârstă, cadență...") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    viewModel.sendQuestionToAi(inputText)
                    inputText = ""
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Trimite", tint = Color(0xFF00E676))
            }
        }
    }
}

@Composable
fun RidesAndGpxScreen(viewModel: CyclingViewModel) {
    val rides by viewModel.allRides.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val importedGpx by viewModel.importedGpx.collectAsState()
    val mlPrediction by viewModel.mlPrediction.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Istoric Curse (Room DB) & Trasee GPX", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.exportLastRideToGpx() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Export .GPX")
            }
            OutlinedButton(
                onClick = { viewModel.importDemoMountainGpxRoute() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Import Traseu .GPX")
            }
        }

        exportStatus?.let {
            Text(it, color = Color(0xFF00E676), fontSize = 12.sp)
        }

        importedGpx?.let { gpx ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF162922))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🗺️ ${gpx.routeName}", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                    Text(
                        "Distanță: ${gpx.totalDistanceKm} km | Elevație: +${gpx.totalElevationGainMeters.toInt()}m | Pantă medie: ${gpx.avgSlopePercent}%",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        "ETA Dinamic ML (ținând cont de oboseală și profilul de elevație): ${mlPrediction.dynamicEtaMinutes} minute",
                        color = Color(0xFFFFD740),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (rides.isEmpty()) {
            Text(
                "Nu ai încă nicio cursă salvată. Pornește o cursă din tab-ul Cockpit HUD, apasă pe '+ Simuleaza Date BLE/GPS' și apoi 'OPRESTE & SALVEAZA'.",
                color = Color.Gray
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rides) { ride ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Cursa #${ride.rideId} • ${"%.2f".format(ride.distanceMeters / 1000f)} km (${ride.durationSeconds / 60} min)",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Viteza medie: ${"%.1f".format(ride.avgSpeedKmh)} km/h | Puls mediu: ${ride.avgHeartRate} BPM | Cadenta: ${ride.avgCadenceRpm} RPM",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                "Elevatie: +${ride.elevationGainMeters.toInt()} m | Calorii: ${ride.caloriesBurned} kcal | Oboseala TFLite: ${(ride.avgFatigueScore * 100).toInt()}%",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorsAndProfileScreen(
    viewModel: CyclingViewModel,
    bleStatus: String
) {
    val profile by viewModel.userProfile.collectAsState()
    val healthSyncStatus by viewModel.healthConnectManager.syncStatus.collectAsState()

    var nameText by remember(profile.name) { mutableStateOf(profile.name) }
    var ageText by remember(profile.age) { mutableStateOf(profile.age.toString()) }
    var weightText by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toString()) }
    var rhrText by remember(profile.restingHeartRate) { mutableStateOf(profile.restingHeartRate.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "👤 Profil Fiziologic (Folosit de Agentul AI & TFLite)",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Nume Ciclist") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it },
                        label = { Text("Vârsta (ani)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Greutate (kg)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rhrText,
                        onValueChange = { rhrText = it },
                        label = { Text("Puls de repaus (BPM)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.updateUserProfile(
                                name = nameText,
                                age = ageText.toIntOrNull() ?: 22,
                                weightKg = weightText.toFloatOrNull() ?: 72f,
                                restingHr = rhrText.toIntOrNull() ?: 60
                            )
                        }
                    ) {
                        Text("Salvează Profilul Local (Room DB)")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "❤️ Google Health Connect",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold
                    )
                    Text(healthSyncStatus, color = Color.White, fontSize = 13.sp)
                    Button(
                        onClick = {
                            viewModel.healthConnectManager.simulateHealthConnectSync(56, 72)
                            rhrText = "56"
                            viewModel.updateUserProfile(
                                name = nameText,
                                age = ageText.toIntOrNull() ?: 22,
                                weightKg = weightText.toFloatOrNull() ?: 72f,
                                restingHr = 56
                            )
                        }
                    ) {
                        Text("Sincronizează Date Health Connect")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "📡 Bluetooth Low Energy (Centură Puls 0x180D / Cadență 0x1816)",
                        color = Color(0xFF00B0FF),
                        fontWeight = FontWeight.Bold
                    )
                    Text("Status curent: $bleStatus", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.bleManager.startScanning() }) {
                            Text("Scanează BLE")
                        }
                        OutlinedButton(onClick = { viewModel.bleManager.simulateTelemetry(168, 86) }) {
                            Text("Simulare Puls Elevat (168 BPM)")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "🚨 Detecție Automată Căzături (Crash Detection)",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Monitorizează accelerometrul și giroscopul prin modelul TFLite + NNAPI. La un impact peste 2.8G urmat de imobilitate, declanșează temporizatorul SOS.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { viewModel.crashManager.triggerSimulatedCrash() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Testează Alerta de Impact SOS (4.6G)")
                    }
                }
            }
        }
    }
}

@Composable
fun CrashSosDialog(
    peakG: Float,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(15) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF3B0A0A),
        title = {
            Text(
                text = "🚨 IMPACT DETECTAT (${"%.1f".format(peakG)}G)!",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (countdown > 0)
                        "Modelul TFLite Crash Detection a identificat o posibilă căzătură. Se transmite alerta SOS cu coordonatele GPS în $countdown secunde..."
                    else
                        "Alerta SOS cu locația GPS a fost activată către contactul de urgență!",
                    color = Color.White
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
            ) {
                Text("SUNT BINE (ANULEAZĂ SOS)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}
