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

data "aws_security_group" "sut_db" {
  name   = "${local.sut_prefix}-rds-sg"
  vpc_id = data.aws_vpc.main.id
}

resource "aws_security_group" "load_generator" {
  name        = "${local.prefix}-sg"
  description = "Load generator: no inbound, SSM session only"
  vpc_id      = data.aws_vpc.main.id

  tags = {
    Name = "${local.prefix}-sg"
  }
}

resource "aws_vpc_security_group_egress_rule" "load_generator_all" {
  security_group_id = aws_security_group.load_generator.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "sut_db_from_load_generator" {
  security_group_id            = data.aws_security_group.sut_db.id
  referenced_security_group_id = aws_security_group.load_generator.id
  ip_protocol                  = "tcp"
  from_port                    = local.sut_db_port
  to_port                      = local.sut_db_port
}
