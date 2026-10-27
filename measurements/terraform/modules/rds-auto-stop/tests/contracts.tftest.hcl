mock_provider "aws" {
  override_during = plan
}

variables {
  name_prefix               = "meterengine-test-slo"
  account_id                = "928618307647"
  region                    = "ap-northeast-2"
  db_identifier             = "meterengine-test-slo-db"
  db_arn                    = "arn:aws:rds:ap-northeast-2:928618307647:db:meterengine-test-slo-db"
  legacy_schedule_name      = "meterengine-test-slo-stop-rds"
  legacy_scheduler_role_arn = "arn:aws:iam::928618307647:role/meterengine-test-slo-scheduler-role"
}

run "scoped_automatic_restart" {
  command = plan

  assert {
    condition     = jsondecode(aws_cloudwatch_event_rule.automatic_restart.event_pattern).detail.EventID == ["RDS-EVENT-0154"]
    error_message = "Only the automatic-start event may match."
  }

  assert {
    condition     = jsondecode(aws_cloudwatch_event_rule.automatic_restart.event_pattern).resources == [var.db_arn]
    error_message = "The rule must match only its own database ARN."
  }

  assert {
    condition     = jsondecode(aws_sfn_state_machine.automatic_restart.definition).States.Configure.Assign.config.db_arn == var.db_arn
    error_message = "Workflow configuration must be fixed by Terraform."
  }

  assert {
    condition     = local.workflow_policy.Statement[0].Resource == var.db_arn
    error_message = "StopDBInstance must be scoped to one database."
  }

  assert {
    condition     = local.workflow_policy.Statement[3].Resource == "arn:aws:scheduler:ap-northeast-2:928618307647:schedule/default/meterengine-test-slo-stop-rds"
    error_message = "Only the old RDS schedule may be changed."
  }

  assert {
    condition     = aws_dynamodb_table.claims.deletion_protection_enabled && aws_dynamodb_table.claims.ttl[0].enabled
    error_message = "Persistent claims must survive routine removal and expire through TTL."
  }

  assert {
    condition     = aws_cloudwatch_event_target.workflow.retry_policy[0].maximum_event_age_in_seconds == 900
    error_message = "Delivery retries must not introduce events older than 15 minutes."
  }

  assert {
    condition     = aws_sfn_state_machine.automatic_restart.type == "STANDARD" && jsondecode(aws_sfn_state_machine.automatic_restart.definition).TimeoutSeconds == 7200
    error_message = "The bounded workflow must retain Standard execution history."
  }
}

run "disabled_detector" {
  command = plan

  variables {
    enabled = false
  }

  assert {
    condition     = aws_cloudwatch_event_rule.automatic_restart.state == "DISABLED"
    error_message = "The detector must support an explicit operational disable."
  }
}
