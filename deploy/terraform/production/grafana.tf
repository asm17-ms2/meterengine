# 운영 ALB가 grafana 이름으로 온 요청을 공용 모니터링 서버의 Grafana로 보낸다. 로그인은 Grafana의 GitHub 조직 로그인이 맡는다.
# Prometheus 화면은 자체 로그인이 없어 열지 않는다. 쿼리는 Grafana Explore에서 한다.
locals {
  grafana_domain             = "grafana.meterengine.com"
  grafana_port               = 3000
  grafana_validation_options = { for option in aws_acm_certificate.grafana.domain_validation_options : option.domain_name => option }
}

# 공용 모니터링 루트가 만든다. 서버를 다시 만들면 다음 운영 apply가 새 서버를 붙일 때까지 Grafana가 503이다.
data "aws_instance" "monitoring" {
  filter {
    name   = "tag:Name"
    values = ["meterengine-monitoring"]
  }
  filter {
    name   = "tag:Role"
    values = ["monitoring"]
  }
  filter {
    name   = "instance-state-name"
    values = ["pending", "running", "stopping", "stopped"]
  }
}

# 사이트 인증서에 이름을 더하면 인증서가 교체되며 리스너와 엉킬 수 있어 따로 만들어 리스너에 더한다(SNI).
resource "aws_acm_certificate" "grafana" {
  domain_name       = local.grafana_domain
  validation_method = "DNS"
  tags              = { Environment = "production" }
  lifecycle {
    create_before_destroy = true
  }
}

# 키를 설정 값으로 고정한다. domain_validation_options로 돌면 plan에서 키를 몰라 for_each가 멈춘다.
resource "aws_route53_record" "grafana_certificate_validation" {
  for_each        = toset([local.grafana_domain])
  zone_id         = local.route53_zone_id
  name            = local.grafana_validation_options[each.key].resource_record_name
  type            = local.grafana_validation_options[each.key].resource_record_type
  records         = [local.grafana_validation_options[each.key].resource_record_value]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "grafana" {
  certificate_arn         = aws_acm_certificate.grafana.arn
  validation_record_fqdns = [for record in aws_route53_record.grafana_certificate_validation : record.fqdn]
}

resource "aws_lb_listener_certificate" "grafana" {
  listener_arn    = module.app_fleet.https_listener_arn
  certificate_arn = aws_acm_certificate_validation.grafana.certificate_arn
}

# port, protocol, name_prefix, vpc_id를 바꾸면 교체된다. 새것을 먼저 만들어 규칙을 옮긴 뒤 옛것을 지운다.
resource "aws_lb_target_group" "grafana" {
  name_prefix          = "graf-"
  port                 = local.grafana_port
  protocol             = "HTTP"
  vpc_id               = "vpc-07498f0672b7f3c9e"
  deregistration_delay = 30
  health_check {
    path                = "/api/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 2
  }
  tags = { Environment = "production", Name = "meterengine-production-grafana" }
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_target_group_attachment" "grafana" {
  target_group_arn = aws_lb_target_group.grafana.arn
  target_id        = data.aws_instance.monitoring.id
  port             = local.grafana_port
}

# Grafana 자체 지표(/metrics)는 로그인 없이 응답해 밖에 열지 않는다. Prometheus는 grafana:3000으로 바로 긁는다.
resource "aws_lb_listener_rule" "hide_grafana_metrics" {
  listener_arn = module.app_fleet.https_listener_arn
  priority     = 25
  condition {
    host_header {
      values = [local.grafana_domain]
    }
  }
  condition {
    path_pattern {
      values = ["/metrics"]
    }
  }
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "grafana" {
  listener_arn = module.app_fleet.https_listener_arn
  priority     = 30
  condition {
    host_header {
      values = [local.grafana_domain]
    }
  }
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.grafana.arn
  }
}

resource "aws_vpc_security_group_egress_rule" "alb_to_grafana" {
  security_group_id            = module.app_fleet.alb_security_group_id
  referenced_security_group_id = data.aws_security_group.monitoring.id
  ip_protocol                  = "tcp"
  from_port                    = local.grafana_port
  to_port                      = local.grafana_port
  tags                         = { Environment = "production" }
}

resource "aws_vpc_security_group_ingress_rule" "grafana_from_alb" {
  security_group_id            = data.aws_security_group.monitoring.id
  referenced_security_group_id = module.app_fleet.alb_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = local.grafana_port
  to_port                      = local.grafana_port
  tags                         = { Environment = "production" }
}

# 경보를 보내는 Grafana가 이 서버 안에 있어, 서버가 굳으면 알림도 같이 멎는다. 서버 밖에서 따로 본다.
# 받는 곳(Slack, 메일)은 공개 저장소에 적지 않고 이 주제에 손으로 구독을 건다.
resource "aws_sns_topic" "monitoring_down" {
  name = "meterengine-monitoring-down"
  tags = { Environment = "production" }
}

resource "aws_cloudwatch_metric_alarm" "grafana_unhealthy" {
  alarm_name          = "meterengine-monitoring-grafana-unhealthy"
  alarm_description   = "The shared monitoring server stopped answering the ALB health check. Grafana alerts are not being sent."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  # 대상이 빠져 지표가 끊겨도 Grafana가 닿지 않는 것이므로 울린다.
  treat_missing_data = "breaching"
  dimensions = {
    LoadBalancer = module.app_fleet.alb_arn_suffix
    TargetGroup  = aws_lb_target_group.grafana.arn_suffix
  }
  alarm_actions = [aws_sns_topic.monitoring_down.arn]
  ok_actions    = [aws_sns_topic.monitoring_down.arn]
  tags          = { Environment = "production" }
}
