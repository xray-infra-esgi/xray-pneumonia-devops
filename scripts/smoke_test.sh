#!/usr/bin/env bash
# End-to-end smoke test: run the real streaming pipeline (consumer in infer
# mode) against committed fixtures and assert that predictions come out.
# Usage: bash scripts/smoke_test.sh <image-tag>     e.g. ... xray:ci
set -euo pipefail

IMAGE="${1:?usage: smoke_test.sh <image-tag>}"
FIXTURES_DIR="$(cd "$(dirname "$0")/../fixtures" && pwd)"

# Isolated workspace: landing zone + output, filled from committed fixtures.
WORKDIR="$(mktemp -d)"
trap 'sudo rm -rf "$WORKDIR" 2>/dev/null || rm -rf "$WORKDIR" 2>/dev/null || true' EXIT
mkdir -p "$WORKDIR/data/incoming-infer" "$WORKDIR/output"
cp "$FIXTURES_DIR"/images/*.jpeg "$WORKDIR/data/incoming-infer/"
chmod -R a+rwX "$WORKDIR"
IMAGE_COUNT=$(ls "$WORKDIR/data/incoming-infer" | wc -l)
echo "==> $IMAGE_COUNT fixture image(s) staged in the landing zone"

# Run the real pipeline: Spark Structured Streaming + TensorFlow inference,
# exactly as in production, bounded to 90 seconds (Spark startup on the
# 2-vCPU GitHub runner takes ~20-30 s).
echo "==> Running consumer (infer mode) from image $IMAGE"
docker run --rm \
  --user 1000:1000 \
  -e HADOOP_USER_NAME=xray \
  -e JAVA_TOOL_OPTIONS="-Xmx3g" \
  -v "$WORKDIR/data:/app/data" \
  -v "$WORKDIR/output:/app/output" \
  -v "$FIXTURES_DIR/model:/app/ml/artifacts/models/saved_model:ro" \
  "$IMAGE" \
  --service consume --mode infer --duration 90 --trigger 2 --threads 2 --predict-batch-size 8

# Assertion: at least one non-empty parquet part file was produced.
PARQUET_COUNT=$(find "$WORKDIR/output/predictions-stream.parquet" \
  -name "*.parquet" -size +0c 2>/dev/null | wc -l)

if [ "$PARQUET_COUNT" -ge 1 ]; then
  echo "==> SMOKE TEST PASSED: $PARQUET_COUNT parquet file(s) with predictions"
else
  echo "==> SMOKE TEST FAILED: no predictions parquet produced" >&2
  echo "--- output directory content ---" >&2
  find "$WORKDIR/output" -maxdepth 3 >&2 || true
  exit 1
fi
