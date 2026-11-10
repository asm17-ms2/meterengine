resource "aws_security_group" "database" {
  name        = "meterengine-staging-db"
  description = "Staging private PostgreSQL"
  vpc_id      = "vpc-07498f0672b7f3c9e"
  tags        = { Environment = "staging" }
}
