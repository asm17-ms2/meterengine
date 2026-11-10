# 지우면 그동안 도메인이 풀리지 않는다.
resource "aws_route53_record" "app" {
  zone_id = "Z08728621CQ5R1K37A7N1"
  name    = "staging.meterengine.com"
  type    = "A"

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
      error_message = "Keep at least one app server behind the domain."
    }
  }
}
