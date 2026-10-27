data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  environments = toset(["test-slo", "test-storage"])
}

data "aws_db_instance" "sut" {
  for_each               = local.environments
  db_instance_identifier = "meterengine-${each.key}-db"
}

data "aws_iam_role" "legacy_scheduler" {
  for_each = local.environments
  name     = "meterengine-${each.key}-scheduler-role"
}

module "rds_auto_stop" {
  source   = "../modules/rds-auto-stop"
  for_each = local.environments

  name_prefix               = "meterengine-${each.key}"
  account_id                = data.aws_caller_identity.current.account_id
  region                    = data.aws_region.current.region
  db_identifier             = data.aws_db_instance.sut[each.key].db_instance_identifier
  db_arn                    = data.aws_db_instance.sut[each.key].db_instance_arn
  legacy_schedule_name      = "meterengine-${each.key}-stop-rds"
  legacy_scheduler_role_arn = data.aws_iam_role.legacy_scheduler[each.key].arn
  enabled                   = var.enabled[each.key]
  alarm_actions             = var.alarm_actions
}
