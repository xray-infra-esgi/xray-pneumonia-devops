#!/usr/bin/env bash
# Open the X-Ray dashboard through an authenticated SSM tunnel.

# Requirements: AWS CLI configured + the session-manager-plugin installed.
# Usage: ./scripts/dashboard-tunnel.sh [dev|prod]   (default: prod)
set -euo pipefail

ENV="${1:-prod}"
REGION="eu-west-3"
LOCAL_PORT="8501"
REMOTE_PORT="8501"

echo "==> Looking up the running '${ENV}' instance..."
INSTANCE_ID=$(aws ec2 describe-instances \
  --region "$REGION" \
  --filters \
    "Name=tag:Project,Values=xray" \
    "Name=tag:Environment,Values=${ENV}" \
    "Name=instance-state-name,Values=running" \
  --query "Reservations[0].Instances[0].InstanceId" \
  --output text)

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "None" ]; then
  echo "No running instance found for environment '${ENV}'." >&2
  echo "Is the infrastructure applied for that workspace?" >&2
  exit 1
fi

echo "==> Instance: $INSTANCE_ID"
echo "==> Opening SSM tunnel — dashboard will be at http://localhost:${LOCAL_PORT}"
echo "==> Press Ctrl+C to close the tunnel."

aws ssm start-session \
  --region "$REGION" \
  --target "$INSTANCE_ID" \
  --document-name AWS-StartPortForwardingSession \
  --parameters "{\"portNumber\":[\"${REMOTE_PORT}\"],\"localPortNumber\":[\"${LOCAL_PORT}\"]}"
