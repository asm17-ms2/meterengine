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

# --- ALB ---

resource "aws_security_group" "alb" {
  name        = "${local.prefix}-alb-sg"
  description = "SUT ALB: HTTP and HTTPS from internet"
  vpc_id      = data.aws_vpc.main.id

  tags = {
    Name = "${local.prefix}-alb-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_to_sut_frontend" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.sut.id
  ip_protocol                  = "tcp"
  from_port                    = 3000
  to_port                      = 3000
}

# 수집 API와 공개 API는 ALB가 백엔드(8080)에 바로 보낸다.
resource "aws_vpc_security_group_egress_rule" "alb_to_sut_backend" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.sut.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# --- SUT EC2 ---

# description을 바꾸면 보안 그룹을 다시 만들어 RDS 규칙까지 바뀌므로 그대로 둔다. 인터넷에서 들어오는 길은 ALB뿐이다.
resource "aws_security_group" "sut" {
  name        = "${local.prefix}-ec2-sg"
  description = "SUT EC2: HTTP and HTTPS from internet"
  vpc_id      = data.aws_vpc.main.id

  tags = {
    Name = "${local.prefix}-ec2-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "sut_frontend_from_alb" {
  security_group_id            = aws_security_group.sut.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 3000
  to_port                      = 3000
}

resource "aws_vpc_security_group_ingress_rule" "sut_backend_from_alb" {
  security_group_id            = aws_security_group.sut.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_vpc_security_group_egress_rule" "sut_all" {
  security_group_id = aws_security_group.sut.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# --- SUT RDS ---

resource "aws_security_group" "db" {
  name        = "${local.prefix}-rds-sg"
  description = "SUT RDS: PostgreSQL from SUT EC2"
  vpc_id      = data.aws_vpc.main.id

  tags = {
    Name = "${local.prefix}-rds-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_sut" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.sut.id
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
}

resource "aws_vpc_security_group_egress_rule" "db_all" {
  security_group_id = aws_security_group.db.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
