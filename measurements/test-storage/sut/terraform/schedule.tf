locals {
  auto_stop_cron     = "cron(0 4 * * ? *)"
  auto_stop_timezone = "Asia/Seoul"
}

resource "aws_scheduler_schedule" "stop_rds" {
  name                         = "${local.prefix}-stop-rds"
  state                        = var.auto_stop_state
  schedule_expression          = local.auto_stop_cron
  schedule_expression_timezone = local.auto_stop_timezone

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

# ASG 서버는 멈추면 ASG가 고장으로 보고 새로 띄워, 위 EC2 정지 대신 대수를 0으로 내린다.
# 다음 날 쓰려면 -var asg_min_size=1로 apply한다(원하는 대수는 최소에 맞춰 올라간다).
resource "aws_autoscaling_schedule" "stop_asg" {
  count = var.auto_stop_state == "ENABLED" ? 1 : 0

  scheduled_action_name  = "${local.prefix}-stop-asg"
  autoscaling_group_name = aws_autoscaling_group.sut.name
  recurrence             = "0 4 * * *"
  time_zone              = local.auto_stop_timezone
  min_size               = 0
  max_size               = 3
  desired_capacity       = 0
}
