"""Excepciones propias del dominio de prediccion."""


class ModelNotLoadedError(RuntimeError):
    """Se lanza cuando el servicio no tiene artefactos cargados."""


class PredictionError(RuntimeError):
    """Se lanza cuando no fue posible completar una prediccion."""
