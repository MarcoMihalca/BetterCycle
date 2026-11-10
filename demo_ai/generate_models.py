import os
import numpy as np
import tensorflow as tf

assets_dir = os.path.join("app", "src", "main", "assets")
os.makedirs(assets_dir, exist_ok=True)

# 1. Model Predictie Oboseala, Cadenta Optima si Factor ETA
X_fatigue = np.random.uniform(
    low=[-0.5, 0.0, 0.2, 0.4, 0.2],
    high=[1.0, 1.0, 1.0, 1.0, 1.0],
    size=(2000, 5),
).astype(np.float32)
fatigue_target = np.clip(
    0.55 * X_fatigue[:, 2]
    + 0.25 * np.maximum(0, X_fatigue[:, 0])
    + 0.20 * X_fatigue[:, 1],
    0.0,
    1.0,
)
cadence_target = np.clip(0.75 - 0.12 * np.maximum(0, X_fatigue[:, 0]), 0.55, 0.85)
decay_target = np.clip(1.0 - 0.25 * fatigue_target, 0.6, 1.0)
Y_fatigue = np.stack([fatigue_target, cadence_target, decay_target], axis=1).astype(
    np.float32
)

fatigue_model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(5,)),
    tf.keras.layers.Dense(16, activation="relu"),
    tf.keras.layers.Dense(8, activation="relu"),
    tf.keras.layers.Dense(3, activation="sigmoid"),
])
fatigue_model.compile(optimizer="adam", loss="mse")
fatigue_model.fit(X_fatigue, Y_fatigue, epochs=15, verbose=0)

converter1 = tf.lite.TFLiteConverter.from_keras_model(fatigue_model)
fatigue_path = os.path.join(assets_dir, "fatigue_pacing_model.tflite")
open(fatigue_path, "wb").write(converter1.convert())

# 2. Model Crash Detection (Impact G-Force, Rotatie Giroscop, Varianta Post-Impact)
X_crash = np.random.uniform(
    low=[0.1, 0.0, 0.0], high=[1.0, 1.0, 1.0], size=(1500, 3)
).astype(np.float32)
Y_crash = (
    (X_crash[:, 0] > 0.35) & (X_crash[:, 1] > 0.25) & (X_crash[:, 2] < 0.3)
).astype(np.float32).reshape(-1, 1)

crash_model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(3,)),
    tf.keras.layers.Dense(8, activation="relu"),
    tf.keras.layers.Dense(1, activation="sigmoid"),
])
crash_model.compile(optimizer="adam", loss="binary_crossentropy")
crash_model.fit(X_crash, Y_crash, epochs=15, verbose=0)

converter2 = tf.lite.TFLiteConverter.from_keras_model(crash_model)
crash_path = os.path.join(assets_dir, "crash_detection_model.tflite")
open(crash_path, "wb").write(converter2.convert())

print(f"Modelele .tflite au fost generate cu succes in {assets_dir}!")
