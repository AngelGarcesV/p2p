"""Configuración del módulo de entrenamiento."""

from pathlib import Path

# Configuración de reproductibilidad
RANDOM_SEED = 42

# Rutas del proyecto
TRAINING_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = TRAINING_DIR.parent
DATASET_PATH = PROJECT_ROOT / "dataset"
MODELS_DIR = PROJECT_ROOT / "models"
OUTPUTS_DIR = PROJECT_ROOT / "outputs"
METRICS_DIR = OUTPUTS_DIR / "metrics"
PLOTS_DIR = OUTPUTS_DIR / "plots"
CACHE_DIR = PROJECT_ROOT / "cache"   # Espectrogramas precalculados para acelerar re-entrenamientos

# Configuración de audio y preprocesamiento (PANNs CNN10 requiere estos parámetros exactos)
MODEL_TYPE = "cnn10"
SAMPLE_RATE = 32000
N_MELS = 64
N_FFT = 1024
HOP_LENGTH = 320
FMIN = 50
FMAX = 14000

MAX_AUDIO_DURATION_SECONDS = 30
SEGMENT_DURATION_SECONDS = 4.0
SEGMENT_OVERLAP = 0.5
FEATURE_TYPE = "mel"

# Umbral de silencio RMS (Root Mean Square)
SILENCE_THRESHOLD = 0.0015

# Configuración del modelo y entrenamiento
BATCH_SIZE = 16             # Fase 1: seguro para GTX 1650 (4 GB VRAM)
FINE_TUNE_BATCH_SIZE = 8    # Fase 2: reducido para absorber el fine-tuning completo de CNN10 en GPU de 4GB VRAM
                            # Mixed precision (float16) ya reduce VRAM ~40%

# --- Fase 1: Transfer Learning (backbone congelado) ---
# Se entrena únicamente la cabeza (predictions). 10 épocas son suficientes.
INITIAL_EPOCHS = 10
INITIAL_LEARNING_RATE = 1e-3

# --- Fase 2: Fine-tuning (descongelado completo) ---
# Se descongela todo el modelo con un learning rate muy bajo.
FINE_TUNE_EPOCHS = 30
FINE_TUNE_LEARNING_RATE = 3e-5

