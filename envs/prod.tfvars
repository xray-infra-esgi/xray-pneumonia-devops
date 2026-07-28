# Production environment: demo-day sizing.
# t3.large = 2 vCPU / 8 GB — full consumer heap (4g) + dashboard + OS headroom.
instance_type      = "t3.large"
consumer_java_opts = "-Xmx4g"

# Same knobs as dev (see dev.tfvars for the rollback-by-SHA pattern).
