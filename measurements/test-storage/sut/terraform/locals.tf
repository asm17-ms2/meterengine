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

  # 공용 모니터링 서버가 앱 서버에서 긁는 포트. 키는 Prometheus job 이름과 같다.
  app_scrape_ports = {
    backend  = 8080
    node     = 9100
    cadvisor = 9101
  }

  # staging, production과 같은 부팅 템플릿을 사본 없이 읽는다. 경로와 키는 deploy/tests의 시험이 템플릿과 대조한다.
  boot_template_path = "${path.root}/../../../../deploy/terraform/modules/app_fleet/templates/app-user-data.sh.tftpl"
  boot_template_vars = {
    environment                = var.sut_name
    parameter_path             = local.param_path
    app_version_parameter_name = aws_ssm_parameter.app_version.name
    aws_region                 = local.region
    repository_url             = "https://github.com/asm17-ms2/meterengine.git"
  }
}
