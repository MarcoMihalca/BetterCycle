# 🚴 BetterCycle - Smart Cycling Assistant & Analytics

> Aplicație mobilă Android pentru monitorizarea curselor de ciclism, analiză a indicilor de sanatate în timp real și predicții asistate de Machine Learning.

---

## 📌 1. Descrierea Temei
**BetterCycle** este o aplicație nativă Android dedicată cicliștilor (atât amatori, cât și avansați), concepută pentru a înregistra, analiza și optimiza performanța pe bicicletă. Aplicația combină telemetria colectată în timp real prin senzorii interni ai telefonului (GPS, accelerometru, barometru) și dispozitive externe Bluetooth (BLE) cu module on-device de **Machine Learning** si **AI**, oferind utilizatorului nu doar simple statistici retrospective, ci recomandări și predicții proactive în timpul cursei, datele fiind protejate, acestea neparasind vreodata dispozitivul mobil.

---

## 🎯 2. Obiectivele Proiectului

### Obiectiv General
Dezvoltarea unei aplicații mobile native și performante pe platforma Android, capabilă de rulare continuă și eficientă, în fundal, ce oferă asistență inteligentă ciclistului prin algoritmi predictivi.

### Obiective Specifice
1. **Tracking GPS & Trasee în Timp Real**:
   - Înregistrare traseu (fișiere GPX / rute locale) folosind un *Foreground Service* rezistent la optimizările de baterie ale sistemului Android.
   - Randare traseu pe hartă interactivă (Google Maps SDK / Mapbox / OpenStreetMap).
   - Calcul în timp real al vitezei, distanței, ritmului, diferenței de nivel (elevație) și vitezei medii/maxime.

2. **Componenta de Inteligență Artificială (Machine Learning on-device)**:
   - Integrarea unui model antrenat și optimizat pentru mobil (**TensorFlow Lite**).
   - **Cazuri de utilizare ML**:
     - *Predicție Oboseală & Ritm Optim (Fatigue & Pacing)*: Estimarea momentului în care performanța începe să scadă în funcție de panta traseului, durata efortului și variația ritmului, sugerând ajustarea cadenței.
     - *Predicție Dinamică ETA*: Estimarea realistă a timpului de sosire pe un traseu predefinit, luând în calcul profilul de elevație, condițiile meteo curente și anduranța istorică a ciclistului.
     - *(Opțional, pe viitor)* *Detecție Automată de Căzături / Accidente (Crash Detection)*: Clasificare în timp real a datelor de la accelerometru/giroscop pentru detectarea impactului și activarea unui temporizator SOS.
   - **Agent AI conversațional personal**:
     - Utilizatorul poate adresa întrebări în limbaj natural despre propriile date înregistrate în aplicație, precum valorile de puls, viteza, ritmul, durata efortului sau evoluția performanței.
     - Agentul interpretează valorile în funcție de istoricul utilizatorului și de vârsta declarată, indicând dacă acestea sunt mai ridicate, mai scăzute sau în limitele așteptate pentru contextul analizat.
     - Pe baza analizelor locale, agentul oferă explicații ușor de înțeles și sugestii personalizate pentru îmbunătățirea condiției fizice și a performanței pe bicicletă.
     - Agentul oferă informații orientative și nu înlocuiește evaluarea sau recomandările unui medic.
   - **Confidențialitate prin procesare locală**: modelele de AI și Machine Learning rulează direct pe dispozitiv (TensorFlow Lite + NNAPI), fără trimiterea datelor personale sau a istoricului de activitate către servere externe. Datele utilizatorului nu sunt colectate în scopuri de antrenare, profilare sau analiză externă și rămân pe dispozitiv.

3. **Integrare Senzori & Conectivitate**:
   - Colectare date de la senzorii interni (frecvență de eșantionare adaptivă pentru minimizarea consumului bateriei).
   - Suport pentru conectivitate **Bluetooth Low Energy (BLE)** (ex: centuri de puls cardiac sau senzori de cadență).

4. **Arhitectură & Stocare Offline-First**:
   - Persistență locală completă prin **Room Database** (datele utilizatorului și cursele sunt salvate local, fără dependență obligatorie de server).
   - Export/Import trasee în format standard `.gpx`.

5. **Interfață Modernă & Mod Ghidon (Bike HUD)**:
   - UI declarativ construit în **Jetpack Compose** cu Material Design 3.
   - Mod special „Cockpit / HUD” cu elemente mari, contrast ridicat și lizibilitate optimă în condiții de lumină puternică la soare.

---

## 🛠️ 3. Tehnologii și Arhitectură

- **Limbaj:** Kotlin
- **Interfață Utilizator:** Jetpack Compose, Material 3
- **Arhitectură:** MVVM (Model-View-ViewModel) + Clean Architecture
- **Asincronicitate:** Kotlin Coroutines & StateFlow / SharedFlow
- **Servicii Fundal:** Android Foreground Service cu Persistent Notification
- **Bază de date locală:** Room DB (SQLite ORM) + DataStore Preferences
- **Machine Learning:** TensorFlow Lite (`.tflite`) rulat local pe CPU/NNAPI
- **Agent AI conversațional:** model on-device pentru întrebări și răspunsuri despre datele proprii ale utilizatorului
- **Confidențialitate:** inferență AI/ML și analiză locală, cu datele personale păstrate pe dispozitiv
- **Hărți & Locație:** Google Play Services Location API + Google Maps SDK / Mapbox
- **Rețea / Vreme (opțional):** Retrofit / Ktor Client + OpenWeather API

---

## 👤 4. Echipa de Dezvoltare
- **Proiect Individual**: Lucrat și dezvoltat de catre mine.
- **Autor**: Mihalca Marco Alexis Dimitri
- **Disciplina**: Calcul Mobil