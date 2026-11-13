# 가입 인증 메일을 보내는 도메인이다. 도메인 인증, Easy DKIM, MAIL FROM, DMARC는 콘솔에서 만들어 Terraform 밖에 있다.
locals {
  email_domain       = "meterengine.com"
  email_from_address = "no-reply@meterengine.com"
  email_identity_arn = "arn:aws:ses:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:identity/${local.email_domain}"
  # 콘솔이 자격 증명의 기본 구성 세트로 걸어 둔 것이다. 권한에 없으면 SES가 발송을 403으로 거절한다.
  email_configuration_set_arn = "arn:aws:ses:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:configuration-set/my-first-configuration-set"
}
