variables {
  environment         = "test-sjpark-m7g"
  instance_type       = "m7g.large"
  db_instance_class   = "db.m7g.large"
  snapshot_identifier = "meterengine-production-test-snapshot"
}

mock_provider "aws" {}

mock_provider "random" {}

# 인증서 검증 레코드가 읽는 검증 값이 plan에서 정해져야 한다.
override_resource {
  target          = aws_acm_certificate.site
  override_during = plan
  values = {
    arn = "arn:aws:acm:ap-northeast-2:928618307647:certificate/00000000-0000-0000-0000-000000000000"
    domain_validation_options = [{
      domain_name           = "test-sjpark-m7g.meterengine.com"
      resource_record_name  = "_test.test-sjpark-m7g.meterengine.com."
      resource_record_type  = "CNAME"
      resource_record_value = "_test.acm-validations.aws."
    }]
  }
}

run "boots_with_the_production_template" {
  command = plan

  assert {
    condition     = strcontains(base64decode(aws_launch_template.app.user_data), "./deploy/deploy.sh 'test-sjpark-m7g' \"$version\"")
    error_message = "The boot script must come from the production template and deploy this environment."
  }
  assert {
    condition     = aws_launch_template.app.image_id == "ami-02b68e7eb6b798d63"
    error_message = "Servers must boot from the same baked image as production."
  }
}

run "servers_run_in_2a_only" {
  command = plan

  assert {
    condition     = aws_autoscaling_group.app.vpc_zone_identifier == toset([data.aws_subnet.public_2a.id])
    error_message = "Servers must stay in the database AZ."
  }
}

run "webhook_off_and_auto_finalize_on" {
  command = plan

  assert {
    condition     = aws_ssm_parameter.plain["webhook-enabled"].value == "false"
    error_message = "Measurement must not send webhooks to the snapshot's real endpoints."
  }
  assert {
    condition     = aws_ssm_parameter.plain["invoice-auto-finalize-enabled"].value == "true"
    error_message = "Auto-finalize must match production."
  }
}

run "db_matches_production_storage" {
  command = plan

  assert {
    condition = (aws_db_instance.sut.snapshot_identifier == var.snapshot_identifier
      && aws_db_instance.sut.storage_type == "gp3" && aws_db_instance.sut.database_insights_mode == "standard"
      && contains(aws_db_instance.sut.enabled_cloudwatch_logs_exports, "postgresql")
      && aws_db_instance.sut.max_allocated_storage == 50
      && aws_db_instance.sut.engine_version == "18.4" && aws_db_instance.sut.backup_retention_period == 7
    && !aws_db_instance.sut.multi_az && aws_db_instance.sut.maintenance_window == "mon:16:00-mon:16:30")
    error_message = "The database must restore the snapshot with production storage settings."
  }
}

run "max_below_min_is_rejected" {
  command = plan

  variables {
    asg_min_size = 1
  }
  expect_failures = [var.asg_max_size]
}

run "max_above_three_is_rejected" {
  command = plan

  variables {
    asg_max_size = 4
  }
  expect_failures = [var.asg_max_size]
}

run "auto_stop_returns_to_defaults" {
  command = plan

  assert {
    condition = (aws_autoscaling_schedule.stop_asg[0].min_size == 0
      && aws_autoscaling_schedule.stop_asg[0].max_size == 0
    && aws_autoscaling_schedule.stop_asg[0].desired_capacity == 0)
    error_message = "The 04:00 schedule must leave the group at the variable defaults so the next plan shows no change."
  }
}

# 정책이 ASG ARN을 담아 plan에서 값이 정해지도록 ARN을 정한다.
run "deploy_role_reaches_only_this_environment" {
  command = plan

  override_resource {
    target          = aws_autoscaling_group.app
    override_during = plan
    values = {
      arn = "arn:aws:autoscaling:ap-northeast-2:928618307647:autoScalingGroup:00000000-0000-0000-0000-000000000000:autoScalingGroupName/meterengine-test-sjpark-m7g-app"
    }
  }

  assert {
    condition = (jsondecode(aws_iam_role.deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"]
    == "repo:asm17-ms2@293532363/meterengine@1313421667:environment:test-sjpark-m7g")
    error_message = "Only jobs in this environment's GitHub Environment may assume the deploy role."
  }
  assert {
    condition = alltrue([for statement in jsondecode(aws_iam_role_policy.deploy.policy).Statement :
      statement.Effect == "Deny" || statement.Resource == "*" || alltrue([for arn in flatten([statement.Resource]) :
        strcontains(arn, "meterengine-test-sjpark-m7g-app") || strcontains(arn, "repository/meterengine-test-sjpark-")
    || endswith(arn, "parameter/meterengine/test-sjpark-m7g/app-version")])])
    error_message = "The deploy role may only write this environment's group, version parameter and the measurement repositories."
  }
}

run "servers_pull_from_the_measurement_repositories" {
  command = plan

  assert {
    condition = anytrue([for statement in jsondecode(aws_iam_role_policy.app.policy).Statement :
      contains(statement.Action, "ecr:BatchGetImage") && alltrue([for arn in statement.Resource : strcontains(arn, "repository/meterengine-test-sjpark-")])
    ])
    error_message = "Servers must pull images from the measurement repositories only."
  }
}
