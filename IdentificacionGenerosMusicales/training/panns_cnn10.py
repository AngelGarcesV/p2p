"""
CNN10 de PANNs (Pretrained Audio Neural Networks) reimplementado en Keras/TensorFlow.

Referencia:
    Kong, Q., Cao, Y., Iqbal, T., Wang, Y., Wang, W., & Plumbley, M. D. (2020).
    PANNs: Large-Scale Pretrained Audio Neural Networks for Audio Pattern Recognition.
    IEEE/ACM Transactions on Audio, Speech, and Language Processing.
    https://arxiv.org/abs/1912.10211

Pesos preentrenados: AudioSet (2M+ clips, 527 categorias de audio de YouTube).
Descarga y conversion: ver training/setup_panns.py

Arquitectura:
    Input (mel_bins=64, time_steps~400, 1 canal)
    → ConvBlock(64)  → ConvBlock(128) → ConvBlock(256) → ConvBlock(512)
    → AudioPooling (reduce freq con avg, luego max+avg sobre tiempo)
    → Dropout(0.5) → Dense(512, relu) → Dropout(0.5) → Dense(n_classes, softmax)
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import keras
from keras import layers
import tensorflow as tf


# ---------------------------------------------------------------------------
# Bloque convolucional
# ---------------------------------------------------------------------------

def _conv_block(x: tf.Tensor, out_channels: int, prefix: str) -> tf.Tensor:
    """
    Bloque convolucional estandar de CNN10.

    Dos capas Conv2D(3×3) con BN y ReLU, seguidas de Dropout(0.2) y AvgPool(2×2).
    El epsilon=1e-5 coincide con el valor por defecto de PyTorch para que los pesos
    preentrenados de BatchNorm funcionen correctamente.
    """
    for i in (1, 2):
        x = layers.Conv2D(
            out_channels,
            kernel_size=(3, 3),
            padding="same",
            use_bias=False,          # PANNs no usa bias en conv (absorvido por BN)
            name=f"{prefix}_conv{i}",
        )(x)
        x = layers.BatchNormalization(
            epsilon=1e-5,            # PyTorch default, importante para cargar pesos
            name=f"{prefix}_bn{i}",
        )(x)
        x = layers.ReLU(name=f"{prefix}_relu{i}")(x)

    x = layers.Dropout(0.2, name=f"{prefix}_drop")(x)
    x = layers.AveragePooling2D((2, 2), name=f"{prefix}_pool")(x)
    return x


# ---------------------------------------------------------------------------
# Pooling de audio personalizado
# ---------------------------------------------------------------------------

class AudioPooling(layers.Layer):
    """
    Pooling final de CNN10 que reproduce la operacion del paper original:

        1. Promedio sobre el eje de frecuencias (mel bins).
        2. Max-pooling y avg-pooling independientes sobre el eje temporal.
        3. Suma de ambos resultados.

    En nuestro layout Keras (H=mel_bins, W=time_steps, C=channels):
        x shape: (batch, mel_bins/16, time_steps/16, 512)
        → reduce H → (batch, time_steps/16, 512)
        → max + avg sobre W → (batch, 512)
    """

    def call(self, x: tf.Tensor, training: bool = False) -> tf.Tensor:
        x = tf.reduce_mean(x, axis=1)       # reduce eje de frecuencias
        x_max = tf.reduce_max(x, axis=1)    # max sobre tiempo
        x_avg = tf.reduce_mean(x, axis=1)   # avg sobre tiempo
        return x_max + x_avg

    def get_config(self) -> dict:
        return super().get_config()


# ---------------------------------------------------------------------------
# Constructor del modelo
# ---------------------------------------------------------------------------

def build_cnn10(input_shape: tuple[int, int, int], num_classes: int) -> keras.Model:
    """
    Construye CNN10 en Keras.

    Args:
        input_shape: (mel_bins, time_steps, 1). PANNs usa (64, ~400, 1) con 32 kHz.
        num_classes: numero de generos a clasificar (10 para GTZAN).

    Returns:
        Modelo Keras sin compilar. Cargar pesos preentrenados con load_panns_weights().
    """
    inputs = layers.Input(shape=input_shape, name="mel_input")

    x = _conv_block(inputs, 64,  prefix="conv_block1")
    x = _conv_block(x,     128, prefix="conv_block2")
    x = _conv_block(x,     256, prefix="conv_block3")
    x = _conv_block(x,     512, prefix="conv_block4")

    x = AudioPooling(name="audio_pool")(x)

    x = layers.Dropout(0.5, name="dropout_fc")(x)
    x = layers.Dense(512, activation="relu", name="fc1")(x)
    x = layers.Dropout(0.5, name="dropout_out")(x)

    # dtype="float32" obligatorio con mixed_float16 para estabilidad numerica de la loss
    outputs = layers.Dense(
        num_classes,
        activation="softmax",
        name="predictions",
        dtype="float32",
    )(x)

    return keras.Model(inputs=inputs, outputs=outputs, name="CNN10_Genre_Classifier")


# ---------------------------------------------------------------------------
# Carga de pesos preentrenados
# ---------------------------------------------------------------------------

def load_panns_weights(model: keras.Model, weights_npz_path: str | Path) -> bool:
    """
    Carga los pesos preentrenados de PANNs CNN10 desde un archivo .npz.

    El archivo .npz debe generarse previamente con training/setup_panns.py.
    Solo se cargan las capas de extraccion de features (conv_block1-4 y fc1).
    La cabeza de clasificacion (predictions) se inicializa aleatoriamente porque
    GTZAN tiene clases distintas de AudioSet.

    Args:
        model: modelo CNN10 construido con build_cnn10().
        weights_npz_path: ruta al archivo .npz con los pesos convertidos.

    Returns:
        True si los pesos se cargaron, False si el archivo no existe.
    """
    weights_path = Path(weights_npz_path)
    if not weights_path.exists():
        print(
            f"AVISO: {weights_path} no encontrado.\n"
            "Ejecuta primero: python3.12 training/setup_panns.py\n"
            "El modelo entrenara desde cero (sin pesos de AudioSet)."
        )
        return False

    data = np.load(str(weights_path))
    loaded_layers = 0

    for layer in model.layers:
        name = layer.name

        # Capas convolucionales (sin bias en PANNs)
        if isinstance(layer, layers.Conv2D):
            key = f"{name}_weight"
            if key in data:
                # PyTorch: (out_ch, in_ch, H, W) → Keras: (H, W, in_ch, out_ch)
                kernel = np.transpose(data[key], (2, 3, 1, 0))
                layer.set_weights([kernel])
                loaded_layers += 1

        # BatchNormalization
        elif isinstance(layer, layers.BatchNormalization):
            keys = {
                "gamma": f"{name}_gamma",
                "beta": f"{name}_beta",
                "mean": f"{name}_mean",
                "var": f"{name}_var",
            }
            if all(k in data for k in keys.values()):
                layer.set_weights([
                    data[keys["gamma"]],
                    data[keys["beta"]],
                    data[keys["mean"]],
                    data[keys["var"]],
                ])
                loaded_layers += 1

        # Capa densa fc1 (no se carga 'predictions' porque las clases son diferentes)
        elif isinstance(layer, layers.Dense) and name == "fc1":
            weight_key = "fc1_weight"
            bias_key = "fc1_bias"
            if weight_key in data and bias_key in data:
                # PyTorch: (out, in) → Keras: (in, out)
                kernel = np.transpose(data[weight_key], (1, 0))
                layer.set_weights([kernel, data[bias_key]])
                loaded_layers += 1

    print(f"Pesos PANNs cargados exitosamente: {loaded_layers} capas desde {weights_path}")
    return True


# ---------------------------------------------------------------------------
# Utilidades de fine-tuning
# ---------------------------------------------------------------------------

def freeze_backbone(model: keras.Model) -> None:
    """
    Congela todos los bloques convolucionales y fc1 (backbone preentrenado).
    Solo la capa 'predictions' quedara entrenable.
    Util para la Fase 1: entrenar solo la cabeza de clasificacion.
    """
    frozen = 0
    for layer in model.layers:
        if layer.name in ("predictions", "dropout_out"):
            layer.trainable = True
        else:
            layer.trainable = False
            frozen += 1

    trainable = sum(1 for l in model.layers if l.trainable)
    print(f"Backbone congelado: {frozen} capas fijas, {trainable} entrenables.")


def unfreeze_all(model: keras.Model) -> None:
    """
    Descongela todas las capas para el fine-tuning completo (Fase 2).
    """
    for layer in model.layers:
        layer.trainable = True

    total_params = model.count_params()
    print(f"Modelo completamente descongelado: {total_params:,} parametros entrenables.")
