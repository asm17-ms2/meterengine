locals {
  name = "meterengine-${var.environment}"
  tags = { Environment = var.environment }
  # ALB가 앱 서버로 보내는 포트. frontend와 backend 대상 그룹의 포트와 같다.
  app_ports = toset(["3000", "8080"])
}

# 이름과 description은 바꾸면 그룹이 다시 만들어져 처음부터 고정 ASCII로 둔다.
resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "MeterEngine ${var.environment} ALB: HTTP and HTTPS from internet"
  vpc_id      = var.vpc_id
  tags        = local.tags
}

resource "aws_security_group" "asg" {
  name        = "${local.name}-asg"
  description = "MeterEngine ${var.environment} app servers behind the ALB"
  vpc_id      = var.vpc_id
  tags        = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "alb_web" {
  for_each          = var.alb_public_ingress ? toset(["80", "443"]) : toset([])
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  tags              = local.tags
}

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  for_each                     = local.app_ports
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.asg.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.key)
  to_port                      = tonumber(each.key)
  tags                         = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  for_each                     = local.app_ports
  security_group_id            = aws_security_group.asg.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.key)
  to_port                      = tonumber(each.key)
  tags                         = local.tags
}

# NAT가 없어 이미지, 패키지, AWS API, 결제 호출과 웹훅이 모두 이 길로 나간다.
resource "aws_vpc_security_group_egress_rule" "app_internet" {
  for_each          = toset([for port in var.app_egress_ports : tostring(port)])
  security_group_id = aws_security_group.asg.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  tags              = local.tags
}

resource "aws_vpc_security_group_egress_rule" "app_to_database" {
  security_group_id            = aws_security_group.asg.id
  referenced_security_group_id = var.database_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  tags                         = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "database_from_app" {
  security_group_id            = var.database_security_group_id
  referenced_security_group_id = aws_security_group.asg.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  tags                         = local.tags
}
