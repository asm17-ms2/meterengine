locals {
  runtime_public = {
    db-host        = module.service.database_host
    db-port        = "5432"
    db-name        = "meterengine_app"
    db-username    = "meterengine_owner"
    operator-email = var.operator_email
  }
  runtime_secret_names = ["db-password", "crypto-encryption-key", "operator-password"]
  # RDS 마스터 비밀번호와 앱이 읽는 값이 어긋나지 않게 db-password는 같은 변수에서 받는다.
  runtime_secret_values   = merge(var.runtime_secrets, { db-password = var.db_password_wo })
  runtime_secret_versions = merge(var.runtime_secret_versions, { db-password = var.db_password_version })
}
resource "aws_ssm_parameter" "runtime" {
  for_each         = setunion(keys(local.runtime_public), local.runtime_secret_names)
  name             = "/meterengine/staging/${each.key}"
  type             = contains(local.runtime_secret_names, each.key) ? "SecureString" : "String"
  value            = contains(local.runtime_secret_names, each.key) ? null : local.runtime_public[each.key]
  value_wo         = contains(local.runtime_secret_names, each.key) ? local.runtime_secret_values[each.key] : null
  value_wo_version = contains(local.runtime_secret_names, each.key) ? local.runtime_secret_versions[each.key] : null
}

# 공용 모니터링의 postgres exporter가 읽는다. 자리만 만들고 실제 값은 사람이 넣어, apply가 그 값을 되돌리지 않는다.
resource "aws_ssm_parameter" "db_exporter_password" {
  name             = "/meterengine/staging/db-exporter-password"
  type             = "SecureString"
  value_wo         = "unset"
  value_wo_version = 1
}
