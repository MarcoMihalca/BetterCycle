Acest director contine modelele TensorFlow Lite (.tflite) rulate on-device prin NNAPI:
- fatigue_pacing_model.tflite
- crash_detection_model.tflite

Pentru a (re)genera modelele binare antrenate, ruleaza din radacina proiectului:
python generate_models.py

Nota: CyclingMlEngine.kt include deja ponderile analitice de rezerva (fallback), astfel incat aplicatia ruleaza imediat chiar si inainte de rularea scriptului Python.
