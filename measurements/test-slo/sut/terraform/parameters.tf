ephemeral "random_password" "db_exporter" {
  length  = 32
  special = false
}

ephemeral "random_password" "grafana_admin" {
  length  = 32
  special = false
}

ephemeral "random_password" "operator" {
  length  = 32
  special = false
}

# 키 원문 형식이 me_live_ 뒤 영문 대소문자와 숫자 32자라 special을 끈다.
ephemeral "random_password" "frontend_api_key" {
  length  = 32
  special = false
}

ephemeral "random_bytes" "crypto_encryption_key" {
  length = 32
}

locals {
  # 올리고 apply하면 DB 마스터와 exporter 비밀번호를 새로 만든다. 바꾸는 순서는 README "비밀번호"를 따른다.
  password_version = 1

  # Grafana는 데이터 볼륨이 처음 만들어질 때만 admin 비밀번호를 받으므로 DB 비밀번호와 따로 바꾼다.
  grafana_admin_password_version = 1

  # 올리면 새 키가 되어 DB에 이미 저장한 암호문을 풀지 못한다. DB를 새로 만들 때만 올린다.
  crypto_encryption_key_version = 1

  # SUT 경보가 팀 Slack으로 가지 않게 아무도 받지 않는 주소를 준다. Grafana가 URL 형식은 요구한다.
  unreachable_slack_webhook_url = "http://127.0.0.1:9/no-slack-in-${var.sut_name}"

  # deploy.sh가 이 경로 아래 이름을 대문자 환경변수로 바꿔 compose에 넘긴다.
  plain_parameters = {
    "db-host"           = aws_db_instance.sut.address
    "db-port"           = tostring(aws_db_instance.sut.port)
    "db-name"           = local.db_name
    "db-username"       = local.db_username
    "organization-id"   = "d7cee55d-8c82-4afc-b996-6749d8b26a4e"
    "organization-name" = "소마 컴퍼니"
    "operator-email"    = "operator@${local.domain}"
  }
}

resource "aws_ssm_parameter" "plain" {
  for_each = local.plain_parameters

  name  = "${local.param_path}/${each.key}"
  type  = "String"
  value = each.value
}

resource "aws_ssm_parameter" "slack_webhook_url" {
  name  = "${local.param_path}/slack-webhook-url"
  type  = "SecureString"
  value = local.unreachable_slack_webhook_url
}

# --- 상태 파일에 남지 않는 비밀번호 ---

resource "aws_ssm_parameter" "db_password" {
  name             = "${local.param_path}/db-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.db.result
  value_wo_version = local.password_version

  # RDS만 다시 만들어지면 새 마스터 비밀번호와 이 값이 어긋난다. id는 교체될 때만 바뀌어야 하므로 자원 전체가 아니라 id를 건다.
  lifecycle {
    replace_triggered_by = [aws_db_instance.sut.id]
  }
}

resource "aws_ssm_parameter" "db_exporter_password" {
  name             = "${local.param_path}/db-exporter-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.db_exporter.result
  value_wo_version = local.password_version
}

resource "aws_ssm_parameter" "grafana_admin_password" {
  name             = "${local.param_path}/grafana-admin-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.grafana_admin.result
  value_wo_version = local.grafana_admin_password_version
}

resource "aws_ssm_parameter" "operator_password" {
  name             = "${local.param_path}/operator-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.operator.result
  value_wo_version = local.password_version
}

resource "aws_ssm_parameter" "frontend_api_key" {
  name             = "${local.param_path}/frontend-api-key"
  type             = "SecureString"
  value_wo         = "me_live_${ephemeral.random_password.frontend_api_key.result}"
  value_wo_version = local.password_version
}

resource "aws_ssm_parameter" "crypto_encryption_key" {
  name             = "${local.param_path}/crypto-encryption-key"
  type             = "SecureString"
  value_wo         = ephemeral.random_bytes.crypto_encryption_key.base64
  value_wo_version = local.crypto_encryption_key_version
}
