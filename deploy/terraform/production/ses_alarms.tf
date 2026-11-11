# SES 반송과 스팸 신고, 발송량을 CloudWatch 경보로 본다. 받는 곳은 코드에 없고 이 주제에 손으로 구독한다.
resource "aws_sns_topic" "ses_alarms" {
  name = "meterengine-ses-alarms"
  tags = { Environment = "production" }
}

locals {
  # AWS가 계정을 검토 대상에 올리는 선이다. 반송률 10%, 신고율 0.5%부터는 발송을 멈출 수 있다.
  ses_reputation_alarms = {
    bounce-rate = {
      metric_name = "Reputation.BounceRate"
      threshold   = 0.05
      description = "The SES account bounce rate reached 5 percent. AWS reviews the account from 5 percent and may pause sending from 10 percent."
    }
    complaint-rate = {
      metric_name = "Reputation.ComplaintRate"
      threshold   = 0.001
      description = "The SES account complaint rate reached 0.1 percent. AWS reviews the account from 0.1 percent and may pause sending from 0.5 percent."
    }
  }
  ses_event_alarms = {
    bounce = {
      metric_name = "Bounce"
      description = "A mail sent through SES bounced. Find the address in the SES account suppression list."
    }
    complaint = {
      metric_name = "Complaint"
      description = "A recipient reported a mail sent through SES as spam. Find the address in the SES account suppression list."
    }
  }
  # 가입 폼을 자동으로 찍어 메일을 보내게 하는 것을 잡는다. 하루 한도 50,000통보다 훨씬 낮게 둔다.
  ses_daily_send_alarm_threshold = 200
}

resource "aws_cloudwatch_metric_alarm" "ses_reputation" {
  for_each = local.ses_reputation_alarms

  alarm_name          = "meterengine-ses-${each.key}"
  alarm_description   = each.value.description
  namespace           = "AWS/SES"
  metric_name         = each.value.metric_name
  statistic           = "Maximum"
  period              = 3600
  evaluation_periods  = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = each.value.threshold
  # 비율은 메일을 보낼 때만 찍힌다. 값이 없다고 풀리면 발송을 멈춘 것이 복구로 보인다.
  treat_missing_data = "ignore"
  alarm_actions      = [aws_sns_topic.ses_alarms.arn]
  ok_actions         = [aws_sns_topic.ses_alarms.arn]
  tags               = { Environment = "production" }
}

resource "aws_cloudwatch_metric_alarm" "ses_event" {
  for_each = local.ses_event_alarms

  alarm_name          = "meterengine-ses-${each.key}"
  alarm_description   = each.value.description
  namespace           = "AWS/SES"
  metric_name         = each.value.metric_name
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  # 첫 반송이나 신고가 나기 전에는 지표가 없다. 없는 것은 0건이다.
  treat_missing_data = "notBreaching"
  # 풀림은 그 뒤에 새 건이 없었다는 뜻뿐이라 알리지 않는다.
  alarm_actions = [aws_sns_topic.ses_alarms.arn]
  tags          = { Environment = "production" }
}

resource "aws_cloudwatch_metric_alarm" "ses_daily_send" {
  alarm_name          = "meterengine-ses-daily-send"
  alarm_description   = "SES accepted ${local.ses_daily_send_alarm_threshold} or more mails in the last 24 hours. Check whether the signup form is being used to send mail."
  namespace           = "AWS/SES"
  metric_name         = "Send"
  statistic           = "Sum"
  period              = 86400
  evaluation_periods  = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = local.ses_daily_send_alarm_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.ses_alarms.arn]
  tags                = { Environment = "production" }
}
