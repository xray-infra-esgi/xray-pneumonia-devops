#!/usr/bin/env bash
# Cloud-init provisioning, MINIMAL version (base working package).
# Scope: Docker runtime ready + provisioning inputs 
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io

mkdir -p /opt/xray

cat > /opt/xray/provision-inputs.env <<EOF
DATA_BUCKET=${data_bucket}
APP_IMAGE=${app_image}
DASH_IMAGE=${dashboard_image}
CONSUMER_JAVA_OPTS=${consumer_java_opts}
EOF

echo "Base provisioning done"
