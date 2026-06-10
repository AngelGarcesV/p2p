"""Configuracion centralizada del backend."""

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    """Rutas y metadatos necesarios para ejecutar la API."""

    project_root: Path
    model_path: Path
    label_encoder_path: Path
    preprocessing_config_path: Path
    app_title: str = "GTZAN Music Genre Classifier"
    app_description: str = "API para clasificar generos musicales desde audios WAV."
    app_version: str = "1.0.0"

    @classmethod
    def from_project_root(cls) -> "Settings":
        project_root = Path(__file__).resolve().parents[3]
        models_path = project_root / "models"
        return cls(
            project_root=project_root,
            model_path=models_path / "best_model.keras",
            label_encoder_path=models_path / "label_encoder.pkl",
            preprocessing_config_path=models_path / "preprocessing_config.json",
        )
