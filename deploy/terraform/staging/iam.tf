locals {
  account_arn      = "arn:aws:iam::928618307647"
  ssm_arn          = "arn:aws:ssm:ap-northeast-2:928618307647"
  instance_arn     = "arn:aws:ec2:ap-northeast-2:928618307647:instance/${module.service.instance_id}"
  oidc_provider    = "${local.account_arn}:oidc-provider/token.actions.githubusercontent.com"
  immutable_repo   = "repo:asm17-ms2@293532363/meterengine@1313421667"
  ecr_repositories = [for name in local.ecr_repository_names : "arn:aws:ecr:ap-northeast-2:928618307647:repository/${name}"]
  pull_statements = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:DescribeImages"], Resource = local.ecr_repositories }
  ]
  runtime_path = ["${local.ssm_arn}:parameter/meterengine/staging", "${local.ssm_arn}:parameter/meterengine/staging/*"]
  outside_runtime = {
    Effect      = "Deny"
    Action      = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"]
    NotResource = local.runtime_path
  }
  app_version_parameter_arn = "${local.ssm_arn}:parameter${module.app_fleet.app_version_parameter_name}"
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
    { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"], Resource = local.runtime_path },
    local.outside_runtime,
    { Effect = "Allow", Action = ["ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel", "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel"], Resource = "*" },
    { Effect = "Allow", Action = ["ssm:UpdateInstanceInformation"], Resource = "*" }
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
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:DescribeImages"], Resource = local.ecr_repositories },
    # 롤링 배포: 버전 파라미터에 SHA를 쓰고 오토스케일링 그룹을 새로 고친다.
    { Effect = "Allow", Action = ["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"], Resource = module.app_fleet.autoscaling_group_arn },
    { Effect = "Allow", Action = ["autoscaling:DescribeAutoScalingGroups", "autoscaling:DescribeInstanceRefreshes", "autoscaling:DescribeScalingActivities"], Resource = "*" },
    { Effect = "Allow", Action = ["elasticloadbalancing:DescribeLoadBalancers", "elasticloadbalancing:DescribeTargetGroups", "elasticloadbalancing:DescribeTargetHealth"], Resource = "*" },
    { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:PutParameter"], Resource = local.app_version_parameter_arn },
    { Effect = "Deny", Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory", "ssm:StartSession"], NotResource = [local.app_version_parameter_arn] }
  ] })
}
