# .github/workflows/deploy-test-sjpark.yml이 OIDC로 받는 역할. 이 환경의 GitHub Environment에서 돈 job만 받는다.
# GitHub Environment는 모든 브랜치를 허용하고 필수 승인자를 둔다. 저장소가 공개라 승인 없이는 이 역할을 받지 못하게 한다.
locals {
  github_oidc_provider      = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
  github_subject            = "repo:asm17-ms2@293532363/meterengine@1313421667:environment:${var.environment}"
  app_version_parameter_arn = "${local.ssm_arn}:parameter${local.app_version_parameter_name}"
}

resource "aws_iam_role" "deploy" {
  name = "${local.name}-deploy"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Federated = local.github_oidc_provider }, Action = "sts:AssumeRoleWithWebIdentity",
    Condition = { StringEquals = {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
      "token.actions.githubusercontent.com:sub" = local.github_subject
    } }
  }] })
  tags = local.tags
}

# staging 배포 역할(deploy/terraform/staging/iam.tf)과 같은 권한을 이 환경의 자원으로 좁힌다.
resource "aws_iam_role_policy" "deploy" {
  name = "${var.environment}-deploy-only"
  role = aws_iam_role.deploy.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:DescribeImages"], Resource = local.ecr_repository_arns },
    { Effect = "Allow", Action = ["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"], Resource = aws_autoscaling_group.app.arn },
    { Effect = "Allow", Action = ["autoscaling:DescribeAutoScalingGroups", "autoscaling:DescribeInstanceRefreshes", "autoscaling:DescribeScalingActivities"], Resource = "*" },
    { Effect = "Allow", Action = ["elasticloadbalancing:DescribeLoadBalancers", "elasticloadbalancing:DescribeTargetGroups", "elasticloadbalancing:DescribeTargetHealth"], Resource = "*" },
    { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:PutParameter"], Resource = local.app_version_parameter_arn },
    { Effect = "Deny", Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory", "ssm:StartSession"], NotResource = [local.app_version_parameter_arn] }
  ] })
}
