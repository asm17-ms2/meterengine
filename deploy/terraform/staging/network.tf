resource "aws_security_group" "app" {
  name        = "meterengine-staging-app"
  description = "Staging web and SSM host"
  vpc_id      = "vpc-07498f0672b7f3c9e"
  tags        = { Environment = "staging" }
}
resource "aws_security_group" "database" {
  name        = "meterengine-staging-db"
  description = "Staging private PostgreSQL"
  vpc_id      = "vpc-07498f0672b7f3c9e"
  tags        = { Environment = "staging" }
}
resource "aws_vpc_security_group_ingress_rule" "web" {
  for_each          = toset(["80", "443"])
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  tags              = { Environment = "staging" }
}
resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  tags              = { Environment = "staging" }
}
resource "aws_vpc_security_group_egress_rule" "database" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.database.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  tags                         = { Environment = "staging" }
}
resource "aws_vpc_security_group_ingress_rule" "database" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  tags                         = { Environment = "staging" }
}
