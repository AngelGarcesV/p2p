"""Módulo para aplicar aumento de datos (Data Augmentation) en señales de audio."""

import random
import librosa
import numpy as np


def add_white_noise(audio: np.ndarray, noise_factor: float = 0.005) -> np.ndarray:
    """Añade ruido blanco aleatorio a la señal de audio."""
    noise = np.random.randn(len(audio))
    return audio + noise_factor * noise


def time_shift(audio: np.ndarray, shift_max_pct: float = 0.1) -> np.ndarray:
    """Desplaza la señal de audio de manera circular en el tiempo."""
    shift_amt = int(random.uniform(-shift_max_pct, shift_max_pct) * len(audio))
    return np.roll(audio, shift_amt)


def time_stretch(audio: np.ndarray, min_rate: float = 0.8, max_rate: float = 1.25) -> np.ndarray:
    """Modifica la velocidad del audio sin cambiar la afinación."""
    rate = random.uniform(min_rate, max_rate)
    try:
        return librosa.effects.time_stretch(y=audio, rate=rate)
    except Exception:
        # En caso de error, retorna el audio original
        return audio


def pitch_shift(audio: np.ndarray, sample_rate: int, min_steps: float = -2, max_steps: float = 2) -> np.ndarray:
    """Modifica el tono (pitch) del audio sin cambiar la duración."""
    n_steps = random.uniform(min_steps, max_steps)
    try:
        return librosa.effects.pitch_shift(y=audio, sr=sample_rate, n_steps=n_steps)
    except Exception:
        # En caso de error, retorna el audio original
        return audio


def random_gain(audio: np.ndarray, min_gain: float = 0.5, max_gain: float = 1.5) -> np.ndarray:
    """Altera de forma aleatoria el volumen (ganancia) del audio."""
    gain = random.uniform(min_gain, max_gain)
    return audio * gain


def augment_audio(audio: np.ndarray, sample_rate: int) -> np.ndarray:
    """
    Aplica una combinación aleatoria de técnicas de Data Augmentation.
    """
    augmented = audio.copy()
    
    # Añadir ruido con un 40% de probabilidad
    if random.random() < 0.4:
        augmented = add_white_noise(augmented, noise_factor=random.uniform(0.002, 0.008))
        
    # Desplazamiento circular con un 40% de probabilidad
    if random.random() < 0.4:
        augmented = time_shift(augmented, shift_max_pct=0.1)
        
    # Cambio de volumen con un 50% de probabilidad
    if random.random() < 0.5:
        augmented = random_gain(augmented, min_gain=0.7, max_gain=1.3)
        
    # Cambio de velocidad con un 30% de probabilidad
    if random.random() < 0.3:
        augmented = time_stretch(augmented, min_rate=0.9, max_rate=1.1)
        
    # Cambio de tono con un 35% de probabilidad
    if random.random() < 0.35:
        augmented = pitch_shift(augmented, sample_rate, min_steps=-1.5, max_steps=1.5)
        
    return augmented
