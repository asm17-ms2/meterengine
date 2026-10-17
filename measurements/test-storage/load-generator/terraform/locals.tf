data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  prefix           = "meterengine-test-storage-load-generator"
  sut_prefix       = "meterengine-test-storage"
  sut_params_arn   = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter/meterengine/test-storage/*"
  sut_db_port      = 5432
  al2023_arm64_ami = "ami-0f4109eb491b0476f"
}
