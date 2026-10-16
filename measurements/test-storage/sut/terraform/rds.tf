# deploy.sh가 값을 .env에 그대로 쓰므로 compose가 해석할 수 있는 특수 문자를 넣지 않는다.
ephemeral "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_instance" "sut" {
  identifier = "${local.prefix}-db"
  engine     = "postgres"
  # 개발 서버와 마이너 버전을 맞춘다. 자동 업그레이드로 올라간 뒤 plan이 되돌리지 않게 lifecycle에서 무시한다.
  engine_version = "18.4"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name             = local.db_name
  username            = local.db_username
  port                = local.db_port
  password_wo         = ephemeral.random_password.db.result
  password_wo_version = local.password_version

  multi_az               = false
  availability_zone      = local.availability_zone
  db_subnet_group_name   = data.aws_db_subnet_group.private.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  parameter_group_name   = "default.postgres18"
  ca_cert_identifier     = "rds-ca-rsa2048-g1"

  backup_retention_period = 7
  backup_window           = "18:00-18:30"
  # 04:00(서울) 자동 중지와 겹치면 RDS가 업그레이드 중이라 중지가 실패한다. 화 01:00-01:30 KST.
  maintenance_window         = "mon:16:00-mon:16:30"
  auto_minor_version_upgrade = true

  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  copy_tags_to_snapshot = true
  deletion_protection   = false
  skip_final_snapshot   = true
  apply_immediately     = true

  tags = {
    Name = "${local.prefix}-db"
  }

  lifecycle {
    ignore_changes = [engine_version]
  }
}
