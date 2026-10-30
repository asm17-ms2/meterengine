data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_db_instance" "sut" {
  for_each               = var.environments
  db_instance_identifier = "meterengine-${each.key}-db"
}

module "rds_auto_stop" {
  source   = "../modules/rds-auto-stop"
  for_each = var.environments

  name_prefix   = "meterengine-${each.key}"
  account_id    = data.aws_caller_identity.current.account_id
  region        = data.aws_region.current.region
  db_identifier = data.aws_db_instance.sut[each.key].db_instance_identifier
  db_arn        = data.aws_db_instance.sut[each.key].db_instance_arn
  enabled       = var.enabled[each.key]
  alarm_actions = var.alarm_actions
}
