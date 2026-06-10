"""
Funciones de preprocesamiento para el proyecto GTZAN.

Aqui se lee el audio, se divide en segmentos y se convierte cada segmento en
features. El mismo codigo se usa en entrenamiento y prediccion para que el
modelo reciba siempre el mismo tipo de entrada.
"""

from pathlib import Path

import librosa
import numpy as np


# ---------------------------------------------------------------------------
# Caché de espectrogramas
# ---------------------------------------------------------------------------

def get_feature_cache_dir(
    cache_root: "str | Path",
    feature_type: str = "mel",
    sample_rate: int = 22050,
    segment_duration: float = 4.0,
    overlap: float = 0.5,
) -> Path:
    """
    Devuelve la subcarpeta de caché para los parámetros dados.

    El nombre de la carpeta codifica TODOS los parámetros relevantes.
    Si cualquiera cambia, se usa una carpeta distinta y el caché se regenera
    automáticamente sin necesidad de borrar nada a mano.

    Ejemplo de ruta:  cache/mel_sr22050_dur4.0s_ov0.5/
    """
    cache_key = f"{feature_type}_sr{sample_rate}_dur{segment_duration}s_ov{overlap}"
    return Path(cache_root) / cache_key


def get_segment_cache_path(audio_file_path: "str | Path", cache_dir: Path) -> Path:
    """
    Ruta del archivo .npy de caché para un audio concreto.

    Preserva la jerarquía género/nombre para facilitar la inspección manual:
        cache/<key>/blues/blues.00000.npy
    """
    p = Path(audio_file_path)
    genre = p.parent.name   # subcarpeta del género
    stem = p.stem           # nombre sin extensión
    return cache_dir / genre / f"{stem}.npy"


def load_or_compute_segments(
    audio_file: "str | Path",
    cache_dir: Path,
    sample_rate: int = 22050,
    segment_duration: float = 4.0,
    overlap: float = 0.5,
    feature_type: str = "mel",
) -> "tuple[np.ndarray, list]":
    """
    Intenta cargar los segmentos de espectrograma desde el caché.
    Si no existen, los calcula desde el WAV y los guarda para futuras ejecuciones.

    Siempre devuelve también los segmentos de audio crudos (float32) porque
    el pipeline de augmentation los necesita para aplicar transformaciones
    antes de calcular los features aumentados.

    Returns:
        base_features : ndarray, shape (n_segs, height, time_steps, 1)
        audio_segments: list de arrays de audio crudo (uno por segmento)
    """
    cache_path = get_segment_cache_path(audio_file, cache_dir)

    # Siempre necesitamos el audio crudo para la augmentación
    audio, sr = load_audio(audio_file, sample_rate=sample_rate)
    audio_segments = split_audio_into_segments(audio, sr, segment_duration, overlap)

    if cache_path.exists():
        base_features = np.load(str(cache_path))
        return base_features, audio_segments

    # Calcular y guardar en caché
    base_features = np.array(
        [audio_to_features(seg, sr, feature_type, segment_duration) for seg in audio_segments],
        dtype=np.float32,
    )
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    np.save(str(cache_path), base_features)

    return base_features, audio_segments


# Semilla fija para que los experimentos sean mas reproducibles.
RANDOM_SEED = 42

try:
    import training.config as config
except ImportError:
    import config

# Cargar parámetros de configuración de CNN10
SAMPLE_RATE = config.SAMPLE_RATE
MAX_AUDIO_DURATION_SECONDS = config.MAX_AUDIO_DURATION_SECONDS
SEGMENT_DURATION_SECONDS = config.SEGMENT_DURATION_SECONDS
SEGMENT_OVERLAP = config.SEGMENT_OVERLAP

N_MELS = config.N_MELS
N_FFT = config.N_FFT
HOP_LENGTH = config.HOP_LENGTH
FMIN = config.FMIN
FMAX = config.FMAX

# Feature por defecto.
FEATURE_TYPE = config.FEATURE_TYPE

# Generos oficiales del dataset GTZAN.
GENRES = [
    "blues",
    "classical",
    "country",
    "disco",
    "hiphop",
    "jazz",
    "metal",
    "pop",
    "reggae",
    "rock",
]


def get_expected_time_steps(segment_duration=SEGMENT_DURATION_SECONDS, sample_rate=SAMPLE_RATE):
    """Calcula cuantos frames temporales tendra cada segmento."""
    segment_samples = int(segment_duration * sample_rate)
    return 1 + int(segment_samples // HOP_LENGTH)


def get_feature_height(feature_type=FEATURE_TYPE):
    """Devuelve cuantas filas tendra la matriz de features."""
    if feature_type == "mel":
        return N_MELS
    raise ValueError("feature_type debe ser 'mel'.")


def get_input_shape(feature_type=FEATURE_TYPE, segment_duration=SEGMENT_DURATION_SECONDS):
    """Forma que espera Keras: alto, tiempo, canal."""
    return (
        get_feature_height(feature_type),
        get_expected_time_steps(segment_duration),
        1,
    )


def load_audio(file_path, sample_rate=SAMPLE_RATE, duration=MAX_AUDIO_DURATION_SECONDS):
    """
    Carga un archivo WAV con librosa.

    librosa convierte el audio a mono por defecto. Esto simplifica el proyecto,
    porque todos los audios quedan representados como una sola senal.
    """
    audio, sr = librosa.load(file_path, sr=sample_rate, duration=duration, mono=True)
    return audio, sr


def split_audio_into_segments(
    audio,
    sample_rate=SAMPLE_RATE,
    segment_duration=SEGMENT_DURATION_SECONDS,
    overlap=SEGMENT_OVERLAP,
):
    """
    Divide un audio en segmentos con overlap.

    Ejemplo: con segmentos de 4 segundos y overlap de 0.5, cada nuevo segmento
    empieza 2 segundos despues del anterior.
    """
    segment_samples = int(segment_duration * sample_rate)
    step_samples = int(segment_samples * (1 - overlap))

    if step_samples <= 0:
        raise ValueError("El overlap debe ser menor que 1.0")

    segments = []

    if len(audio) <= segment_samples:
        segments.append(pad_audio(audio, segment_samples))
        return segments

    last_added_start = 0
    for start in range(0, len(audio) - segment_samples + 1, step_samples):
        end = start + segment_samples
        segments.append(audio[start:end])
        last_added_start = start

    # Si queda un pedazo final, lo completamos con padding. Asi no perdemos el
    # cierre de la cancion.
    last_start = len(audio) - segment_samples
    if segments and last_start > last_added_start:
        segments.append(audio[last_start : last_start + segment_samples])

    return segments


def pad_audio(audio, target_samples):
    """Completa audio corto con ceros hasta alcanzar el tamano necesario."""
    if len(audio) >= target_samples:
        return audio[:target_samples]

    missing_samples = target_samples - len(audio)
    return np.pad(audio, (0, missing_samples), mode="constant")


def audio_to_features(
    audio,
    sample_rate=SAMPLE_RATE,
    feature_type=FEATURE_TYPE,
    segment_duration=SEGMENT_DURATION_SECONDS,
):
    """
    Convierte un segmento de audio en Mel Spectrogram.
    """
    target_time_steps = get_expected_time_steps(segment_duration, sample_rate)

    if feature_type == "mel":
        features = librosa.feature.melspectrogram(
            y=audio,
            sr=sample_rate,
            n_mels=N_MELS,
            n_fft=N_FFT,
            hop_length=HOP_LENGTH,
            fmin=FMIN,
            fmax=FMAX,
        )
        features = librosa.power_to_db(features, ref=np.max)

    else:
        raise ValueError("feature_type debe ser 'mel'.")

    features = normalize_features(features)
    features = pad_or_truncate(features, target_time_steps)

    # Keras espera imagenes con canal: alto, ancho, canales.
    return features[..., np.newaxis].astype(np.float32)


def normalize_features(features):
    """
    Normalizacion simple.

    Se resta la media y se divide por la desviacion estandar. El valor pequeno
    evita divisiones por cero si algun audio raro tiene desviacion 0.
    """
    mean = np.mean(features)
    std = np.std(features)

    # Si por alguna razon el segmento tiene variacion casi nula, evitamos una
    # division inestable. En ese caso solo centramos los valores.
    if std < 1e-6:
        return features - mean

    return (features - mean) / std


def pad_or_truncate(features, target_time_steps):
    """
    Ajusta todos los espectrogramas al mismo ancho temporal.

    Las redes neuronales necesitan entradas del mismo tamano. Si el audio queda
    corto, agregamos ceros. Si queda largo, recortamos.
    """
    current_time_steps = features.shape[1]

    if current_time_steps < target_time_steps:
        missing_steps = target_time_steps - current_time_steps
        features = np.pad(
            features,
            pad_width=((0, 0), (0, missing_steps)),
            mode="constant",
        )
    elif current_time_steps > target_time_steps:
        features = features[:, :target_time_steps]

    return features


def process_audio_file(
    file_path,
    feature_type=FEATURE_TYPE,
    segment_duration=SEGMENT_DURATION_SECONDS,
    overlap=SEGMENT_OVERLAP,
):
    """
    Carga un WAV y devuelve todos sus segmentos procesados.

    Retorna un array con forma:
    segmentos, alto, tiempo, canal
    """
    audio, sr = load_audio(file_path)
    segments = split_audio_into_segments(audio, sr, segment_duration, overlap)

    return np.array(
        [
            audio_to_features(segment, sr, feature_type, segment_duration)
            for segment in segments
        ],
        dtype=np.float32,
    )


def is_audio_silent(
    file_path,
    sample_rate=SAMPLE_RATE,
    duration=MAX_AUDIO_DURATION_SECONDS,
    threshold=0.0015,
) -> bool:
    """
    Carga un archivo de audio y determina si consiste principalmente en silencio.
    
    Calcula el valor RMS (Root Mean Square) del audio y lo compara con un umbral.
    """
    try:
        audio, sr = load_audio(file_path, sample_rate=sample_rate, duration=duration)
        if len(audio) == 0:
            return True
        rms = librosa.feature.rms(y=audio, frame_length=2048, hop_length=512)
        mean_rms = float(np.mean(rms))
        return mean_rms < threshold
    except Exception:
        # Si ocurre un error al procesar el audio, lo consideramos como silencio por seguridad
        return True


def get_dataset_files(dataset_path):
    """
    Recorre la carpeta dataset y devuelve rutas de audio con sus etiquetas.

    Estructura esperada:
    dataset/
      blues/blues.00000.wav
      classical/classical.00000.wav
      ...
    """
    dataset_path = Path(dataset_path)
    audio_files = []
    labels = []

    for genre in GENRES:
        genre_folder = dataset_path / genre
        if not genre_folder.exists():
            print(f"Advertencia: no existe la carpeta {genre_folder}")
            continue

        for wav_file in sorted(genre_folder.glob("*.wav")):
            # Este archivo de GTZAN es conocido por estar corrupto.
            if wav_file.name == "jazz.00054.wav":
                print("Ignorando archivo corrupto: jazz.00054.wav")
                continue

            audio_files.append(str(wav_file))
            labels.append(genre)

    return audio_files, labels
