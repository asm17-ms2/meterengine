module "service" {
  source                    = "../modules/service"
  name                      = "meterengine-server"
  ami_id                    = "ami-0f4109eb491b0476f"
  instance_type             = var.instance_type
  subnet_id                 = "subnet-0348a18c1d399c193"
  ec2_security_group_ids    = ["sg-0c2691d037bcdd8b1"]
  iam_instance_profile_name = "meterengine-ec2-role"
  root_block_device = {
    volume_size = 20, volume_type = "gp3", iops = 3000, throughput = 125,
    encrypted   = false, delete_on_termination = true
  }
  db_identifier              = "meterengine-db"
  db_instance_class          = var.db_instance_class
  db_subnet_group_name       = "meterengine-rds-subnet-private"
  rds_security_group_ids     = ["sg-068a54ab4cfa91730"]
  rds_initial_database_name  = "meterengine"
  database_username          = "meterengine"
  database_port              = 5432
  database_apply_immediately = true
  db_password_wo             = null
  db_password_version        = null
  database_kms_key_id        = "arn:aws:kms:ap-northeast-2:928618307647:key/c90dd8e8-d6a0-4676-9aa2-d2ad709856f9"
  database_iops              = 3000
  database_throughput        = 125
  backup_retention_period    = 7
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "meterengine-production-final"
  user_data                  = null
}
