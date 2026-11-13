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

run "without_kafka_one_group_does_everything_and_nothing_bills" {
  command = plan

  override_resource {
    target = module.service.aws_db_instance.app
    values = { id = "db-72GJNZYUHSQRXAKNQGC7ECV7UI" }
  }

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
    condition     = length(module.app_fleet.kafka_parameter_names) == 0
    error_message = "Without Kafka no Kafka parameter may exist, or servers would switch to the Kafka write path."
  }
}

run "kafka_splits_servers_into_three_groups" {
  command = plan

  override_resource {
    target = module.service.aws_db_instance.app
    values = { id = "db-72GJNZYUHSQRXAKNQGC7ECV7UI" }
  }

  variables {
    kafka_enabled = true
  }

  assert {
    condition     = module.app_fleet.kafka_autoscaling_group_names == ["meterengine-production-consumer", "meterengine-production-ingest"]
    error_message = "Kafka must add a consumer group and an ingest group. Deployment rolls them in this order."
  }

  assert {
    condition     = one([for tag in module.app_fleet.app_group_tags : tag.value if tag.key == "ServerRole"]) == "api"
    error_message = "With Kafka the original group must take the api role so scheduled jobs keep one home."
  }

  assert {
    condition     = module.app_fleet.kafka_group_roles == { "meterengine-production-consumer" = "consumer", "meterengine-production-ingest" = "ingest" }
    error_message = "Each new group must carry its own role tag, or its servers come up doing everything."
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
    condition     = module.app_fleet.kafka_parameters["kafka-consumer-threads"] == "3"
    error_message = "Two production consumer servers must run three threads each so each of the six partitions gets its own thread."
  }

  assert {
    condition     = module.app_fleet.ingest_rule_methods == toset(["POST"]) && module.app_fleet.ingest_rule_paths == toset(["/v1/events", "/v1/event-batches"])
    error_message = "Only event ingestion (POST on the two ingest paths) may go to the ingest group. Reads stay on the api group."
  }
}

run "brokers_match_the_measured_cluster" {
  command = plan

  override_resource {
    target = module.service.aws_db_instance.app
    values = { id = "db-72GJNZYUHSQRXAKNQGC7ECV7UI" }
  }

  variables {
    kafka_enabled = true
  }

  assert {
    condition     = module.kafka[0].broker_count == 3 && module.kafka[0].broker_instance_type == "kafka.t3.small"
    error_message = "Production runs three kafka.t3.small brokers."
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
