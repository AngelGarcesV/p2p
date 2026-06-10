# Guia de Usuario

Esta guia explica como usar el sistema despues de tenerlo instalado y entrenado.

## 1. Entrenar el modelo

Antes de usar la API o la interfaz, debes entrenar el modelo:

```bash
python training/train.py
```

Al finalizar deben existir estos archivos:

```text
models/best_model.keras
models/label_encoder.pkl
models/preprocessing_config.json
```

Sin esos archivos, la API no puede hacer predicciones.

## 2. Iniciar el backend

Abre una terminal y entra a la carpeta `backend`:

```bash
cd backend
uvicorn main:app --reload
```

Si todo esta bien, veras que FastAPI queda ejecutandose en:

```text
http://127.0.0.1:8000
```

Deja esta terminal abierta.

## 3. Iniciar el frontend

Abre otra terminal y entra a la carpeta `frontend`:

```bash
cd frontend
streamlit run streamlit_app.py
```

Streamlit abrira una pagina web local.

## 4. Subir un audio

En la interfaz:

1. Presiona el selector de archivo.
2. Elige un archivo `.wav`.
3. Reproduce el audio si quieres escucharlo.
4. Presiona `Predecir genero`.

## 5. Interpretar resultados

La interfaz muestra:

- Genero predicho: la clase con mayor probabilidad.
- Confianza: probabilidad asignada al genero ganador.
- Grafico de barras: probabilidad para cada genero.

Ejemplo:

```json
{
  "predicted_genre": "rock",
  "confidence": 0.91,
  "probabilities": {
    "blues": 0.01,
    "classical": 0.00,
    "country": 0.02,
    "disco": 0.01,
    "hiphop": 0.01,
    "jazz": 0.00,
    "metal": 0.02,
    "pop": 0.01,
    "reggae": 0.01,
    "rock": 0.91
  }
}
```

Una confianza alta indica que el modelo esta mas seguro. Una confianza baja indica que el audio puede parecerse a varios generos.

## Problemas comunes

### La API no inicia

Verifica que ya entrenaste el modelo y que existen:

```text
models/best_model.keras
models/label_encoder.pkl
```

### Streamlit no conecta con la API

Verifica que FastAPI este corriendo en:

```text
http://127.0.0.1:8000
```

### El entrenamiento tarda mucho

Es normal. TensorFlow procesa muchos audios y ademas se aplican aumentos de datos. Puedes probar con menos epocas:

```bash
python training/train.py --dataset_path dataset --epochs 10
```

Si quieres una ejecucion mas corta mientras pruebas cambios:

```bash
python training/train.py --epochs 10 --cv_epochs 5 --skip_cross_validation
```

Los parametros principales estan en:

```text
training/config.py
```

Para uso normal no necesitas pasarlos por consola.
