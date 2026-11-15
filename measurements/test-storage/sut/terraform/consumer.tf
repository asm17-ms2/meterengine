# 카프카를 읽어 DB에 넣는 컨슈머 서버. 수집 서버와 같은 이미지와 부팅 스크립트로 뜨고 ServerRole 태그만 다르다.
# 수집 서버와 달리 늘리고 줄이지 않는다. 대수가 바뀔 때마다 파티션을 다시 나눠 읽기가 멈추기 때문이다.
# 브로커가 인증을 하지 않아 역할로 권한을 나눌 수 없다. 수집 서버와 같은 역할을 쓴다.

resource "aws_launch_template" "consumer" {
  name_prefix            = "${local.prefix}-consumer-"
  image_id               = var.ami_id
  instance_type          = var.sut_instance_type
  update_default_version = true
  user_data              = base64encode(templatefile(local.boot_template_path, local.boot_template_vars))

  iam_instance_profile {
    name = aws_iam_instance_profile.sut.name
  }

  # NAT가 없어 나가는 길에 공인 IP가 필요하다. 보안 그룹은 수집 서버 것을 같이 써서 DB와 모니터링 규칙을 그대로 받는다.
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [aws_security_group.sut.id]
  }

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    # deploy.sh가 IMDS 태그로 ServerRole을 읽는다.
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

  monitoring {
    enabled = true
  }
}

resource "aws_autoscaling_group" "consumer" {
  name             = "${local.prefix}-consumer"
  min_size         = var.consumer_count
  max_size         = 6
  desired_capacity = var.consumer_count
  # 수집 서버와 같은 가용 영역에 둔다.
  vpc_zone_identifier = [data.aws_subnet.public_2a.id]

  # 요청을 받지 않아 ALB에 붙이지 않는다. 서버가 죽으면 다시 띄우는 것만 맡긴다.
  health_check_type         = "EC2"
  health_check_grace_period = 600

  launch_template {
    id      = aws_launch_template.consumer.id
    version = aws_launch_template.consumer.latest_version
  }

  # 새 서버를 먼저 띄우면 컨슈머가 잠깐 늘어 파티션을 두 번 다시 나눈다. 한 대씩 내리고 띄운다.
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
    }
  }

  tag {
    key                 = "Name"
    value               = "${local.prefix}-consumer"
    propagate_at_launch = true
  }

  # 공용 모니터링 서버의 Prometheus가 Role로 긁을 서버를 고른다. 컨슈머 지표도 같은 포트로 낸다.
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

  # deploy.sh가 이 태그를 METERENGINE_ROLE로 넘겨 backend가 카프카 리스너만 켠다.
  tag {
    key                 = "ServerRole"
    value               = "consumer"
    propagate_at_launch = true
  }

  # 서버는 부팅할 때 카프카 파라미터를 한 번만 읽는다. 파라미터보다 먼저 뜨면 오류 없이 리스너 없는 서버가 된다.
  depends_on = [aws_ssm_parameter.kafka]

  lifecycle {
    ignore_changes = [desired_capacity]

    precondition {
      condition     = var.consumer_count == 0 || var.kafka_enabled
      error_message = "consumer_count를 올리려면 kafka_enabled가 true여야 한다. 읽을 클러스터가 없다."
    }

    # 버전이 커밋 SHA가 아니면 서버가 부팅 중 실패해 교체만 되풀이한다.
    precondition {
      condition     = var.consumer_count == 0 || can(regex("^[0-9a-f]{40}$", data.aws_ssm_parameter.app_version.insecure_value))
      error_message = "consumer_count를 올리기 전에 sut-deploy.sh로 버전 파라미터에 커밋 SHA를 기록한다."
    }
  }
}

# 수집 서버 ASG와 같은 시각에 0대로 내린다. MSK는 멈출 수 없어 kafka_enabled=false로 apply해야 과금이 멈춘다.
resource "aws_autoscaling_schedule" "stop_consumer" {
  count = var.auto_stop_state == "ENABLED" ? 1 : 0

  scheduled_action_name  = "${local.prefix}-stop-consumer"
  autoscaling_group_name = aws_autoscaling_group.consumer.name
  recurrence             = "0 4 * * *"
  time_zone              = local.auto_stop_timezone
  min_size               = 0
  max_size               = 6
  desired_capacity       = 0
}
