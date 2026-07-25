output "instance_id" {
  description = "EC2 instance id (target for SSM sessions)"
  value       = aws_instance.xray.id
}

output "dashboard_access" {
  description = "The dashboard is private. Open the SSM tunnel below, then browse http://localhost:8501"
  value       = "./scripts/dashboard-tunnel.sh ${terraform.workspace}"
}

output "connect_command" {
  description = "Open a shell on the VM without SSH"
  value       = "aws ssm start-session --target ${aws_instance.xray.id} --region ${var.aws_region}"
}

output "data_bucket" {
  description = "Cold-storage bucket for dataset and model"
  value       = aws_s3_bucket.data.bucket
}
