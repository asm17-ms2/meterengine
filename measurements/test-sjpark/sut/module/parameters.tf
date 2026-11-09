# 배포가 커밋 SHA를 쓰고 서버가 부팅 때 읽는다. Terraform은 자리만 만들고 값을 되돌리지 않는다.
resource "aws_ssm_parameter" "app_version" {
  name  = local.app_version_parameter_name
  type  = "String"
  value = "unset"
  tags  = local.tags
  lifecycle {
    ignore_changes = [value]
  }
}

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

# random_bytes.base64 44자는 oauth2-proxy가 44바이트로 읽어 기동하지 못하므로 32자 문자열로 만든다.
ephemeral "random_password" "oauth2_proxy_cookie_secret" {
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

  # 운영과 다름(Slack, oauth2 값): 측정 경보가 팀에 가지 않게
  # SUT 경보가 팀 Slack으로 가지 않게 아무도 받지 않는 주소를 준다. Grafana가 URL 형식은 요구한다.
  unreachable_slack_webhook_url = "http://127.0.0.1:9/no-slack-in-${var.environment}"

  # 운영과 다름(Slack, oauth2 값): 측정 경보가 팀에 가지 않게
  # SUT는 GitHub 로그인을 쓰지 않는다. oauth2-proxy는 기동할 때 GitHub에 묻지 않아 가짜 값으로도 뜬다.
  unused_oauth2_proxy_client = "unused-in-${var.environment}"

  # deploy.sh가 이 경로 아래 이름을 대문자 환경변수로 바꿔 compose에 넘긴다.
  plain_parameters = {
    "db-host"           = aws_db_instance.sut.address
    "db-port"           = tostring(aws_db_instance.sut.port)
    "db-name"           = "meterengine"
    "db-username"       = "meterengine"
    "organization-id"   = "d7cee55d-8c82-4afc-b996-6749d8b26a4e"
    "organization-name" = "소마 컴퍼니"
    "operator-email"    = "operator@${local.domain}"
    # 운영과 다름(webhook-enabled): 스냅샷의 실제 주소로 나가지 않게
    "webhook-enabled"               = "false"
    "invoice-auto-finalize-enabled" = "true"
  }
}

resource "aws_ssm_parameter" "plain" {
  for_each = local.plain_parameters

  name  = "${local.parameter_path}/${each.key}"
  type  = "String"
  value = each.value
}

resource "aws_ssm_parameter" "slack_webhook_url" {
  name  = "${local.parameter_path}/slack-webhook-url"
  type  = "SecureString"
  value = local.unreachable_slack_webhook_url
}

resource "aws_ssm_parameter" "oauth2_proxy_client_id" {
  name  = "${local.parameter_path}/oauth2-proxy-client-id"
  type  = "SecureString"
  value = local.unused_oauth2_proxy_client
}

resource "aws_ssm_parameter" "oauth2_proxy_client_secret" {
  name  = "${local.parameter_path}/oauth2-proxy-client-secret"
  type  = "SecureString"
  value = local.unused_oauth2_proxy_client
}

# --- 상태 파일에 남지 않는 비밀번호 ---

resource "aws_ssm_parameter" "db_password" {
  name             = "${local.parameter_path}/db-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.db.result
  value_wo_version = local.password_version

  # RDS만 다시 만들어지면 새 마스터 비밀번호와 이 값이 어긋난다. id는 교체될 때만 바뀌어야 하므로 자원 전체가 아니라 id를 건다.
  lifecycle {
    replace_triggered_by = [aws_db_instance.sut.id]
  }
}

resource "aws_ssm_parameter" "db_exporter_password" {
  name             = "${local.parameter_path}/db-exporter-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.db_exporter.result
  value_wo_version = local.password_version
}

resource "aws_ssm_parameter" "grafana_admin_password" {
  name             = "${local.parameter_path}/grafana-admin-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.grafana_admin.result
  value_wo_version = local.grafana_admin_password_version
}

resource "aws_ssm_parameter" "operator_password" {
  name             = "${local.parameter_path}/operator-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.operator.result
  value_wo_version = local.password_version
}

resource "aws_ssm_parameter" "frontend_api_key" {
  name             = "${local.parameter_path}/frontend-api-key"
  type             = "SecureString"
  value_wo         = "me_live_${ephemeral.random_password.frontend_api_key.result}"
  value_wo_version = local.password_version
}

# 운영과 다름(암호화 키): 운영 비밀 분리. 토스 자격을 풀지 못해 결제 호출이 실패로 끝남
resource "aws_ssm_parameter" "crypto_encryption_key" {
  name             = "${local.parameter_path}/crypto-encryption-key"
  type             = "SecureString"
  value_wo         = ephemeral.random_bytes.crypto_encryption_key.base64
  value_wo_version = local.crypto_encryption_key_version
}

resource "aws_ssm_parameter" "oauth2_proxy_cookie_secret" {
  name             = "${local.parameter_path}/oauth2-proxy-cookie-secret"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.oauth2_proxy_cookie_secret.result
  value_wo_version = local.password_version
}
