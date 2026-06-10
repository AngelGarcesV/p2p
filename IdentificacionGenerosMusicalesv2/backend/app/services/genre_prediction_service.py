"""Servicio de negocio para clasificacion de generos musicales."""

import numpy as np

from app.domain.exceptions import ModelNotLoadedError, PredictionError
from app.domain.interfaces import AudioFeatureExtractor, ModelRepository
from app.domain.models import ModelBundle, PredictionResult


class KerasGenrePredictionService:
    """Orquesta carga de artefactos, extraccion de features y prediccion."""

    def __init__(
        self,
        model_repository: ModelRepository,
        feature_extractor: AudioFeatureExtractor,
    ) -> None:
        self._model_repository = model_repository
        self._feature_extractor = feature_extractor
        self._bundle: ModelBundle | None = None

    def load(self) -> None:
        self._bundle = self._model_repository.load()

    def is_ready(self) -> bool:
        return self._bundle is not None

    def get_genres(self) -> list[str]:
        bundle = self._get_loaded_bundle()
        return bundle.label_encoder.classes_.tolist()

    def predict(self, wav_bytes: bytes) -> PredictionResult:
        bundle = self._get_loaded_bundle()

        try:
            segments = self._feature_extractor.extract(
                wav_bytes,
                bundle.preprocessing_config,
            )
            segment_probabilities = bundle.model.predict(segments)
            probabilities = np.mean(segment_probabilities, axis=0)
        except Exception as error:
            raise PredictionError(f"No fue posible procesar el audio: {error}") from error

        predicted_index = int(np.argmax(probabilities))
        predicted_genre = bundle.label_encoder.inverse_transform([predicted_index])[0]
        confidence = float(probabilities[predicted_index])
        probabilities_by_genre = {
            genre: float(probabilities[index])
            for index, genre in enumerate(bundle.label_encoder.classes_)
        }

        return PredictionResult(
            predicted_genre=predicted_genre,
            confidence=confidence,
            probabilities=probabilities_by_genre,
        )

    def _get_loaded_bundle(self) -> ModelBundle:
        if self._bundle is None:
            raise ModelNotLoadedError("Modelo no cargado.")
        return self._bundle
