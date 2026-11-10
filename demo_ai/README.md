# 🚴 BetterCycle — Smart Cycling Assistant & Analytics

> Aplicație mobilă Android pentru monitorizarea curselor de ciclism, analiză a indicilor de sănătate în timp real și predicții asistate de Machine Learning & AI On-Device.

---

## 📌 1. Descrierea Temei

**BetterCycle** este o aplicație nativă Android dedicată cicliștilor (atât amatori, cât și avansați), concepută pentru a înregistra, analiza și optimiza performanța pe bicicletă. Aplicația combină telemetria colectată în timp real prin senzorii interni ai telefonului (GPS, accelerometru, giroscop, barometru), dispozitive externe Bluetooth Low Energy (BLE) și Google Health Connect cu module **on-device de Machine Learning și AI**, oferind utilizatorului nu doar simple statistici retrospective, ci recomandări și predicții proactive în timpul cursei. Toate datele sunt protejate prin procesare locală, acestea nepărăsind niciodată dispozitivul mobil.

---

## 🎯 2. Obiectivele Proiectului

### Obiectiv General
Dezvoltarea unei aplicații mobile native și performante pe platforma Android, capabilă de rulare continuă și eficientă în fundal, ce oferă asistență inteligentă ciclistului prin algoritmi predictivi și un agent conversațional local.

### Obiective Specifice

1. **Tracking GPS & Trasee în Timp Real**:
   - Înregistrare traseu (fișiere GPX / rute locale) folosind un `ForegroundService` rezistent la optimizările de baterie (*Doze Mode*) ale sistemului Android.
   - Randare traseu pe hartă interactivă / canvas vectorial offline-first.
   - Calcul în timp real al vitezei curente, vitezei medii/maxime, distanței, pantei (%) și diferenței de nivel (elevație).

2. **Componenta de Inteligență Artificială (Machine Learning On-Device)**:
   - Integrarea modelelor antrenate și optimizate pentru mobil (**TensorFlow Lite** cu accelerare hardware **NNAPI**).
   - **Cazuri de utilizare ML**:
     - *Predicție Oboseală & Ritm Optim (Fatigue & Pacing)*: Estimarea momentului în care performanța începe să scadă în funcție de panta traseului, durata efortului, rezerva de puls (HRR) și variația ritmului, sugerând ajustarea cadenței (RPM).
     - *Predicție Dinamică ETA*: Estimarea realistă a timpului de sosire pe un traseu predefinit (sau importat din `.gpx`), luând în calcul profilul de elevație și factorul de oboseală al ciclistului.
     - *Detecție Automată de Căzături / Accidente (Crash Detection)*: Clasificare în timp real a datelor de la accelerometru și giroscop pentru detectarea impactului ($>2.8G$ urmat de imobilitate) și activarea automată a unui temporizator SOS.

3. **Agent AI Conversațional Personal (100% On-Device)**:
   - Utilizatorul poate adresa întrebări în limbaj natural despre propriile date înregistrate în aplicație (valori de puls, viteză, cadență, durata efortului, oboseală sau evoluția performanței).
   - Agentul interpretează valorile în funcție de istoricul utilizatorului (Room DB + Google Health Connect) și de vârsta declarată (folosind ecuațiile fiziologice Tanaka și Karvonen), indicând dacă acestea sunt scăzute, normale/optime sau elevate pentru vârsta și contextul analizat.
   - Pe baza analizelor locale, agentul oferă explicații clare și sugestii personalizate pentru îmbunătățirea condiției fizice și a performanței pe bicicletă.
   - *Notă medicală*: Agentul oferă informații orientative și nu înlocuiește evaluarea sau recomandările unui medic.
   - **Confidențialitate prin procesare locală**: Modelele de AI și Machine Learning rulează direct pe dispozitiv (TensorFlow Lite + NNAPI / MediaPipe GenAI), fără trimiterea datelor personale sau a istoricului de activitate către servere externe.

4. **Integrare Senzori, BLE & Google Health Connect**:
   - Colectare date de la senzorii interni cu frecvență de eșantionare adaptivă pentru minimizarea consumului bateriei.
   - Suport pentru conectivitate **Bluetooth Low Energy (BLE)** (centuri de puls cardiac GATT `0x180D` și senzori de cadență `0x1816`).
   - Sincronizare cu **Google Health Connect** pentru citirea pulsului de repaus / istoric și salvarea sesiunilor de antrenament.

5. **Arhitectură & Stocare Offline-First**:
   - Persistență locală completă prin **Room Database** (datele utilizatorului, profilul fiziologic, cursele și punctele de telemetrie sunt salvate local, fără dependență de server).
   - Export și Import trasee în format standard `.gpx` (GPX 1.1 cu extensii pentru puls și cadență).

6. **Interfață Modernă & Mod Ghidon (Bike HUD)**:
   - UI declarativ construit în **Jetpack Compose** cu Material Design 3.
   - Mod special **„Cockpit / Sunlight HUD”** cu elemente mari, contrast ridicat (OLED Galben/Negru) și lizibilitate optimă în condiții de lumină puternică la soare.

---

## 🛠️ 3. Tehnologii și Arhitectură

- **Limbaj**: Kotlin
- **Interfață Utilizator**: Jetpack Compose, Material Design 3
- **Arhitectură**: MVVM (Model-View-ViewModel) + Clean Architecture (`data`, `domain`, `service`, `ui`)
- **Asincronicitate**: Kotlin Coroutines & `StateFlow` / `SharedFlow`
- **Servicii Fundal**: Android `ForegroundService` (`location` type) cu Persistent Notification
- **Bază de date locală**: Room DB (SQLite ORM) + DataStore Preferences
- **Machine Learning**: TensorFlow Lite (`.tflite`) rulat local pe CPU / NNAPI Hardware Acceleration
- **Agent AI Conversațional**: Sistem RAG On-Device (MediaPipe GenAI LLM Inference + Motor Fiziologic Local)
- **Senzori & Sănătate**: Android SensorManager, Bluetooth Low Energy (GATT), Google Health Connect API
- **Hărți & Locație**: Google Play Services FusedLocationProviderClient + Live Vectorial Route Canvas / Maps Compose

---

## 📂 4. Structura Proiectului

```text
app/src/main/
├── assets/
│   ├── fatigue_pacing_model.tflite       # Model TFLite oboseala, cadenta & ETA
│   └── crash_detection_model.tflite      # Model TFLite detectie impact/accidente
├── java/ro/utcn/bettercycle/
│   ├── data/
│   │   ├── local/
│   │   │   ├── Entities.kt               # Tabelele Room (UserProfile, Ride, TelemetryPoint)
│   │   │   ├── CyclingDao.kt             # Interogari SQL reactive cu Flow & agregate istorice
│   │   │   └── AppDatabase.kt            # Instanta Room DB (Offline-First)
│   │   ├── gpx/
│   │   │   └── GpxManager.kt             # Export si Import fisiere standard .gpx
│   │   └── sensors/
│   │       ├── BleHeartRateManager.kt    # Conectivitate BLE (Puls 0x180D & Cadenta 0x1816)
│   │       ├── CrashDetectionManager.kt  # Accelerometru/Giroscop + TFLite Crash Detection
│   │       └── HealthConnectManager.kt   # Integrare Google Health Connect
│   ├── domain/
│   │   ├── ml/
│   │   │   └── CyclingMlEngine.kt        # Motorul TensorFlow Lite + NNAPI
│   │   └── agent/
│   │       └── LocalCyclingAiAgent.kt    # Agentul AI Conversational On-Device
│   ├── service/
│   │   └── CyclingForegroundService.kt   # Serviciu GPS rezistent la Doze Mode
│   └── ui/
│       ├── CyclingViewModel.kt           # State Management unificat (MVVM)
│       └── MainActivity.kt               # UI Compose, Bike Cockpit HUD, AI Chat & Alerta SOS
└── AndroidManifest.xml
```

---

## 🚀 5. Rulare și Testare în Android Studio

1. Deschide folderul proiectului în **Android Studio** (*File -> Open*).
2. Așteaptă finalizarea **Gradle Sync** și apasă **Run ('app')** pe un dispozitiv fizic sau emulator Android (API 26+).
3. *(Opțional)* Pentru regenerarea fișierelor `.tflite` antrenate personalizat, rulează scriptul `python generate_models.py`, care salvează modelele direct în `app/src/main/assets/`.

---

## 👤 6. Echipa de Dezvoltare

- **Proiect Individual**: Lucrat și dezvoltat integral individual.
- **Autor**: Mihalca Marco Alexis Dimitri
- **Disciplina**: Calcul Mobil
