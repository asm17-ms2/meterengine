# --- 스케줄러 역할 ---

data "aws_caller_identity" "current" {}

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
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${local.prefix}-scheduler-role"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

data "aws_iam_policy_document" "scheduler" {
  statement {
    sid       = "StopLoadgenEc2"
    actions   = ["ec2:StopInstances"]
    resources = [aws_instance.loadgen.arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "${local.prefix}-scheduler-policy"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler.json
}


# --- 매일 04:00 중지 스케줄 ---

resource "aws_scheduler_schedule" "stop_ec2" {
  name                         = "${local.prefix}-stop-ec2"
  state                        = var.auto_stop_state
  schedule_expression          = "cron(0 4 * * ? *)"
  schedule_expression_timezone = "Asia/Seoul"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler.arn
    input = jsonencode({
      InstanceIds = [aws_instance.loadgen.id]
    })

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}
