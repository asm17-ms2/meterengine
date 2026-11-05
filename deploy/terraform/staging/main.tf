data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

module "service" {
  source                    = "../modules/service"
  name                      = "meterengine-staging"
  ami_id                    = "ami-0f4109eb491b0476f"
  instance_type             = "t4g.medium"
  subnet_id                 = "subnet-0348a18c1d399c193"
  ec2_security_group_ids    = [aws_security_group.app.id]
  iam_instance_profile_name = aws_iam_instance_profile.runtime.name
  root_block_device = {
    volume_size           = 30
    volume_type           = "gp3"
    iops                  = 3000
    throughput            = 125
    encrypted             = true
    delete_on_termination = true
  }
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
  user_data                 = file("${path.module}/bootstrap.sh")

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
  ami_id                     = "ami-0f4109eb491b0476f"
  # Caddy 없는 배포의 SHA가 버전 파라미터에 기록되기 전에는 서버가 떠도 헬스를 통과하지 못해 교체만 반복한다.
  min_size = 0
  # 측정 환경 숫자로 잡은 추정값이다.
  scaling_target_requests_per_second = 450
  alb_deletion_protection            = false
  monitoring_security_group_id       = data.aws_security_group.monitoring.id
}
