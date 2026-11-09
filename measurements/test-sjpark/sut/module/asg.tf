locals {
  app_version_parameter_name = "${local.parameter_path}/app-version"
  # 부팅 템플릿이 받는 값의 전부다. 키는 deploy/tests의 시험이 템플릿과 대조한다.
  boot_template_vars = {
    environment                = var.environment
    parameter_path             = local.parameter_path
    app_version_parameter_name = local.app_version_parameter_name
    aws_region                 = data.aws_region.current.region
    repository_url             = "https://github.com/asm17-ms2/meterengine.git"
  }
}

resource "aws_launch_template" "app" {
  name_prefix = "${local.name}-app-"
  image_id    = var.ami_id
  # 운영과 다름(인스턴스 유형): 측정 목적
  instance_type          = var.instance_type
  update_default_version = true
  user_data              = base64encode(templatefile(local.boot_template_path, local.boot_template_vars))

  iam_instance_profile {
    name = aws_iam_instance_profile.app.name
  }

  # NAT가 없어 나가는 길에 공인 IP가 필요하다.
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [aws_security_group.asg.id]
    delete_on_termination       = true
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    # 10초 지표 스크립트가 태그로 자기 ASG 이름을 읽는다.
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

  tags = local.tags
  lifecycle {
    create_before_destroy = true
  }
}

# 서버를 켤 때만 읽는다. 0대인 새 환경은 파라미터가 생기기 전에도 plan이 된다.
data "aws_ssm_parameter" "app_version" {
  count = var.asg_min_size > 0 ? 1 : 0
  name  = local.app_version_parameter_name
}

resource "aws_autoscaling_group" "app" {
  name     = "${local.name}-app"
  min_size = var.asg_min_size
  # 운영과 다름(최대 대수): 한 대 한계 측정
  max_size = var.asg_max_size
  # 운영과 다름(서버 가용 영역): DB와 같은 AZ로 네트워크 조건 고정
  vpc_zone_identifier = [data.aws_subnet.public_2a.id]
  target_group_arns   = [aws_lb_target_group.frontend.arn, aws_lb_target_group.backend.arn]

  # 최소 대수를 바꾸는 apply가 두 대상 그룹에서 그만큼 healthy가 될 때까지 기다려, 서버 없이 apply가 끝나지 않게 한다.
  wait_for_elb_capacity     = var.asg_min_size > 0 ? var.asg_min_size : null
  wait_for_capacity_timeout = "20m"

  # 새 서버가 저장소를 받고 배포를 마칠 때까지 헬스 체크로 바꾸지 않게 넉넉히 기다린다.
  health_check_type         = "ELB"
  health_check_grace_period = 600
  default_instance_warmup   = 180

  # $Latest로 두면 템플릿이 바뀌어도 Terraform이 아래 롤링을 시작하지 않는다.
  launch_template {
    id      = aws_launch_template.app.id
    version = aws_launch_template.app.latest_version
  }

  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 100
      max_healthy_percentage = 200
      instance_warmup        = 180
    }
  }

  # 상태 확인으로 바꿀 때도 새 서버가 healthy가 된 뒤 옛 서버를 뺀다. 한 대뿐인 서버를 먼저 끄지 않는다.
  instance_maintenance_policy {
    min_healthy_percentage = 100
    max_healthy_percentage = 200
  }

  tag {
    key                 = "Name"
    value               = "${local.name}-app"
    propagate_at_launch = true
  }
  # 공용 모니터링이 Role로 긁을 서버를 고르고 Environment를 env 라벨로 옮긴다.
  tag {
    key                 = "Role"
    value               = "app"
    propagate_at_launch = true
  }
  tag {
    key                 = "Environment"
    value               = var.environment
    propagate_at_launch = true
  }

  # 원하는 대수는 확장 정책이 정한다.
  lifecycle {
    ignore_changes = [desired_capacity]
    # 자리 값이나 빈 값으로 서버를 띄우면 부팅이 실패해 교체만 되풀이한다.
    precondition {
      condition     = var.asg_min_size == 0 || can(regex("^[0-9a-f]{40}$", one(data.aws_ssm_parameter.app_version[*].insecure_value)))
      error_message = "Record a 40-character commit SHA in the app-version parameter before keeping servers running."
    }
  }
}

# 서버마다 (초당 수집 요청 / 원하는 대수)를 올리고 그 합을 본다. 계산식은 공급자가 period를 못 보내 60초로 가서 쓰지 않는다.
resource "aws_autoscaling_policy" "ingest_requests" {
  name                   = "${local.name}-ingest-requests-per-server"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"
  target_tracking_configuration {
    customized_metric_specification {
      namespace   = "MeterEngine/Scaling"
      metric_name = "IngestRequestsPerSecondPerDesiredInstance"
      statistic   = "Sum"
      period      = 10
      metric_dimension {
        name  = "AutoScalingGroupName"
        value = aws_autoscaling_group.app.name
      }
    }
    target_value = local.scaling_target_requests_per_second
  }
}
