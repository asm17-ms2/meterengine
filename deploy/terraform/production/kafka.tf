# 카프카를 켜면 MSK 클러스터가 생기고 서버 그룹이 수집, 관리 API, 컨슈머 셋으로 나뉜다(deploy/README.md "카프카 켜기와 끄기").
# 끄면 서버 그룹 하나가 전부 맡고 요청마다 DB에 저장한다. MSK는 멈출 수 없고 떠 있는 동안 과금된다.

locals {
  # 브로커를 한 대씩 둘 프라이빗 서브넷. 2c는 측정 환경 루트(measurements/test-storage/sut/terraform)가 만든다.
  kafka_broker_subnet_names = [
    "meterengine-subnet-private1-ap-northeast-2a",
    "meterengine-subnet-private2-ap-northeast-2b",
    "meterengine-subnet-private3-ap-northeast-2c",
  ]
}

data "aws_subnet" "kafka_broker" {
  for_each = var.kafka_enabled ? toset(local.kafka_broker_subnet_names) : toset([])

  vpc_id = "vpc-07498f0672b7f3c9e"
  filter {
    name   = "tag:Name"
    values = [each.key]
  }
}

module "kafka" {
  count  = var.kafka_enabled ? 1 : 0
  source = "../modules/kafka"

  name                         = "meterengine-production"
  vpc_id                       = "vpc-07498f0672b7f3c9e"
  broker_subnet_ids            = [for name in local.kafka_broker_subnet_names : data.aws_subnet.kafka_broker[name].id]
  client_security_group_id     = module.app_fleet.app_security_group_id
  monitoring_security_group_id = data.aws_security_group.monitoring.id
  tags                         = { Environment = "production" }
}

locals {
  # 컨슈머 서버 2대가 파티션 6개를 나눠 읽는다.
  app_fleet_kafka = var.kafka_enabled ? {
    bootstrap_servers = module.kafka[0].bootstrap_brokers_tls
    ingest_min_size   = 1
    consumer_count    = 2
  } : null
}
