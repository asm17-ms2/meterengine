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
}

# 손으로 만든 도메인 레코드를 가져오는 import는 가짜 공급자가 받지 않아 결과를 정해 준다.
# 가져온 상태에 모르는 값이 남지 않게 별칭이 가리킬 ALB 주소도 plan에서 정한다.
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

run "production_preserves_identity_and_requests_resize" {
  command = plan
  override_resource {
    target = module.service.aws_db_instance.app
    values = {
      id      = "db-72GJNZYUHSQRXAKNQGC7ECV7UI"
      address = "meterengine-db.example.invalid"
    }
  }
  assert {
    condition     = output.deployment_target.deployment_environment == "production" && output.deployment_target.parameter_path == "/meterengine/production"
    error_message = "Production must preserve its deployment identity."
  }
}

run "import_service_configuration" {
  command = plan
  module { source = "../modules/service" }
  variables {
    db_identifier              = "meterengine-db"
    db_instance_class          = "db.t4g.small"
    database_apply_immediately = true
    db_subnet_group_name       = "meterengine-rds-subnet-private"
    rds_security_group_ids     = ["sg-068a54ab4cfa91730"]
    rds_initial_database_name  = "meterengine"
    database_username          = "meterengine"
    database_port              = 5432
    backup_retention_period    = 7
    deletion_protection        = true
    skip_final_snapshot        = false
    final_snapshot_identifier  = "meterengine-production-final"
  }
  assert {
    condition     = aws_db_instance.app.instance_class == "db.t4g.small" && aws_db_instance.app.apply_immediately && aws_db_instance.app.db_name == "meterengine" && aws_db_instance.app.username == "meterengine"
    error_message = "Resize must preserve the original database and owner."
  }
  assert {
    condition     = aws_db_instance.app.password == null && aws_db_instance.app.password_wo_version == null
    error_message = "Import must not rotate owner credentials."
  }
  assert {
    condition     = aws_db_instance.app.deletion_protection && !aws_db_instance.app.skip_final_snapshot && !aws_db_instance.app.delete_automated_backups
    error_message = "Enable safe deletion retention."
  }
}

run "production_rejects_m7g_sizes" {
  command = plan
  variables {
    db_instance_class = "db.m7g.large"
  }
  expect_failures = [var.db_instance_class]
}

run "domains_point_at_the_alb" {
  command = plan

  assert {
    condition     = toset(keys(aws_route53_record.public)) == toset(["meterengine.com", "www.meterengine.com", "grafana.meterengine.com"])
    error_message = "The apex, www and grafana records are imported and managed here."
  }
  assert {
    condition = alltrue([
      for record in values(aws_route53_record.public) :
      one(record.alias).name == "meterengine-production-alb-1.ap-northeast-2.elb.amazonaws.com" && one(record.alias).zone_id == "ZWKZPGTI48KDX" && !one(record.alias).evaluate_target_health && record.ttl == null && record.records == null
    ])
    error_message = "Every production domain is an alias of the production ALB."
  }
}

run "guide_domain_points_at_mintlify" {
  command = plan

  assert {
    condition     = aws_route53_record.guide.name == "docs.meterengine.com" && aws_route53_record.guide.type == "CNAME" && aws_route53_record.guide.records == toset(["cname.mintlify.builders"])
    error_message = "The guide domain is a CNAME to Mintlify."
  }
  assert {
    condition     = toset(keys(aws_route53_record.guide_verification)) == toset(["_acme-challenge.docs.meterengine.com", "_cf-custom-hostname.docs.meterengine.com"]) && alltrue([for record in values(aws_route53_record.guide_verification) : record.type == "TXT"])
    error_message = "Mintlify needs both verification TXT records for the guide domain."
  }
}

run "alb_domains_need_a_server" {
  command = plan
  override_module {
    target = module.app_fleet
    outputs = {
      min_size                   = 0
      alb_dns_name               = "meterengine-production-alb-1.ap-northeast-2.elb.amazonaws.com"
      alb_zone_id                = "ZWKZPGTI48KDX"
      autoscaling_group_name     = "meterengine-production-app"
      autoscaling_group_arn      = "arn:aws:autoscaling:ap-northeast-2:928618307647:autoScalingGroup:00000000-0000-0000-0000-000000000000:autoScalingGroupName/meterengine-production-app"
      app_version_parameter_name = "/meterengine/production/app-version"
      https_listener_arn         = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:listener/app/meterengine-production-alb/0000000000000000/0000000000000000"
      alb_security_group_id      = "sg-44444444444444444"
    }
  }
  expect_failures = [aws_route53_record.public]
}

run "auto_finalize_switch_is_coded" {
  command = plan
  assert {
    condition     = contains(keys(aws_ssm_parameter.coded), "invoice-auto-finalize-enabled")
    error_message = "The auto-finalize switch must stay in code."
  }
}
