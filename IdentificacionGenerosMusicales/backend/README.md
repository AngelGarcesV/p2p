# Documentacion de la Carpeta `backend`

Esta carpeta contiene la API REST con FastAPI.

El backend es responsable de:

- cargar el modelo entrenado
- recibir archivos WAV
- aplicar el mismo preprocesamiento usado en entrenamiento
- devolver la prediccion en formato JSON

## Archivos

```text
backend/
├── main.py
├── predictor.py
└── requirements.txt
```

## 1. `main.py`

Define la API FastAPI.

### Variables globales

```python
model = None
label_encoder = None
preprocessing_config = None
```

Se inicializan como `None` y se cargan al iniciar la API.

### `startup_event`

Se ejecuta cuando FastAPI arranca.

Carga:

- `models/best_model.keras`
- `models/label_encoder.pkl`
- `models/preprocessing_config.json`

Esto evita cargar el modelo en cada peticion.

### `home`

Endpoint:

```text
GET /
```

Sirve para comprobar que la API esta viva.

### `predict`

Endpoint:

```text
POST /predict
```

Recibe un archivo WAV.

Validaciones basicas:

- verifica extension `.wav`
- verifica que el archivo no este vacio

Luego llama:

```python
predict_genre(model, label_encoder, preprocessing_config, wav_bytes)
```

## 2. `predictor.py`

Contiene la logica real de prediccion.

### Rutas importantes

```python
MODEL_PATH = PROJECT_ROOT / "models" / "best_model.keras"
LABEL_ENCODER_PATH = PROJECT_ROOT / "models" / "label_encoder.pkl"
PREPROCESSING_CONFIG_PATH = PROJECT_ROOT / "models" / "preprocessing_config.json"
```

### `load_model_and_labels`

Carga:

1. modelo Keras
2. label encoder
3. configuracion de preprocesamiento

El label encoder permite convertir indices a nombres de genero.

Ejemplo:

```text
0 -> blues
1 -> classical
...
```

### Compatibilidad de configuracion

Si no existe `preprocessing_config.json`, usa valores por defecto compatibles con la primera version del proyecto:

```python
{
    "feature_type": "mel",
    "segment_duration": 10.0,
    "overlap": 0.0
}
```

Cuando entrenas con la version nueva, `train.py` guarda la configuracion real.

### `predict_genre`

Recibe:

- modelo
- label encoder
- configuracion
- bytes del WAV

Flujo:

```text
bytes WAV
    |
archivo temporal
    |
process_audio_file
    |
segmentos procesados
    |
model.predict(segmentos)
    |
promedio de probabilidades
    |
JSON final
```

### Por Que Se Usa Archivo Temporal

FastAPI recibe el archivo en memoria como bytes.

`librosa` trabaja comodamente con rutas de archivos, por eso el backend guarda temporalmente el WAV.

En Windows se usa:

```python
NamedTemporaryFile(delete=False)
```

Esto evita el error de permisos donde Windows bloquea archivos abiertos.

## 3. Respuesta de la API

Ejemplo:

```json
{
  "predicted_genre": "rock",
  "confidence": 0.91,
  "probabilities": {
    "blues": 0.01,
    "classical": 0.00,
    "country": 0.02
  }
}
```

## 4. Ejecutar Backend

Desde `backend/`:

```bash
uvicorn main:app --reload
```

La API queda en:

```text
http://127.0.0.1:8000
```

