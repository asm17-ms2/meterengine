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
