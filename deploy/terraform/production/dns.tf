locals {
  route53_zone_id = "Z08728621CQ5R1K37A7N1"
  # 손으로 만든 레코드를 가져온다. 집합이 serve_domain_from_alb와 무관해 어느 쪽으로 바꿔도 지워지지 않는다.
  public_record_names = toset(["meterengine.com", "www.meterengine.com", "grafana.meterengine.com"])
}

# A 레코드와 ALB 별칭을 오가도 같은 레코드를 제자리에서 고친다. 되돌릴 때 이 파일을 git revert하지 않는다.
resource "aws_route53_record" "public" {
  for_each = local.public_record_names
  zone_id  = local.route53_zone_id
  name     = each.key
  type     = "A"
  ttl      = var.serve_domain_from_alb ? null : 60
  records  = var.serve_domain_from_alb ? null : [var.legacy_server_ip]

  dynamic "alias" {
    for_each = var.serve_domain_from_alb ? [module.app_fleet.alb_dns_name] : []
    content {
      name                   = alias.value
      zone_id                = module.app_fleet.alb_zone_id
      evaluate_target_health = false
    }
  }

  # 서버가 healthy가 될 때까지 기다리는 오토스케일링 그룹 변경 뒤에 바꾼다.
  depends_on = [module.app_fleet]

  lifecycle {
    prevent_destroy = true
    precondition {
      condition     = !var.serve_domain_from_alb || module.app_fleet.min_size >= 1
      error_message = "Keep at least one app server before pointing the domains at the ALB."
    }
  }
}
