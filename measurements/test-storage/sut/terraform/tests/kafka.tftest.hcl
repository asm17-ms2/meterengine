# 정책 문서는 실제 JSON이어야 역할과 정책의 plan 검증을 지난다.
mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
}

mock_provider "random" {}

# 인증서 검증 레코드의 for_each 키가 plan에서 정해져야 한다.
override_resource {
  target          = aws_acm_certificate.sut
  override_during = plan
  values = {
    arn = "arn:aws:acm:ap-northeast-2:928618307647:certificate/00000000-0000-0000-0000-000000000000"
    domain_validation_options = [{
      domain_name           = "test-storage.meterengine.com"
      resource_record_name  = "_test.test-storage.meterengine.com."
      resource_record_type  = "CNAME"
      resource_record_value = "_test.acm-validations.aws."
    }]
  }
}

run "without_kafka_nothing_bills_and_servers_keep_every_role" {
  command = plan

  assert {
    condition     = length(aws_msk_cluster.events) == 0
    error_message = "MSK must not exist unless kafka_enabled is true. It cannot be stopped and bills while it exists."
  }

  assert {
    condition     = length(aws_ssm_parameter.kafka) == 0
    error_message = "Without the kafka parameters the backend keeps the direct write path."
  }

  assert {
    condition     = one([for tag in aws_autoscaling_group.sut.tag : tag.value if tag.key == "ServerRole"]) == "all"
    error_message = "Without kafka the app servers must run every role, scheduled jobs included."
  }

  assert {
    condition     = aws_autoscaling_group.consumer.min_size == 0
    error_message = "Consumer servers must stay at zero by default."
  }
}

run "kafka_uses_three_brokers_reached_over_tls_only" {
  command = plan

  variables {
    kafka_enabled = true
  }

  assert {
    condition     = aws_msk_cluster.events[0].number_of_broker_nodes == 3 && aws_subnet.private_2c.availability_zone == "ap-northeast-2c"
    error_message = "Three brokers need a third availability zone. The VPC had only 2a and 2b."
  }

  assert {
    condition     = aws_msk_cluster.events[0].encryption_info[0].encryption_in_transit[0].client_broker == "TLS"
    error_message = "Client traffic must be TLS only. Plaintext must stay closed."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.kafka_from_sut[0].from_port == 9094 && aws_vpc_security_group_ingress_rule.kafka_from_sut[0].cidr_ipv4 == null
    error_message = "Brokers do not authenticate, so only the app servers' security group may reach the TLS port."
  }

  assert {
    condition     = aws_ssm_parameter.kafka["event-write-path"].value == "kafka" && aws_ssm_parameter.kafka["kafka-security-protocol"].value == "SSL"
    error_message = "Servers must read the kafka write path and the TLS protocol from Parameter Store."
  }

  assert {
    condition     = aws_ssm_parameter.kafka["kafka-create-topics"].value == "true"
    error_message = "The backend creates the topics on boot. Nothing else creates them."
  }

  assert {
    condition     = one([for tag in aws_autoscaling_group.sut.tag : tag.value if tag.key == "ServerRole"]) == "ingest"
    error_message = "With kafka the app servers only ingest. Consumer servers store."
  }

  assert {
    condition     = one([for tag in aws_autoscaling_group.consumer.tag : tag.value if tag.key == "ServerRole"]) == "consumer"
    error_message = "Consumer servers must carry the consumer role tag that deploy.sh reads."
  }

  assert {
    condition     = aws_autoscaling_group.consumer.health_check_type == "EC2"
    error_message = "Consumer servers take no requests, so the load balancer cannot judge their health."
  }
}

run "consumers_need_a_cluster" {
  command = plan

  variables {
    consumer_count = 1
  }

  expect_failures = [aws_autoscaling_group.consumer]
}

run "consumer_threads_cover_every_partition_once" {
  command = plan

  variables {
    kafka_enabled  = true
    consumer_count = 2
  }

  assert {
    condition     = aws_ssm_parameter.kafka["kafka-consumer-threads"].value == "3"
    error_message = "Two consumer servers must run three threads each so that each of the six partitions gets its own thread."
  }
}

run "broker_logs_are_kept_for_the_experiment" {
  command = plan

  variables {
    kafka_enabled = true
  }

  assert {
    condition     = aws_msk_cluster.events[0].logging_info[0].broker_logs[0].cloudwatch_logs[0].enabled && aws_cloudwatch_log_group.kafka_broker[0].retention_in_days == 7
    error_message = "Leader elections during a broker reboot are visible only in broker logs."
  }
}
