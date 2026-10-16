data "aws_vpc" "main" {
  id = "vpc-07498f0672b7f3c9e"
}

data "aws_db_subnet_group" "private" {
  name = "meterengine-rds-subnet-private"
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

resource "aws_vpc_security_group_egress_rule" "db_all" {
  security_group_id = aws_security_group.db.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
