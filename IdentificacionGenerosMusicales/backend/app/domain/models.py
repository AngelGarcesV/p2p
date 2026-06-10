"""Modelos internos del dominio."""

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class PreprocessingConfig:
    """Configuracion usada para reproducir el preprocesamiento del entrenamiento."""

    feature_type: str = "mel"
    segment_duration: float = 10.0
    overlap: float = 0.0

    @classmethod
    def from_dict(cls, values: dict[str, Any]) -> "PreprocessingConfig":
        return cls(
            feature_type=str(values.get("feature_type", cls.feature_type)),
            segment_duration=float(values.get("segment_duration", cls.segment_duration)),
            overlap=float(values.get("overlap", cls.overlap)),
        )


@dataclass(frozen=True)
class ModelBundle:
    """Artefactos requeridos para predecir."""

    model: Any
    label_encoder: Any
    preprocessing_config: PreprocessingConfig


@dataclass(frozen=True)
class PredictionResult:
    """Respuesta de prediccion independiente del framework web."""

    predicted_genre: str
    confidence: float
    probabilities: dict[str, float]

    def to_dict(self) -> dict[str, object]:
        return {
            "predicted_genre": self.predicted_genre,
            "confidence": self.confidence,
            "probabilities": self.probabilities,
        }
