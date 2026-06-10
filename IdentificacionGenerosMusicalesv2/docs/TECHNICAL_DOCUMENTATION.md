# Documentacion Tecnica

Este documento explica el proyecto de forma sencilla, pensando en estudiantes que deben entender y exponer el codigo.

## 1. Objetivo

El objetivo es clasificar archivos de audio WAV en uno de los 10 generos del dataset GTZAN.

El sistema completo tiene tres partes:

```text
Entrenamiento -> API REST -> Interfaz Streamlit
```

## 1.1 Mapa de archivos

```text
training/config.py          parametros principales
training/preprocessing.py   audio -> segmentos -> features
training/augmentations.py   aumentos de datos
training/models.py          arquitecturas Keras
training/evaluation.py      metricas y graficas
training/train.py           flujo completo de entrenamiento
training/utils.py           funciones auxiliares

backend/main.py             API FastAPI
backend/predictor.py        carga modelo y predice

frontend/streamlit_app.py   interfaz de usuario
```

## 1.2 Flujo de datos

Durante entrenamiento:

```text
WAV local
  -> librosa.load
  -> segmentacion
  -> Mel Spectrogram o MFCC
  -> normalizacion
  -> CNN
  -> BiLSTM
  -> Softmax
  -> probabilidades por genero
```

Durante prediccion desde la interfaz:

```text
Streamlit
  -> FastAPI
  -> predictor.py
  -> preprocessing.py
  -> best_model.keras
  -> JSON de respuesta
```

## 2. Preprocesamiento

Archivo principal:

```text
training/preprocessing.py
```

El modelo no recibe el audio crudo directamente. Primero convertimos el audio en un Mel Spectrogram.

### Pasos principales

1. Cargar el audio con `librosa.load`.
2. Convertirlo a mono.
3. Usar una frecuencia de muestreo fija de 22050 Hz.
4. Dividir el audio en segmentos de pocos segundos.
5. Generar Mel Spectrogram o MFCCs.
6. Normalizar restando media y dividiendo por desviacion estandar.
7. Aplicar padding o truncado para que todos tengan el mismo tamano.
8. Agregar un canal final para que Keras lo trate como imagen.

Forma final con Mel Spectrograms y segmentos de 4 segundos:

```text
(128, tiempo, 1)
```

Esto significa:

- 128 bandas Mel.
- pasos temporales segun la duracion del segmento.
- 1 canal.

La configuracion usada se guarda en:

```text
models/preprocessing_config.json
```

La API lee ese archivo para aplicar el mismo preprocesamiento durante la prediccion.

## 2.1 Segmentacion

Antes, cada audio era una sola muestra. Ahora cada cancion se divide en varios segmentos.

Esto ayuda porque:

- aumenta la cantidad efectiva de ejemplos
- permite aprender patrones locales
- reduce la dependencia de una sola parte de la cancion

La division train/validation/test se hace antes de segmentar. Asi evitamos que segmentos de una misma cancion queden en entrenamiento y prueba al mismo tiempo.

## 3. Division del dataset

Archivo:

```text
training/train.py
```

Configuracion:

```text
training/config.py
```

Los valores recomendados estan centralizados en `config.py` para no depender de comandos largos por consola.

La division es:

- 70% entrenamiento.
- 15% validacion.
- 15% prueba.

Se usa `stratify` para mantener una proporcion similar de generos en cada grupo.

## 4. Aumento de datos

El aumento se aplica solo a entrenamiento.

Por cada audio de entrenamiento se generan:

- segmentos originales
- copias con ruido
- copias con desplazamiento temporal
- copias con pitch shifting
- copias con time stretching
- copias con volumen aleatorio
- SpecAugment simple sobre espectrogramas

Esto ayuda a que el modelo no memorice exactamente los audios originales.

No se aumenta validacion ni prueba, porque esos conjuntos deben medir el rendimiento con datos reales.

## 5. Modelos

Archivo:

```text
training/models.py
```

### CNN simple

La CNN ve el Mel Spectrogram como una imagen. Aprende patrones de frecuencia y tiempo usando capas convolucionales.

Componentes:

- `Conv2D`
- `BatchNormalization`
- `MaxPooling2D`
- `Dropout`
- `GlobalAveragePooling2D`
- `Dense`
- `Softmax`

### CNN + BiLSTM

Este modelo combina:

- CNN para extraer caracteristicas del espectrograma.
- BiLSTM para analizar la evolucion temporal de esas caracteristicas.

La idea es que la musica tiene cambios a lo largo del tiempo, no solo patrones estaticos.

BatchNormalization ayuda a estabilizar el entrenamiento. Dropout ayuda a reducir sobreajuste.

## 6. Entrenamiento

Archivo:

```text
training/train.py
```

El script entrena los dos modelos y compara su `validation accuracy`.

Callbacks usados:

- `EarlyStopping`: detiene entrenamiento si no mejora.
- `ReduceLROnPlateau`: reduce el learning rate si el modelo se estanca.
- `ModelCheckpoint`: guarda automaticamente la mejor version de cada modelo.

Archivos generados:

```text
models/best_model.keras
models/label_encoder.pkl
outputs/metrics/metrics.json
outputs/metrics/cross_validation.json
outputs/plots/simple_cnn_history.png
outputs/plots/cnn_bilstm_history.png
outputs/plots/simple_cnn_confusion_matrix.png
outputs/plots/cnn_bilstm_confusion_matrix.png
```

## 7. Evaluacion

Archivo:

```text
training/evaluation.py
```

Metricas calculadas:

- accuracy
- precision
- recall
- F1-score
- matriz de confusion

La matriz de confusion permite ver que generos se confunden mas entre si.

En los resultados anteriores se observo que `rock` y `metal` se confundian con facilidad. Esto es normal porque ambos pueden compartir guitarras distorsionadas, bateria fuerte y energia similar. La segmentacion busca que el modelo vea mas fragmentos internos de cada cancion y no dependa solo de un resumen global.

## 7.1 Validacion Cruzada

El entrenamiento incluye validacion cruzada estratificada de 2 folds para el modelo CNN + BiLSTM.

Se reporta:

- accuracy de cada fold
- accuracy media
- desviacion estandar

Esto ayuda a saber si el rendimiento es estable o si depende demasiado de una unica particion del dataset.

## 8. API REST

Archivos:

```text
backend/main.py
backend/predictor.py
```

La API usa FastAPI.

Endpoint:

```text
POST /predict
```

La API:

1. Carga el modelo una sola vez al iniciar.
2. Recibe un archivo WAV.
3. Guarda temporalmente el archivo.
4. Aplica el mismo preprocesamiento usado en entrenamiento.
5. Divide el audio en segmentos.
6. Predice cada segmento.
7. Promedia probabilidades.
8. Devuelve genero, confianza y probabilidades.

Respuesta:

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

## 9. Frontend

Archivo:

```text
frontend/streamlit_app.py
```

Streamlit permite:

- subir un WAV
- reproducirlo
- enviarlo a la API
- mostrar genero predicho
- mostrar probabilidades
- dibujar grafico de barras

El frontend no carga TensorFlow ni el modelo. Solo consume la API.

## 10. Flujo completo

```text
Usuario sube WAV
        |
        v
Streamlit envia archivo a FastAPI
        |
        v
FastAPI llama predictor.py
        |
        v
preprocessing.py genera Mel Spectrogram
        |
        v
TensorFlow predice probabilidades
        |
        v
Streamlit muestra resultados
```

## 11. Por que el codigo es simple

El proyecto evita clases y arquitecturas empresariales porque el objetivo es educativo.

Se usan funciones pequeñas y archivos separados por responsabilidad:

- `preprocessing.py`: preparar audios.
- `augmentations.py`: aplicar aumentos simples.
- `models.py`: crear modelos.
- `evaluation.py`: medir resultados.
- `utils.py`: utilidades pequenas.
- `train.py`: flujo completo de entrenamiento.
- `predictor.py`: logica de prediccion.
- `main.py`: API.
- `streamlit_app.py`: interfaz.

## 12. Proceso de entrenamiento paso a paso

### Paso 1: fijar semilla

Se usa una semilla para reducir variaciones entre ejecuciones:

```python
set_random_seed(RANDOM_SEED)
```

Esto no garantiza resultados identicos al 100%, porque TensorFlow puede usar operaciones numericas con pequenas diferencias, pero mejora la reproducibilidad.

### Paso 2: leer rutas y etiquetas

`get_dataset_files` recorre las carpetas del dataset y obtiene:

```python
audio_files = ["dataset/blues/blues.00000.wav", ...]
labels = ["blues", ...]
```

Tambien ignora `jazz.00054.wav`.

### Paso 3: dividir dataset

Se divide por archivo:

```text
70% train
15% validation
15% test
```

La division se hace antes de segmentar para evitar fuga de datos.

Fuga de datos seria, por ejemplo, entrenar con el segundo 0-4 de una cancion y probar con el segundo 4-8 de esa misma cancion. Eso inflaria artificialmente el resultado.

### Paso 4: codificar labels

Keras no entrena directamente con textos como `rock` o `jazz`.

Por eso se usa `LabelEncoder`:

```text
blues -> 0
classical -> 1
...
rock -> 9
```

Luego se guarda en:

```text
models/label_encoder.pkl
```

### Paso 5: validacion cruzada

Si esta activa, se ejecuta `StratifiedKFold` con 2 particiones.

Esto sirve para responder una pregunta:

```text
El modelo funciona de manera estable o solo tuvo suerte con una particion?
```

Se guarda:

```text
outputs/metrics/cross_validation.json
```

### Paso 6: preparar entrenamiento

Para entrenamiento:

1. se carga cada audio
2. se segmenta
3. se conserva cada segmento original
4. se crea una copia aumentada
5. se convierte todo a features

Esto produce mas ejemplos que los 999 audios originales.

### Paso 7: preparar validacion

Para validacion:

1. se carga cada audio
2. se segmenta
3. se convierte a features

No se aplica augmentation.

### Paso 8: entrenar modelos

Se entrenan:

- CNN simple
- CNN + BiLSTM

Ambos modelos usan:

```python
loss="sparse_categorical_crossentropy"
metrics=["accuracy"]
```

`sparse_categorical_crossentropy` se usa porque las etiquetas son numeros enteros, no vectores one-hot.

### Paso 9: callbacks

`EarlyStopping` detiene entrenamiento si no mejora.

`ReduceLROnPlateau` baja el learning rate si el modelo se estanca.

`ModelCheckpoint` guarda la mejor version del modelo segun `val_accuracy`.

### Paso 10: evaluar en test

La evaluacion final se hace por archivo completo:

1. se divide el audio de test en segmentos
2. se predice cada segmento
3. se promedian probabilidades
4. se calcula la clase final

Esto coincide con lo que hace la API.

### Paso 11: guardar artefactos

Al final se guarda:

```text
models/best_model.keras
models/label_encoder.pkl
models/preprocessing_config.json
outputs/metrics/metrics.json
outputs/plots/*.png
```

## 13. Artefactos y para que sirven

| Archivo | Para que sirve |
| --- | --- |
| `models/best_model.keras` | modelo final para prediccion |
| `models/label_encoder.pkl` | traduce indices a generos |
| `models/preprocessing_config.json` | guarda feature type, duracion de segmento y overlap |
| `outputs/metrics/metrics.json` | metricas finales de modelos |
| `outputs/metrics/cross_validation.json` | resultados de validacion cruzada |
| `outputs/plots/*_history.png` | curvas de entrenamiento |
| `outputs/plots/*_confusion_matrix.png` | matrices de confusion |

## 14. Como explicarlo en una sustentacion

Una forma clara de explicarlo:

1. Primero convertimos el audio en una representacion visual llamada Mel Spectrogram.
2. Dividimos cada cancion en segmentos para aumentar ejemplos y aprender patrones locales.
3. Entrenamos una CNN simple y una CNN + BiLSTM.
4. La CNN detecta patrones de frecuencia y tiempo.
5. La BiLSTM analiza la evolucion temporal de esos patrones.
6. Aplicamos augmentation solo al entrenamiento para mejorar generalizacion.
7. Evaluamos con accuracy, precision, recall, F1 y matriz de confusion.
8. Guardamos el mejor modelo y lo usamos desde una API FastAPI.
9. La interfaz Streamlit consume la API, no carga el modelo directamente.
