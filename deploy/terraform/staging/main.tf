data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

module "service" {
  source                    = "../modules/service"
  db_identifier             = "meterengine-staging-db"
  db_instance_class         = "db.t4g.small"
  db_subnet_group_name      = "meterengine-rds-subnet-private"
  rds_security_group_ids    = [aws_security_group.database.id]
  rds_initial_database_name = "meterengine_app"
  database_username         = "meterengine_owner"
  database_port             = 5432
  db_password_wo            = var.db_password_wo
  db_password_version       = var.db_password_version
  backup_retention_period   = 7
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "meterengine-staging-final"

  database_apply_immediately = true
}

module "app_fleet" {
  source                     = "../modules/app_fleet"
  environment                = "staging"
  parameter_path             = "/meterengine/staging"
  site_domain                = "staging.meterengine.com"
  vpc_id                     = "vpc-07498f0672b7f3c9e"
  public_subnet_ids          = ["subnet-0348a18c1d399c193", "subnet-0e375e82d415767a3"]
  route53_zone_id            = "Z08728621CQ5R1K37A7N1"
  database_security_group_id = aws_security_group.database.id
  ecr_repository_names       = local.ecr_repository_names
  ami_id                     = "ami-02b68e7eb6b798d63"
  # 버전 파라미터에 40자 SHA가 기록되기 전에는 모듈의 precondition이 plan을 멈춘다.
  min_size = 1
  # 측정 환경 숫자로 잡은 추정값이다.
  scaling_target_average_cpu_percent = 60
  alb_deletion_protection            = false
  monitoring_security_group_id       = data.aws_security_group.monitoring.id
  # 스테이징은 팀만 쓴다. 들어올 IP는 콘솔에서 ALB 보안 그룹에 직접 넣는다(deploy/README.md "스테이징 접근").
  alb_public_ingress = false
}
