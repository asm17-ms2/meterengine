# deploy.sh가 값을 .env에 그대로 쓰므로 compose가 해석할 수 있는 특수 문자를 넣지 않는다.
ephemeral "random_password" "db" {
  length  = 32
  special = false
}

# description을 바꾸면 그룹이 다시 만들어져 처음부터 고정 ASCII로 둔다.
resource "aws_security_group" "db" {
  name        = "${local.name}-rds"
  description = "MeterEngine ${var.environment} RDS: PostgreSQL from app servers"
  vpc_id      = data.aws_vpc.main.id
  tags        = local.tags
}

# 스토리지, 로그, Insights 값은 운영 modules/service와 같다.
# DB 이름, 사용자, 크기, KMS 키는 스냅샷이 정한다. 암호화는 적지 않으면 다음 plan이 교체하려 해 스냅샷과 같게 둔다.
resource "aws_db_instance" "sut" {
  identifier = "${local.name}-db"
  # 운영과 다름(RDS 생성): 같은 데이터
  snapshot_identifier = var.snapshot_identifier
  instance_class      = var.db_instance_class
  apply_immediately   = true

  engine                   = "postgres"
  engine_version           = "18.4"
  engine_lifecycle_support = "open-source-rds-extended-support-disabled"
  port                     = local.db_port
  # 복원 뒤 modify로 적용된다.
  password_wo         = ephemeral.random_password.db.result
  password_wo_version = local.password_version

  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true
  # 400GiB 미만 gp3는 기준값 3000/125로 고정되고, 복원 뒤 modify로 보내면 AWS가 거부해 적지 않는다.

  multi_az = false
  # 운영과 다름(서버 가용 영역): DB와 같은 AZ로 네트워크 조건 고정
  availability_zone      = local.availability_zone
  db_subnet_group_name   = data.aws_db_subnet_group.private.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  ca_cert_identifier     = "rds-ca-rsa2048-g1"

  backup_retention_period = 7
  backup_window           = "18:00-18:30"
  # 운영과 다름(RDS 유지 보수 창): 04:00 자동 중지와 겹치지 않게
  maintenance_window       = "mon:16:00-mon:16:30"
  delete_automated_backups = false
  copy_tags_to_snapshot    = true

  # 운영과 다름(성능 개선 도우미 키): 측정 뒤 성능 데이터는 버리므로 운영 키 사용 권한을 측정 역할에 더 주지 않고 AWS 기본 키를 쓴다
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  database_insights_mode                = "standard"
  enabled_cloudwatch_logs_exports       = ["postgresql"]

  # 운영과 다름(RDS 삭제 보호, 최종 스냅샷): 지울 수 있게
  deletion_protection = false
  skip_final_snapshot = true

  tags = merge(local.tags, { Name = "${local.name}-db" })
}
