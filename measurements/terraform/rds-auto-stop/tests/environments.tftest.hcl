mock_provider "aws" {
  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "928618307647" }
  }

  override_data {
    target = data.aws_region.current
    values = { region = "ap-northeast-2" }
  }

  override_data {
    target = data.aws_db_instance.sut
    values = {
      db_instance_identifier = "meterengine-test-db"
      db_instance_arn        = "arn:aws:rds:ap-northeast-2:928618307647:db:meterengine-test-db"
    }
  }
}

run "both_environments_by_default" {
  command = plan

  assert {
    condition     = toset(keys(module.rds_auto_stop)) == toset(["test-slo", "test-storage"])
    error_message = "Both test environments must be detected by default."
  }

  assert {
    condition     = toset(keys(output.dead_letter_queue_urls)) == toset(["test-slo", "test-storage"])
    error_message = "Operators need each environment's dead-letter queue URL to handle delivery alarms."
  }
}

run "one_environment_can_be_removed_before_its_sut" {
  command = plan

  variables {
    environments = ["test-storage"]
    enabled      = { test-storage = true }
  }

  assert {
    condition     = toset(keys(module.rds_auto_stop)) == toset(["test-storage"])
    error_message = "Removing an environment must drop only its detector."
  }
}

run "unknown_environment_is_rejected" {
  command = plan

  variables {
    environments = ["test-slo", "prod"]
  }

  expect_failures = [var.environments]
}
