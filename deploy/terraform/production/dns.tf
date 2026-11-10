locals {
  route53_zone_id = "Z08728621CQ5R1K37A7N1"
  # 손으로 만든 레코드를 가져온다.
  public_record_names = toset(["meterengine.com", "www.meterengine.com", "grafana.meterengine.com"])
}

resource "aws_route53_record" "public" {
  for_each = local.public_record_names
  zone_id  = local.route53_zone_id
  name     = each.key
  type     = "A"

  alias {
    name                   = module.app_fleet.alb_dns_name
    zone_id                = module.app_fleet.alb_zone_id
    evaluate_target_health = false
  }

  # 서버가 healthy가 될 때까지 기다리는 오토스케일링 그룹 변경 뒤에 바꾼다.
  depends_on = [module.app_fleet]

  lifecycle {
    prevent_destroy = true
    precondition {
      condition     = module.app_fleet.min_size >= 1
      error_message = "Keep at least one app server before pointing the domains at the ALB."
    }
  }
}
