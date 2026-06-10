"""Script principal para entrenar el modelo de identificación de géneros musicales."""

import argparse
import json
import pickle
import sys
from pathlib import Path

import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
from sklearn.preprocessing import LabelEncoder

# Agregar el directorio raíz al path para importar módulos correctamente
sys.path.append(str(Path(__file__).resolve().parents[1]))

import training.config as config
from training.preprocessing import (
    get_dataset_files,
    get_feature_cache_dir,
    is_audio_silent,
    load_or_compute_segments,
    process_audio_file,
    load_audio,
    split_audio_into_segments,
    audio_to_features,
    get_input_shape,
)
from training.augmentations import augment_audio
from training.panns_cnn10 import build_cnn10, load_panns_weights, freeze_backbone, unfreeze_all
from training.utils import (
    set_random_seed,
    create_directories,
    plot_training_history,
    plot_confusion_matrix,
)

import keras

class GenreDataset(keras.utils.PyDataset):
    """Dataset para alimentar el modelo lote por lote en RAM, evitando duplicación de memoria."""
    def __init__(self, X, y, batch_size, shuffle=False):
        super().__init__()
        self.X = X
        self.y = y
        self.batch_size = batch_size
        self.shuffle = shuffle
        self.indices = np.arange(len(self.X))
        if self.shuffle:
            np.random.shuffle(self.indices)

    def __len__(self):
        return int(np.ceil(len(self.X) / self.batch_size))

    def __getitem__(self, idx):
        start = idx * self.batch_size
        end = min(start + self.batch_size, len(self.X))
        batch_indices = self.indices[start:end]
        return self.X[batch_indices], self.y[batch_indices]

    def on_epoch_end(self):
        if self.shuffle:
            np.random.shuffle(self.indices)


def parse_args():
    """Parsea los argumentos de la línea de comandos."""
    parser = argparse.ArgumentParser(description="Entrenamiento de CNN10 (PANNs) para Clasificación de Géneros.")
    parser.add_argument(
        "--dataset_path",
        type=str,
        default=str(config.DATASET_PATH),
        help="Ruta de la carpeta del dataset."
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=config.INITIAL_EPOCHS,
        help="Número de épocas para la fase de Transfer Learning."
    )
    parser.add_argument(
        "--fine_tune_epochs",
        type=int,
        default=config.FINE_TUNE_EPOCHS,
        help="Número de épocas para la fase de Fine-tuning."
    )
    parser.add_argument(
        "--batch_size",
        type=int,
        default=config.BATCH_SIZE,
        help="Tamaño del batch de entrenamiento."
    )
    return parser.parse_args()


def load_and_preprocess_dataset(
    audio_files, labels, label_encoder,
    sample_rate, segment_duration, overlap, feature_type,
    cache_dir,
    augment=False,
):
    """
    Carga archivos de audio, extrae segmentos y features.

    Los espectrogramas base (sin augmentación) se cachean en disco como .npy.
    Así, los re-entrenamientos solo leen arrays numpy en lugar de ejecutar
    librosa.melspectrogram() sobre cada WAV, lo que ahorra varios minutos.

    Para los segmentos aumentados se sigue necesitando el audio crudo porque
    las transformaciones (pitch shift, time stretch) se aplican sobre la señal.
    """
    X = []
    y = []

    total_files = len(audio_files)
    cached_files = 0
    print(f"Cargando y procesando {total_files} archivos (cache en: {cache_dir})...")

    for idx, (file_path, label) in enumerate(zip(audio_files, labels)):
        if idx % 50 == 0:
            print(f"  Archivo {idx}/{total_files} | cacheados hasta ahora: {cached_files}")

        try:
            # load_or_compute_segments devuelve los features base (desde caché si
            # existen) y los segmentos de audio crudo (necesarios para augmentación).
            from pathlib import Path
            cache_exists_before = get_segment_cache_path_exists(file_path, cache_dir)
            base_features, audio_segments = load_or_compute_segments(
                file_path,
                cache_dir=cache_dir,
                sample_rate=sample_rate,
                segment_duration=segment_duration,
                overlap=overlap,
                feature_type=feature_type,
            )
            if cache_exists_before:
                cached_files += 1

            label_id = label_encoder.transform([label])[0]

            for i, feat_orig in enumerate(base_features):
                X.append(feat_orig)
                y.append(label_id)

                if augment:
                    # Para el aumentado necesitamos el audio crudo del segmento
                    aug_segment = augment_audio(audio_segments[i], sample_rate)
                    feat_aug = audio_to_features(aug_segment, sample_rate, feature_type, segment_duration)
                    X.append(feat_aug)
                    y.append(label_id)

        except Exception as e:
            print(f"  Error al procesar {file_path}: {e}")
            continue

    print(f"Preprocesamiento completo. {cached_files}/{total_files} archivos leídos desde caché.")
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)


def get_segment_cache_path_exists(audio_file, cache_dir) -> bool:
    """Helper para saber si ya existía el caché antes de llamar load_or_compute."""
    from training.preprocessing import get_segment_cache_path
    return get_segment_cache_path(audio_file, cache_dir).exists()


def configure_gpu() -> None:
    """
    Configura la GPU para usar memory growth y precision mixta (float16).
    - Memory growth: evita OOM al reservar toda la VRAM de golpe.
    - Mixed precision: reduce uso de VRAM ~40% y acelera calculo en Tensor Cores.
      El GTX 1650 (Turing) soporta float16 nativo.
    """
    import tensorflow as tf
    gpus = tf.config.list_physical_devices("GPU")
    if gpus:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print(f"GPU configurada con memory_growth: {[g.name for g in gpus]}")
    else:
        print("AVISO: No se detecto GPU. El entrenamiento se ejecutara en CPU.")

    # Usar precisión estándar float32.
    # En tarjetas como la GTX 1650 (arquitectura Turing sin Tensor Cores), mixed_float16
    # puede causar emulación lenta y cuellos de botella de conversión en CPU.
    import keras
    keras.mixed_precision.set_global_policy("float32")
    print("Precision estandar activada: float32")


def main():
    args = parse_args()

    # Configurar GPU antes de importar Keras/crear modelos
    configure_gpu()

    # Fijar la semilla y crear directorios de salida
    set_random_seed(config.RANDOM_SEED)
    create_directories([config.MODELS_DIR, config.METRICS_DIR, config.PLOTS_DIR])

    # 1. Obtener archivos y etiquetas
    print("Recuperando lista de archivos del dataset...")
    audio_files_raw, labels_raw = get_dataset_files(args.dataset_path)
    
    if not audio_files_raw:
        print(f"Error: No se encontraron archivos WAV en la carpeta {args.dataset_path}.")
        sys.exit(1)

    # 2. Detección y filtrado de audios vacíos/silenciosos en el dataset
    print("Filtrando audios vacíos o silenciosos...")
    audio_files = []
    labels = []
    silent_count = 0

    for file_path, label in zip(audio_files_raw, labels_raw):
        if is_audio_silent(file_path, threshold=config.SILENCE_THRESHOLD):
            silent_count += 1
            print(f"Ignorando audio silencioso/blanco: {Path(file_path).name}")
        else:
            audio_files.append(file_path)
            labels.append(label)

    print(f"Filtrado finalizado. Se ignoraron {silent_count} archivos silenciosos. Quedan {len(audio_files)} archivos válidos.")

    # 3. Codificación de etiquetas
    label_encoder = LabelEncoder()
    label_encoder.fit(labels)
    
    label_encoder_path = config.MODELS_DIR / "label_encoder.pkl"
    with open(label_encoder_path, "wb") as f:
        pickle.dump(label_encoder, f)
    print(f"Label encoder guardado en {label_encoder_path}")

    # 4. División Train / Val / Test (Estratificada a nivel de archivo)
    print("Dividiendo dataset de forma estratificada...")
    # Primero separamos Train (70%) y Temp (30%)
    files_train, files_temp, labels_train, labels_temp = train_test_split(
        audio_files,
        labels,
        test_size=0.30,
        random_state=config.RANDOM_SEED,
        stratify=labels
    )
    # De Temp separamos Validación (50%) y Prueba (50%), resultando en 15% y 15% del total
    files_val, files_test, labels_val, labels_test = train_test_split(
        files_temp,
        labels_temp,
        test_size=0.50,
        random_state=config.RANDOM_SEED,
        stratify=labels_temp
    )

    print(f"Particiones del Dataset: Train={len(files_train)}, Val={len(files_val)}, Test={len(files_test)}")

    # Preparar directorio de caché con clave basada en los parámetros de audio
    feature_cache_dir = get_feature_cache_dir(
        config.CACHE_DIR,
        feature_type=config.FEATURE_TYPE,
        sample_rate=config.SAMPLE_RATE,
        segment_duration=config.SEGMENT_DURATION_SECONDS,
        overlap=config.SEGMENT_OVERLAP,
    )
    feature_cache_dir.mkdir(parents=True, exist_ok=True)
    print(f"Caché de espectrogramas: {feature_cache_dir}")

    # 5. Cargar y procesar conjuntos de datos
    print("\n--- Procesando Conjunto de Entrenamiento ---")
    X_train, y_train = load_and_preprocess_dataset(
        files_train, labels_train, label_encoder,
        sample_rate=config.SAMPLE_RATE,
        segment_duration=config.SEGMENT_DURATION_SECONDS,
        overlap=config.SEGMENT_OVERLAP,
        feature_type=config.FEATURE_TYPE,
        cache_dir=feature_cache_dir,
        augment=True,
    )

    print("\n--- Procesando Conjunto de Validación ---")
    X_val, y_val = load_and_preprocess_dataset(
        files_val, labels_val, label_encoder,
        sample_rate=config.SAMPLE_RATE,
        segment_duration=config.SEGMENT_DURATION_SECONDS,
        overlap=config.SEGMENT_OVERLAP,
        feature_type=config.FEATURE_TYPE,
        cache_dir=feature_cache_dir,
        augment=False,
    )

    print(f"\nDatos cargados en memoria. Train shape: {X_train.shape}, Val shape: {X_val.shape}")

    # Crear datasets ligeros de Keras para evitar duplicaciones en memoria RAM y OOM
    print("\nCreando datasets Keras PyDataset para control de memoria RAM y VRAM...")
    train_dataset_p1 = GenreDataset(X_train, y_train, batch_size=args.batch_size, shuffle=True)
    val_dataset_p1 = GenreDataset(X_val, y_val, batch_size=args.batch_size, shuffle=False)

    train_dataset_p2 = GenreDataset(X_train, y_train, batch_size=config.FINE_TUNE_BATCH_SIZE, shuffle=True)
    val_dataset_p2 = GenreDataset(X_val, y_val, batch_size=config.FINE_TUNE_BATCH_SIZE, shuffle=False)

    # 6. Construir el modelo
    input_shape = get_input_shape(config.FEATURE_TYPE, config.SEGMENT_DURATION_SECONDS)
    num_classes = len(label_encoder.classes_)
    
    print("\nConstruyendo arquitectura de CNN10 (PANNs)...")
    model = build_cnn10(input_shape, num_classes)
    
    # Cargar pesos preentrenados
    weights_npz_path = config.MODELS_DIR / "panns_cnn10_weights.npz"
    load_panns_weights(model, weights_npz_path)
    
    model.summary()

    # 7. FASE 1: Entrenar la cabeza de clasificación (backbone congelado)
    print("\n--- FASE 1: Entrenando clasificador superior (Transfer Learning) ---")
    freeze_backbone(model)
    
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=config.INITIAL_LEARNING_RATE),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    callbacks = [
        keras.callbacks.EarlyStopping(
            monitor="val_accuracy",
            patience=5,
            restore_best_weights=True,
            verbose=1
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=3,
            min_lr=1e-6,
            verbose=1
        )
    ]

    history = model.fit(
        train_dataset_p1,
        validation_data=val_dataset_p1,
        epochs=args.epochs,
        callbacks=callbacks,
        verbose=1
    )

    # 8. FASE 2: Entrenar con descongelado completo (Fine-tuning)
    print("\n--- FASE 2: Descongelando todo el modelo y afinando (Fine-tuning) ---")
    unfreeze_all(model)
    
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=config.FINE_TUNE_LEARNING_RATE),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    # Re-definir callbacks con paciencia extendida para el ajuste fino
    fine_tune_callbacks = [
        keras.callbacks.EarlyStopping(
            monitor="val_accuracy",
            patience=8,
            restore_best_weights=True,
            verbose=1
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=4,
            min_lr=1e-7,
            verbose=1
        )
    ]

    fine_tune_history = model.fit(
        train_dataset_p2,
        validation_data=val_dataset_p2,
        epochs=args.fine_tune_epochs,
        callbacks=fine_tune_callbacks,
        verbose=1
    )

    # 9. Evaluar en el conjunto de prueba (Test) a nivel de ARCHIVO completo
    # Para coincidir con la lógica del backend, promediamos las predicciones de los segmentos de cada archivo
    print("\n--- Evaluando en Conjunto de Prueba (File-Level) ---")
    y_test_true = []
    y_test_pred = []

    for file_path, label in zip(files_test, labels_test):
        try:
            # Procesar el archivo en sus segmentos
            segments = process_audio_file(
                file_path,
                feature_type=config.FEATURE_TYPE,
                segment_duration=config.SEGMENT_DURATION_SECONDS,
                overlap=config.SEGMENT_OVERLAP
            )
            
            # Predecir probabilidades de cada segmento
            segment_probabilities = model.predict(segments, verbose=0)
            
            # Promediar probabilidades a nivel de archivo
            mean_probabilities = np.mean(segment_probabilities, axis=0)
            
            # Obtener índice de clase ganador
            predicted_idx = int(np.argmax(mean_probabilities))
            predicted_label = label_encoder.inverse_transform([predicted_idx])[0]
            
            y_test_true.append(label)
            y_test_pred.append(predicted_label)
        except Exception as e:
            print(f"Error evaluando archivo de prueba {file_path}: {e}")
            continue

    # 10. Reportar métricas y generar plots
    classes_list = label_encoder.classes_.tolist()
    report = classification_report(y_test_true, y_test_pred, target_names=classes_list, output_dict=True)
    print("\nResultado de la clasificación:")
    print(classification_report(y_test_true, y_test_pred, target_names=classes_list))

    # Guardar métricas en JSON
    metrics_path = config.METRICS_DIR / "metrics.json"
    with open(metrics_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=4)
    print(f"Métricas detalladas guardadas en {metrics_path}")

    # Guardar curvas e historial
    history_plot_path = config.PLOTS_DIR / "cnn10_training_history.png"
    plot_training_history(history, fine_tune_history, history_plot_path)

    # Guardar matriz de confusión
    cm_plot_path = config.PLOTS_DIR / "cnn10_confusion_matrix.png"
    plot_confusion_matrix(y_test_true, y_test_pred, classes_list, cm_plot_path)

    # 11. Guardar modelo y archivo de configuración final de preprocesamiento
    best_model_path = config.MODELS_DIR / "best_model.keras"
    model.save(best_model_path)
    print(f"\nModelo final guardado exitosamente en {best_model_path}")

    preprocessing_config = {
        "feature_type": config.FEATURE_TYPE,
        "segment_duration": config.SEGMENT_DURATION_SECONDS,
        "overlap": config.SEGMENT_OVERLAP
    }
    
    config_json_path = config.MODELS_DIR / "preprocessing_config.json"
    with open(config_json_path, "w", encoding="utf-8") as f:
        json.dump(preprocessing_config, f, indent=4)
    print(f"Configuración de preprocesamiento guardada en {config_json_path}")


if __name__ == "__main__":
    main()
