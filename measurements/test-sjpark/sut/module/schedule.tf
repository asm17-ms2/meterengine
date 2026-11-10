locals {
  auto_stop_cron     = "cron(0 4 * * ? *)"
  auto_stop_timezone = "Asia/Seoul"
}

# 운영과 다름(자동 중지): 비용
resource "aws_scheduler_schedule" "stop_rds" {
  name                         = "${local.name}-stop-rds"
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

# 운영과 다름(자동 중지): 비용
# 변수 기본값과 같은 0대로 내려 다음 plan에 차이가 남지 않는다. 다시 쓰려면 asg_min_size와 asg_max_size를 올려 apply한다.
resource "aws_autoscaling_schedule" "stop_asg" {
  count = var.auto_stop_state == "ENABLED" ? 1 : 0

  scheduled_action_name  = "${local.name}-stop-asg"
  autoscaling_group_name = aws_autoscaling_group.app.name
  recurrence             = "0 4 * * *"
  time_zone              = local.auto_stop_timezone
  min_size               = 0
  max_size               = 0
  desired_capacity       = 0
}
