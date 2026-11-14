mock_provider "aws" {}

run "parameters_follow_the_shared_name_list" {
  command = plan

  assert {
    condition     = toset([for parameter in aws_ssm_parameter.monitoring : parameter.name]) == toset(["/meterengine/monitoring/grafana-admin-password", "/meterengine/monitoring/grafana-github-client-id", "/meterengine/monitoring/grafana-github-client-secret", "/meterengine/monitoring/slack-webhook-url"])
    error_message = "The monitoring secrets must match deploy/monitoring/parameters.json."
  }
  assert {
    condition     = alltrue([for parameter in aws_ssm_parameter.monitoring : parameter.type == "SecureString" && parameter.value_wo_version == 1])
    error_message = "Every monitoring secret is a write-only SecureString."
  }
  assert {
    condition     = startswith(local.slack_placeholder_url, "http")
    error_message = "Grafana rejects a Slack contact point whose URL is not a URL."
  }
}

run "server_reads_only_listed_parameters" {
  command = plan

  assert {
    condition = toset(jsondecode(aws_iam_role_policy.monitoring.policy).Statement[0].Resource) == toset([
      for name in concat(local.monitoring_parameter_names, local.environment_parameter_names, local.kafka_parameter_names) : "arn:aws:ssm:ap-northeast-2:928618307647:parameter${name}"
    ]) && length(local.environment_parameter_names) == 20 && length(local.kafka_parameter_names) == 5
    error_message = "The server reads exactly the names in parameters.json."
  }
  assert {
    condition     = contains(local.environment_parameter_names, "/meterengine/production/db-exporter-password") && !contains(local.environment_parameter_names, "/meterengine/production/db-password")
    error_message = "The server reads the exporter password, never the master password."
  }
  assert {
    condition     = contains(local.kafka_parameter_names, "/meterengine/staging/kafka-bootstrap-servers")
    error_message = "The server reads each environment's broker addresses to write the Prometheus target files."
  }
  assert {
    condition = (jsondecode(aws_iam_role_policy.monitoring.policy).Statement[1].Effect == "Deny"
      && toset(jsondecode(aws_iam_role_policy.monitoring.policy).Statement[1].NotResource) == toset(jsondecode(aws_iam_role_policy.monitoring.policy).Statement[0].Resource)
    && contains(jsondecode(aws_iam_role_policy.monitoring.policy).Statement[1].Action, "ssm:GetParametersByPath"))
    error_message = "Every other parameter, including path reads, is denied."
  }
}

run "server_scrapes_inside_the_vpc" {
  command = plan

  assert {
    condition     = aws_security_group.monitoring.name == "meterengine-monitoring" && can(regex("^[ -~]+$", aws_security_group.monitoring.description))
    error_message = "Other roots find this group by name. Its ASCII description cannot change without replacing it."
  }
  assert {
    condition     = toset([for rule in aws_vpc_security_group_egress_rule.scrape : "${rule.cidr_ipv4}:${rule.from_port}-${rule.to_port}"]) == toset(["10.0.0.0/16:8080-8080", "10.0.0.0/16:9100-9100", "10.0.0.0/16:9101-9101", "10.0.0.0/16:5432-5432"])
    error_message = "The server scrapes backend, agents and databases inside the VPC only."
  }
  assert {
    condition     = aws_vpc_security_group_egress_rule.https.cidr_ipv4 == "0.0.0.0/0" && aws_vpc_security_group_egress_rule.https.from_port == 443 && aws_vpc_security_group_egress_rule.https.to_port == 443
    error_message = "Only HTTPS leaves the VPC."
  }
}

run "server_shape" {
  command = plan

  assert {
    condition     = aws_instance.monitoring.instance_type == "t4g.medium" && aws_instance.monitoring.associate_public_ip_address && aws_instance.monitoring.subnet_id == "subnet-0348a18c1d399c193"
    error_message = "The server is a t4g.medium in the public 2a subnet with a public IP."
  }
  assert {
    condition     = aws_instance.monitoring.metadata_options[0].http_tokens == "required" && aws_instance.monitoring.metadata_options[0].http_put_response_hop_limit == 2
    error_message = "IMDSv2 with one extra hop for containers."
  }
  assert {
    condition     = aws_instance.monitoring.root_block_device[0].volume_size == 30 && aws_instance.monitoring.root_block_device[0].volume_type == "gp3" && aws_instance.monitoring.root_block_device[0].encrypted
    error_message = "History lives on an encrypted 30 GB gp3 root disk."
  }
  assert {
    condition     = aws_instance.monitoring.tags == tomap({ Name = "meterengine-monitoring", Role = "monitoring" })
    error_message = "Role must not be app, or Prometheus would scrape itself as an app server."
  }
}

run "state_keeps_no_secret" {
  command = apply

  assert {
    condition     = alltrue([for parameter in aws_ssm_parameter.monitoring : parameter.value_wo == null])
    error_message = "Write-only values must not be retained in state."
  }
  assert {
    condition     = !can(regex("\\n\\s+value\\s+=", file("${path.module}/parameters.tf")))
    error_message = "A plain value would overwrite the real secret put by hand on the next apply."
  }
}
