output "deployment_target" {
  value = {
    deployment_environment = "production"
    aws_account_id         = data.aws_caller_identity.current.account_id
    aws_region             = data.aws_region.current.region
    instance_id            = module.service.instance_id
    database_identifier    = module.service.database_identifier
    database_host          = module.service.database_host
    parameter_path         = "/meterengine/production"
    site_domain            = "meterengine.com"
    site_aliases           = ["www.meterengine.com"]
  }
  precondition {
    condition     = data.aws_caller_identity.current.account_id == "928618307647" && data.aws_region.current.region == "ap-northeast-2"
    error_message = "Production account or region differs from the verified target."
  }
}
