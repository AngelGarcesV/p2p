"""Adaptadores de compatibilidad para codigo que aun importa predictor.py.

La implementacion principal vive ahora en app/services e app/infrastructure.
"""

from app.core.config import Settings
from app.domain.models import ModelBundle, PreprocessingConfig
from app.infrastructure.audio_feature_extractor import TrainingAudioFeatureExtractor
from app.infrastructure.keras_model_repository import KerasModelRepository
from app.services.genre_prediction_service import KerasGenrePredictionService


def load_model_and_labels():
    """Carga artefactos usando la arquitectura nueva."""
    bundle = _load_bundle()
    return bundle.model, bundle.label_encoder, bundle.preprocessing_config


def predict_genre(model, label_encoder, preprocessing_config, wav_bytes):
    """Predice manteniendo la firma publica anterior."""
    settings = Settings.from_project_root()
    if isinstance(preprocessing_config, dict):
        preprocessing_config = PreprocessingConfig.from_dict(preprocessing_config)

    service = KerasGenrePredictionService(
        model_repository=_LoadedBundleRepository(
            ModelBundle(model, label_encoder, preprocessing_config),
        ),
        feature_extractor=TrainingAudioFeatureExtractor(settings.project_root),
    )
    service.load()
    return service.predict(wav_bytes).to_dict()


def _load_bundle() -> ModelBundle:
    settings = Settings.from_project_root()
    return KerasModelRepository(settings).load()


class _LoadedBundleRepository:
    """Repositorio en memoria para preservar la API legacy de predict_genre."""

    def __init__(self, bundle: ModelBundle) -> None:
        self._bundle = bundle

    def load(self) -> ModelBundle:
        return self._bundle
