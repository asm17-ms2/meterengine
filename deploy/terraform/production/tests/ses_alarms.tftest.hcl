mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "928618307647" }
  }
  mock_data "aws_region" {
    defaults = { region = "ap-northeast-2" }
  }
  mock_data "aws_ssm_parameter" {
    defaults = { insecure_value = "0123456789abcdef0123456789abcdef01234567" }
  }
  mock_data "aws_instance" {
    defaults = { id = "i-0aaaaaaaaaaaaaaaa" }
  }
  mock_data "aws_security_group" {
    defaults = { id = "sg-55555555555555555" }
  }
}

# 가짜 공급자가 받지 않는 import의 결과와, 도메인 레코드가 가리킬 ALB 쪽 값을 plan에서 정한다.
override_resource {
  target = aws_route53_record.public
  values = {}
}
override_resource {
  target = aws_ssm_parameter.coded
  values = {}
}
override_resource {
  target = module.service.aws_db_instance.app
  values = { id = "db-72GJNZYUHSQRXAKNQGC7ECV7UI" }
}
override_resource {
  target          = module.app_fleet.aws_lb.app
  override_during = plan
  values = {
    arn      = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:loadbalancer/app/meterengine-production-alb/0000000000000000"
    dns_name = "meterengine-production-alb-1.ap-northeast-2.elb.amazonaws.com"
    zone_id  = "ZWKZPGTI48KDX"
  }
}
override_resource {
  target          = module.app_fleet.aws_lb_listener.https
  override_during = plan
  values          = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:listener/app/meterengine-production-alb/0000000000000000/0000000000000443" }
}
override_resource {
  target          = module.app_fleet.aws_security_group.alb
  override_during = plan
  values          = { id = "sg-44444444444444444" }
}
# 경보가 어느 주제로 가는지 plan에서 견주려고 주제의 ARN을 정한다.
override_resource {
  target          = aws_sns_topic.ses_alarms
  override_during = plan
  values          = { arn = "arn:aws:sns:ap-northeast-2:928618307647:meterengine-ses-alarms" }
}

run "the_reputation_alarms_use_the_aws_review_thresholds" {
  command = plan

  assert {
    condition = (
      toset(keys(aws_cloudwatch_metric_alarm.ses_reputation)) == toset(["bounce-rate", "complaint-rate"]) &&
      aws_cloudwatch_metric_alarm.ses_reputation["bounce-rate"].metric_name == "Reputation.BounceRate" &&
      aws_cloudwatch_metric_alarm.ses_reputation["bounce-rate"].threshold == 0.05 &&
      aws_cloudwatch_metric_alarm.ses_reputation["complaint-rate"].metric_name == "Reputation.ComplaintRate" &&
      aws_cloudwatch_metric_alarm.ses_reputation["complaint-rate"].threshold == 0.001
    )
    error_message = "AWS reviews the account from a 5 percent bounce rate and a 0.1 percent complaint rate."
  }
  assert {
    condition = alltrue([
      for alarm in aws_cloudwatch_metric_alarm.ses_reputation :
      alarm.comparison_operator == "GreaterThanOrEqualToThreshold" && alarm.statistic == "Maximum" &&
      alarm.period == 3600 && alarm.evaluation_periods == 1 && alarm.treat_missing_data == "ignore"
    ])
    error_message = "The rate is published only when mail is sent, so a missing value keeps the alarm state."
  }
  assert {
    condition = alltrue([
      for alarm in aws_cloudwatch_metric_alarm.ses_reputation :
      alarm.ok_actions == toset([aws_sns_topic.ses_alarms.arn])
    ])
    error_message = "A rate that falls back under the threshold is announced too."
  }
}

run "a_single_bounce_or_complaint_raises_an_alarm" {
  command = plan

  assert {
    condition = (
      toset(keys(aws_cloudwatch_metric_alarm.ses_event)) == toset(["bounce", "complaint"]) &&
      aws_cloudwatch_metric_alarm.ses_event["bounce"].metric_name == "Bounce" &&
      aws_cloudwatch_metric_alarm.ses_event["complaint"].metric_name == "Complaint"
    )
    error_message = "Bounces and complaints are counted separately."
  }
  assert {
    condition = alltrue([
      for alarm in aws_cloudwatch_metric_alarm.ses_event :
      alarm.comparison_operator == "GreaterThanOrEqualToThreshold" && alarm.statistic == "Sum" && alarm.threshold == 1 &&
      alarm.period == 300 && alarm.evaluation_periods == 1 && alarm.treat_missing_data == "notBreaching"
    ])
    error_message = "One event in five minutes raises the alarm, and a metric that does not exist yet counts as zero."
  }
  assert {
    condition     = alltrue([for alarm in aws_cloudwatch_metric_alarm.ses_event : alarm.ok_actions == null])
    error_message = "Going back to OK only means no new event followed, so it is not announced."
  }
}

run "a_burst_of_mail_raises_an_alarm_far_below_the_quota" {
  command = plan

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.ses_daily_send.metric_name == "Send" &&
      aws_cloudwatch_metric_alarm.ses_daily_send.statistic == "Sum" &&
      aws_cloudwatch_metric_alarm.ses_daily_send.period == 86400 &&
      aws_cloudwatch_metric_alarm.ses_daily_send.evaluation_periods == 1 &&
      aws_cloudwatch_metric_alarm.ses_daily_send.comparison_operator == "GreaterThanOrEqualToThreshold" &&
      aws_cloudwatch_metric_alarm.ses_daily_send.treat_missing_data == "notBreaching"
    )
    error_message = "The alarm sums the mail accepted over the last 24 hours."
  }
  assert {
    condition     = aws_cloudwatch_metric_alarm.ses_daily_send.threshold >= 1 && aws_cloudwatch_metric_alarm.ses_daily_send.threshold <= 5000
    error_message = "The threshold stays at a tenth of the 50,000 daily quota or lower."
  }
}

run "every_ses_alarm_watches_the_account_and_goes_to_the_ses_topic" {
  command = plan

  assert {
    condition = alltrue([
      for alarm in concat(values(aws_cloudwatch_metric_alarm.ses_reputation), values(aws_cloudwatch_metric_alarm.ses_event), [aws_cloudwatch_metric_alarm.ses_daily_send]) :
      alarm.namespace == "AWS/SES" && alarm.dimensions == null && alarm.alarm_actions == toset([aws_sns_topic.ses_alarms.arn])
    ])
    error_message = "The account-level SES metrics have no dimensions, and every alarm goes to the SES topic."
  }
  assert {
    condition = alltrue([
      for alarm in concat(values(aws_cloudwatch_metric_alarm.ses_reputation), values(aws_cloudwatch_metric_alarm.ses_event), [aws_cloudwatch_metric_alarm.ses_daily_send]) :
      can(regex("^[ -~]+$", alarm.alarm_description)) && startswith(alarm.alarm_name, "meterengine-ses-")
    ])
    error_message = "The alarm text is ASCII and the names share one prefix."
  }
  assert {
    condition     = aws_sns_topic.ses_alarms.name == "meterengine-ses-alarms" && aws_sns_topic.ses_alarms.name != aws_sns_topic.monitoring_down.name
    error_message = "SES alarms have their own topic, apart from the monitoring-down topic."
  }
}
