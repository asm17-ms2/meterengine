# 서버는 staging, production과 같은 미리 구운 이미지와 부팅 스크립트로 버전 파라미터의 커밋을 배포한다.
# 그 스크립트를 바꾸면 다음 apply에서 이 시작 템플릿도 새 버전이 되고 떠 있는 서버가 바뀐다.
resource "aws_launch_template" "sut" {
  name_prefix            = "${local.prefix}-sut-"
  image_id               = var.ami_id
  instance_type          = var.sut_instance_type
  update_default_version = true
  user_data              = base64encode(templatefile(local.boot_template_path, local.boot_template_vars))

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

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 30
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
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

  # ALB 헬스 체크(backend readiness)로 살았는지 본다. 부팅이 같으니 staging, production과 같은 유예를 준다.
  health_check_type         = "ELB"
  health_check_grace_period = 600
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

  # 상태 확인으로 바꿀 때도 새 서버가 healthy가 된 뒤 옛 서버를 뺀다.
  instance_maintenance_policy {
    min_healthy_percentage = 100
    max_healthy_percentage = 200
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

    # 버전이 커밋 SHA가 아니면 서버가 부팅 중 실패해 교체만 되풀이한다.
    precondition {
      condition     = var.asg_min_size == 0 || can(regex("^[0-9a-f]{40}$", data.aws_ssm_parameter.app_version.insecure_value))
      error_message = "asg_min_size를 올리기 전에 sut-deploy.sh로 버전 파라미터에 커밋 SHA를 기록한다."
    }
  }
}

# 리소스의 value는 ignore_changes라 sut-deploy.sh가 쓴 실제 값을 따로 읽는다.
data "aws_ssm_parameter" "app_version" {
  name = aws_ssm_parameter.app_version.name
}

# 서버마다 10초마다 (CPU / 원하는 대수)를 올리고(deploy/server/cpu-metric.sh) 같은 칸의 합이 60%를 넘으면 늘린다.
resource "aws_autoscaling_policy" "sut_average_cpu" {
  name                   = "${local.prefix}-average-cpu"
  autoscaling_group_name = aws_autoscaling_group.sut.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    customized_metric_specification {
      namespace   = "MeterEngine/Scaling"
      metric_name = "CPUUtilizationPerDesiredInstance"
      statistic   = "Sum"
      period      = 10
      metric_dimension {
        name  = "AutoScalingGroupName"
        value = aws_autoscaling_group.sut.name
      }
    }
    target_value = 60
  }
}
