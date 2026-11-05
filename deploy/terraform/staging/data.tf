# 공용 모니터링 루트가 만든다. 그 그룹을 다시 만들거나 이름을 바꾸면 이 루트의 plan이 멈춘다.
data "aws_security_group" "monitoring" {
  name   = "meterengine-monitoring"
  vpc_id = "vpc-07498f0672b7f3c9e"
}
