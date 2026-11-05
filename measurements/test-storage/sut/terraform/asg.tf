# 오토스케일링 실험: 측정한 서버를 이미지로 떠서 ASG가 같은 서버를 부하에 따라 늘리고 줄인다.
resource "aws_ami_from_instance" "sut" {
  # 이름을 바꾸면 server-1에서 다시 뜬다. v4는 10초 지표 스크립트, v5는 거기에 앱과 agents(node-exporter, cAdvisor)만 띄우는 설정이 든 이미지다.
  name               = "${local.prefix}-sut-asg-v5"
  source_instance_id = aws_instance.sut.id

  tags = {
    Name = "${local.prefix}-sut-asg-v5"
  }

  # 옛 이미지를 먼저 지우면 그 사이 시작 템플릿이 없는 이미지를 가리켜 늘리기가 실패한다.
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_launch_template" "sut" {
  name_prefix            = "${local.prefix}-sut-"
  image_id               = aws_ami_from_instance.sut.id
  instance_type          = var.sut_instance_type
  update_default_version = true

  iam_instance_profile {
    name = aws_iam_instance_profile.sut.name
  }

  # NAT가 없어 나가는 길에 공인 IP가 필요하다.
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [aws_security_group.sut.id]
  }

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    # 10초 지표 스크립트가 IMDS 태그로 자기 ASG 이름을 읽는다.
    instance_metadata_tags = "enabled"
  }

  # 줄어든 서버의 기록은 서버와 함께 사라지므로 CloudWatch 1분 CPU로 본다.
  monitoring {
    enabled = true
  }
}

resource "aws_autoscaling_group" "sut" {
  name             = "${local.prefix}-sut"
  min_size         = var.asg_min_size
  max_size         = 3
  desired_capacity = var.asg_min_size
  # 2026-11-02 여러 대 측정과 같은 조건으로 2a 하나에 둔다. 운영은 가용 영역 두 곳으로 나눈다.
  vpc_zone_identifier = [data.aws_subnet.public_2a.id]
  target_group_arns   = [aws_lb_target_group.backend.arn, aws_lb_target_group.frontend.arn]

  # ALB 헬스 체크(backend readiness)로 살았는지 본다. 유예는 띄우고 healthy까지 실측(1분 38초에서 1분 56초)의 약 두 배다.
  health_check_type         = "ELB"
  health_check_grace_period = 240
  default_instance_warmup   = 180

  # 버전 번호를 적어야 시작 템플릿이 바뀔 때 Terraform이 아래 롤링을 시작한다. $Latest면 이미 떠 있는 서버가 옛 이미지로 남는다.
  launch_template {
    id      = aws_launch_template.sut.id
    version = aws_launch_template.sut.latest_version
  }

  # 시작 템플릿이 바뀌면 새 서버가 헬스 체크를 통과한 뒤 옛 서버를 뺀다. 한 대씩.
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 100
      max_healthy_percentage = 200
      instance_warmup        = 180
    }
  }

  tag {
    key                 = "Name"
    value               = "${local.prefix}-asg"
    propagate_at_launch = true
  }

  # 공용 모니터링 서버의 Prometheus가 Role로 긁을 서버를 고르고, Environment를 env 라벨로 옮긴다. 태그는 새로 뜨는 서버부터 붙는다.
  tag {
    key                 = "Role"
    value               = "app"
    propagate_at_launch = true
  }

  tag {
    key                 = "Environment"
    value               = var.sut_name
    propagate_at_launch = true
  }

  lifecycle {
    ignore_changes = [desired_capacity]
  }
}

# 서버당 초당 600건(한 대 CPU 80% 지점 약 810건보다 낮게)을 넘으면 늘린다. ALB 1분 지표는 늦어 서버가 10초마다 올리는 값을 쓴다.
# 각 서버가 자기 초당 요청을 원하는 대수로 나눠 올리고 그 합을 본다. 계산식으로 나누면 공급자가 period를 60초로 보내 거절된다.
resource "aws_autoscaling_policy" "sut_requests" {
  name                   = "${local.prefix}-requests-per-server"
  autoscaling_group_name = aws_autoscaling_group.sut.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    customized_metric_specification {
      namespace   = "MeterEngine/Scaling"
      metric_name = "IngestRequestsPerSecondPerDesiredInstance"
      statistic   = "Sum"
      period      = 10
      metric_dimension {
        name  = "AutoScalingGroupName"
        value = aws_autoscaling_group.sut.name
      }
    }
    target_value = 600
  }
}
