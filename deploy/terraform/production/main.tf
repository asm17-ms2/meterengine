module "service" {
  source                    = "../modules/service"
  name                      = "meterengine-server"
  ami_id                    = "ami-0f4109eb491b0476f"
  instance_type             = var.instance_type
  subnet_id                 = "subnet-0348a18c1d399c193"
  ec2_security_group_ids    = ["sg-0c2691d037bcdd8b1"]
  iam_instance_profile_name = "meterengine-ec2-role"
  root_block_device = {
    volume_size = 20, volume_type = "gp3", iops = 3000, throughput = 125,
    encrypted   = false, delete_on_termination = true
  }
  db_identifier              = "meterengine-db"
  db_instance_class          = var.db_instance_class
  db_subnet_group_name       = "meterengine-rds-subnet-private"
  rds_security_group_ids     = ["sg-068a54ab4cfa91730"]
  rds_initial_database_name  = "meterengine"
  database_username          = "meterengine"
  database_port              = 5432
  database_apply_immediately = true
  db_password_wo             = null
  db_password_version        = null
  database_kms_key_id        = "arn:aws:kms:ap-northeast-2:928618307647:key/c90dd8e8-d6a0-4676-9aa2-d2ad709856f9"
  database_iops              = 3000
  database_throughput        = 125
  backup_retention_period    = 7
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "meterengine-production-final"
  user_data                  = null
}

module "app_fleet" {
  source                     = "../modules/app_fleet"
  environment                = "production"
  parameter_path             = "/meterengine/production"
  site_domain                = "meterengine.com"
  site_aliases               = ["www.meterengine.com"]
  vpc_id                     = "vpc-07498f0672b7f3c9e"
  public_subnet_ids          = ["subnet-0348a18c1d399c193", "subnet-0e375e82d415767a3"]
  route53_zone_id            = "Z08728621CQ5R1K37A7N1"
  database_security_group_id = "sg-068a54ab4cfa91730"
  ecr_repository_names       = ["meterengine-backend", "meterengine-frontend"]
  ami_id                     = "ami-02b68e7eb6b798d63"
  # 버전 파라미터에 40자 SHA가 기록되기 전에는 모듈의 precondition이 plan을 멈춘다.
  min_size = 1
  # 측정 환경 숫자로 잡은 추정값이다.
  scaling_target_average_cpu_percent = 60
  # main 체크아웃에서 잘못 apply해도 운영 ALB가 지워지지 않게 한다.
  alb_deletion_protection      = true
  app_egress_ports             = [443]
  monitoring_security_group_id = data.aws_security_group.monitoring.id
}
