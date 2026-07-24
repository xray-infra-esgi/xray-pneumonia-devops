#!/usr/bin/env python3
"""Stress-test tooling for the streaming consumer.

Subcommands:
  make-dataset   build a large flat set of test images (hardlinks: instant,
                 zero extra disk space, unique names for the checkpoint)
  run            pre-fill the landing zone, drain it with a dedicated consumer
                 container for a fixed duration, and report the sustained
                 throughput (img/s); the resident consumer-infer is stopped
                 during the run and the stack is restarted afterwards

Examples:
  python3 scripts/bench.py make-dataset 5000
  python3 scripts/bench.py run --threads 8 --max-files 256 --duration 120
"""

import argparse
import csv
import os
import shutil
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = PROJECT_ROOT / "data" / "chest_xray" / "test"
STRESS_DIR = PROJECT_ROOT / "data" / "stress-source"
LANDING_DIR = PROJECT_ROOT / "data" / "incoming-infer"
METRICS_CSV = PROJECT_ROOT / "output" / "metrics" / "consume-infer.csv"

# Sink + checkpoint + metrics + landing zone: they live and die together.
RUN_STATE = [
    PROJECT_ROOT / "output" / "predictions-stream.parquet",
    PROJECT_ROOT / "output" / "rejected-stream.parquet",
    PROJECT_ROOT / "output" / "checkpoints" / "consume-infer",
    METRICS_CSV,
    LANDING_DIR,
]

WARMUP_BATCHES = 2  # excluded from the average: JVM/TensorFlow cold start


def compose(*args: str, check: bool = True) -> None:
    subprocess.run(["docker", "compose", *args], cwd=PROJECT_ROOT, check=check)


def make_dataset(count: int) -> None:
    originals = sorted(SOURCE_DIR.glob("*/*.jpeg"))
    if not originals:
        sys.exit(f"no images found under {SOURCE_DIR}")

    shutil.rmtree(STRESS_DIR, ignore_errors=True)
    STRESS_DIR.mkdir(parents=True)
    for i in range(count):
        source = originals[i % len(originals)]
        os.link(source, STRESS_DIR / f"stress_{i:06d}.jpeg")  # hardlink: no copy
    print(f"{STRESS_DIR}: {count} files (hardlinks, no extra disk space)")


def clean_run_state() -> None:
    for path in RUN_STATE:
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()


def fill_landing_zone() -> int:
    files = sorted(STRESS_DIR.glob("*.jpeg"))
    if not files:
        sys.exit("stress dataset missing: run 'bench.py make-dataset' first")
    LANDING_DIR.mkdir(parents=True)
    (LANDING_DIR / ".gitkeep").touch()
    for f in files:
        target = LANDING_DIR / f.name
        os.link(f, target)
        os.utime(target)  # hardlinks keep the source mtime: stamp the deposit time
    return len(files)


def run_bench(threads: int, max_files: int, duration: int) -> None:
    compose("stop", "consumer-infer")
    clean_run_state()
    backlog = fill_landing_zone()
    print(f"=== bench: threads={threads}  max-files={max_files}  "
          f"duration={duration}s  backlog={backlog} files ===")
    print("suivi en direct : http://localhost:8501")

    command = [
        "docker", "compose", "run", "--rm", "consumer-infer",
        "--service", "consume", "--mode", "infer",
        "--threads", str(threads),
        "--max-files", str(max_files),
        "--duration", str(duration),
    ]
    try:
        consumer = subprocess.Popen(command, cwd=PROJECT_ROOT,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in consumer.stdout:  # live progress, Spark noise filtered out
            if "micro-batch" in line or "Consumer started" in line:
                print(line, end="")
        consumer.wait()
    finally:
        compose("up", "-d", check=False)

    if not METRICS_CSV.exists():
        sys.exit("no metrics produced (no batch ran?)")

    with open(METRICS_CSV) as f:
        batches = list(csv.DictReader(f))
    steady = batches[WARMUP_BATCHES:]
    if not steady:
        sys.exit("not enough batches for a steady-state measure (increase duration)")

    images = sum(int(b["numInputRows"]) for b in steady)
    rate = sum(float(b["processedRowsPerSecond"]) for b in steady) / len(steady)
    print()
    print(f"RESULT  batches(steady)={len(steady)}  images={images}  "
          f"sustained={rate:.1f} img/s  (= {rate * 60:.0f} img/min)")

    remaining = sum(1 for _ in LANDING_DIR.glob("*.jpeg"))
    print(f"files left in landing zone: {remaining} (last processed batch awaits its cleanup)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    p_make = sub.add_parser("make-dataset", help="build the stress dataset")
    p_make.add_argument("count", type=int, nargs="?", default=5000)

    p_run = sub.add_parser("run", help="measure sustained throughput")
    p_run.add_argument("--threads", type=int, default=8)
    p_run.add_argument("--max-files", type=int, default=256)
    p_run.add_argument("--duration", type=int, default=120)

    args = parser.parse_args()
    if args.command == "make-dataset":
        make_dataset(args.count)
    else:
        run_bench(args.threads, args.max_files, args.duration)


if __name__ == "__main__":
    main()
