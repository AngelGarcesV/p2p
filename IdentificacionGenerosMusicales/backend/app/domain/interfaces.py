"""Contratos que desacoplan el servicio de sus implementaciones externas."""

from typing import Protocol

import numpy as np

from app.domain.models import ModelBundle, PredictionResult, PreprocessingConfig


class ModelRepository(Protocol):
    """Contrato para cargar artefactos del modelo."""

    def load(self) -> ModelBundle:
        """Carga y retorna el modelo, etiquetas y configuracion."""


class AudioFeatureExtractor(Protocol):
    """Contrato para convertir audio WAV en features del modelo."""

    def extract(self, wav_bytes: bytes, config: PreprocessingConfig) -> np.ndarray:
        """Extrae segmentos procesados desde bytes WAV."""

    def is_silent(self, wav_bytes: bytes) -> bool:
        """Determina si el audio de entrada es silencioso."""


class GenrePredictionService(Protocol):
    """Contrato de la logica de negocio de prediccion."""

    def load(self) -> None:
        """Carga los artefactos necesarios para predecir."""

    def is_ready(self) -> bool:
        """Indica si el servicio esta listo."""

    def get_genres(self) -> list[str]:
        """Devuelve los generos soportados."""

    def predict(self, wav_bytes: bytes) -> PredictionResult:
        """Predice el genero de un audio WAV."""
