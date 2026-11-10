import zipfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

OUTPUT_PPTX = "BetterCycle_Prezentare_Calcul_Mobil.pptx"

SLIDES_DATA = [
    {
        "title": "BetterCycle — Smart Cycling Assistant & Analytics",
        "subtitle": "Arhitectura Room DB, Modele ML On-Device (TFLite + NNAPI), Crash Detection, BLE & Health Connect",
        "bullets": [
            "Autor: Mihalca Marco Alexis Dimitri",
            "Disciplina: Calcul Mobil (Proiect Individual)",
            "Platforma: Android Nativ (Kotlin, Jetpack Compose, Material 3, Min SDK 26)",
            "Arhitectura: Clean Architecture + MVVM (data, domain, service, ui)",
            "Principiu de baza: 100% On-Device Privacy — datele si inferentele AI/ML nu parasesc telefonul"
        ]
    },
    {
        "title": "1. Baza de Date Locala: Room Database (Offline-First)",
        "subtitle": "Persistenta completa SQLite ORM verificata la compilare prin KSP si Kotlin Flow",
        "bullets": [
            "Fisier baza de date locala: better_cycle_offline.db (stocare 100% offline pe dispozitiv)",
            "Tabel 1 — user_profile (UserProfileEntity): id, name, age, weightKg, restingHeartRate, vo2MaxEstimate, emergencyContactPhone",
            "Tabel 2 — rides (RideEntity): rideId (PK), startTime, endTime, duration, distanceMeters, avgSpeed, maxSpeed, elevationGain, avgHr, maxHr, avgCadence, calories, avgFatigueScore",
            "Tabel 3 — telemetry_points (TelemetryPointEntity): pointId (PK), rideOwnerId (FK -> rides.rideId cu CASCADE & Index), timestamp, lat, lon, alt, speed, slopePercent, hrBpm, cadenceRpm, predictedFatigue",
            "CyclingDao & HistoricalAggregates: interogari reactive Flow<List<RideEntity>> + agregari SQL (COUNT, SUM, AVG, MAX) livrate instant Agentului AI"
        ]
    },
    {
        "title": "2. Modelul ML #1: Predictie Oboseala, Cadenta & ETA",
        "subtitle": "Rulat local prin Google LiteRT 1.4.0 (TensorFlow Lite) + Accelerare Hardware NNAPI",
        "bullets": [
            "Fisier model: fatigue_pacing_model.tflite (Retea neuronala Dense 5 -> 16 -> 8 -> 3, activare ReLU/Sigmoid)",
            "PARAMETRI DE INTRARE — Input Tensor [1, 5] (valori normalizate):",
            "   1. slopePercent / 20f : Panta curenta a traseului (%) calculata din altitudine GPS / barometru",
            "   2. durationMinutes / 180f : Durata acumulata a efortului in cursa curenta",
            "   3. heartRateReserveRatio (HRR) : (HR_curent - HR_repaus) / (HR_max_varsta - HR_repaus) conform Karvonen",
            "   4. currentCadence / 120f : Cadenta curenta de pedalare (RPM) preluata de la senzorul BLE",
            "   5. currentSpeedKmh / 50f : Viteza curenta de deplasare (km/h)"
        ]
    },
    {
        "title": "3. Parametrii de Iesire ML (Fatigue, Pacing & Dynamic ETA)",
        "subtitle": "Interpretarea in timp real a vectorului Output Tensor [1, 3] in modul Cockpit HUD",
        "bullets": [
            "PARAMETRI DE IESIRE — Output Tensor [1, 3]:",
            "   1. fatigueScore [0.0 .. 1.0] : Indicele de oboseala neuromusculara afisat procentual (0% - 100%)",
            "   2. optimalCadenceNormalized (x 120 -> RPM) : Cadenta optima recomandata (ex: 75 RPM pe panta >6%, 88-90 RPM pe plat)",
            "   3. speedDecayFactor [0.60 .. 1.05] : Factorul de degradare a vitezei cauzat de oboseala si panta",
            "Calculul ETA Dinamic pe traseu .GPX: effectiveSpeed = currentSpeed * speedDecayFactor -> ETA = (remainingKm / effectiveSpeed) * 60",
            "Recomandari proactive in UI: avertizare automata la intrarea in Zona 5 anaeroba sau la oboseala > 78%"
        ]
    },
    {
        "title": "4. Modelul ML #2: Crash Detection (Cum Functioneaza)",
        "subtitle": "Fuziune de senzori (Accelerometru + Giroscop) si clasificare TFLite + Alerta SOS",
        "bullets": [
            "Pasul 1 — Pre-filtrare fizica: Calculul vectorului rezultant al acceleratiei SVM = sqrt(ax^2 + ay^2 + az^2) / 9.81 (in multiplii de G)",
            "Pasul 2 — Fereastra glisanta (Sliding Window 10 esantioane): Calculeaza varianta post-impact pentru a detecta imobilitatea",
            "Pasul 3 — Declansare Inferenta TFLite (crash_detection_model.tflite) cand apare un soc mecanic > 2.8G:",
            "   • Intrare [0]: peakGForce / 10f (Varful fortei de impact G, ex: 4.6G)",
            "   • Intrare [1]: gyroRadPerSec / 15f (Viteza unghiulara sqrt(gx^2+gy^2+gz^2) ce surprinde rasturnarea bicicletei)",
            "   • Intrare [2]: postImpactVariance (Varianta mica < 0.3 confirma ca ciclistul a ramas nemiscat pe sol, nu a trecut doar peste o denivelare)",
            "   • Iesire [1, 1]: crashProbability (0..1) -> Daca > 0.72 (72%), declanseaza Dialogul SOS cu cronometru de 15 secunde"
        ]
    },
    {
        "title": "5. Conectivitate: BLE Direct & Google Health Connect API",
        "subtitle": "Colectare telemetrie in timp real + context fiziologic istoric pe 7 zile",
        "bullets": [
            "Conectare Directa Bluetooth Low Energy (BLE GATT — BleHeartRateManager.kt):",
            "   • Heart Rate Service (UUID 0x180D) & Characteristic 0x2A37: notificari live prin descriptorul 0x2902, decodare binara 8/16-bit BPM",
            "   • Cycling Speed & Cadence Service (UUID 0x1816) & Characteristic 0x2A5B: citire directa a cadentei (RPM)",
            "Integrare Google Health Connect API (HealthConnectManager.kt):",
            "   • Citeste inregistrarile HeartRateRecord si ExerciseSessionRecord din ultimele 7 zile",
            "   • Extrage pulsul de repaus real (Resting HR) si media saptamanala pentru calibrarea formulelor Tanaka (HRmax = 208 - 0.7 * varsta) si Karvonen",
            "   • Alimenteaza Agentul AI local pentru a evalua daca pulsul ciclistului este scazut, optim (Zona 2) sau elevat pentru varsta sa"
        ]
    },
    {
        "title": "6. Ce Model AI Local putem rula pe un iPhone 18 Pro Max?",
        "subtitle": "Extindere Cross-Platform On-Device AI pe hardware mobil de generatie urmatoare",
        "bullets": [
            "Profil Hardware iPhone 18 Pro Max: Cip Apple A20 Pro (2nm), Neural Engine >45-50 TOPS, 12-16 GB RAM unificat cu latime de banda mare",
            "1. Apple Foundation Models (~3B parametri, 2-bit/4-bit quantization): integrat nativ in iOS prin framework-ul FoundationModels / CoreML (0 MB overhead in app)",
            "2. Llama 3.2 3B Instruct / Llama 4 Scout (4-bit MLX): ocupa doar ~1.8 GB RAM, ruleaza cu >50 tokeni/sec pe GPU/Neural Engine, excelent pentru RAG in romana",
            "3. Google Gemma 3 4B / Gemma 2 2B (int4): rulat prin MediaPipe LLM Inference (Metal pe iOS / NNAPI & GPU pe Android) — arhitectura 100% comuna cu Android!",
            "4. Qwen 2.5 / Qwen 3 (7B Instruct, 4-bit Apple MLX) sau Phi-4 Mini (3.8B): datorita celor 12-16 GB RAM, un model 7B cuantizat (~4.2 GB) ruleaza complet offline pe telefon, oferind coaching sportiv avansat cu 100% confidentialitate"
        ]
    }
]


def make_slide_xml(title: str, subtitle: str, bullets: list[str]) -> str:
    paras = []
    # Title paragraph
    paras.append(f"""
      <a:p>
        <a:pPr algn="l"/>
        <a:r>
          <a:rPr lang="ro-RO" sz="2800" b="1" dirty="0">
            <a:solidFill><a:srgbClr val="00E676"/></a:solidFill>
            <a:latin typeface="Arial"/>
          </a:rPr>
          <a:t>{escape(title)}</a:t>
        </a:r>
      </a:p>
    """)
    # Subtitle paragraph
    paras.append(f"""
      <a:p>
        <a:pPr algn="l" spcAft="12000"/>
        <a:r>
          <a:rPr lang="ro-RO" sz="1600" i="1" dirty="0">
            <a:solidFill><a:srgbClr val="00B0FF"/></a:solidFill>
            <a:latin typeface="Arial"/>
          </a:rPr>
          <a:t>{escape(subtitle)}</a:t>
        </a:r>
      </a:p>
      <a:p><a:endParaRPr lang="ro-RO" sz="1000"/></a:p>
    """)
    # Bullet paragraphs
    for b in bullets:
        is_sub = b.startswith("   ")
        clean_b = b.strip()
        prefix = "• " if not is_sub else "    ◦ "
        font_sz = "1500" if not is_sub else "1350"
        color = "FFFFFF" if not is_sub else "E0E0E0"
        paras.append(f"""
          <a:p>
            <a:pPr algn="l"/>
            <a:r>
              <a:rPr lang="ro-RO" sz="{font_sz}" dirty="0">
                <a:solidFill><a:srgbClr val="{color}"/></a:solidFill>
                <a:latin typeface="Arial"/>
              </a:rPr>
              <a:t>{escape(prefix + clean_b)}</a:t>
            </a:r>
          </a:p>
        """)

    joined_paras = "\n".join(paras)
    return f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg>
      <p:bgPr>
        <a:solidFill><a:srgbClr val="101418"/></a:solidFill>
        <a:effectLst/>
      </p:bgPr>
    </p:bg>
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="0" cy="0"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="0" cy="0"/>
        </a:xfrm>
      </p:grpSpPr>
      <p:sp>
        <p:nvSpPr>
          <p:cNvPr id="2" name="ContentBox"/>
          <p:cNvSpPr txBox="1"/>
          <p:nvPr/>
        </p:nvSpPr>
        <p:spPr>
          <a:xfrm>
            <a:off x="457200" y="365760"/>
            <a:ext cx="11277600" cy="6126480"/>
          </a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
          <a:noFill/>
        </p:spPr>
        <p:txBody>
          <a:bodyPr wrap="square" rtlCol="0"/>
          <a:lstStyle/>
          {joined_paras}
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>"""


def create_pptx():
    num_slides = len(SLIDES_DATA)
    with zipfile.ZipFile(OUTPUT_PPTX, "w", zipfile.ZIP_DEFLATED) as zf:
        # [Content_Types].xml
        slide_overrides = "\n".join(
            f'  <Override PartName="/ppt/slides/slide{i}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>'
            for i in range(1, num_slides + 1)
        )
        zf.writestr(
            "[Content_Types].xml",
            f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
{slide_overrides}
</Types>""",
        )

        # _rels/.rels
        zf.writestr(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>""",
        )

        # ppt/_rels/presentation.xml.rels
        slide_rels = "\n".join(
            f'  <Relationship Id="rId{i+2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide{i}.xml"/>'
            for i in range(1, num_slides + 1)
        )
        zf.writestr(
            "ppt/_rels/presentation.xml.rels",
            f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
{slide_rels}
</Relationships>""",
        )

        # ppt/presentation.xml
        sld_id_lst = "\n".join(
            f'    <p:sldId id="{255 + i}" r:id="rId{i+2}"/>'
            for i in range(1, num_slides + 1)
        )
        zf.writestr(
            "ppt/presentation.xml",
            f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst>
    <p:sldMasterId id="2147483648" r:id="rId1"/>
  </p:sldMasterIdLst>
  <p:sldIdLst>
{sld_id_lst}
  </p:sldIdLst>
  <p:sldSz cx="12192000" cy="6858000" type="screen16x9"/>
  <p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>""",
        )

        # ppt/slideMasters/slideMaster1.xml & rels
        zf.writestr(
            "ppt/slideMasters/_rels/slideMaster1.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>""",
        )
        zf.writestr(
            "ppt/slideMasters/slideMaster1.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst>
    <p:sldLayoutId id="2147483649" r:id="rId1"/>
  </p:sldLayoutIdLst>
</p:sldMaster>""",
        )

        # ppt/slideLayouts/slideLayout1.xml & rels
        zf.writestr(
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>""",
        )
        zf.writestr(
            "ppt/slideLayouts/slideLayout1.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank">
  <p:cSld name="Blank">
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sldLayout>""",
        )

        # ppt/theme/theme1.xml
        zf.writestr(
            "ppt/theme/theme1.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="BetterCycleTheme">
  <a:themeElements>
    <a:clrScheme name="BetterCycle">
      <a:dk1><a:srgbClr val="101418"/></a:dk1>
      <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="1B2228"/></a:dk2>
      <a:lt2><a:srgbClr val="E0E0E0"/></a:lt2>
      <a:accent1><a:srgbClr val="00E676"/></a:accent1>
      <a:accent2><a:srgbClr val="00B0FF"/></a:accent2>
      <a:accent3><a:srgbClr val="FFD740"/></a:accent3>
      <a:accent4><a:srgbClr val="FF5252"/></a:accent4>
      <a:accent5><a:srgbClr val="7C4DFF"/></a:accent5>
      <a:accent6><a:srgbClr val="00E5FF"/></a:accent6>
      <a:hlink><a:srgbClr val="00B0FF"/></a:hlink>
      <a:folHlink><a:srgbClr val="00E676"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="BetterCycleFonts">
      <a:majorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>
      <a:minorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="BetterCycleFmt">
      <a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
      <a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
      <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
      <a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>""",
        )

        # Slides
        for idx, s in enumerate(SLIDES_DATA, start=1):
            zf.writestr(
                f"ppt/slides/_rels/slide{idx}.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>""",
            )
            zf.writestr(
                f"ppt/slides/slide{idx}.xml",
                make_slide_xml(s["title"], s["subtitle"], s["bullets"]),
            )

    print(f"Prezentarea {OUTPUT_PPTX} a fost generata cu succes!")


if __name__ == "__main__":
    create_pptx()
