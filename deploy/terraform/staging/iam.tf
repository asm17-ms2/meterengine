locals {
  account_arn    = "arn:aws:iam::928618307647"
  ssm_arn        = "arn:aws:ssm:ap-northeast-2:928618307647"
  instance_arn   = "arn:aws:ec2:ap-northeast-2:928618307647:instance/${module.service.instance_id}"
  oidc_provider  = "${local.account_arn}:oidc-provider/token.actions.githubusercontent.com"
  immutable_repo = "repo:asm17-ms2@293532363/meterengine@1313421667"
  ecr_repositories = [
    "arn:aws:ecr:ap-northeast-2:928618307647:repository/meterengine-backend",
    "arn:aws:ecr:ap-northeast-2:928618307647:repository/meterengine-frontend"
  ]
  pull_statements = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:DescribeImages"], Resource = local.ecr_repositories }
  ]
  outside_runtime = {
    Effect      = "Deny"
    Action      = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"]
    NotResource = [for name in keys(local.runtime_read_parameters) : "${local.ssm_arn}:parameter/meterengine/staging/${name}"]
  }
}
resource "aws_iam_role" "runtime" {
  name = "meterengine-staging-ec2"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }
  ] })
}
resource "aws_iam_instance_profile" "runtime" {
  name = "meterengine-staging-ec2"
  role = aws_iam_role.runtime.name
}
resource "aws_iam_role_policy" "runtime" {
  name = "staging-runtime-boundary"
  role = aws_iam_role.runtime.id
  policy = jsonencode({ Version = "2012-10-17", Statement = concat([
    { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:GetParameters"], Resource = [for name in keys(local.runtime_read_parameters) : "${local.ssm_arn}:parameter/meterengine/staging/${name}"] },
    local.outside_runtime,
    { Effect = "Deny", Action = ["ssm:GetParametersByPath", "ssm:GetParameterHistory"], Resource = "*" },
    { Effect = "Allow", Action = ["ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel", "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel"], Resource = "*" },
    { Effect = "Allow", Action = ["ssm:UpdateInstanceInformation"], Resource = "*" },
    { Effect = "Allow", Action = ["rds:DescribeDBInstances"], Resource = "*" }
  ], local.pull_statements) })
}
resource "aws_iam_role" "github_deploy" {
  name = "meterengine-staging-deploy"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Federated = local.oidc_provider }, Action = "sts:AssumeRoleWithWebIdentity",
    Condition = { StringEquals = {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
      "token.actions.githubusercontent.com:sub" = "${local.immutable_repo}:environment:staging"
    } }
  }] })
}
resource "aws_iam_role_policy" "github_deploy" {
  name = "staging-deploy-only"
  role = aws_iam_role.github_deploy.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ssm:SendCommand"], Resource = [local.instance_arn, "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"] },
    { Effect = "Allow", Action = ["ssm:GetCommandInvocation"], Resource = "*" },
    { Effect = "Deny", Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory", "ssm:StartSession"], Resource = "*" }
  ] })
}
resource "aws_iam_role" "github_owner" {
  name = "meterengine-staging-owner"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Federated = local.oidc_provider }, Action = "sts:AssumeRoleWithWebIdentity",
    Condition = { StringEquals = {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
      "token.actions.githubusercontent.com:sub" = "${local.immutable_repo}:ref:refs/heads/main"
    } }
  }] })
}
resource "aws_iam_role_policy" "github_owner" {
  name = "staging-owner-only"
  role = aws_iam_role.github_owner.id
  policy = jsonencode({ Version = "2012-10-17", Statement = concat([
    { Effect    = "Allow", Action = ["ssm:StartSession"], Resource = [local.instance_arn, "arn:aws:ssm:ap-northeast-2::document/AWS-StartPortForwardingSessionToRemoteHost"],
      Condition = { BoolIfExists = { "ssm:SessionDocumentAccessCheck" = "true" } }
    },
    { Effect = "Allow", Action = ["ssm:ResumeSession", "ssm:TerminateSession", "ssmmessages:OpenDataChannel"], Resource = "arn:aws:ssm:ap-northeast-2:928618307647:session/$${aws:userid}-*" },
    { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:GetParameters"], Resource = [
      "${local.ssm_arn}:parameter/meterengine/staging/db-host", "${local.ssm_arn}:parameter/meterengine/staging/db-port", "${local.ssm_arn}:parameter/meterengine/staging/db-name",
      "${local.ssm_arn}:parameter/meterengine/staging-migration/db-migration-username", "${local.ssm_arn}:parameter/meterengine/staging-migration/db-migration-password",
      "${local.ssm_arn}:parameter/meterengine/staging/db-shared-username", "${local.ssm_arn}:parameter/meterengine/staging/db-shared-password",
      "${local.ssm_arn}:parameter/meterengine/staging/db-test-username", "${local.ssm_arn}:parameter/meterengine/staging/db-test-password",
      "${local.ssm_arn}:parameter/meterengine/staging/db-live-username", "${local.ssm_arn}:parameter/meterengine/staging/db-live-password"
    ] },
    { Effect = "Deny", Action = ["ssm:SendCommand", "ssm:GetParametersByPath", "ssm:GetParameterHistory"], Resource = "*" }
  ], local.pull_statements) })
}
