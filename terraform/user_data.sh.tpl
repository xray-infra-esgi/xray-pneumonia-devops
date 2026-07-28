#!/usr/bin/env bash
# Cloud-init provisioning, FULL version (provisioning work package).
# Replaces the minimal base version: the VM goes from "nothing" to "pipeline
# running" with zero manual step — that is the whole point of IaC.
# Runs once at first boot; logs land in /var/log/cloud-init-output.log.
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io docker-compose-v2 unzip

# AWS CLI v2 — installed from the official bundle because Ubuntu 24.04 has no
# apt package for it. Needed below for the S3 sync.
curl -sSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install

# Directory layout mirrors the app's expectations (see app/docker-compose.yml):
# data/ (landing zones), output/ (parquet sinks), ml-artifacts/ (model).
mkdir -p /opt/xray/data/incoming-infer /opt/xray/output /opt/xray/ml-artifacts/models

# Cold-storage sync. `|| true`: an empty bucket must not break provisioning
# (first boot may happen before dataset/model upload).
aws s3 sync "s3://${data_bucket}/ml-artifacts" /opt/xray/ml-artifacts || true
aws s3 sync "s3://${data_bucket}/dataset"      /opt/xray/data/chest_xray || true

# Containers run as uid 1000 (non-root): volumes must belong to that uid.
chown -R 1000:1000 /opt/xray

# Production compose: same services as the local dev compose, but images are
# PULLED from GHCR (built and tested by CI) — the server never builds.
cat > /opt/xray/docker-compose.yml <<EOF
services:
  consumer-infer:
    image: ${app_image}
    command: ["--service", "consume", "--mode", "infer"]
    restart: unless-stopped
    user: "1000:1000"
    environment:
      JAVA_TOOL_OPTIONS: "${consumer_java_opts}"
      HADOOP_USER_NAME: xray
    volumes:
      - /opt/xray/data:/app/data
      - /opt/xray/output:/app/output
      - /opt/xray/ml-artifacts:/app/ml/artifacts

  dashboard:
    image: ${dashboard_image}
    restart: unless-stopped
    ports:
      - "8501:8501"
    user: "1000:1000"
    environment:
      HOME: /tmp
    volumes:
      - /opt/xray/data:/app/data
      - /opt/xray/output:/app/output
    depends_on:
      - consumer-infer
EOF

docker compose -f /opt/xray/docker-compose.yml up -d

echo "Provisioning done: pipeline containers started"
