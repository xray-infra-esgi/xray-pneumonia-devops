import argparse
import json
from pathlib import Path

from model_registry import DEFAULT_SAVED_MODEL_PATH, PROJECT_ROOT, REGISTRY_PATH


def parse_args():
    parser = argparse.ArgumentParser(description="Register the Keras model paired with the current SavedModel.")
    parser.add_argument(
        "--keras-model",
        required=True,
        help="Path to the .keras model that matches the exported SavedModel.",
    )
    parser.add_argument(
        "--saved-model",
        default=str(DEFAULT_SAVED_MODEL_PATH),
        help="Path to the SavedModel directory used by Spark inference.",
    )
    return parser.parse_args()


def to_project_relative(path: Path):
    try:
        return path.resolve().relative_to(PROJECT_ROOT).as_posix()
    except ValueError:
        return str(path.resolve())


def main():
    args = parse_args()
    keras_model_path = Path(args.keras_model).resolve()
    saved_model_path = Path(args.saved_model).resolve()

    if not keras_model_path.exists():
        raise FileNotFoundError(f"Keras model not found: {keras_model_path}")

    if not saved_model_path.exists():
        raise FileNotFoundError(f"SavedModel directory not found: {saved_model_path}")

    payload = {
        "keras_model": to_project_relative(keras_model_path),
        "saved_model": to_project_relative(saved_model_path),
    }

    REGISTRY_PATH.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"Model registry updated: {REGISTRY_PATH}")
    print(f"Keras model: {payload['keras_model']}")
    print(f"SavedModel: {payload['saved_model']}")


if __name__ == "__main__":
    main()
