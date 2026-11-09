data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  ssm_arn             = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}"
  runtime_path        = ["${local.ssm_arn}:parameter${var.parameter_path}", "${local.ssm_arn}:parameter${var.parameter_path}/*"]
  ecr_repository_arns = [for name in var.ecr_repository_names : "arn:aws:ecr:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:repository/${name}"]
}

resource "aws_iam_role" "app" {
  name = "${local.name}-app"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }
  ] })
  tags = local.tags
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name}-app"
  role = aws_iam_role.app.name
  tags = local.tags
}

resource "aws_iam_role_policy" "app" {
  name = "${var.environment}-app-runtime"
  role = aws_iam_role.app.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"], Resource = local.runtime_path },
    {
      Effect      = "Deny"
      Action      = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"]
      NotResource = local.runtime_path
    },
    { Effect = "Allow", Action = ["ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel", "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel"], Resource = "*" },
    { Effect = "Allow", Action = ["ssm:UpdateInstanceInformation"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:DescribeImages"], Resource = local.ecr_repository_arns },
    # 10초 CPU 지표 스크립트가 쓴다. PutMetricData는 자원으로 못 좁혀 이름공간으로 막는다.
    {
      Effect    = "Allow"
      Action    = ["cloudwatch:PutMetricData"]
      Resource  = "*"
      Condition = { StringEquals = { "cloudwatch:namespace" = "MeterEngine/Scaling" } }
    },
    { Effect = "Allow", Action = ["autoscaling:DescribeAutoScalingGroups"], Resource = "*" }
  ] })
}
