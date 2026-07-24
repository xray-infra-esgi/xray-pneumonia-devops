#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose stop consumer-infer
rm -rf output/predictions-stream.parquet output/rejected-stream.parquet \
       output/features-stream.parquet output/metrics output/checkpoints
find data/incoming data/incoming-infer -type f ! -name .gitkeep -delete
docker compose up -d
echo "Reset terminé — stack relancée sur un état vierge."
