# Recorrido Completo del Proyecto

Este documento explica el proyecto de extremo a extremo. La idea es que puedas entender que hace cada parte, como se conectan los archivos y que ocurre desde que tienes audios WAV hasta que la interfaz muestra una prediccion.

## 1. Idea General

El proyecto clasifica generos musicales usando aprendizaje profundo.

Entrada:

```text
archivo WAV
```

Salida:

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

El sistema completo tiene tres partes:

```text
training  -> entrena y guarda el modelo
backend   -> carga el modelo y expone una API REST
frontend  -> permite subir audio desde una interfaz web
```

## 2. Flujo Principal

```text
1. El usuario coloca GTZAN en dataset/
2. training/train.py entrena los modelos
3. Se guarda models/best_model.keras
4. Se guarda models/label_encoder.pkl
5. Se guarda models/preprocessing_config.json
6. FastAPI carga esos archivos
7. Streamlit envia un WAV a FastAPI
8. FastAPI procesa el audio igual que en entrenamiento
9. El modelo predice probabilidades
10. Streamlit muestra el resultado
```

## 3. Por Que No Se Usa Audio Crudo Directamente

Un archivo WAV es una senal en el tiempo. Para una persona junior/intermedia puede pensarse como una lista muy larga de numeros:

```text
[0.01, 0.02, -0.01, 0.00, ...]
```

Aunque una red neuronal podria trabajar con senales crudas, en este proyecto usamos una representacion mas comun y mas facil de explicar:

```text
Mel Spectrogram
```

Un Mel Spectrogram muestra:

- eje vertical: frecuencias
- eje horizontal: tiempo
- intensidad: energia del sonido

Esto permite tratar el audio como una especie de imagen, y por eso una CNN puede aprender patrones.

## 4. Que Aprende La CNN

La CNN analiza el espectrograma y busca patrones locales.

Ejemplos intuitivos:

- bateria fuerte puede verse como energia repetida en ciertas zonas
- voz o instrumentos pueden ocupar bandas de frecuencia particulares
- metal y rock suelen tener energia alta y texturas densas
- classical puede tener patrones mas limpios o menos percusivos

La CNN no entiende musica como una persona. Aprende relaciones numericas entre espectrogramas y etiquetas.

## 5. Por Que Usar BiLSTM

La musica no es una imagen estatica. Cambia con el tiempo.

La CNN extrae caracteristicas del espectrograma, y luego la BiLSTM analiza como esas caracteristicas evolucionan.

Una LSTM es util cuando importa el orden temporal. `Bidirectional` significa que analiza la secuencia en dos direcciones:

- de inicio a fin
- de fin a inicio

Esto puede ayudar a capturar patrones temporales mas completos.

## 6. Segmentacion de Audio

Antes, cada audio era una sola muestra.

Ahora el audio se divide en segmentos pequenos, por defecto:

```text
4 segundos por segmento
50% de overlap
```

Si una cancion dura 30 segundos, el sistema genera varios segmentos. Esto ayuda porque:

- aumenta la cantidad de ejemplos
- el modelo ve distintas partes de la cancion
- se reduce la dependencia de una intro o un fragmento especifico
- se aprende mejor la variedad interna del genero

Importante: la division train/validation/test se hace antes de segmentar. Esto evita que segmentos de la misma cancion queden repartidos entre entrenamiento y prueba.

## 7. Data Augmentation

El aumento de datos se aplica solo al entrenamiento.

Se usan tecnicas simples:

- ruido suave
- desplazamiento temporal
- cambio de pitch
- cambio de velocidad
- cambio de volumen
- SpecAugment

Esto obliga al modelo a generalizar. En vez de memorizar exactamente un audio, aprende patrones que sobreviven a pequenas variaciones.

## 8. Normalizacion

Cada espectrograma o matriz de features se normaliza:

```text
features_normalizadas = (features - media) / desviacion_estandar
```

Esto ayuda a que los valores entren al modelo en una escala mas estable.

Si la desviacion estandar es casi cero, el codigo evita dividir por un numero demasiado pequeno.

## 9. Entrenamiento

El entrenamiento principal ocurre en:

```text
training/train.py
```

El archivo hace:

1. lee rutas de audios
2. divide train/validation/test
3. codifica labels
4. ejecuta validacion cruzada si esta activa
5. prepara segmentos de entrenamiento con augmentation
6. prepara segmentos de validacion sin augmentation
7. entrena CNN simple
8. entrena CNN + BiLSTM
9. evalua en test por audio completo
10. guarda el mejor modelo

## 10. Archivos Generados

Despues de entrenar:

```text
models/best_model.keras
```

Modelo Keras final seleccionado.

```text
models/label_encoder.pkl
```

Objeto que traduce indices numericos a nombres de genero.

```text
models/preprocessing_config.json
```

Configuracion de preprocesamiento usada durante entrenamiento. La API la lee para procesar nuevos audios igual que el entrenamiento.

```text
outputs/metrics/metrics.json
```

Metricas de los modelos.

```text
outputs/metrics/cross_validation.json
```

Resultados de validacion cruzada.

```text
outputs/plots/
```

Graficas de entrenamiento y matrices de confusion.

## 11. API REST

La API esta en:

```text
backend/main.py
backend/predictor.py
```

Cuando inicia:

1. carga el modelo
2. carga el label encoder
3. carga la configuracion de preprocesamiento

Cuando recibe un WAV:

1. valida que sea `.wav`
2. guarda temporalmente el archivo
3. genera segmentos
4. convierte segmentos a features
5. predice cada segmento
6. promedia las probabilidades
7. responde JSON

## 12. Frontend Streamlit

El frontend esta en:

```text
frontend/streamlit_app.py
```

Hace algo muy simple:

1. permite subir un WAV
2. reproduce el audio
3. envia el archivo a FastAPI
4. recibe JSON
5. muestra genero, confianza y grafico de barras

Streamlit no carga el modelo. Esa responsabilidad es de FastAPI.

## 13. Como Estudiar El Proyecto

Orden recomendado:

1. Lee `training/config.py`.
2. Lee `training/preprocessing.py`.
3. Lee `training/models.py`.
4. Lee `training/train.py`.
5. Lee `backend/predictor.py`.
6. Lee `backend/main.py`.
7. Lee `frontend/streamlit_app.py`.

Ese orden sigue el camino real de los datos.

