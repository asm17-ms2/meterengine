# 옛 서버 IP다. 도메인을 옛 서버로 되돌릴 때 쓴다.
resource "aws_eip" "app" {
  domain = "vpc"
  tags   = { Environment = "staging" }
}
resource "aws_eip_association" "app" {
  instance_id   = module.service.instance_id
  allocation_id = aws_eip.app.id
}
# A 레코드와 ALB 별칭을 오가도 같은 레코드를 제자리에서 고친다. 지우면 그동안 도메인이 풀리지 않는다.
resource "aws_route53_record" "app" {
  zone_id = "Z08728621CQ5R1K37A7N1"
  name    = "staging.meterengine.com"
  type    = "A"
  ttl     = var.serve_domain_from_alb ? null : 60
  records = var.serve_domain_from_alb ? null : [aws_eip.app.public_ip]

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
      error_message = "Keep at least one app server before pointing the domain at the ALB."
    }
  }
}
