# 공용 모니터링 서버의 보안 그룹은 다른 루트가 만들어 이름으로 찾는다. 없으면 이 모듈의 plan이 멈춘다.
data "aws_security_group" "shared_monitoring" {
  vpc_id = data.aws_vpc.main.id
  name   = "meterengine-monitoring"
}

# 공용 모니터링 서버가 앱 서버의 backend, node exporter, cAdvisor와 DB를 긁는다. 수신은 긁히는 쪽이 연다.
resource "aws_vpc_security_group_ingress_rule" "app_from_monitoring" {
  for_each                     = toset(["8080", "9100", "9101"])
  security_group_id            = aws_security_group.asg.id
  referenced_security_group_id = data.aws_security_group.shared_monitoring.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.key)
  to_port                      = tonumber(each.key)
  tags                         = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "database_from_monitoring" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = data.aws_security_group.shared_monitoring.id
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  tags                         = local.tags
}
