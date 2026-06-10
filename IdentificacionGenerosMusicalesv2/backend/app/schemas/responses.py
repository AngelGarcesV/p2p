"""Modelos de respuesta HTTP."""

from pydantic import BaseModel


class HomeResponse(BaseModel):
    message: str


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool


class GenresResponse(BaseModel):
    genres: list[str]


class PredictionResponse(BaseModel):
    predicted_genre: str
    confidence: float
    probabilities: dict[str, float]
