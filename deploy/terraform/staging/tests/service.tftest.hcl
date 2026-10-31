mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "928618307647" }
  }
  mock_data "aws_region" {
    defaults = { region = "ap-northeast-2" }
  }
}


variables {
  db_password_wo      = "D2_TEST_ONLY_OWNER_NOT_A_REAL_SECRET"
  db_password_version = 1
  runtime_secrets = {
    db-shared-password    = "D2_TEST_ONLY_SHARED_NOT_A_REAL_SECRET"
    db-test-password      = "D2_TEST_ONLY_TEST_NOT_A_REAL_SECRET"
    db-live-password      = "D2_TEST_ONLY_LIVE_NOT_A_REAL_SECRET"
    crypto-encryption-key = "D2_TEST_ONLY_CRYPTO_NOT_A_REAL_SECRET"
    operator-password     = "D2_TEST_ONLY_OPERATOR_NOT_A_REAL_SECRET"
  }
  runtime_secret_versions = {
    db-shared-password    = 1, db-test-password = 1, db-live-password = 1,
    crypto-encryption-key = 1, operator-password = 1
  }
  operator_email = "d2-fixture@example.invalid"
}

run "staging_is_isolated" {
  command = plan

  override_resource {
    target          = aws_security_group.app
    override_during = plan
    values          = { id = "sg-11111111111111111" }
  }
  override_resource {
    target          = aws_security_group.database
    override_during = plan
    values          = { id = "sg-22222222222222222" }
  }


  assert {
    condition = alltrue(concat(
      [aws_security_group.app.tags.Environment == "staging", aws_security_group.database.tags.Environment == "staging", aws_eip.app.tags.Environment == "staging"],
      [for rule in values(aws_vpc_security_group_ingress_rule.web) : rule.tags.Environment == "staging"],
      [aws_vpc_security_group_ingress_rule.database.tags.Environment == "staging", aws_vpc_security_group_egress_rule.database.tags.Environment == "staging", aws_vpc_security_group_egress_rule.https.tags.Environment == "staging"]
    ))
    error_message = "Staging network resources need enforceable ownership tags."
  }
  assert {
    condition     = output.deployment_target.aws_account_id == "928618307647" && output.deployment_target.aws_region == "ap-northeast-2"
    error_message = "Staging must stay in the verified account and region."
  }
  assert {
    condition     = aws_vpc_security_group_ingress_rule.database.referenced_security_group_id == aws_security_group.app.id && aws_vpc_security_group_ingress_rule.database.security_group_id == aws_security_group.database.id
    error_message = "Only the independent staging app SG can reach the staging DB SG."
  }
  assert {
    condition     = aws_security_group.app.vpc_id == "vpc-07498f0672b7f3c9e" && aws_security_group.database.vpc_id == "vpc-07498f0672b7f3c9e"
    error_message = "Staging must use the verified VPC."
  }
  assert {
    condition     = output.deployment_target.deployment_environment == "staging" && output.deployment_target.parameter_path == "/meterengine/staging" && output.deployment_target.compose_project == "meterengine-staging" && output.deployment_target.site_domain == "staging.meterengine.com"
    error_message = "Target output must preserve the isolated manifest contract."
  }
  assert {
    condition     = !contains(keys(aws_ssm_parameter.runtime), "db-migration-password") && aws_ssm_parameter.migration_password.name == "/meterengine/staging-migration/db-migration-password"
    error_message = "Owner credentials must be outside the runtime path."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.runtime.policy).Statement[0].Action == ["ssm:GetParameter", "ssm:GetParameters"] && jsondecode(aws_iam_role_policy.runtime.policy).Statement[1].Effect == "Deny"
    error_message = "Runtime parameter reads must be exact-name and deny other environments."
  }
  assert {
    condition = length(aws_ssm_parameter.runtime) == 13 && alltrue([
      for suffix in ["login-ip-limit", "login-account-limit", "login-window", "signup-ip-limit", "signup-account-limit", "signup-window"] :
      !contains(keys(aws_ssm_parameter.runtime), "meterengine-auth-rate-limit-${suffix}") &&
      contains(jsondecode(aws_iam_role_policy.runtime.policy).Statement[0].Resource, "${local.ssm_arn}:parameter/meterengine/staging/meterengine-auth-rate-limit-${suffix}") &&
      contains(jsondecode(aws_iam_role_policy.runtime.policy).Statement[1].NotResource, "${local.ssm_arn}:parameter/meterengine/staging/meterengine-auth-rate-limit-${suffix}")
    ])
    error_message = "Optional authentication limits are readable by exact name but are not Terraform-managed parameters."
  }
  assert {
    condition = (length(jsondecode(aws_iam_role_policy.runtime.policy).Statement[0].Resource) == 20 &&
      jsondecode(aws_iam_role_policy.runtime.policy).Statement[2].Effect == "Deny" &&
      jsondecode(aws_iam_role_policy.runtime.policy).Statement[2].Resource == "*" &&
      contains(jsondecode(aws_iam_role_policy.runtime.policy).Statement[2].Action, "ssm:GetParametersByPath") &&
    alltrue([for name in jsondecode(aws_iam_role_policy.runtime.policy).Statement[0].Resource : startswith(name, "${local.ssm_arn}:parameter/meterengine/staging/")]))
    error_message = "Runtime reads stay in staging and cannot enumerate paths."
  }
  assert {
    condition     = jsondecode(aws_iam_role.github_deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:asm17-ms2@293532363/meterengine@1313421667:environment:staging" && jsondecode(aws_iam_role.github_owner.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:asm17-ms2@293532363/meterengine@1313421667:ref:refs/heads/main"
    error_message = "Deployment and owner trust subjects must be independently constrained."
  }
}

run "service_is_protected" {
  command = plan
  module {
    source = "../modules/service"
  }
  variables {
    name                      = "meterengine-staging"
    ami_id                    = "ami-0f4109eb491b0476f"
    instance_type             = "m7g.large"
    subnet_id                 = "subnet-0348a18c1d399c193"
    ec2_security_group_ids    = ["sg-11111111111111111"]
    iam_instance_profile_name = "meterengine-staging-ec2"
    db_identifier             = "meterengine-staging-db"
    db_instance_class         = "db.m7g.large"
    db_subnet_group_name      = "meterengine-rds-subnet-private"
    rds_security_group_ids    = ["sg-22222222222222222"]
    rds_initial_database_name = "meterengine_app"
    database_username         = "meterengine_owner"
    database_port             = 5432
    backup_retention_period   = 7
    deletion_protection       = true
    skip_final_snapshot       = false
    final_snapshot_identifier = "meterengine-staging-final"
    user_data                 = "#!/bin/bash\ntrue\n"
  }
  assert {
    condition     = aws_instance.app.instance_type == "m7g.large" && aws_db_instance.app.instance_class == "db.m7g.large"
    error_message = "Staging capacity must use the approved m7g classes."
  }
  assert {
    condition     = !aws_db_instance.app.publicly_accessible && aws_db_instance.app.storage_encrypted && aws_instance.app.metadata_options[0].http_tokens == "required"
    error_message = "RDS must be private/encrypted and EC2 must require IMDSv2."
  }
  assert {
    condition     = aws_db_instance.app.deletion_protection && !aws_db_instance.app.skip_final_snapshot && aws_db_instance.app.backup_retention_period == 7 && aws_db_instance.app.final_snapshot_identifier == "meterengine-staging-final" && !aws_db_instance.app.delete_automated_backups
    error_message = "Backups and a final snapshot must survive deletion."
  }
}

run "staging_state_excludes_secrets" {
  command = apply
  assert {
    condition     = aws_ssm_parameter.runtime["db-shared-password"].value_wo == null && aws_ssm_parameter.migration_password.value_wo == null
    error_message = "Write-only secrets must not be retained in mock state."
  }
  assert {
    condition = jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[0].Resource == [
      "arn:aws:ec2:ap-northeast-2:928618307647:instance/${module.service.instance_id}",
      "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
    ]
    error_message = "SendCommand must be limited to the staging instance and approved document."
  }
}

run "wrong_account_is_rejected" {
  command = plan
  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "000000000000" }
  }
  expect_failures = [output.deployment_target]
}
