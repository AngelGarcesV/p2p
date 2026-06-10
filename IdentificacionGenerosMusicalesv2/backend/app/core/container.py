"""Construccion explicita de dependencias de la aplicacion."""

from app.core.config import Settings
from app.infrastructure.audio_feature_extractor import TrainingAudioFeatureExtractor
from app.infrastructure.keras_model_repository import KerasModelRepository
from app.services.genre_prediction_service import KerasGenrePredictionService


def build_prediction_service() -> KerasGenrePredictionService:
    """Crea el servicio con sus dependencias concretas."""
    settings = Settings.from_project_root()
    model_repository = KerasModelRepository(settings)
    feature_extractor = TrainingAudioFeatureExtractor(settings.project_root)
    return KerasGenrePredictionService(model_repository, feature_extractor)
