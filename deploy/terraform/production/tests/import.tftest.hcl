mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "928618307647" }
  }
  mock_data "aws_region" {
    defaults = { region = "ap-northeast-2" }
  }
}

run "production_preserves_identity_and_requests_resize" {
  command = plan
  override_resource {
    target = module.service.aws_instance.app
    values = { id = "i-0f47bb1f028cd29a9" }
  }
  override_resource {
    target = module.service.aws_db_instance.app
    values = {
      id      = "db-72GJNZYUHSQRXAKNQGC7ECV7UI"
      address = "meterengine-db.example.invalid"
    }
  }
  assert {
    condition     = module.service.instance_id != "" && output.deployment_target.deployment_environment == "production" && output.deployment_target.parameter_path == "/meterengine/production"
    error_message = "Production must preserve its deployment identity."
  }
}

run "import_service_configuration" {
  command = plan
  module { source = "../modules/service" }
  variables {
    name                       = "meterengine-server"
    ami_id                     = "ami-0f4109eb491b0476f"
    instance_type              = "m7g.large"
    subnet_id                  = "subnet-0348a18c1d399c193"
    ec2_security_group_ids     = ["sg-0c2691d037bcdd8b1"]
    iam_instance_profile_name  = "meterengine-ec2-role"
    db_identifier              = "meterengine-db"
    db_instance_class          = "db.m7g.large"
    database_apply_immediately = true
    db_subnet_group_name       = "meterengine-rds-subnet-private"
    rds_security_group_ids     = ["sg-068a54ab4cfa91730"]
    rds_initial_database_name  = "meterengine"
    database_username          = "meterengine"
    database_port              = 5432
    backup_retention_period    = 7
    deletion_protection        = true
    skip_final_snapshot        = false
    final_snapshot_identifier  = "meterengine-production-final"
    root_block_device = {
      volume_size = 20, volume_type = "gp3", iops = 3000, throughput = 125,
      encrypted   = false, delete_on_termination = true
    }
  }
  assert {
    condition     = aws_instance.app.instance_type == "m7g.large" && aws_db_instance.app.instance_class == "db.m7g.large" && aws_db_instance.app.apply_immediately && aws_db_instance.app.db_name == "meterengine" && aws_db_instance.app.username == "meterengine"
    error_message = "Resize must preserve the original database and owner."
  }
  assert {
    condition     = aws_db_instance.app.password == null && aws_db_instance.app.password_wo_version == null && aws_instance.app.user_data == null
    error_message = "Import must not rotate owner credentials or replace bootstrap data."
  }
  assert {
    condition     = !aws_instance.app.root_block_device[0].encrypted && aws_instance.app.root_block_device[0].volume_size == 20 && aws_db_instance.app.deletion_protection && !aws_db_instance.app.skip_final_snapshot && !aws_db_instance.app.delete_automated_backups
    error_message = "Preserve the volume and enable safe deletion retention."
  }
}
