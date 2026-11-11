# 비밀값과 공개 저장소에 올리지 않을 값은 Terraform 밖에서 put-parameter로 관리한다.
locals {
  parameter_path = "/meterengine/production"
  coded_parameters = {
    db-host                       = module.service.database_host
    db-port                       = "5432"
    db-name                       = "meterengine"
    db-username                   = "meterengine"
    invoice-auto-finalize-enabled = "true"
  }
}

resource "aws_ssm_parameter" "coded" {
  for_each = local.coded_parameters
  name     = "${local.parameter_path}/${each.key}"
  type     = "String"
  value    = each.value
}

import {
  for_each = toset(keys(local.coded_parameters))
  to       = aws_ssm_parameter.coded[each.key]
  id       = "${local.parameter_path}/${each.key}"
}

# 가입 인증 메일과 캡차의 공개 설정이다. 켜고 끄는 값과 Turnstile 키는 손으로 등록한다(deploy/README.md "설정값").
# 아직 없는 파라미터라 위 coded처럼 import하지 않는다.
locals {
  signup_parameters = {
    mail-sender                 = "ses"
    mail-from                   = "MeterEngine <${local.email_from_address}>"
    turnstile-allowed-hostnames = "meterengine.com"
  }
}

resource "aws_ssm_parameter" "signup" {
  for_each = local.signup_parameters
  name     = "${local.parameter_path}/${each.key}"
  type     = "String"
  value    = each.value
}
