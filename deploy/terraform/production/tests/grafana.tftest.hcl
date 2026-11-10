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

# 가짜 공급자가 받지 않는 import의 결과와, 규칙이 가리킬 ALB 쪽 값을 plan에서 정한다.
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

run "grafana_opens_on_the_production_alb" {
  command = plan

  assert {
    condition     = aws_acm_certificate.grafana.domain_name == "grafana.meterengine.com" && toset(keys(aws_route53_record.grafana_certificate_validation)) == toset(["grafana.meterengine.com"])
    error_message = "Grafana has its own certificate so the site certificate is never replaced."
  }
  assert {
    condition     = aws_lb_listener_certificate.grafana.listener_arn == module.app_fleet.https_listener_arn
    error_message = "The Grafana certificate is added to the HTTPS listener by SNI."
  }
  assert {
    condition = (
      aws_lb_target_group.grafana.name_prefix == "graf-" && aws_lb_target_group.grafana.port == 3000 &&
      aws_lb_target_group.grafana.health_check[0].path == "/api/health" && aws_lb_target_group.grafana.vpc_id == "vpc-07498f0672b7f3c9e"
    )
    error_message = "The Grafana target group checks the health endpoint on port 3000."
  }
  assert {
    condition     = aws_lb_target_group_attachment.grafana.target_id == "i-0aaaaaaaaaaaaaaaa" && aws_lb_target_group_attachment.grafana.port == 3000
    error_message = "The shared monitoring server is the only Grafana target."
  }
}

run "grafana_hides_its_metrics" {
  command = plan

  assert {
    condition = (
      aws_lb_listener_rule.hide_grafana_metrics.priority == 25 &&
      aws_lb_listener_rule.hide_grafana_metrics.priority < aws_lb_listener_rule.grafana.priority &&
      toset(flatten([for condition in aws_lb_listener_rule.hide_grafana_metrics.condition : [for pattern in condition.path_pattern : tolist(pattern.values)]])) == toset(["/metrics"]) &&
      aws_lb_listener_rule.hide_grafana_metrics.action[0].fixed_response[0].status_code == "404"
    )
    error_message = "Grafana metrics answer without login, so the ALB returns 404 before forwarding."
  }
  assert {
    condition = alltrue([
      for rule in [aws_lb_listener_rule.hide_grafana_metrics, aws_lb_listener_rule.grafana] :
      rule.listener_arn == module.app_fleet.https_listener_arn &&
      toset(flatten([for condition in rule.condition : [for host in condition.host_header : tolist(host.values)]])) == toset(["grafana.meterengine.com"])
    ])
    error_message = "Only the grafana host reaches Grafana, and only over HTTPS."
  }
  assert {
    condition     = aws_lb_listener_rule.grafana.priority == 30 && aws_lb_listener_rule.grafana.action[0].type == "forward"
    error_message = "Every other grafana request goes to the Grafana target group."
  }
}

run "only_the_alb_reaches_grafana" {
  command = plan

  assert {
    condition = (
      aws_vpc_security_group_egress_rule.alb_to_grafana.security_group_id == "sg-44444444444444444" &&
      aws_vpc_security_group_egress_rule.alb_to_grafana.referenced_security_group_id == "sg-55555555555555555" &&
      aws_vpc_security_group_ingress_rule.grafana_from_alb.security_group_id == "sg-55555555555555555" &&
      aws_vpc_security_group_ingress_rule.grafana_from_alb.referenced_security_group_id == "sg-44444444444444444"
    )
    error_message = "The ALB group sends to and the monitoring group admits port 3000 between the two groups only."
  }
  assert {
    condition = alltrue([
      for rule in [aws_vpc_security_group_egress_rule.alb_to_grafana, aws_vpc_security_group_ingress_rule.grafana_from_alb] :
      rule.from_port == 3000 && rule.to_port == 3000 && rule.ip_protocol == "tcp" && rule.cidr_ipv4 == null
    ])
    error_message = "Grafana is open on port 3000 to the ALB alone."
  }
}
