# --- 기존 VPC와 서브넷 (조회만) ---

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

# --- 보안 그룹 ---

# 접속과 대시보드는 SSM으로만 하므로 인바운드 규칙을 두지 않는다.
resource "aws_security_group" "loadgen" {
  name        = "${local.prefix}-sg"
  description = "k6 load generator: no inbound, all outbound"
  vpc_id      = data.aws_vpc.main.id

  tags = {
    Name = "${local.prefix}-sg"
  }
}

resource "aws_vpc_security_group_egress_rule" "loadgen_all" {
  security_group_id = aws_security_group.loadgen.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
