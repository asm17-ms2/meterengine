# 카프카를 켰을 때만 생기는 것들. 수집 서버 그룹은 이벤트를 받아 카프카에 넣고, 컨슈머 서버 그룹은 카프카를 읽어 DB에 넣는다.
# 원래 서버 그룹(asg.tf)은 api 역할로 바뀌어 관리 API, 화면, 예약 작업을 맡는다. 세 그룹이 같은 시작 템플릿과 이미지를 쓴다.

# deploy.sh가 대문자 환경변수로 바꿔 backend에 넘긴다. 서버는 뜰 때 한 번 읽는다.
resource "aws_ssm_parameter" "kafka" {
  for_each = local.kafka_enabled ? {
    "event-write-path"        = "kafka"
    "kafka-security-protocol" = "SSL"
    "kafka-bootstrap-servers" = var.kafka.bootstrap_servers
    # 먼저 뜬 서버가 토픽을 만들고 나머지는 있는 것을 그대로 쓴다.
    "kafka-create-topics" = "true"
    # 파티션마다 스레드 하나가 붙게, 파티션 수를 컨슈머 서버 대수로 나눈 값을 서버마다 띄운다.
    "kafka-consumer-threads" = tostring(ceil(local.kafka_ingest_partitions / var.kafka.consumer_count))
  } : {}

  name  = "${var.parameter_path}/${each.key}"
  type  = "String"
  value = each.value
  tags  = local.tags
}

resource "aws_lb_target_group" "ingest" {
  count = local.kafka_enabled ? 1 : 0

  name_prefix                   = "ingst-"
  port                          = 8080
  protocol                      = "HTTP"
  vpc_id                        = var.vpc_id
  load_balancing_algorithm_type = "least_outstanding_requests"
  deregistration_delay          = 30
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 5
  }
  tags = merge(local.tags, { Name = "${local.name}-ingest" })
  lifecycle {
    create_before_destroy = true
  }
}

# 리스너 규칙에 쓰이지 않는 대상 그룹은 헬스 체크가 돌지 않아 서버가 healthy가 되지 못한다.
# 실제 요청이 걸리지 않는 호스트 이름으로 먼저 이어 두고, 수집 요청을 돌리는 규칙은 서버가 healthy가 된 뒤에 만든다.
resource "aws_lb_listener_rule" "ingest_health_only" {
  count = local.kafka_enabled ? 1 : 0

  listener_arn = aws_lb_listener.https.arn
  priority     = 44
  condition {
    host_header {
      values = ["${local.name}-ingest.invalid"]
    }
  }
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ingest[0].arn
  }
}

resource "aws_autoscaling_group" "ingest" {
  count = local.kafka_enabled ? 1 : 0

  name                = "${local.name}-ingest"
  min_size            = var.kafka.ingest_min_size
  max_size            = 3
  vpc_zone_identifier = var.public_subnet_ids
  target_group_arns   = [aws_lb_target_group.ingest[0].arn]

  # 아래 리스너 규칙이 이 그룹을 기다린다. 서버가 healthy가 되기 전에 수집 요청을 이쪽으로 돌리지 않는다.
  wait_for_elb_capacity     = var.kafka.ingest_min_size
  wait_for_capacity_timeout = "20m"

  health_check_type         = "ELB"
  health_check_grace_period = 600
  default_instance_warmup   = 180

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

  instance_maintenance_policy {
    min_healthy_percentage = 100
    max_healthy_percentage = 200
  }

  depends_on = [aws_ssm_parameter.kafka, aws_lb_listener_rule.ingest_health_only]

  tag {
    key                 = "Name"
    value               = "${local.name}-ingest"
    propagate_at_launch = true
  }
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
  tag {
    key                 = "ServerRole"
    value               = "ingest"
    propagate_at_launch = true
  }

  lifecycle {
    ignore_changes = [desired_capacity]
    precondition {
      condition     = can(regex("^[0-9a-f]{40}$", one(data.aws_ssm_parameter.app_version[*].insecure_value)))
      error_message = "Record a 40-character commit SHA in the app-version parameter before turning Kafka on."
    }
  }
}

# 이벤트를 받는 일은 수집 서버 그룹이 한다. 원래 그룹의 확장 정책과 같은 지표와 목표를 쓴다.
resource "aws_autoscaling_policy" "ingest_average_cpu" {
  count = local.kafka_enabled ? 1 : 0

  name                   = "${local.name}-ingest-average-cpu"
  autoscaling_group_name = aws_autoscaling_group.ingest[0].name
  policy_type            = "TargetTrackingScaling"
  target_tracking_configuration {
    customized_metric_specification {
      namespace   = "MeterEngine/Scaling"
      metric_name = "CPUUtilizationPerDesiredInstance"
      statistic   = "Sum"
      period      = 10
      metric_dimension {
        name  = "AutoScalingGroupName"
        value = aws_autoscaling_group.ingest[0].name
      }
    }
    target_value = var.scaling_target_average_cpu_percent
  }
}

# 이벤트를 받는 두 주소(POST /v1/events, POST /v1/event-batches)만 수집 서버 그룹으로 보낸다.
# 같은 주소의 조회(GET)와 나머지 /v1/*은 api_to_backend 규칙을 따라 원래 그룹으로 간다.
resource "aws_lb_listener_rule" "ingest_to_ingest_servers" {
  count = local.kafka_enabled ? 1 : 0

  listener_arn = aws_lb_listener.https.arn
  priority     = 45
  condition {
    host_header {
      values = [var.site_domain]
    }
  }
  condition {
    path_pattern {
      values = ["/v1/events", "/v1/event-batches"]
    }
  }
  condition {
    http_request_method {
      values = ["POST"]
    }
  }
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ingest[0].arn
  }

  depends_on = [aws_autoscaling_group.ingest]
}

# 수집 서버와 달리 늘리고 줄이지 않는다. 대수가 바뀔 때마다 파티션을 다시 나눠 읽기가 멈춘다.
resource "aws_autoscaling_group" "consumer" {
  count = local.kafka_enabled ? 1 : 0

  name                = "${local.name}-consumer"
  min_size            = var.kafka.consumer_count
  max_size            = var.kafka.consumer_count
  vpc_zone_identifier = var.public_subnet_ids

  # 요청을 받지 않아 ALB에 붙이지 않는다. 서버가 죽으면 다시 띄우는 것만 맡긴다.
  health_check_type         = "EC2"
  health_check_grace_period = 600
  default_instance_warmup   = 180

  launch_template {
    id      = aws_launch_template.app.id
    version = aws_launch_template.app.latest_version
  }

  # 새 서버를 먼저 띄우면 컨슈머가 잠깐 늘어 파티션을 두 번 다시 나눈다. 한 대씩 내리고 띄운다.
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
    }
  }

  depends_on = [aws_ssm_parameter.kafka]

  tag {
    key                 = "Name"
    value               = "${local.name}-consumer"
    propagate_at_launch = true
  }
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
  tag {
    key                 = "ServerRole"
    value               = "consumer"
    propagate_at_launch = true
  }

  lifecycle {
    precondition {
      condition     = can(regex("^[0-9a-f]{40}$", one(data.aws_ssm_parameter.app_version[*].insecure_value)))
      error_message = "Record a 40-character commit SHA in the app-version parameter before turning Kafka on."
    }
  }
}
