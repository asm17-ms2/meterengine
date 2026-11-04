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
  target = module.service.aws_instance.app[0]
  values = { id = "i-0f47bb1f028cd29a9" }
}
override_resource {
  target = module.service.aws_db_instance.app
  values = { id = "db-72GJNZYUHSQRXAKNQGC7ECV7UI" }
}

run "production_preserves_identity_and_requests_resize" {
  command = plan
  override_resource {
    target = module.service.aws_instance.app[0]
    values = { id = "i-0f47bb1f028cd29a9" }
  }
  override_resource {
    target = module.service.aws_db_instance.app
    values = {
      id      = "db-72GJNZYUHSQRXAKNQGC7ECV7UI"
      address = "meterengine-db.example.invalid"
    }
  }
  assert {
    condition     = module.service.instance_id != "" && output.deployment_target.deployment_environment == "production" && output.deployment_target.parameter_path == "/meterengine/production"
    error_message = "Production must preserve its deployment identity."
  }
}

run "import_service_configuration" {
  command = plan
  module { source = "../modules/service" }
  variables {
    name                       = "meterengine-server"
    ami_id                     = "ami-0f4109eb491b0476f"
    instance_type              = "t4g.medium"
    subnet_id                  = "subnet-0348a18c1d399c193"
    ec2_security_group_ids     = ["sg-0c2691d037bcdd8b1"]
    iam_instance_profile_name  = "meterengine-ec2-role"
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
    root_block_device = {
      volume_size = 20, volume_type = "gp3", iops = 3000, throughput = 125,
      encrypted   = false, delete_on_termination = true
    }
  }
  assert {
    condition     = aws_instance.app[0].instance_type == "t4g.medium" && aws_db_instance.app.instance_class == "db.t4g.small" && aws_db_instance.app.apply_immediately && aws_db_instance.app.db_name == "meterengine" && aws_db_instance.app.username == "meterengine"
    error_message = "Resize must preserve the original database and owner."
  }
  assert {
    condition     = aws_db_instance.app.password == null && aws_db_instance.app.password_wo_version == null && aws_instance.app[0].user_data == null
    error_message = "Import must not rotate owner credentials or replace bootstrap data."
  }
  assert {
    condition     = !aws_instance.app[0].root_block_device[0].encrypted && aws_instance.app[0].root_block_device[0].volume_size == 20 && aws_db_instance.app.deletion_protection && !aws_db_instance.app.skip_final_snapshot && !aws_db_instance.app.delete_automated_backups
    error_message = "Preserve the volume and enable safe deletion retention."
  }
}

run "old_server_can_be_switched_off" {
  command = plan
  module { source = "../modules/service" }
  variables {
    create_instance            = false
    name                       = "meterengine-server"
    ami_id                     = "ami-0f4109eb491b0476f"
    instance_type              = "t4g.medium"
    subnet_id                  = "subnet-0348a18c1d399c193"
    ec2_security_group_ids     = ["sg-0c2691d037bcdd8b1"]
    iam_instance_profile_name  = "meterengine-ec2-role"
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
    condition     = length(aws_instance.app) == 0 && output.instance_id == null && aws_db_instance.app.identifier == "meterengine-db"
    error_message = "Switching off the old server must keep the database."
  }
}

run "production_rejects_m7g_sizes" {
  command = plan
  variables {
    instance_type     = "m7g.large"
    db_instance_class = "db.m7g.large"
  }
  expect_failures = [var.instance_type, var.db_instance_class]
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

run "turning_on_keeps_the_domains_on_the_old_server" {
  command = plan
  variables {
    serve_domain_from_alb = false
  }

  assert {
    condition = toset(keys(aws_route53_record.public)) == toset(["meterengine.com", "www.meterengine.com", "grafana.meterengine.com"]) && alltrue([
      for record in values(aws_route53_record.public) : length(record.alias) == 0 && record.ttl == 60 && record.records == toset(["43.203.50.31"])
    ])
    error_message = "Without the switch the same three records stay A records of the old server, so neither direction deletes one."
  }
}

run "rollback_allows_an_empty_fleet" {
  command = plan
  variables {
    serve_domain_from_alb = false
  }
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

  assert {
    condition     = alltrue([for record in values(aws_route53_record.public) : length(record.alias) == 0])
    error_message = "Once the domains are back on the old server the fleet can drop to zero."
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
