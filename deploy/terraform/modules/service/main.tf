resource "aws_db_instance" "app" {
  identifier                            = var.db_identifier
  instance_class                        = var.db_instance_class
  apply_immediately                     = var.database_apply_immediately
  db_subnet_group_name                  = var.db_subnet_group_name
  vpc_security_group_ids                = var.rds_security_group_ids
  db_name                               = var.rds_initial_database_name
  username                              = var.database_username
  port                                  = var.database_port
  password_wo                           = var.db_password_wo
  password_wo_version                   = var.db_password_version
  engine                                = "postgres"
  engine_version                        = "18.4"
  engine_lifecycle_support              = "open-source-rds-extended-support-disabled"
  allocated_storage                     = 20
  max_allocated_storage                 = 50
  storage_type                          = "gp3"
  storage_encrypted                     = true
  kms_key_id                            = var.database_kms_key_id
  performance_insights_kms_key_id       = var.database_kms_key_id
  iops                                  = var.database_iops
  storage_throughput                    = var.database_throughput
  publicly_accessible                   = false
  backup_retention_period               = var.backup_retention_period
  backup_window                         = "18:00-18:30"
  maintenance_window                    = "mon:19:00-mon:19:30"
  deletion_protection                   = var.deletion_protection
  skip_final_snapshot                   = var.skip_final_snapshot
  final_snapshot_identifier             = var.final_snapshot_identifier
  delete_automated_backups              = false
  copy_tags_to_snapshot                 = true
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  database_insights_mode                = "standard"
  enabled_cloudwatch_logs_exports       = ["postgresql"]
  ca_cert_identifier                    = "rds-ca-rsa2048-g1"
  tags                                  = { Name = var.db_identifier }
  lifecycle {
    prevent_destroy = true
  }
}
