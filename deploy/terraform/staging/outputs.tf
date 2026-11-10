output "deployment_target" {
  value = {
    deployment_environment = "staging"
    aws_account_id         = data.aws_caller_identity.current.account_id
    aws_region             = data.aws_region.current.region
    database_identifier    = module.service.database_identifier
    database_host          = module.service.database_host
    parameter_path         = "/meterengine/staging"
    site_domain            = "staging.meterengine.com"
    site_aliases           = []
  }
  precondition {
    condition     = data.aws_caller_identity.current.account_id == "928618307647" && data.aws_region.current.region == "ap-northeast-2"
    error_message = "Staging account or region differs from the verified target."
  }
}
output "app_fleet" {
  value = {
    alb_dns_name               = module.app_fleet.alb_dns_name
    alb_zone_id                = module.app_fleet.alb_zone_id
    autoscaling_group_name     = module.app_fleet.autoscaling_group_name
    autoscaling_group_arn      = module.app_fleet.autoscaling_group_arn
    app_version_parameter_name = module.app_fleet.app_version_parameter_name
  }
}
