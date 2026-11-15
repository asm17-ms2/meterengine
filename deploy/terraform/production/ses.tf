# 가입 인증 메일을 보내는 도메인이다. 콘솔에서 만든 것을 import 블록으로 가져왔다.
locals {
  email_domain       = "meterengine.com"
  email_from_address = "no-reply@meterengine.com"
  # Easy DKIM이 만든 토큰이다. 공개 DNS 값이라 코드에 둔다.
  email_dkim_tokens = toset([
    "7foazkcq5seycv24smkdslw7wtw3iqx4",
    "grvdqjz5esr4kkxycv7kwymcaf432fdc",
    "acsyowc4yxzp7eksbhnxifuyeykclkgq",
  ])
  email_mail_from_domain = "mail.${local.email_domain}"
  email_dns_ttl          = 1800
}

# 콘솔이 만든 기본 구성 세트다. 앱 역할 권한에 이 ARN이 없으면 SES가 발송을 403으로 거절한다.
resource "aws_sesv2_configuration_set" "default" {
  configuration_set_name = "my-first-configuration-set"

  reputation_options {
    reputation_metrics_enabled = true
  }
  sending_options {
    sending_enabled = true
  }
}

resource "aws_sesv2_email_identity" "site" {
  email_identity         = local.email_domain
  configuration_set_name = aws_sesv2_configuration_set.default.configuration_set_name

  # 지웠다 다시 만들면 DKIM 토큰이 바뀌어 인증부터 다시 해야 한다.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_sesv2_email_identity_mail_from_attributes" "site" {
  email_identity         = aws_sesv2_email_identity.site.email_identity
  mail_from_domain       = local.email_mail_from_domain
  behavior_on_mx_failure = "USE_DEFAULT_VALUE"
}

resource "aws_route53_record" "email_dkim" {
  for_each = local.email_dkim_tokens
  zone_id  = local.route53_zone_id
  name     = "${each.key}._domainkey.${local.email_domain}"
  type     = "CNAME"
  ttl      = local.email_dns_ttl
  records  = ["${each.key}.dkim.amazonses.com"]
}

resource "aws_route53_record" "email_dmarc" {
  zone_id = local.route53_zone_id
  name    = "_dmarc.${local.email_domain}"
  type    = "TXT"
  ttl     = local.email_dns_ttl
  records = ["v=DMARC1; p=none;"]
}

resource "aws_route53_record" "email_mail_from_mx" {
  zone_id = local.route53_zone_id
  name    = local.email_mail_from_domain
  type    = "MX"
  ttl     = local.email_dns_ttl
  records = ["10 feedback-smtp.${data.aws_region.current.region}.amazonses.com"]
}

resource "aws_route53_record" "email_mail_from_spf" {
  zone_id = local.route53_zone_id
  name    = local.email_mail_from_domain
  type    = "TXT"
  ttl     = local.email_dns_ttl
  records = ["v=spf1 include:amazonses.com ~all"]
}
