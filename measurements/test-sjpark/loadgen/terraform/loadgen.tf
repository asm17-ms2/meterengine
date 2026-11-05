# --- AMI (최신 Amazon Linux 2023 arm64) ---

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# --- 부하 발생기 EC2 ---

resource "aws_instance" "loadgen" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.insecure_value
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnet.public_2a.id
  vpc_security_group_ids = [aws_security_group.loadgen.id]
  iam_instance_profile   = aws_iam_instance_profile.loadgen.name
  # 서브넷이 공인 IP를 자동으로 주지 않는다. 공인 IP가 없으면 인터넷에 나가지 못해 SSM 등록, k6 설치, SUT 도메인 요청이 모두 막힌다.
  associate_public_ip_address = true

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  user_data = templatefile("${path.module}/templates/loadgen-user-data.sh.tftpl", {
    k6_version = var.k6_version
  })
  # user_data는 첫 부팅에만 실행돼, 바꾸면 서버를 새로 띄워 반영한다. 서버에 상태가 없어 잃는 것이 없다.
  user_data_replace_on_change = true

  tags = {
    Name = "${local.prefix}-server"
  }

  # 프로파일만 참조하면 역할에 정책이 붙기 전에 서버가 뜰 수 있다. 그러면 SSM 등록이 늦어지고, 잠깐 파라미터를 읽을 수 있다.
  depends_on = [
    aws_iam_role_policy_attachment.loadgen_ssm_core,
    aws_iam_role_policy.loadgen,
  ]

  # 새 AMI가 나와도 서버를 교체하지 않는다. 최신 AMI는 서버를 새로 만들 때 받는다.
  # 꺼 두면 공인 IP가 반납돼 associate_public_ip_address가 false로 읽힌다. 무시하지 않으면 plan이 꺼진 서버를 교체한다.
  lifecycle {
    ignore_changes = [ami, associate_public_ip_address]
  }
}
