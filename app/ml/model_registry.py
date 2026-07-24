import json
from pathlib import Path


PROJECT_ROOT = Path(__file__).parent.parent.resolve()
MODEL_DIR = PROJECT_ROOT / "ml" / "artifacts" / "models"
DEFAULT_SAVED_MODEL_PATH = MODEL_DIR / "saved_model"
REGISTRY_PATH = MODEL_DIR / "current_model.json"


def find_default_model_path():
    models = sorted(MODEL_DIR.glob("*.keras"))
    if not models:
        return MODEL_DIR / "missing-model.keras"

    final_models = [path for path in models if path.name.endswith("_final.keras")]
    if final_models:
        return final_models[-1]
    return models[-1]


def read_registry():
    if not REGISTRY_PATH.exists():
        return {}

    try:
        return json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}


def resolve_current_keras_model_path():
    registry = read_registry()
    keras_model = registry.get("keras_model")
    if keras_model:
        keras_path = Path(keras_model)
        if not keras_path.is_absolute():
            keras_path = PROJECT_ROOT / keras_path
        if keras_path.exists():
            return keras_path.resolve()

    return find_default_model_path().resolve()


def registry_display_name():
    return resolve_current_keras_model_path().name
