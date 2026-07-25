variable "project" {
  description = "Project name, used as prefix for resource names"
  type        = string
  default     = "xray"
}

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "eu-west-3"
}

variable "instance_type" {
  description = "EC2 instance type (set per environment in envs/*.tfvars)"
  type        = string
  default     = "t3.large"
}

variable "app_image" {
  description = "Consumer image pulled by the VM. Pin to a commit-SHA tag to roll back."
  type        = string
  default     = "ghcr.io/xray-infra-esgi/xray:latest"
}

variable "dashboard_image" {
  description = "Dashboard image pulled by the VM"
  type        = string
  default     = "ghcr.io/xray-infra-esgi/xray-dashboard:latest"
}

variable "consumer_java_opts" {
  description = "JVM options for the consumer container (heap sized per environment)"
  type        = string
  default     = "-Xmx4g"
}
