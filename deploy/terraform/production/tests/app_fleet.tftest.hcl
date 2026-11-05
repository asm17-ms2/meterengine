mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "928618307647" }
  }
  mock_data "aws_region" {
    defaults = { region = "ap-northeast-2" }
  }
}

run "production_fleet_is_wired_beside_the_old_server" {
  command = plan
  override_resource {
    target = module.service.aws_instance.app[0]
    values = { id = "i-0f47bb1f028cd29a9" }
  }
  override_resource {
    target = module.service.aws_db_instance.app
    values = { id = "db-72GJNZYUHSQRXAKNQGC7ECV7UI" }
  }

  assert {
    condition     = output.app_fleet.autoscaling_group_name == "meterengine-production-app" && output.app_fleet.app_version_parameter_name == "/meterengine/production/app-version"
    error_message = "Rollout reads the fleet and version parameter names from these outputs."
  }
  assert {
    condition     = module.service.instance_id == "i-0f47bb1f028cd29a9"
    error_message = "The old server keeps serving while the fleet is empty."
  }
}

run "production_fleet_configuration" {
  command = plan
  module {
    source = "../modules/app_fleet"
  }
  variables {
    environment                        = "production"
    parameter_path                     = "/meterengine/production"
    site_domain                        = "meterengine.com"
    site_aliases                       = ["www.meterengine.com"]
    vpc_id                             = "vpc-07498f0672b7f3c9e"
    public_subnet_ids                  = ["subnet-0348a18c1d399c193", "subnet-0e375e82d415767a3"]
    route53_zone_id                    = "Z08728621CQ5R1K37A7N1"
    database_security_group_id         = "sg-068a54ab4cfa91730"
    ecr_repository_names               = ["meterengine-backend", "meterengine-frontend"]
    ami_id                             = "ami-0f4109eb491b0476f"
    min_size                           = 0
    scaling_target_requests_per_second = 450
    alb_deletion_protection            = true
    app_egress_ports                   = [443]
    monitoring_security_group_id       = "sg-33333333333333333"
  }

  assert {
    condition     = aws_lb.app.name == "meterengine-production-alb" && aws_lb.app.enable_deletion_protection && length(aws_lb.app.access_logs) == 0
    error_message = "The production ALB is protected from deletion and writes no access logs."
  }
  assert {
    condition     = aws_autoscaling_group.app.name == "meterengine-production-app" && aws_autoscaling_group.app.min_size == 0 && aws_autoscaling_group.app.max_size == 3
    error_message = "The production fleet starts at zero servers."
  }
  assert {
    condition     = toset(keys(aws_route53_record.certificate_validation)) == toset(["meterengine.com", "www.meterengine.com"]) && aws_acm_certificate.site.subject_alternative_names == toset(["www.meterengine.com"])
    error_message = "The certificate covers the domain and the www alias."
  }
  assert {
    condition = (
      length(aws_lb_listener_rule.alias_to_site) == 1 &&
      aws_lb_listener_rule.alias_to_site[0].priority == 10 &&
      aws_lb_listener_rule.alias_to_site[0].action[0].redirect[0].host == "meterengine.com" &&
      aws_lb_listener_rule.alias_to_site[0].action[0].redirect[0].status_code == "HTTP_301"
    )
    error_message = "www redirects to the domain before any other rule."
  }
  assert {
    condition     = aws_vpc_security_group_ingress_rule.database_from_app.security_group_id == "sg-068a54ab4cfa91730" && aws_vpc_security_group_ingress_rule.database_from_monitoring.security_group_id == "sg-068a54ab4cfa91730"
    error_message = "Only rules are added to the production DB group, which stays outside Terraform."
  }
  assert {
    condition     = toset(keys(aws_vpc_security_group_egress_rule.app_internet)) == toset(["443"])
    error_message = "New production servers send out on the confirmed ports only."
  }
  assert {
    condition     = strcontains(aws_iam_role_policy.app.policy, "repository/meterengine-backend") && !strcontains(aws_iam_role_policy.app.policy, "meterengine-staging")
    error_message = "Production servers pull production images only."
  }
  assert {
    condition     = strcontains(base64decode(aws_launch_template.app.user_data), "--name '/meterengine/production/app-version'") && aws_ssm_parameter.app_version.value == "unset"
    error_message = "Boot reads the production version parameter, which starts unset."
  }
}
