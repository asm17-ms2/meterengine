data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

# --- SUT EC2 ---

resource "aws_iam_role" "sut" {
  name               = "${local.prefix}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "sut_ssm_core" {
  role       = aws_iam_role.sut.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "sut" {
  statement {
    sid = "ParameterStoreRead"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = [
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.param_path}",
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.param_path}/*",
    ]
  }

  # AmazonSSMManagedInstanceCore가 ssm:GetParameter(s)를 Resource *로 허용해, 막지 않으면 자기 경로 밖의 팀 비밀을 읽을 수 있다.
  statement {
    sid    = "DenyParametersOutsideOwnPath"
    effect = "Deny"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:GetParameterHistory",
    ]
    not_resources = [
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.param_path}",
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.param_path}/*",
    ]
  }

  # 위 문장의 경로를 실수로 넓혀도 개발 서버 비밀은 계속 막는다.
  statement {
    sid    = "DenyDevServerParameters"
    effect = "Deny"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:GetParameterHistory",
    ]
    resources = [
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.dev_param_path}",
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.dev_param_path}/*",
    ]
  }

  statement {
    sid       = "ECRAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "ECRPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
    ]
    resources = [for repo in local.ecr_repos : "arn:aws:ecr:${local.region}:${local.account_id}:repository/${repo}"]
  }

  statement {
    sid = "CloudWatchRead"
    actions = [
      "cloudwatch:GetMetricData",
      "cloudwatch:GetMetricStatistics",
      "cloudwatch:ListMetrics",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "sut" {
  name   = "${local.prefix}-ec2-policy"
  role   = aws_iam_role.sut.id
  policy = data.aws_iam_policy_document.sut.json
}

resource "aws_iam_instance_profile" "sut" {
  name = "${local.prefix}-ec2-profile"
  role = aws_iam_role.sut.name
}

# --- 자동 중지 스케줄러 ---

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${local.prefix}-scheduler-role"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

data "aws_iam_policy_document" "scheduler" {
  statement {
    sid       = "StopSutRds"
    actions   = ["rds:StopDBInstance"]
    resources = [aws_db_instance.sut.arn]
  }

  statement {
    sid       = "StopSutEc2"
    actions   = ["ec2:StopInstances"]
    resources = [aws_instance.sut.arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "${local.prefix}-scheduler-policy"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler.json
}
