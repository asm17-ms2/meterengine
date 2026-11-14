locals {
  parameters                 = jsondecode(file("${path.module}/../../monitoring/parameters.json"))
  monitoring_parameter_names = [for name in local.parameters.monitoring : "${local.parameters.path_prefix}/monitoring/${name}"]
  environment_parameter_names = flatten([
    for env in local.parameters.environments : [for name in local.parameters.environment_parameters : "${local.parameters.path_prefix}/${env}/${name}"]
  ])
  # 카프카를 켠 환경에만 있는 브로커 주소다. start.sh가 읽어 Prometheus의 대상 파일을 쓴다.
  kafka_parameter_names = [for env in local.parameters.environments : "${local.parameters.path_prefix}/${env}/${local.parameters.kafka_bootstrap_parameter}"]
  # 받는 곳이 없는 주소다. Grafana가 URL 형식이 아닌 값으로는 경보 설정을 읽지 못해 unset을 쓰지 않는다.
  slack_placeholder_url = "http://127.0.0.1:9/slack-webhook-placeholder"
}

ephemeral "random_password" "grafana_admin" {
  length  = 32
  special = false
}

# 값은 모두 write-only라 상태에 남지 않고, 사람이 put-parameter로 넣은 실제 값을 다음 apply가 되돌리지 않는다.
resource "aws_ssm_parameter" "monitoring" {
  for_each         = toset(local.parameters.monitoring)
  name             = "${local.parameters.path_prefix}/monitoring/${each.key}"
  type             = "SecureString"
  value_wo         = each.key == "grafana-admin-password" ? ephemeral.random_password.grafana_admin.result : each.key == "slack-webhook-url" ? local.slack_placeholder_url : "unset"
  value_wo_version = 1
}
