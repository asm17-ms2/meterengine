locals {
  runtime_contract = jsondecode(file("${path.module}/../../runtime-parameters.v1.json"))
  runtime_read_parameters = {
    for parameter in local.runtime_contract.parameters : parameter.parameter_name => parameter
    if parameter.service == "backend"
  }
  runtime_parameters = {
    for name, parameter in local.runtime_read_parameters : name => parameter
    if parameter.required
  }
  runtime_public = {
    db-host                  = module.service.database_host
    db-port                  = "5432"
    db-name                  = "meterengine_app"
    db-shared-username       = "meterengine_shared"
    db-test-username         = "meterengine_test"
    db-live-username         = "meterengine_live"
    meterengine-cutover-mode = "active"
    operator-email           = var.operator_email
  }
}
resource "aws_ssm_parameter" "runtime" {
  for_each         = local.runtime_parameters
  name             = "/meterengine/staging/${each.key}"
  type             = each.value.secret ? "SecureString" : "String"
  value            = each.value.secret ? null : local.runtime_public[each.key]
  value_wo         = each.value.secret ? var.runtime_secrets[each.key] : null
  value_wo_version = each.value.secret ? var.runtime_secret_versions[each.key] : null
}
resource "aws_ssm_parameter" "migration_username" {
  name  = "/meterengine/staging-migration/db-migration-username"
  type  = "String"
  value = "meterengine_owner"
}
resource "aws_ssm_parameter" "migration_password" {
  name             = "/meterengine/staging-migration/db-migration-password"
  type             = "SecureString"
  value_wo         = var.db_password_wo
  value_wo_version = var.db_password_version
}
