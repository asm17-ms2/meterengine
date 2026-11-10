locals {
  name = "meterengine-${var.environment}"
  tags = { Environment = var.environment }
  # ALB가 앱 서버로 보내는 포트. frontend와 backend 대상 그룹의 포트와 같다.
  app_ports = toset(["3000", "8080"])
}

data "aws_vpc" "main" {
  id = "vpc-07498f0672b7f3c9e"
}

data "aws_subnet" "public_2a" {
  vpc_id = data.aws_vpc.main.id
  filter {
    name   = "tag:Name"
    values = ["meterengine-subnet-public1-ap-northeast-2a"]
  }
}

# ALB는 가용 영역 두 곳의 서브넷이 있어야 만들 수 있다. 서버는 2a에만 있다.
data "aws_subnet" "public_2b" {
  vpc_id = data.aws_vpc.main.id
  filter {
    name   = "tag:Name"
    values = ["meterengine-subnet-public2-ap-northeast-2b"]
  }
}

data "aws_db_subnet_group" "private" {
  name = "meterengine-rds-subnet-private"
}

data "aws_route53_zone" "main" {
  name = "meterengine.com"
}

# 이름과 description은 바꾸면 그룹이 다시 만들어져 처음부터 고정 ASCII로 둔다.
resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "MeterEngine ${var.environment} ALB: HTTP and HTTPS from internet"
  vpc_id      = data.aws_vpc.main.id
  tags        = local.tags
}

resource "aws_security_group" "asg" {
  name        = "${local.name}-asg"
  description = "MeterEngine ${var.environment} app servers behind the ALB"
  vpc_id      = data.aws_vpc.main.id
  tags        = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "alb_web" {
  for_each          = toset(["80", "443"])
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
  for_each          = toset([for port in local.app_egress_ports : tostring(port)])
  security_group_id = aws_security_group.asg.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  tags              = local.tags
}

resource "aws_vpc_security_group_egress_rule" "app_to_database" {
  security_group_id            = aws_security_group.asg.id
  referenced_security_group_id = aws_security_group.db.id
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  tags                         = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "database_from_app" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.asg.id
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  tags                         = local.tags
}
