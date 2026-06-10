"""Carga de artefactos Keras y sklearn desde disco."""

import json
import pickle

import keras

from app.core.config import Settings
from app.domain.models import ModelBundle, PreprocessingConfig


class KerasModelRepository:
    """Repositorio de artefactos entrenados."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def load(self) -> ModelBundle:
        self._ensure_artifacts_exist()

        # Importar AudioPooling dinámicamente desde el módulo de entrenamiento
        import sys
        training_path = self._settings.project_root / "training"
        if str(training_path) not in sys.path:
            sys.path.append(str(training_path))
        from panns_cnn10 import AudioPooling

        model = keras.models.load_model(
            self._settings.model_path,
            custom_objects={"AudioPooling": AudioPooling},
            compile=False
        )
        with open(self._settings.label_encoder_path, "rb") as file:
            label_encoder = pickle.load(file)

        preprocessing_config = self._load_preprocessing_config()
        return ModelBundle(model, label_encoder, preprocessing_config)

    def _ensure_artifacts_exist(self) -> None:
        if not self._settings.model_path.exists():
            raise FileNotFoundError(
                f"No se encontro el modelo en {self._settings.model_path}. "
                "Primero ejecuta training/train.py."
            )

        if not self._settings.label_encoder_path.exists():
            raise FileNotFoundError(
                f"No se encontro label_encoder.pkl en {self._settings.label_encoder_path}."
            )

    def _load_preprocessing_config(self) -> PreprocessingConfig:
        values = {}
        if self._settings.preprocessing_config_path.exists():
            with open(self._settings.preprocessing_config_path, "r", encoding="utf-8") as file:
                values = json.load(file)

        return PreprocessingConfig.from_dict(values)
