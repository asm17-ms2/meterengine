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
    crypto-encryption-key = "D2_TEST_ONLY_CRYPTO_NOT_A_REAL_SECRET"
    operator-password     = "D2_TEST_ONLY_OPERATOR_NOT_A_REAL_SECRET"
  }
  runtime_secret_versions = {
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
    condition     = output.deployment_target.deployment_environment == "staging" && output.deployment_target.parameter_path == "/meterengine/staging" && output.deployment_target.site_domain == "staging.meterengine.com"
    error_message = "Target output must preserve the isolated manifest contract."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.runtime.policy).Statement[1].Effect == "Deny" && toset(jsondecode(aws_iam_role_policy.runtime.policy).Statement[1].NotResource) == toset(local.runtime_path)
    error_message = "Runtime reads must stay inside the staging path."
  }
  assert {
    condition     = jsondecode(aws_iam_role.github_deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:asm17-ms2@293532363/meterengine@1313421667:environment:staging"
    error_message = "The deploy role trusts the staging GitHub Environment only."
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
    instance_type             = "t4g.medium"
    subnet_id                 = "subnet-0348a18c1d399c193"
    ec2_security_group_ids    = ["sg-11111111111111111"]
    iam_instance_profile_name = "meterengine-staging-ec2"
    db_identifier             = "meterengine-staging-db"
    db_instance_class         = "db.t4g.small"
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
    condition     = aws_instance.app[0].instance_type == "t4g.medium" && aws_db_instance.app.instance_class == "db.t4g.small"
    error_message = "Staging capacity must use the approved t4g classes."
  }
  assert {
    condition     = !aws_db_instance.app.publicly_accessible && aws_db_instance.app.storage_encrypted && aws_instance.app[0].metadata_options[0].http_tokens == "required"
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
    condition     = aws_ssm_parameter.runtime["db-password"].value_wo == null && aws_ssm_parameter.runtime["operator-password"].value_wo == null
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

run "runtime_uses_single_account" {
  command = plan

  assert {
    condition     = toset(keys(aws_ssm_parameter.runtime)) == toset(["db-host", "db-port", "db-name", "db-username", "db-password", "crypto-encryption-key", "operator-email", "operator-password"])
    error_message = "Staging must define exactly the parameters deploy.sh requires."
  }
  assert {
    condition     = aws_ssm_parameter.runtime["db-username"].value == "meterengine_owner"
    error_message = "Staging connects with the RDS master account, as production does."
  }
  assert {
    condition     = !strcontains(file("${path.module}/iam.tf"), "github_owner")
    error_message = "The owner role must be gone."
  }
  assert {
    condition = alltrue([
      for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement :
      statement.Resource == local.ecr_repositories if statement.Effect == "Allow" && contains(flatten([statement.Action]), "ecr:PutImage")
    ]) && anytrue([for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : contains(flatten([statement.Action]), "ecr:PutImage")])
    error_message = "The deploy role pushes images to the two ECR repositories only."
  }
  assert {
    condition = anytrue([
      for statement in jsondecode(aws_iam_role_policy.runtime.policy).Statement :
      statement.Effect == "Allow" && contains(flatten([statement.Action]), "ssm:GetParametersByPath") && toset(statement.Resource) == toset(["${local.ssm_arn}:parameter/meterengine/staging", "${local.ssm_arn}:parameter/meterengine/staging/*"])
    ])
    error_message = "deploy.sh reads the whole staging path."
  }
}

run "images_are_separate_from_production" {
  command = plan

  assert {
    condition     = toset(keys(aws_ecr_repository.image)) == toset(["meterengine-staging-backend", "meterengine-staging-frontend"])
    error_message = "Staging owns exactly two image repositories."
  }
  assert {
    condition     = alltrue([for repository in values(aws_ecr_repository.image) : repository.image_tag_mutability == "IMMUTABLE"])
    error_message = "Image tags cannot be overwritten, as in production."
  }
  assert {
    condition     = toset(keys(aws_ecr_lifecycle_policy.image)) == toset(keys(aws_ecr_repository.image))
    error_message = "Every repository expires old images."
  }
  assert {
    condition     = !strcontains(aws_iam_role_policy.github_deploy.policy, "repository/meterengine-backend") && !strcontains(aws_iam_role_policy.github_deploy.policy, "repository/meterengine-frontend")
    error_message = "The deploy role must not write to production repositories."
  }
  assert {
    condition     = !strcontains(aws_iam_role_policy.runtime.policy, "repository/meterengine-backend") && !strcontains(aws_iam_role_policy.runtime.policy, "repository/meterengine-frontend")
    error_message = "The staging server must not read production repositories."
  }
}
