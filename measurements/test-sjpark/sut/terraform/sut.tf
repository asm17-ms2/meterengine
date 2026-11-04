resource "aws_instance" "sut" {
  ami                    = var.ami_id
  instance_type          = var.sut_instance_type
  subnet_id              = data.aws_subnet.public_2a.id
  vpc_security_group_ids = [aws_security_group.sut.id]
  iam_instance_profile   = aws_iam_instance_profile.sut.name
  # 서브넷이 공인 IP를 자동으로 주지 않아, 없으면 EIP가 붙기 전 user_data의 dnf와 git clone이 실패한다.
  associate_public_ip_address = true

  metadata_options {
    http_tokens = "required"
    # 컨테이너 안의 cloudwatch exporter가 역할 자격을 받으려면 한 홉이 더 필요하다.
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  user_data = templatefile("${path.module}/templates/sut-user-data.sh.tftpl", {
    param_path = local.param_path
    domain     = local.domain
  })

  tags = {
    Name = "${local.prefix}-server"
  }

  # 다시 만들면 Caddy가 인증서를 새로 받아 Let's Encrypt 발급 횟수 제한에 걸릴 수 있다.
  lifecycle {
    ignore_changes = [user_data, ami]
  }
}

resource "aws_eip" "sut" {
  domain   = "vpc"
  instance = aws_instance.sut.id

  tags = {
    Name = local.prefix
  }
}

resource "aws_route53_record" "sut" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = local.domain
  type    = "A"
  ttl     = 60
  records = [aws_eip.sut.public_ip]
}
