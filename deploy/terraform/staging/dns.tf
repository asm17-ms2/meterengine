resource "aws_eip" "app" {
  domain = "vpc"
  tags   = { Environment = "staging" }
}
resource "aws_eip_association" "app" {
  instance_id   = module.service.instance_id
  allocation_id = aws_eip.app.id
}
resource "aws_route53_record" "app" {
  zone_id = "Z08728621CQ5R1K37A7N1"
  name    = "staging.meterengine.com"
  type    = "A"
  ttl     = 60
  records = [aws_eip.app.public_ip]
}
