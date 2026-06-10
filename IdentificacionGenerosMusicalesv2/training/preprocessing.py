"""
Funciones de preprocesamiento para el proyecto GTZAN.

Aqui se lee el audio, se divide en segmentos y se convierte cada segmento en
features. El mismo codigo se usa en entrenamiento y prediccion para que el
modelo reciba siempre el mismo tipo de entrada.
"""

from pathlib import Path

import librosa
import numpy as np


# Semilla fija para que los experimentos sean mas reproducibles.
RANDOM_SEED = 42

# GTZAN normalmente viene con audios de 30 segundos.
SAMPLE_RATE = 22050
MAX_AUDIO_DURATION_SECONDS = 30

# En lugar de usar una cancion completa como una sola muestra, dividimos el
# audio en segmentos pequenos. Esto aumenta la cantidad de ejemplos y ayuda a
# capturar patrones locales como ritmos, timbres e instrumentos.
SEGMENT_DURATION_SECONDS = 4.0
SEGMENT_OVERLAP = 0.5

N_MELS = 128
N_MFCC = 20
N_FFT = 2048
HOP_LENGTH = 512

# Feature por defecto. Opciones: "mel", "mfcc", "mfcc_delta".
FEATURE_TYPE = "mel"

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
    if feature_type == "mfcc":
        return N_MFCC
    if feature_type == "mfcc_delta":
        return N_MFCC * 3
    raise ValueError("feature_type debe ser 'mel', 'mfcc' o 'mfcc_delta'.")


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
    Convierte un segmento de audio en Mel Spectrogram o MFCCs.

    MFCC + delta + delta-delta agrega informacion sobre como cambian las
    caracteristicas en el tiempo. Es util en audio y sigue siendo facil de
    explicar en una sustentacion.
    """
    target_time_steps = get_expected_time_steps(segment_duration, sample_rate)

    if feature_type == "mel":
        features = librosa.feature.melspectrogram(
            y=audio,
            sr=sample_rate,
            n_mels=N_MELS,
            n_fft=N_FFT,
            hop_length=HOP_LENGTH,
        )
        features = librosa.power_to_db(features, ref=np.max)

    elif feature_type in ["mfcc", "mfcc_delta"]:
        mfcc = librosa.feature.mfcc(
            y=audio,
            sr=sample_rate,
            n_mfcc=N_MFCC,
            n_fft=N_FFT,
            hop_length=HOP_LENGTH,
        )

        if feature_type == "mfcc":
            features = mfcc
        else:
            delta = librosa.feature.delta(mfcc)
            delta_delta = librosa.feature.delta(mfcc, order=2)
            features = np.concatenate([mfcc, delta, delta_delta], axis=0)

    else:
        raise ValueError("feature_type debe ser 'mel', 'mfcc' o 'mfcc_delta'.")

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
