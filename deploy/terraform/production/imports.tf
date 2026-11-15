import {
  to = module.service.aws_db_instance.app
  id = "meterengine-db"
}
import {
  for_each = local.public_record_names
  to       = aws_route53_record.public[each.key]
  id       = "${local.route53_zone_id}_${each.key}_A"
}
import {
  to = aws_sesv2_configuration_set.default
  id = "my-first-configuration-set"
}
import {
  to = aws_sesv2_email_identity.site
  id = local.email_domain
}
import {
  to = aws_sesv2_email_identity_mail_from_attributes.site
  id = local.email_domain
}
import {
  for_each = local.email_dkim_tokens
  to       = aws_route53_record.email_dkim[each.key]
  id       = "${local.route53_zone_id}_${each.key}._domainkey.${local.email_domain}_CNAME"
}
import {
  to = aws_route53_record.email_dmarc
  id = "${local.route53_zone_id}__dmarc.${local.email_domain}_TXT"
}
import {
  to = aws_route53_record.email_mail_from_mx
  id = "${local.route53_zone_id}_${local.email_mail_from_domain}_MX"
}
import {
  to = aws_route53_record.email_mail_from_spf
  id = "${local.route53_zone_id}_${local.email_mail_from_domain}_TXT"
}
