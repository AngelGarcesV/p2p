"""Adaptador de preprocesamiento de audio basado en el modulo de entrenamiento."""

import sys
import tempfile
from pathlib import Path

import numpy as np

from app.domain.models import PreprocessingConfig


class TrainingAudioFeatureExtractor:
    """Reutiliza el mismo preprocesamiento usado durante el entrenamiento."""

    def __init__(self, project_root: Path) -> None:
        training_path = project_root / "training"
        if str(training_path) not in sys.path:
            sys.path.append(str(training_path))

        from preprocessing import is_audio_silent, process_audio_file  # noqa: WPS433

        self._is_audio_silent = is_audio_silent
        self._process_audio_file = process_audio_file

    def extract(self, wav_bytes: bytes, config: PreprocessingConfig) -> np.ndarray:
        temp_path = None

        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
                temp_file.write(wav_bytes)
                temp_path = temp_file.name

            return self._process_audio_file(
                temp_path,
                feature_type=config.feature_type,
                segment_duration=config.segment_duration,
                overlap=config.overlap,
            )
        finally:
            if temp_path is not None:
                Path(temp_path).unlink(missing_ok=True)

    def is_silent(self, wav_bytes: bytes) -> bool:
        """Determina si los bytes de audio representan un silencio."""
        temp_path = None

        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
                temp_file.write(wav_bytes)
                temp_path = temp_file.name

            return self._is_audio_silent(temp_path)
        finally:
            if temp_path is not None:
                Path(temp_path).unlink(missing_ok=True)
