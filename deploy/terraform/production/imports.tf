import {
  to = module.service.aws_db_instance.app
  id = "meterengine-db"
}
import {
  for_each = local.public_record_names
  to       = aws_route53_record.public[each.key]
  id       = "${local.route53_zone_id}_${each.key}_A"
}
