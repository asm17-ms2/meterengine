data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  prefix                = "meterengine-${var.sut_name}"
  domain                = "${var.sut_name}.meterengine.com"
  param_path            = "/meterengine/${var.sut_name}"
  dev_param_path        = "/meterengine/prod"
  production_param_path = "/meterengine/production"
  account_id            = data.aws_caller_identity.current.account_id
  region                = data.aws_region.current.region
  availability_zone     = "ap-northeast-2a"
  ecr_repos             = ["meterengine-backend", "meterengine-frontend"]
  db_name               = "meterengine"
  db_username           = "meterengine"
  db_port               = 5432

  # 공용 모니터링 서버가 이 서버에서 긁는 포트. 키는 Prometheus job 이름과 같다.
  # backend는 블루그린이라 호스트에 8080이 없어 열지 않고, 공용 설정이 이 환경을 긁지 않는다.
  app_scrape_ports = {
    node     = 9100
    cadvisor = 9101
  }
}
