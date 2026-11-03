resource "aws_instance" "app" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = var.ec2_security_group_ids
  iam_instance_profile   = var.iam_instance_profile_name
  user_data              = var.user_data
  dynamic "root_block_device" {
    for_each = var.root_block_device == null ? [] : [var.root_block_device]
    content {
      volume_size           = root_block_device.value.volume_size
      volume_type           = root_block_device.value.volume_type
      iops                  = root_block_device.value.iops
      throughput            = root_block_device.value.throughput
      encrypted             = root_block_device.value.encrypted
      delete_on_termination = root_block_device.value.delete_on_termination
    }
  }
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    http_protocol_ipv6          = "disabled"
    instance_metadata_tags      = "disabled"
  }
  tags = { Name = var.name }
  lifecycle {
    prevent_destroy = true
  }
}

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
