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
  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:ap-northeast-2:928618307647:certificate/00000000-0000-0000-0000-000000000000"
      domain_validation_options = [{
        domain_name           = "staging.meterengine.com"
        resource_record_name  = "_test.staging.meterengine.com."
        resource_record_type  = "CNAME"
        resource_record_value = "_test.acm-validations.aws."
      }]
    }
  }
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:loadbalancer/app/meterengine-staging-alb/0000000000000000" }
  }
  mock_resource "aws_lb_listener" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:listener/app/meterengine-staging-alb/0000000000000000/0000000000000000" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:targetgroup/test/0000000000000000" }
  }
  mock_resource "aws_launch_template" {
    defaults = { id = "lt-00000000000000000", latest_version = 1 }
  }
  mock_resource "aws_autoscaling_group" {
    defaults = { arn = "arn:aws:autoscaling:ap-northeast-2:928618307647:autoScalingGroup:00000000-0000-0000-0000-000000000000:autoScalingGroupName/meterengine-staging-app" }
  }
}


variables {
  db_password_wo      = "D2_TEST_ONLY_OWNER_NOT_A_REAL_SECRET"
  db_password_version = 1
  runtime_secrets = {
    crypto-encryption-key = "D2_TEST_ONLY_CRYPTO_NOT_A_REAL_SECRET"
    operator-password     = "D2_TEST_ONLY_OPERATOR_NOT_A_REAL_SECRET"
  }
  runtime_secret_versions = {
    crypto-encryption-key = 1, operator-password = 1
  }
  operator_email = "d2-fixture@example.invalid"
}

# 가짜 공급자가 받지 않는 import의 결과를 정해 준다.
override_resource {
  target = aws_ssm_parameter.runtime
  values = {}
}

run "without_kafka_one_group_does_everything_and_nothing_bills" {
  command = plan

  assert {
    condition     = length(module.kafka) == 0
    error_message = "MSK must not exist unless kafka_enabled is true. It cannot be stopped and bills while it exists."
  }

  assert {
    condition     = length(module.app_fleet.kafka_autoscaling_group_names) == 0
    error_message = "Ingest and consumer server groups must not exist without a cluster."
  }

  assert {
    condition     = length([for tag in module.app_fleet.app_group_tags : tag if tag.key == "ServerRole"]) == 0
    error_message = "Without Kafka the app group must carry no ServerRole tag. Adding one replaces every running server."
  }

  assert {
    condition     = length(local.github_deploy_kafka_statements) == 0
    error_message = "Without Kafka the deploy role policy must stay exactly as it was."
  }

  assert {
    condition     = length(module.app_fleet.kafka_parameter_names) == 0
    error_message = "Without Kafka no Kafka parameter may exist, or servers would switch to the Kafka write path."
  }
}

run "kafka_splits_servers_into_three_groups" {
  command = plan

  variables {
    kafka_enabled = true
  }

  assert {
    condition     = module.app_fleet.kafka_autoscaling_group_names == ["meterengine-staging-consumer", "meterengine-staging-ingest"]
    error_message = "Kafka must add a consumer group and an ingest group. Deployment rolls them in this order."
  }

  assert {
    condition     = one([for tag in module.app_fleet.app_group_tags : tag.value if tag.key == "ServerRole"]) == "api"
    error_message = "With Kafka the original group must take the api role so scheduled jobs keep one home."
  }

  assert {
    condition     = module.app_fleet.kafka_group_roles == { "meterengine-staging-consumer" = "consumer", "meterengine-staging-ingest" = "ingest" }
    error_message = "Each new group must carry its own role tag, or its servers come up doing everything."
  }

  assert {
    condition     = length(local.github_deploy_kafka_statements) == 1 && toset(local.github_deploy_kafka_statements[0].Action) == toset(["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"])
    error_message = "The deploy role must be able to refresh the consumer and ingest groups, or deployment stops at the first of them."
  }

  assert {
    condition     = module.app_fleet.ingest_rule_priority == 45
    error_message = "The ingest rule must sit before the /v1/* rule at 50, or no ingest request reaches the ingest group."
  }

  assert {
    condition     = contains(module.app_fleet.app_group_refresh_triggers, "tag")
    error_message = "A role tag change must replace running servers, or old servers keep the old role."
  }

  assert {
    condition     = module.app_fleet.kafka_parameters["event-write-path"] == "kafka"
    error_message = "Servers must read the Kafka write path once the cluster exists."
  }

  assert {
    condition     = module.app_fleet.kafka_parameters["kafka-consumer-threads"] == "6"
    error_message = "One staging consumer server must run six threads so each of the six partitions gets its own thread."
  }

  assert {
    condition     = module.app_fleet.ingest_rule_methods == toset(["POST"]) && module.app_fleet.ingest_rule_paths == toset(["/v1/events", "/v1/event-batches"])
    error_message = "Only event ingestion (POST on the two ingest paths) may go to the ingest group. Reads stay on the api group."
  }
}

run "brokers_match_the_measured_cluster" {
  command = plan

  variables {
    kafka_enabled = true
  }

  assert {
    condition     = module.kafka[0].broker_count == 3 && module.kafka[0].broker_instance_type == "kafka.t3.small"
    error_message = "Staging runs three kafka.t3.small brokers."
  }

  assert {
    condition     = module.kafka[0].client_ports == { ingress = 9094, egress = 9094 }
    error_message = "Servers leave only on listed ports. Brokers must accept 9094 and servers must be allowed out on 9094."
  }

  assert {
    condition     = module.kafka[0].client_broker_encryption == "TLS"
    error_message = "Brokers must accept TLS only."
  }

  assert {
    condition     = strcontains(module.kafka[0].server_properties, "min.insync.replicas=2\n") && strcontains(module.kafka[0].server_properties, "default.replication.factor=3\n") && strcontains(module.kafka[0].server_properties, "unclean.leader.election.enable=false") && strcontains(module.kafka[0].server_properties, "auto.create.topics.enable=false")
    error_message = "Broker settings must match the measured cluster: two in-sync replicas, no unclean leader election, no topic auto creation."
  }
}
