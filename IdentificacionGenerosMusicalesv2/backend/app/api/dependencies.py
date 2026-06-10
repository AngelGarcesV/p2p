"""Dependencias compartidas por los controladores FastAPI."""

from fastapi import Request

from app.domain.interfaces import GenrePredictionService


def get_prediction_service(request: Request) -> GenrePredictionService:
    """Obtiene el servicio de prediccion inicializado en el startup."""
    return request.app.state.prediction_service
