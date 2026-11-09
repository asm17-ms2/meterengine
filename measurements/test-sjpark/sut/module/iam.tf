data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  ssm_arn             = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}"
  runtime_path        = ["${local.ssm_arn}:parameter${local.parameter_path}", "${local.ssm_arn}:parameter${local.parameter_path}/*"]
  ecr_repository_arns = [for name in local.ecr_repository_names : "arn:aws:ecr:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:repository/${name}"]
  # 개발 서버와 운영의 비밀 경로. 자기 경로 거부 문장을 실수로 넓혀도 이 둘은 계속 막는다.
  other_secret_paths = flatten([for path in [local.dev_param_path, local.production_param_path] : ["${local.ssm_arn}:parameter${path}", "${local.ssm_arn}:parameter${path}/*"]])
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
    # 운영과 다름(파라미터 읽기 범위): 자기 경로 밖과 개발 서버, 운영 비밀을 이중으로 막는다
    {
      Sid         = "DenyParametersOutsideOwnPath"
      Effect      = "Deny"
      Action      = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"]
      NotResource = local.runtime_path
    },
    {
      Sid      = "DenyDevServerParameters"
      Effect   = "Deny"
      Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"]
      Resource = local.other_secret_paths
    },
    { Effect = "Allow", Action = ["ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel", "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel"], Resource = "*" },
    { Effect = "Allow", Action = ["ssm:UpdateInstanceInformation"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:DescribeImages"], Resource = local.ecr_repository_arns },
    # 10초 지표 스크립트가 쓴다. PutMetricData는 자원으로 못 좁혀 이름공간으로 막는다.
    {
      Effect    = "Allow"
      Action    = ["cloudwatch:PutMetricData"]
      Resource  = "*"
      Condition = { StringEquals = { "cloudwatch:namespace" = "MeterEngine/Scaling" } }
    },
    { Effect = "Allow", Action = ["autoscaling:DescribeAutoScalingGroups"], Resource = "*" }
  ] })
}

# 운영과 다름(자동 중지): 비용
resource "aws_iam_role" "scheduler" {
  name = "${local.name}-scheduler"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [
    {
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id } }
    }
  ] })
  tags = local.tags
}

resource "aws_iam_role_policy" "scheduler" {
  name = "${local.name}-stop-rds"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["rds:StopDBInstance"], Resource = aws_db_instance.sut.arn }
  ] })
}
