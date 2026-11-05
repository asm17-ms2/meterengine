# 모든 환경의 앱 서버와 DB를 긁는 공용 모니터링 서버 한 대. 기록이 이 서버 디스크에 남아 다시 만들면 지워진다.
locals {
  vpc_id   = "vpc-07498f0672b7f3c9e"
  vpc_cidr = "10.0.0.0/16"
  # 앱 서버의 백엔드, node exporter, cAdvisor와 DB 포트다.
  vpc_scrape_ports = ["8080", "9100", "9101", "5432"]
}

# 다른 루트의 앱 서버와 DB 보안 그룹이 이 이름으로 찾아 들인다. 이름을 바꾸면 그 규칙이 끊긴다.
resource "aws_security_group" "monitoring" {
  name        = "meterengine-monitoring"
  description = "Shared monitoring server: scrapes app servers and databases, no inbound"
  vpc_id      = local.vpc_id
  tags        = { Name = "meterengine-monitoring" }
}

# NAT가 없어 이미지 받기, AWS API, GitHub이 모두 인터넷 443으로 나간다.
resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.monitoring.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "scrape" {
  for_each          = toset(local.vpc_scrape_ports)
  security_group_id = aws_security_group.monitoring.id
  cidr_ipv4         = local.vpc_cidr
  ip_protocol       = "tcp"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
}

resource "aws_instance" "monitoring" {
  ami                    = "ami-0f4109eb491b0476f"
  instance_type          = "t4g.small"
  subnet_id              = "subnet-0348a18c1d399c193"
  vpc_security_group_ids = [aws_security_group.monitoring.id]
  iam_instance_profile   = aws_iam_instance_profile.monitoring.name
  # 서브넷이 공인 IP를 자동으로 주지 않아, 없으면 부팅 때 dnf와 git clone이 실패한다.
  associate_public_ip_address = true
  user_data                   = file("${path.module}/bootstrap.sh")

  metadata_options {
    http_tokens = "required"
    # 컨테이너 안의 Prometheus와 cloudwatch exporter가 역할 자격을 받으려면 한 홉이 더 필요하다.
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  tags = { Name = "meterengine-monitoring", Role = "monitoring" }

  # 기록이 디스크에 있어 AMI나 부팅 스크립트가 바뀌어도 서버를 다시 만들지 않는다.
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}
