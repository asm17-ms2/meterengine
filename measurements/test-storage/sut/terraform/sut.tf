resource "aws_route53_record" "sut" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = local.domain
  type    = "A"

  alias {
    name                   = aws_lb.sut.dns_name
    zone_id                = aws_lb.sut.zone_id
    evaluate_target_health = false
  }
}
