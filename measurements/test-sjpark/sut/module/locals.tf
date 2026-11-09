locals {
  domain                = "${var.environment}.meterengine.com"
  parameter_path        = "/meterengine/${var.environment}"
  dev_param_path        = "/meterengine/prod"
  production_param_path = "/meterengine/production"
  availability_zone     = "ap-northeast-2a"
  db_port               = 5432
  # 운영과 다름(이미지): develop 대상 실험과 비교
  ecr_repository_names = ["meterengine-staging-backend", "meterengine-staging-frontend"]
  # 운영 루트와 같은 값이다.
  scaling_target_requests_per_second = 450
  app_egress_ports                   = [443]

  # staging, production과 같은 부팅 템플릿을 사본 없이 읽는다. 경로와 키는 deploy/tests의 시험이 템플릿과 대조한다.
  boot_template_path = "${path.module}/../../../../deploy/terraform/modules/app_fleet/templates/app-user-data.sh.tftpl"
}
