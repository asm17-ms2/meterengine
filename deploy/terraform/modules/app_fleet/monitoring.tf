# 공용 모니터링 서버가 앱 서버의 backend, node exporter, cAdvisor와 DB를 긁는다. 수신은 긁히는 쪽이 연다.
resource "aws_vpc_security_group_ingress_rule" "app_from_monitoring" {
  for_each                     = toset(["8080", "9100", "9101"])
  security_group_id            = aws_security_group.asg.id
  referenced_security_group_id = var.monitoring_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.key)
  to_port                      = tonumber(each.key)
  tags                         = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "database_from_monitoring" {
  security_group_id            = var.database_security_group_id
  referenced_security_group_id = var.monitoring_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  tags                         = local.tags
}
