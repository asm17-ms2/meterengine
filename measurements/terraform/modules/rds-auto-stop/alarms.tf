resource "aws_cloudwatch_metric_alarm" "execution_failed" {
  alarm_name          = "${local.name}-execution-failed"
  alarm_description   = "Automatic RDS stop or legacy schedule cutover failed; inspect execution history"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  threshold           = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions

  metric_query {
    id          = "failures"
    expression  = "failed + timedout + aborted"
    return_data = true
  }

  dynamic "metric_query" {
    for_each = {
      failed   = "ExecutionsFailed"
      timedout = "ExecutionsTimedOut"
      aborted  = "ExecutionsAborted"
    }
    content {
      id = metric_query.key
      metric {
        metric_name = metric_query.value
        namespace   = "AWS/States"
        period      = 300
        stat        = "Sum"
        dimensions  = { StateMachineArn = local.state_machine_arn }
      }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "delivery_failed" {
  alarm_name          = "${local.name}-delivery-failed"
  alarm_description   = "EventBridge could not start the RDS auto-stop workflow"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "FailedInvocations"
  namespace           = "AWS/Events"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  treat_missing_data  = "notBreaching"
  dimensions          = { RuleName = aws_cloudwatch_event_rule.automatic_restart.name }
  alarm_actions       = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "undelivered" {
  alarm_name          = "${local.name}-undelivered"
  alarm_description   = "RDS auto-stop events await investigation in the dead-letter queue"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Maximum"
  threshold           = 1
  treat_missing_data  = "notBreaching"
  dimensions          = { QueueName = aws_sqs_queue.undelivered.name }
  alarm_actions       = var.alarm_actions
}
