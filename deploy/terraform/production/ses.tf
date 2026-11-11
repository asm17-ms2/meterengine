# 가입 인증 메일을 보내는 도메인이다. 도메인 인증, Easy DKIM, MAIL FROM, DMARC는 콘솔에서 만들어 Terraform 밖에 있다.
locals {
  email_domain       = "meterengine.com"
  email_from_address = "no-reply@meterengine.com"
  email_identity_arn = "arn:aws:ses:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:identity/${local.email_domain}"
}
