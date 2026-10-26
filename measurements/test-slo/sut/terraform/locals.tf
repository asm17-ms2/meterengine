data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  prefix            = "meterengine-${var.sut_name}"
  domain            = "${var.sut_name}.meterengine.com"
  param_path        = "/meterengine/${var.sut_name}"
  dev_param_path    = "/meterengine/prod"
  account_id        = data.aws_caller_identity.current.account_id
  region            = data.aws_region.current.region
  availability_zone = "ap-northeast-2a"
  ecr_repos         = ["meterengine-backend", "meterengine-frontend"]
  db_name           = "meterengine"
  db_username       = "meterengine"
  db_port           = 5432
}
