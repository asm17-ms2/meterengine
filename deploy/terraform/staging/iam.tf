locals {
  account_arn               = "arn:aws:iam::928618307647"
  ssm_arn                   = "arn:aws:ssm:ap-northeast-2:928618307647"
  oidc_provider             = "${local.account_arn}:oidc-provider/token.actions.githubusercontent.com"
  immutable_repo            = "repo:asm17-ms2@293532363/meterengine@1313421667"
  ecr_repositories          = [for name in local.ecr_repository_names : "arn:aws:ecr:ap-northeast-2:928618307647:repository/${name}"]
  app_version_parameter_arn = "${local.ssm_arn}:parameter${module.app_fleet.app_version_parameter_name}"
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
  name   = "staging-deploy-only"
  role   = aws_iam_role.github_deploy.id
  policy = jsonencode({ Version = "2012-10-17", Statement = concat(local.github_deploy_statements, local.github_deploy_kafka_statements) })
}

locals {
  # 카프카를 켜면 배포가 수집 서버 그룹과 컨슈머 서버 그룹도 새로 고친다.
  github_deploy_kafka_statements = [
    for arns in [module.app_fleet.kafka_autoscaling_group_arns] :
    { Effect = "Allow", Action = ["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"], Resource = arns }
    if var.kafka_enabled
  ]
  github_deploy_statements = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:DescribeImages"], Resource = local.ecr_repositories },
    # 롤링 배포: 버전 파라미터에 SHA를 쓰고 오토스케일링 그룹을 새로 고친다.
    { Effect = "Allow", Action = ["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"], Resource = module.app_fleet.autoscaling_group_arn },
    { Effect = "Allow", Action = ["autoscaling:DescribeAutoScalingGroups", "autoscaling:DescribeInstanceRefreshes", "autoscaling:DescribeScalingActivities"], Resource = "*" },
    { Effect = "Allow", Action = ["elasticloadbalancing:DescribeLoadBalancers", "elasticloadbalancing:DescribeTargetGroups", "elasticloadbalancing:DescribeTargetHealth"], Resource = "*" },
    { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:PutParameter"], Resource = local.app_version_parameter_arn },
    { Effect = "Deny", Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory", "ssm:StartSession"], NotResource = [local.app_version_parameter_arn] }
  ]
}
