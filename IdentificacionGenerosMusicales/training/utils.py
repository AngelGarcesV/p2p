"""Funciones auxiliares para el entrenamiento y evaluación."""

import os
import random
from pathlib import Path
import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf
from sklearn.metrics import confusion_matrix
import seaborn as sns


def set_random_seed(seed: int = 42) -> None:
    """Fija la semilla aleatoria para asegurar reproducibilidad."""
    random.seed(seed)
    np.random.seed(seed)
    tf.random.set_seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    os.environ["TF_DETERMINISTIC_OPS"] = "1"
    print(f"Semilla de reproducibilidad fijada en {seed}.")


def create_directories(directories: list[Path]) -> None:
    """Crea los directorios necesarios si no existen."""
    for directory in directories:
        directory.mkdir(parents=True, exist_ok=True)


def plot_training_history(
    history: tf.keras.callbacks.History,
    fine_tune_history: tf.keras.callbacks.History | None,
    save_path: Path
) -> None:
    """
    Dibuja y guarda la curva de pérdida (loss) y precisión (accuracy)
    para el entrenamiento de la cabeza y el posterior fine-tuning.
    """
    # Combinar métricas de la fase inicial y fine-tuning si existe
    acc = history.history["accuracy"]
    val_acc = history.history["val_accuracy"]
    loss = history.history["loss"]
    val_loss = history.history["val_loss"]

    initial_epochs = len(acc)

    if fine_tune_history is not None:
        acc += fine_tune_history.history["accuracy"]
        val_acc += fine_tune_history.history["val_accuracy"]
        loss += fine_tune_history.history["loss"]
        val_loss += fine_tune_history.history["val_loss"]

    epochs_range = range(1, len(acc) + 1)

    plt.figure(figsize=(12, 6))

    # Precisión
    plt.subplot(1, 2, 1)
    plt.plot(epochs_range, acc, label="Train Accuracy", color="#2ca02c")
    plt.plot(epochs_range, val_acc, label="Val Accuracy", color="#d62728")
    if fine_tune_history is not None:
        plt.axvline(x=initial_epochs, label="Inicio Fine-Tuning", color="#1f77b4", linestyle="--")
    plt.title("Precisión de Entrenamiento y Validación")
    plt.xlabel("Épocas")
    plt.ylabel("Precisión")
    plt.legend(loc="lower right")
    plt.grid(True, linestyle=":", alpha=0.6)

    # Pérdida
    plt.subplot(1, 2, 2)
    plt.plot(epochs_range, loss, label="Train Loss", color="#2ca02c")
    plt.plot(epochs_range, val_loss, label="Val Loss", color="#d62728")
    if fine_tune_history is not None:
        plt.axvline(x=initial_epochs, label="Inicio Fine-Tuning", color="#1f77b4", linestyle="--")
    plt.title("Pérdida de Entrenamiento y Validación")
    plt.xlabel("Épocas")
    plt.ylabel("Pérdida")
    plt.legend(loc="upper right")
    plt.grid(True, linestyle=":", alpha=0.6)

    plt.tight_layout()
    plt.savefig(save_path, dpi=300)
    plt.close()
    print(f"Curvas de aprendizaje guardadas en: {save_path}")


def plot_confusion_matrix(
    y_true: list[str] | np.ndarray,
    y_pred: list[str] | np.ndarray,
    classes: list[str],
    save_path: Path
) -> None:
    """
    Calcula, dibuja y guarda la matriz de confusión.
    """
    cm = confusion_matrix(y_true, y_pred, labels=classes)
    
    # Normalizar por fila (proporción de predicciones por clase verdadera)
    cm_normalized = cm.astype("float") / cm.sum(axis=1)[:, np.newaxis]
    # Evitar divisiones por cero o valores nulos
    cm_normalized = np.nan_to_num(cm_normalized)

    plt.figure(figsize=(10, 8))
    sns.heatmap(
        cm_normalized,
        annot=True,
        fmt=".2f",
        cmap="Blues",
        xticklabels=classes,
        yticklabels=classes,
        cbar=True,
        square=True
    )
    plt.title("Matriz de Confusión Normalizada")
    plt.ylabel("Género Real")
    plt.xlabel("Género Predicho")
    plt.xticks(rotation=45, ha="right")
    plt.yticks(rotation=0)
    plt.tight_layout()
    plt.savefig(save_path, dpi=300)
    plt.close()
    print(f"Matriz de confusión guardada en: {save_path}")
