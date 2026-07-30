data "aws_caller_identity" "current" {}

data "aws_vpc" "default" {
  default = true
}

# Always resolve the latest Ubuntu 24.04 LTS image published by Canonical.
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical's official AWS account

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Cold storage: dataset + trained model
#tfsec:ignore:aws-s3-enable-bucket-logging
resource "aws_s3_bucket" "data" {
  bucket = "${var.project}-${terraform.workspace}-data-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "${var.project}-${terraform.workspace}-s3-bucket"
  }
}

resource "aws_s3_bucket_public_access_block" "data" {
  bucket = aws_s3_bucket.data.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "data" {
  bucket = aws_s3_bucket.data.id

  versioning_configuration {
    status = "Enabled"
  }
}

#tfsec:ignore:aws-s3-encryption-customer-key 
resource "aws_s3_bucket_server_side_encryption_configuration" "data" {
  bucket = aws_s3_bucket.data.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
  }
}

# The trained model is provisioned as code: Terraform seeds the bucket from
# the repo, so a new environment needs no manual upload.
resource "aws_s3_object" "model" {
  for_each = fileset("${path.module}/../app/ml/artifacts/models/saved_model", "**")

  bucket      = aws_s3_bucket.data.id
  key         = "ml-artifacts/models/saved_model/${each.value}"
  source      = "${path.module}/../app/ml/artifacts/models/saved_model/${each.value}"
  source_hash = filemd5("${path.module}/../app/ml/artifacts/models/saved_model/${each.value}")
}

# VM identity
resource "aws_iam_role" "vm" {
  name = "${var.project}-${terraform.workspace}-vm"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Permissions: read-only on the data bucket.
resource "aws_iam_role_policy" "s3_read" {
  name = "s3-data-read"
  role = aws_iam_role.vm.id

  #tfsec:ignore:aws-iam-no-policy-wildcards
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:ListBucket", "s3:GetObject"]
      Resource = [aws_s3_bucket.data.arn, "${aws_s3_bucket.data.arn}/*"]
    }]
  })
}

# SSM Session Manager: shell access without SSH keys or open port 22.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.vm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "vm" {
  name = "${var.project}-${terraform.workspace}-vm"
  role = aws_iam_role.vm.name
}

# --- Network exposure --------------------------------------------------------
# Shell access and the dashboard both go through authenticated SSM tunnels
# Egress only — for image pulls, apt, S3, and the SSM agent's outbound connection.
resource "aws_security_group" "xray" {
  name        = "${var.project}-${terraform.workspace}-sg"
  description = "Egress-only: the VM is reached exclusively via SSM"
  vpc_id      = data.aws_vpc.default.id

  egress {
    description = "All outbound (image pulls, apt, S3, SSM)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"] #tfsec:ignore:aws-ec2-no-public-egress-sgr
  }
}

# --- The VM ------------------------------------------------------------------
resource "aws_instance" "xray" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  vpc_security_group_ids = [aws_security_group.xray.id]
  iam_instance_profile   = aws_iam_instance_profile.vm.name

  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    data_bucket        = aws_s3_bucket.data.bucket
    app_image          = var.app_image
    dashboard_image    = var.dashboard_image
    consumer_java_opts = var.consumer_java_opts
  })

  # Immutable deployment: any provisioning change recreates the VM from scratch.
  user_data_replace_on_change = true

  tags = {
    Name = "${var.project}-${terraform.workspace}"
  }

  depends_on = [aws_s3_object.model]

}