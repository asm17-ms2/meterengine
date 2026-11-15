# 수집 서버가 발행하고 컨슈머 서버가 읽는 MSK 클러스터. 설정값은 측정 환경에서 잰 것과 같다(Discussion #601).

locals {
  # TLS로 붙는 포트. 인증은 하지 않는다.
  tls_port = 9094
  # 브로커가 Prometheus에 내는 JMX 지표와 서버 지표의 포트
  exporter_ports = {
    jmx  = 11001
    node = 11002
  }
}

resource "aws_security_group" "kafka" {
  name        = "${var.name}-kafka"
  description = "MSK brokers: Kafka from app servers"
  vpc_id      = var.vpc_id
  tags        = merge(var.tags, { Name = "${var.name}-kafka" })
}

# 인증이 없어 이 규칙이 브로커에 붙을 수 있는 쪽을 정하는 유일한 장치다.
resource "aws_vpc_security_group_ingress_rule" "kafka_from_clients" {
  security_group_id            = aws_security_group.kafka.id
  referenced_security_group_id = var.client_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = local.tls_port
  to_port                      = local.tls_port
}

# 서버 보안 그룹은 적힌 포트로만 나간다. 들어오는 규칙만 있으면 서버가 브로커에 닿지 못한다.
resource "aws_vpc_security_group_egress_rule" "clients_to_kafka" {
  security_group_id            = var.client_security_group_id
  referenced_security_group_id = aws_security_group.kafka.id
  ip_protocol                  = "tcp"
  from_port                    = local.tls_port
  to_port                      = local.tls_port
}

resource "aws_vpc_security_group_ingress_rule" "exporters_from_monitoring" {
  for_each = local.exporter_ports

  security_group_id            = aws_security_group.kafka.id
  referenced_security_group_id = var.monitoring_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}

resource "aws_vpc_security_group_egress_rule" "monitoring_to_exporters" {
  for_each = local.exporter_ports

  security_group_id            = var.monitoring_security_group_id
  referenced_security_group_id = aws_security_group.kafka.id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}

# 토픽을 backend가 정한 값으로만 만들게 자동 생성을 끈다.
# unclean.leader.election.enable은 MSK 기본값이 true라 꼭 적는다. 따라오지 못한 복제본이 리더가 되면 메시지를 잃는다.
resource "aws_msk_configuration" "events" {
  name           = "${var.name}-events"
  kafka_versions = [var.kafka_version]

  server_properties = <<-PROPERTIES
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    unclean.leader.election.enable=false
    num.partitions=6
  PROPERTIES
}

resource "aws_cloudwatch_log_group" "broker" {
  name              = "/meterengine/${var.name}/kafka-broker"
  retention_in_days = 30
  tags              = var.tags
}

resource "aws_msk_cluster" "events" {
  cluster_name           = "${var.name}-events"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.broker_subnet_ids
    security_groups = [aws_security_group.kafka.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.broker_volume_size
      }
    }
  }

  # 조직 정책이 IAM 인증에 쓰는 kafka-cluster 권한을 막아 인증 없이 받는다.
  # 붙을 수 있는 쪽은 보안 그룹이 가르고, 오가는 길은 TLS로만 받는다.
  client_authentication {
    unauthenticated = true
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.events.arn
    revision = aws_msk_configuration.events.latest_revision
  }

  # 브로커가 내려갔을 때 리더를 언제 다시 뽑았는지는 브로커 로그에만 남는다.
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.broker.name
      }
    }
  }

  open_monitoring {
    prometheus {
      jmx_exporter {
        enabled_in_broker = true
      }
      node_exporter {
        enabled_in_broker = true
      }
    }
  }

  tags = var.tags
}
