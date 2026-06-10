"""Controladores HTTP para la API de prediccion."""

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile

from app.api.dependencies import get_prediction_service
from app.domain.exceptions import ModelNotLoadedError, PredictionError
from app.domain.interfaces import GenrePredictionService
from app.schemas.responses import GenresResponse, HealthResponse, HomeResponse, PredictionResponse


router = APIRouter()


@router.get("/", response_model=HomeResponse)
def home() -> HomeResponse:
    """Verifica que la API esta activa."""
    return HomeResponse(message="API de clasificacion de generos musicales funcionando")


@router.get("/health", response_model=HealthResponse)
def health(
    service: GenrePredictionService = Depends(get_prediction_service),
) -> HealthResponse:
    """Indica si el modelo esta cargado y listo para recibir peticiones."""
    ready = service.is_ready()
    return HealthResponse(status="ok" if ready else "error", model_loaded=ready)


@router.get("/genres", response_model=GenresResponse)
def genres(
    service: GenrePredictionService = Depends(get_prediction_service),
) -> GenresResponse:
    """Lista de generos que el modelo puede predecir."""
    try:
        return GenresResponse(genres=service.get_genres())
    except ModelNotLoadedError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error


@router.post("/predict", response_model=PredictionResponse)
async def predict(
    file: UploadFile = File(...),
    service: GenrePredictionService = Depends(get_prediction_service),
) -> PredictionResponse:
    """Recibe un archivo WAV y responde el genero predicho."""
    if not file.filename or not file.filename.lower().endswith(".wav"):
        raise HTTPException(status_code=400, detail="El archivo debe ser WAV.")

    wav_bytes = await file.read()
    if not wav_bytes:
        raise HTTPException(status_code=400, detail="El archivo esta vacio.")

    try:
        prediction = service.predict(wav_bytes)
        return PredictionResponse(**prediction.to_dict())
    except ModelNotLoadedError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except PredictionError as error:
        raise HTTPException(status_code=500, detail=str(error)) from error
