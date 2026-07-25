terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state: shared S3 bucket with native lockfile.
  # Bucket created ONCE by hand before the first `terraform init`
  # Workspaces (dev/prod) are isolated automatically under env:/<workspace>/.
  backend "s3" {
    bucket       = "tfstate-xray-345307374945"
    key          = "xray/terraform.tfstate"
    region       = "eu-west-3"
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = terraform.workspace
      ManagedBy   = "terraform"
    }
  }
}
