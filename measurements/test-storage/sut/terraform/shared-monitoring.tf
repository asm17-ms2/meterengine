# 공용 모니터링 서버의 보안 그룹은 다른 루트가 만들어 이름으로 찾는다. 없으면 이 루트의 plan이 멈춘다.
data "aws_security_group" "shared_monitoring" {
  vpc_id = data.aws_vpc.main.id
  name   = "meterengine-monitoring"
}

resource "aws_vpc_security_group_ingress_rule" "sut_scrape_from_shared_monitoring" {
  for_each = local.app_scrape_ports

  security_group_id            = aws_security_group.sut.id
  referenced_security_group_id = data.aws_security_group.shared_monitoring.id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}

# postgres-exporter가 공용 모니터링 서버에서 DB 통계를 읽는다.
resource "aws_vpc_security_group_ingress_rule" "db_from_shared_monitoring" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = data.aws_security_group.shared_monitoring.id
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
}
