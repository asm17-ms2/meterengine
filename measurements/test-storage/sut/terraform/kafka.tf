# 수집 서버가 발행하고 컨슈머 서버가 읽는 MSK 클러스터. var.kafka_enabled로 실험하는 날만 만든다.
# 토픽(event.ingest, event.dlq)은 backend가 뜰 때 만든다(파라미터 kafka-create-topics).

locals {
  # TLS로 붙는 포트. 인증은 하지 않는다.
  kafka_tls_port = 9094
  # backend가 만드는 event.ingest 토픽의 파티션 수
  kafka_ingest_partitions = 6
  # 브로커가 Prometheus에 내는 JMX 지표와 서버 지표의 포트
  kafka_exporter_ports = {
    jmx  = 11001
    node = 11002
  }
}

# --- 네트워크 ---

data "aws_subnet" "private_2a" {
  vpc_id = data.aws_vpc.main.id

  filter {
    name   = "tag:Name"
    values = ["meterengine-subnet-private1-ap-northeast-2a"]
  }
}

data "aws_subnet" "private_2b" {
  vpc_id = data.aws_vpc.main.id

  filter {
    name   = "tag:Name"
    values = ["meterengine-subnet-private2-ap-northeast-2b"]
  }
}

# 브로커 3대를 가용 영역 세 곳에 하나씩 두려면 2c가 필요한데 VPC에 2a와 2b뿐이었다.
# 라우팅 테이블을 잇지 않아 VPC 안에서만 통한다. 브로커는 밖으로 나갈 일이 없다.
resource "aws_subnet" "private_2c" {
  vpc_id            = data.aws_vpc.main.id
  cidr_block        = "10.0.160.0/20"
  availability_zone = "ap-northeast-2c"

  tags = {
    Name = "meterengine-subnet-private3-ap-northeast-2c"
  }
}

resource "aws_security_group" "kafka" {
  count = var.kafka_enabled ? 1 : 0

  name        = "${local.prefix}-kafka-sg"
  description = "SUT MSK: Kafka from SUT EC2"
  vpc_id      = data.aws_vpc.main.id

  tags = {
    Name = "${local.prefix}-kafka-sg"
  }
}

# 인증이 없어 이 규칙이 브로커에 붙을 수 있는 쪽을 정하는 유일한 장치다. 수집 서버와 컨슈머 서버만 들인다.
resource "aws_vpc_security_group_ingress_rule" "kafka_from_sut" {
  count = var.kafka_enabled ? 1 : 0

  security_group_id            = aws_security_group.kafka[0].id
  referenced_security_group_id = aws_security_group.sut.id
  ip_protocol                  = "tcp"
  from_port                    = local.kafka_tls_port
  to_port                      = local.kafka_tls_port
}

resource "aws_vpc_security_group_ingress_rule" "kafka_exporters_from_shared_monitoring" {
  for_each = var.kafka_enabled ? local.kafka_exporter_ports : {}

  security_group_id            = aws_security_group.kafka[0].id
  referenced_security_group_id = data.aws_security_group.shared_monitoring.id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}

# 공용 모니터링 보안 그룹은 적힌 포트로만 나간다. 들어오는 규칙만 있으면 긁기가 시간 초과로 끝난다.
resource "aws_vpc_security_group_egress_rule" "shared_monitoring_to_kafka_exporters" {
  for_each = var.kafka_enabled ? local.kafka_exporter_ports : {}

  security_group_id            = data.aws_security_group.shared_monitoring.id
  referenced_security_group_id = aws_security_group.kafka[0].id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}

# --- 클러스터 ---

# 토픽을 backend가 정한 값으로만 만들게 자동 생성을 끈다. 나머지는 MS2-568에서 정한 event.ingest의 값과 같다.
# unclean.leader.election.enable은 MSK 기본값이 true라 꼭 적는다. 따라오지 못한 복제본이 리더가 되면 메시지를 잃는다.
# 리더를 다시 고르게 펴는 auto.leader.rebalance.enable은 MSK 기본값이 true이고 여기서 정할 수 있는 속성이 아니라 적지 않는다.
resource "aws_msk_configuration" "events" {
  count = var.kafka_enabled ? 1 : 0

  name           = "${local.prefix}-events"
  kafka_versions = [var.kafka_version]

  server_properties = <<-PROPERTIES
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    unclean.leader.election.enable=false
    num.partitions=6
  PROPERTIES
}

# 실험한 날의 로그만 보면 되므로 짧게 두고 지운다. 클러스터를 지우면 같이 지워진다.
resource "aws_cloudwatch_log_group" "kafka_broker" {
  count = var.kafka_enabled ? 1 : 0

  name              = "/meterengine/${var.sut_name}/kafka-broker"
  retention_in_days = 7
}

resource "aws_msk_cluster" "events" {
  count = var.kafka_enabled ? 1 : 0

  cluster_name           = "${local.prefix}-events"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = var.kafka_broker_instance_type
    client_subnets  = [data.aws_subnet.private_2a.id, data.aws_subnet.private_2b.id, aws_subnet.private_2c.id]
    security_groups = [aws_security_group.kafka[0].id]

    storage_info {
      ebs_storage_info {
        volume_size = var.kafka_broker_volume_size
      }
    }
  }

  # 조직 정책이 IAM 인증에 쓰는 kafka-cluster 권한을 막아 인증 없이 받는다(staging, production도 같은 계정이라 같다).
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
    arn      = aws_msk_configuration.events[0].arn
    revision = aws_msk_configuration.events[0].latest_revision
  }

  # 브로커를 재부팅했을 때 리더를 언제 다시 뽑았는지는 브로커 로그에만 남는다.
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.kafka_broker[0].name
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
}

# --- 서버가 읽는 값 ---

# deploy.sh가 대문자 환경변수로 바꿔 backend에 넘긴다. 없으면 backend는 요청 안에서 DB에 저장한다(direct).
# 떠 있는 서버는 다시 배포해야 이 값을 읽는다.
resource "aws_ssm_parameter" "kafka" {
  for_each = var.kafka_enabled ? {
    "event-write-path"        = "kafka"
    "kafka-security-protocol" = "SSL"
    "kafka-bootstrap-servers" = aws_msk_cluster.events[0].bootstrap_brokers_tls
    # 먼저 뜬 서버가 토픽을 만들고 나머지는 있는 것을 그대로 쓴다.
    "kafka-create-topics" = "true"
    # 파티션마다 스레드 하나가 붙게, 파티션 수를 컨슈머 서버 대수로 나눈 값을 서버마다 띄운다.
    "kafka-consumer-threads" = tostring(ceil(local.kafka_ingest_partitions / max(var.consumer_count, 1)))
  } : {}

  name  = "${local.param_path}/${each.key}"
  type  = "String"
  value = each.value
}
