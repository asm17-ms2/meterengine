locals {
  auto_stop_cron     = "cron(0 4 * * ? *)"
  auto_stop_timezone = "Asia/Seoul"
}

resource "aws_scheduler_schedule" "stop_rds" {
  name                         = "${local.prefix}-stop-rds"
  state                        = "ENABLED"
  schedule_expression          = local.auto_stop_cron
  schedule_expression_timezone = local.auto_stop_timezone

  lifecycle {
    ignore_changes = [state]
  }

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:rds:stopDBInstance"
    role_arn = aws_iam_role.scheduler.arn
    input = jsonencode({
      DbInstanceIdentifier = aws_db_instance.sut.identifier
    })

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}

resource "aws_scheduler_schedule" "stop_ec2" {
  name                         = "${local.prefix}-stop-ec2"
  state                        = var.auto_stop_state
  schedule_expression          = local.auto_stop_cron
  schedule_expression_timezone = local.auto_stop_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler.arn
    input = jsonencode({
      InstanceIds = [aws_instance.sut.id]
    })

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}
