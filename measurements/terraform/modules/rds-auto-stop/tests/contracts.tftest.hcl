mock_provider "aws" {
  override_during = plan
}

variables {
  name_prefix   = "meterengine-test-slo"
  account_id    = "928618307647"
  region        = "ap-northeast-2"
  db_identifier = "meterengine-test-slo-db"
  db_arn        = "arn:aws:rds:ap-northeast-2:928618307647:db:meterengine-test-slo-db"
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
    condition     = toset(flatten(regexall("\\$config\\.([a-z_]+)", output.workflow_definition))) == toset(keys(jsondecode(output.workflow_definition).States.Configure.Assign.config))
    error_message = "Every $config key the workflow reads must be assigned by Terraform, and every assigned key must be read."
  }

  assert {
    condition     = jsondecode(output.workflow_definition).States.Configure.Assign.config.claim_table == aws_dynamodb_table.claims.name
    error_message = "The workflow must claim through this module's table."
  }

  assert {
    condition     = local.workflow_policy.Statement[0].Resource == var.db_arn
    error_message = "StopDBInstance must be scoped to one database."
  }

  assert {
    condition     = alltrue(flatten([for statement in local.workflow_policy.Statement : [for action in statement.Action : !startswith(action, "scheduler:") && action != "iam:PassRole"]]))
    error_message = "The workflow must not change any schedule or pass a role."
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

run "name_prefix_fits_iam_role_names" {
  command = plan

  variables {
    name_prefix = "a23456789-123456789-123456789-1234567890b"
  }
}

run "name_prefix_too_long_for_iam_role_names" {
  command = plan

  variables {
    name_prefix = "a23456789-123456789-123456789-1234567890bc"
  }

  expect_failures = [var.name_prefix]
}
