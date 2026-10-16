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

data "aws_db_subnet_group" "private" {
  name = "meterengine-rds-subnet-private"
}

data "aws_route53_zone" "main" {
  name = "meterengine.com"
}

# --- SUT EC2 ---

# Caddy가 인증서를 받으려면 80과 443이 인터넷에 열려 있어야 한다.
resource "aws_security_group" "sut" {
  name        = "${local.prefix}-ec2-sg"
  description = "SUT EC2: HTTP and HTTPS from internet"
  vpc_id      = data.aws_vpc.main.id

  tags = {
    Name = "${local.prefix}-ec2-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "sut_http" {
  security_group_id = aws_security_group.sut.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "sut_https" {
  security_group_id = aws_security_group.sut.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
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
