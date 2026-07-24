#!/usr/bin/env bash
# Creates the local Python environment used for model training
# (ml/train_tf.ipynb): a plain venv built from ml/requirements.txt.

set -e
cd "$(dirname "$0")/.."

python3 -m venv .venv-ml
.venv-ml/bin/pip install --upgrade pip
.venv-ml/bin/pip install -r ml/requirements.txt

# Register the Jupyter kernel so the notebook can pick it directly.
.venv-ml/bin/python -m ipykernel install --user --name xray-ml --display-name "Python (xray-ml)"

echo ""
echo "Done. Open ml/train_tf.ipynb with the 'Python (xray-ml)' kernel,"
echo "or activate the environment with: source .venv-ml/bin/activate"
