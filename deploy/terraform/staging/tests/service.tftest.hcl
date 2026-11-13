mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "928618307647" }
  }
  mock_data "aws_region" {
    defaults = { region = "ap-northeast-2" }
  }
  mock_data "aws_ssm_parameter" {
    defaults = { insecure_value = "0123456789abcdef0123456789abcdef01234567" }
  }
  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:ap-northeast-2:928618307647:certificate/00000000-0000-0000-0000-000000000000"
      domain_validation_options = [{
        domain_name           = "staging.meterengine.com"
        resource_record_name  = "_test.staging.meterengine.com."
        resource_record_type  = "CNAME"
        resource_record_value = "_test.acm-validations.aws."
      }]
    }
  }
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:loadbalancer/app/meterengine-staging-alb/0000000000000000" }
  }
  mock_resource "aws_lb_listener" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:listener/app/meterengine-staging-alb/0000000000000000/0000000000000000" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:targetgroup/test/0000000000000000" }
  }
  mock_resource "aws_launch_template" {
    defaults = { id = "lt-00000000000000000", latest_version = 1 }
  }
  mock_resource "aws_autoscaling_group" {
    defaults = { arn = "arn:aws:autoscaling:ap-northeast-2:928618307647:autoScalingGroup:00000000-0000-0000-0000-000000000000:autoScalingGroupName/meterengine-staging-app" }
  }
}


variables {
  db_password_wo      = "D2_TEST_ONLY_OWNER_NOT_A_REAL_SECRET"
  db_password_version = 1
  runtime_secrets = {
    crypto-encryption-key = "D2_TEST_ONLY_CRYPTO_NOT_A_REAL_SECRET"
    operator-password     = "D2_TEST_ONLY_OPERATOR_NOT_A_REAL_SECRET"
  }
  runtime_secret_versions = {
    crypto-encryption-key = 1, operator-password = 1
  }
  operator_email = "d2-fixture@example.invalid"
}

# 가짜 공급자가 받지 않는 import의 결과를 정해 준다.
override_resource {
  target = aws_ssm_parameter.runtime
  values = {}
}

run "staging_is_isolated" {
  command = plan

  override_resource {
    target          = aws_security_group.database
    override_during = plan
    values          = { id = "sg-22222222222222222" }
  }


  assert {
    condition     = aws_security_group.database.tags.Environment == "staging"
    error_message = "Staging network resources need enforceable ownership tags."
  }
  assert {
    condition     = output.deployment_target.aws_account_id == "928618307647" && output.deployment_target.aws_region == "ap-northeast-2"
    error_message = "Staging must stay in the verified account and region."
  }
  assert {
    condition     = aws_security_group.database.vpc_id == "vpc-07498f0672b7f3c9e"
    error_message = "Staging must use the verified VPC."
  }
  assert {
    condition     = output.deployment_target.deployment_environment == "staging" && output.deployment_target.parameter_path == "/meterengine/staging" && output.deployment_target.site_domain == "staging.meterengine.com"
    error_message = "Target output must preserve the isolated manifest contract."
  }
  assert {
    condition     = jsondecode(aws_iam_role.github_deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:asm17-ms2@293532363/meterengine@1313421667:environment:staging"
    error_message = "The deploy role trusts the staging GitHub Environment only."
  }
}

run "service_is_protected" {
  command = plan
  module {
    source = "../modules/service"
  }
  variables {
    db_identifier             = "meterengine-staging-db"
    db_instance_class         = "db.t4g.small"
    db_subnet_group_name      = "meterengine-rds-subnet-private"
    rds_security_group_ids    = ["sg-22222222222222222"]
    rds_initial_database_name = "meterengine_app"
    database_username         = "meterengine_owner"
    database_port             = 5432
    backup_retention_period   = 7
    deletion_protection       = true
    skip_final_snapshot       = false
    final_snapshot_identifier = "meterengine-staging-final"
  }
  assert {
    condition     = aws_db_instance.app.instance_class == "db.t4g.small"
    error_message = "Staging database must use the approved t4g class."
  }
  assert {
    condition     = !aws_db_instance.app.publicly_accessible && aws_db_instance.app.storage_encrypted
    error_message = "RDS must be private and encrypted."
  }
  assert {
    condition     = aws_db_instance.app.deletion_protection && !aws_db_instance.app.skip_final_snapshot && aws_db_instance.app.backup_retention_period == 7 && aws_db_instance.app.final_snapshot_identifier == "meterengine-staging-final" && !aws_db_instance.app.delete_automated_backups
    error_message = "Backups and a final snapshot must survive deletion."
  }
}

run "staging_state_excludes_secrets" {
  command = apply
  assert {
    condition     = aws_ssm_parameter.runtime["db-password"].value_wo == null && aws_ssm_parameter.runtime["operator-password"].value_wo == null
    error_message = "Write-only secrets must not be retained in mock state."
  }
  assert {
    condition     = aws_ssm_parameter.db_exporter_password.type == "SecureString" && aws_ssm_parameter.db_exporter_password.value_wo == null && aws_ssm_parameter.db_exporter_password.value_wo_version == 1
    error_message = "The exporter password is a write-only placeholder that apply never resets."
  }
  assert {
    condition = (
      contains(jsondecode(file("${path.module}/../../monitoring/parameters.json")).environments, "staging") &&
      contains(jsondecode(file("${path.module}/../../monitoring/parameters.json")).environment_parameters, "db-exporter-password") &&
      aws_ssm_parameter.db_exporter_password.name == "${jsondecode(file("${path.module}/../../monitoring/parameters.json")).path_prefix}/staging/db-exporter-password"
    )
    error_message = "The shared monitoring server must be allowed to read the staging exporter password under the same name."
  }
  assert {
    condition     = !strcontains(aws_iam_role_policy.github_deploy.policy, "ssm:SendCommand")
    error_message = "The deploy role rolls the fleet and runs no commands on servers."
  }
}

run "wrong_account_is_rejected" {
  command = plan
  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "000000000000" }
  }
  expect_failures = [output.deployment_target]
}

run "runtime_uses_single_account" {
  command = plan

  assert {
    condition     = toset(keys(aws_ssm_parameter.runtime)) == toset(["db-host", "db-port", "db-name", "db-username", "db-password", "crypto-encryption-key", "operator-email", "operator-password", "invoice-auto-finalize-enabled"])
    error_message = "Staging must define exactly the parameters deploy.sh requires."
  }
  assert {
    condition     = aws_ssm_parameter.runtime["db-username"].value == "meterengine_owner"
    error_message = "Staging connects with the RDS master account, as production does."
  }
  assert {
    condition     = !strcontains(file("${path.module}/iam.tf"), "github_owner")
    error_message = "The owner role must be gone."
  }
  assert {
    condition = alltrue([
      for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement :
      statement.Resource == local.ecr_repositories if statement.Effect == "Allow" && contains(flatten([statement.Action]), "ecr:PutImage")
    ]) && anytrue([for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : contains(flatten([statement.Action]), "ecr:PutImage")])
    error_message = "The deploy role pushes images to the two ECR repositories only."
  }
}

run "images_are_separate_from_production" {
  command = plan

  assert {
    condition     = toset(keys(aws_ecr_repository.image)) == toset(["meterengine-staging-backend", "meterengine-staging-frontend"])
    error_message = "Staging owns exactly two image repositories."
  }
  assert {
    condition     = alltrue([for repository in values(aws_ecr_repository.image) : repository.image_tag_mutability == "IMMUTABLE"])
    error_message = "Image tags cannot be overwritten, as in production."
  }
  assert {
    condition     = toset(keys(aws_ecr_lifecycle_policy.image)) == toset(keys(aws_ecr_repository.image))
    error_message = "Every repository expires old images."
  }
  assert {
    condition     = !strcontains(aws_iam_role_policy.github_deploy.policy, "repository/meterengine-backend") && !strcontains(aws_iam_role_policy.github_deploy.policy, "repository/meterengine-frontend")
    error_message = "The deploy role must not write to production repositories."
  }
}

run "deploy_role_rolls_the_fleet" {
  command = apply

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement :
      statement.Effect == "Allow" && toset(statement.Action) == toset(["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"]) && statement.Resource == module.app_fleet.autoscaling_group_arn
    ])
    error_message = "The deploy role starts and cancels refreshes of the staging fleet only."
  }
  assert {
    condition = anytrue([
      for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement :
      statement.Effect == "Allow" && toset(statement.Action) == toset(["ssm:GetParameter", "ssm:PutParameter"]) && statement.Resource == "${local.ssm_arn}:parameter/meterengine/staging/app-version"
    ])
    error_message = "The deploy role reads and writes the version parameter only."
  }
  assert {
    condition = alltrue([
      for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement :
      statement.NotResource == ["${local.ssm_arn}:parameter/meterengine/staging/app-version"] && !can(statement.Resource) if statement.Effect == "Deny"
    ])
    error_message = "Every other parameter and sessions stay denied."
  }
  assert {
    condition     = output.app_fleet.autoscaling_group_name == "meterengine-staging-app" && output.app_fleet.app_version_parameter_name == "/meterengine/staging/app-version"
    error_message = "Rollout reads the fleet and version parameter names from these outputs."
  }
}

run "fleet_can_start_empty" {
  command = plan
  module {
    source = "../modules/app_fleet"
  }
  variables {
    environment                        = "staging"
    parameter_path                     = "/meterengine/staging"
    site_domain                        = "staging.meterengine.com"
    vpc_id                             = "vpc-07498f0672b7f3c9e"
    public_subnet_ids                  = ["subnet-0348a18c1d399c193", "subnet-0e375e82d415767a3"]
    route53_zone_id                    = "Z08728621CQ5R1K37A7N1"
    database_security_group_id         = "sg-22222222222222222"
    ecr_repository_names               = ["meterengine-staging-backend", "meterengine-staging-frontend"]
    ami_id                             = "ami-0f4109eb491b0476f"
    min_size                           = 0
    scaling_target_average_cpu_percent = 60
    alb_deletion_protection            = false
    monitoring_security_group_id       = "sg-33333333333333333"
  }

  assert {
    condition     = aws_autoscaling_group.app.name == "meterengine-staging-app" && aws_autoscaling_group.app.min_size == 0 && aws_autoscaling_group.app.max_size == 3 && aws_autoscaling_group.app.health_check_type == "ELB" && aws_autoscaling_group.app.health_check_grace_period == 600
    error_message = "The fleet starts at zero servers with ALB health checks."
  }
  assert {
    condition     = aws_autoscaling_group.app.instance_refresh[0].preferences[0].min_healthy_percentage == 100 && aws_autoscaling_group.app.instance_maintenance_policy[0].min_healthy_percentage == 100 && aws_autoscaling_group.app.instance_maintenance_policy[0].max_healthy_percentage == 200
    error_message = "Replacements bring a healthy server up before taking one down."
  }
  assert {
    condition     = { for tag in aws_autoscaling_group.app.tag : tag.key => tag.value if tag.propagate_at_launch } == { Name = "meterengine-staging-app", Role = "app", Environment = "staging" }
    error_message = "Monitoring finds servers by Role and labels them by Environment."
  }
  assert {
    condition     = aws_launch_template.app.instance_type == "t4g.medium" && aws_launch_template.app.metadata_options[0].http_tokens == "required" && aws_launch_template.app.metadata_options[0].instance_metadata_tags == "enabled" && aws_launch_template.app.network_interfaces[0].associate_public_ip_address == "true"
    error_message = "Servers need IMDSv2, readable tags and a public IP without NAT."
  }
  assert {
    condition     = aws_launch_template.app.block_device_mappings[0].ebs[0].encrypted == "true" && aws_launch_template.app.block_device_mappings[0].ebs[0].volume_size == 30
    error_message = "Root volumes are encrypted 30GB gp3."
  }
  assert {
    condition     = strcontains(base64decode(aws_launch_template.app.user_data), "--name '/meterengine/staging/app-version'") && strcontains(base64decode(aws_launch_template.app.user_data), "./deploy/deploy.sh 'staging' \"$version\"")
    error_message = "Boot reads the staging version parameter and deploys staging."
  }
  assert {
    condition = (
      aws_autoscaling_policy.average_cpu.target_tracking_configuration[0].target_value == 60 &&
      aws_autoscaling_policy.average_cpu.target_tracking_configuration[0].customized_metric_specification[0].metric_name == "CPUUtilizationPerDesiredInstance" &&
      aws_autoscaling_policy.average_cpu.target_tracking_configuration[0].customized_metric_specification[0].period == 10 &&
      aws_autoscaling_policy.average_cpu.target_tracking_configuration[0].customized_metric_specification[0].statistic == "Sum"
    )
    error_message = "Scaling sums the ten second CPU per desired server against the target."
  }
  assert {
    condition     = aws_lb.app.idle_timeout == 130 && aws_lb.app.xff_header_processing_mode == "append" && !aws_lb.app.enable_deletion_protection && length(aws_lb.app.access_logs) == 0
    error_message = "The ALB keeps long payment calls, appends client IPs and writes no access logs."
  }
  assert {
    condition     = aws_lb_listener.https.ssl_policy == "ELBSecurityPolicy-TLS13-1-2-2021-06" && aws_lb_listener.https.default_action[0].fixed_response[0].status_code == "404"
    error_message = "HTTPS offers TLS 1.3 and rejects unknown hosts."
  }
  assert {
    condition     = aws_lb_target_group.frontend.name_prefix == "front-" && aws_lb_target_group.frontend.health_check[0].path == "/health" && aws_lb_target_group.backend.name_prefix == "back-" && aws_lb_target_group.backend.health_check[0].path == "/actuator/health/readiness"
    error_message = "Target groups use prefixes so replacements do not collide."
  }
  assert {
    condition     = length(aws_lb_listener_rule.alias_to_site) == 0 && toset(flatten([for condition in aws_lb_listener_rule.api_to_backend.condition : [for pattern in condition.path_pattern : tolist(pattern.values)]])) == toset(["/v1/*"]) && toset(keys(aws_route53_record.certificate_validation)) == toset(["staging.meterengine.com"])
    error_message = "Staging has no alias and sends only the API to the backend."
  }
  assert {
    condition     = aws_security_group.alb.name == "meterengine-staging-alb" && aws_security_group.asg.name == "meterengine-staging-asg" && toset(keys(aws_vpc_security_group_egress_rule.app_internet)) == toset(["443"])
    error_message = "Fleet groups are named after the environment."
  }
  assert {
    condition     = toset(keys(aws_vpc_security_group_ingress_rule.app_from_monitoring)) == toset(["8080", "9100", "9101"]) && aws_vpc_security_group_ingress_rule.database_from_monitoring.referenced_security_group_id == "sg-33333333333333333"
    error_message = "Shared monitoring scrapes the app servers and the database."
  }
  assert {
    condition = anytrue([
      for statement in jsondecode(aws_iam_role_policy.app.policy).Statement :
      statement.Effect == "Allow" && statement.Action == ["cloudwatch:PutMetricData"] && statement.Condition.StringEquals["cloudwatch:namespace"] == "MeterEngine/Scaling"
    ]) && jsondecode(aws_iam_role_policy.app.policy).Statement[1].Effect == "Deny" && toset(jsondecode(aws_iam_role_policy.app.policy).Statement[1].NotResource) == toset(local.runtime_path)
    error_message = "App servers read only their path and put only scaling metrics."
  }
  assert {
    condition     = aws_ssm_parameter.app_version.name == "/meterengine/staging/app-version" && aws_ssm_parameter.app_version.type == "String" && aws_ssm_parameter.app_version.value == "unset"
    error_message = "The version parameter starts unset."
  }
}

run "domain_points_at_the_alb" {
  command = plan
  # 앞의 apply가 만든 자원은 plan 덮어쓰기가 먹지 않아 빈 상태에서 본다.
  state_key = "domain"
  override_resource {
    target          = module.app_fleet.aws_lb.app
    override_during = plan
    values = {
      arn      = "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:loadbalancer/app/meterengine-staging-alb/0000000000000000"
      dns_name = "meterengine-staging-alb-1.ap-northeast-2.elb.amazonaws.com"
      zone_id  = "ZWKZPGTI48KDX"
    }
  }

  assert {
    condition = (
      one(aws_route53_record.app.alias).name == "meterengine-staging-alb-1.ap-northeast-2.elb.amazonaws.com" &&
      one(aws_route53_record.app.alias).zone_id == "ZWKZPGTI48KDX" &&
      !one(aws_route53_record.app.alias).evaluate_target_health &&
      aws_route53_record.app.ttl == null && aws_route53_record.app.records == null
    )
    error_message = "The staging domain is an alias of the staging ALB."
  }
  assert {
    condition     = module.app_fleet.min_size == 1
    error_message = "Staging keeps one server running behind the domain."
  }
  assert {
    condition     = strcontains(file("${path.module}/dns.tf"), "depends_on = [module.app_fleet]") && strcontains(file("${path.module}/dns.tf"), "prevent_destroy = true")
    error_message = "The record changes after the fleet is healthy and is never deleted."
  }
}

run "alb_domain_needs_a_server" {
  command   = plan
  state_key = "fleet_outputs_overridden"
  override_module {
    target = module.app_fleet
    outputs = {
      min_size                   = 0
      alb_dns_name               = "meterengine-staging-alb-1.ap-northeast-2.elb.amazonaws.com"
      alb_zone_id                = "ZWKZPGTI48KDX"
      autoscaling_group_name     = "meterengine-staging-app"
      autoscaling_group_arn      = "arn:aws:autoscaling:ap-northeast-2:928618307647:autoScalingGroup:00000000-0000-0000-0000-000000000000:autoScalingGroupName/meterengine-staging-app"
      app_version_parameter_name = "/meterengine/staging/app-version"
    }
  }
  expect_failures = [aws_route53_record.app]
}

run "staging_alb_takes_no_internet_ingress_from_terraform" {
  command = plan

  assert {
    condition     = module.app_fleet.alb_public_ingress_ports == toset([])
    error_message = "Staging ALB ingress is added by hand per team IP, so Terraform must open nothing to the internet."
  }
}

run "alb_ingress_can_be_left_to_the_console" {
  command = plan
  module {
    source = "../modules/app_fleet"
  }
  variables {
    environment                        = "staging"
    parameter_path                     = "/meterengine/staging"
    site_domain                        = "staging.meterengine.com"
    vpc_id                             = "vpc-07498f0672b7f3c9e"
    public_subnet_ids                  = ["subnet-0348a18c1d399c193", "subnet-0e375e82d415767a3"]
    route53_zone_id                    = "Z08728621CQ5R1K37A7N1"
    database_security_group_id         = "sg-22222222222222222"
    ecr_repository_names               = ["meterengine-staging-backend", "meterengine-staging-frontend"]
    ami_id                             = "ami-0f4109eb491b0476f"
    min_size                           = 0
    scaling_target_average_cpu_percent = 60
    alb_deletion_protection            = false
    monitoring_security_group_id       = "sg-33333333333333333"
    alb_public_ingress                 = false
  }

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.alb_web) == 0
    error_message = "With public ingress off the module defines no ALB ingress rule."
  }
  assert {
    condition     = toset(keys(aws_vpc_security_group_egress_rule.alb_to_app)) == toset(["3000", "8080"]) && toset(keys(aws_vpc_security_group_ingress_rule.app_from_alb)) == toset(["3000", "8080"])
    error_message = "The ALB still reaches the app servers."
  }
}

run "staging_signup_uses_its_own_hostname" {
  command = plan

  assert {
    condition     = aws_ssm_parameter.signup["turnstile-allowed-hostnames"].value == "staging.meterengine.com"
    error_message = "Staging accepts Turnstile tokens issued for the staging hostname only."
  }
  assert {
    condition     = aws_ssm_parameter.signup["mail-sender"].value == "ses" && aws_ssm_parameter.signup["mail-from"].value == "MeterEngine Staging <no-reply@meterengine.com>"
    error_message = "Staging sends signup mail through SES and names itself in the sender."
  }
  assert {
    condition     = toset(keys(aws_ssm_parameter.signup)) == toset(["mail-sender", "mail-from", "turnstile-allowed-hostnames"])
    error_message = "Keys and on/off values are registered by hand, not by Terraform."
  }
}
