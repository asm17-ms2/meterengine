data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

module "service" {
  source                    = "../modules/service"
  name                      = "meterengine-staging"
  ami_id                    = "ami-0f4109eb491b0476f"
  instance_type             = "m7g.large"
  subnet_id                 = "subnet-0348a18c1d399c193"
  ec2_security_group_ids    = [aws_security_group.app.id]
  iam_instance_profile_name = aws_iam_instance_profile.runtime.name
  root_block_device = {
    volume_size           = 30
    volume_type           = "gp3"
    iops                  = 3000
    throughput            = 125
    encrypted             = true
    delete_on_termination = true
  }
  db_identifier             = "meterengine-staging-db"
  db_instance_class         = "db.m7g.large"
  db_subnet_group_name      = "meterengine-rds-subnet-private"
  rds_security_group_ids    = [aws_security_group.database.id]
  rds_initial_database_name = "meterengine_app"
  database_username         = "meterengine_owner"
  database_port             = 5432
  db_password_wo            = var.db_password_wo
  db_password_version       = var.db_password_version
  backup_retention_period   = 7
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "meterengine-staging-final"
  user_data                 = file("${path.module}/bootstrap.sh")
}
