# Development environment.
# Purpose: validate infrastructure changes cheaply, disposable at will.
# t3.medium = 2 vCPU / 4 GB — enough to boot the pipeline, not to benchmark it.
instance_type      = "t3.medium"
consumer_java_opts = "-Xmx2g"

# Image tags. `latest` follows the newest CI-tested build; pin a commit SHA
# here to freeze (or roll back) the deployed version:
# app_image       = "ghcr.io/xray-infra-esgi/xray:<commit-sha>"
# dashboard_image = "ghcr.io/xray-infra-esgi/xray-dashboard:<commit-sha>"
