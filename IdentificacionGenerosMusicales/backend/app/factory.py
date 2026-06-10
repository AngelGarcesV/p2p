"""Fabrica de la aplicacion FastAPI."""

from fastapi import FastAPI

from app.api.routes import router
from app.core.config import Settings
from app.core.container import build_prediction_service


def create_app() -> FastAPI:
    """Crea la aplicacion y registra rutas/eventos."""
    settings = Settings.from_project_root()
    app = FastAPI(
        title=settings.app_title,
        description=settings.app_description,
        version=settings.app_version,
    )

    app.state.prediction_service = build_prediction_service()

    @app.on_event("startup")
    def startup_event() -> None:
        app.state.prediction_service.load()

    app.include_router(router)
    return app
