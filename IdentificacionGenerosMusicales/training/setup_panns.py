"""
Descarga y conversión de pesos de PANNs CNN10 (PyTorch -> Keras NPZ).
"""

import sys
import urllib.request
from pathlib import Path
import numpy as np

# Configurar rutas
TRAINING_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = TRAINING_DIR.parent
MODELS_DIR = PROJECT_ROOT / "models"
PTH_PATH = MODELS_DIR / "Cnn10_mAP=0.380.pth"
NPZ_PATH = MODELS_DIR / "panns_cnn10_weights.npz"

ZENODO_URL = "https://zenodo.org/records/3987831/files/Cnn10_mAP%3D0.380.pth?download=1"


def download_weights():
    """Descarga el archivo .pth desde Zenodo si no existe."""
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    if PTH_PATH.exists():
        print(f"El archivo PyTorch {PTH_PATH} ya existe.")
        return

    print(f"Descargando pesos de PANNs CNN10 desde Zenodo...")
    print(f"URL: {ZENODO_URL}")
    print("Esto puede tardar un momento (~94 MB)...")
    
    try:
        urllib.request.urlretrieve(ZENODO_URL, PTH_PATH)
        print(f"Descarga completada y guardada en {PTH_PATH}")
    except Exception as e:
        print(f"Error al descargar los pesos: {e}")
        sys.exit(1)


def convert_weights():
    """Carga los pesos de PyTorch y los guarda en un archivo numpy NPZ con mapeo Keras."""
    print("Iniciando conversión de pesos...")
    
    try:
        import torch
    except ImportError:
        print("\nError: Se requiere PyTorch para realizar la conversión de pesos.")
        print("Instálalo en tu entorno de WSL ejecutando:")
        print("  pip install torch --index-url https://download.pytorch.org/whl/cpu")
        sys.exit(1)

    if not PTH_PATH.exists():
        print(f"Error: {PTH_PATH} no existe. No se puede convertir.")
        sys.exit(1)

    print(f"Cargando pesos de PyTorch desde {PTH_PATH}...")
    checkpoint = torch.load(PTH_PATH, map_location="cpu")
    
    # En los checkpoints de PANNs, los pesos están en la clave 'model'
    state_dict = checkpoint["model"] if "model" in checkpoint else checkpoint

    converted_dict = {}
    mapped_count = 0

    for key, value in state_dict.items():
        # Convertir a array de numpy
        weight_np = value.numpy()
        
        # Mapeo de nombres de capas:
        # PyTorch: conv_block1.conv1.weight -> Keras: conv_block1_conv1_weight
        # PyTorch: conv_block1.bn1.weight/bias/running_mean/running_var -> Keras: conv_block1_bn1_gamma/beta/mean/var
        parts = key.split(".")
        
        if parts[0].startswith("conv_block") and len(parts) >= 3:
            block = parts[0]
            sublayer = parts[1]
            param_type = parts[2]
            
            keras_layer_prefix = f"{block}_{sublayer}"
            
            if sublayer.startswith("conv"):
                if param_type == "weight":
                    keras_key = f"{keras_layer_prefix}_weight"
                    converted_dict[keras_key] = weight_np
                    mapped_count += 1
            elif sublayer.startswith("bn"):
                if param_type == "weight":
                    keras_key = f"{keras_layer_prefix}_gamma"
                elif param_type == "bias":
                    keras_key = f"{keras_layer_prefix}_beta"
                elif param_type == "running_mean":
                    keras_key = f"{keras_layer_prefix}_mean"
                elif param_type == "running_var":
                    keras_key = f"{keras_layer_prefix}_var"
                else:
                    continue
                
                converted_dict[keras_key] = weight_np
                mapped_count += 1

        elif parts[0] == "fc1" and len(parts) >= 2:
            param_type = parts[1]
            if param_type == "weight":
                converted_dict["fc1_weight"] = weight_np
                mapped_count += 1
            elif param_type == "bias":
                converted_dict["fc1_bias"] = weight_np
                mapped_count += 1

    # Guardar en formato .npz
    print(f"Guardando {mapped_count} parámetros convertidos en {NPZ_PATH}...")
    np.savez(NPZ_PATH, **converted_dict)
    print("Conversión completada exitosamente.")


def main():
    download_weights()
    convert_weights()


if __name__ == "__main__":
    main()
