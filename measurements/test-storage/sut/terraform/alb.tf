resource "aws_lb" "sut" {
  name               = "${local.prefix}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [data.aws_subnet.public_2a.id, data.aws_subnet.public_2b.id]

  tags = {
    Name = "${local.prefix}-alb"
  }
}

resource "aws_lb_target_group" "frontend" {
  name_prefix = "front-"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.main.id
  # 빠지는 서버가 하던 요청을 끝낼 시간. 기본 300초면 축소와 롤링 배포 때 서버마다 5분을 기다린다. 가장 느린 조회도 DB 소켓 제한 30초 안에 끝난다.
  deregistration_delay = 30

  health_check {
    path                = "/health"
    matcher             = "200"
    interval            = 10
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 2
  }

  tags = {
    Name = "${local.prefix}-frontend"
  }

  # 이름이 고정이면 교체할 때 옛 그룹을 먼저 지우려다 규칙이 쓰는 중이라 실패한다(ResourceInUse). 새 그룹을 먼저 만든다.
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate" "sut" {
  domain_name       = local.domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "sut_certificate_validation" {
  for_each = {
    for option in aws_acm_certificate.sut.domain_validation_options : option.domain_name => option
  }

  zone_id         = data.aws_route53_zone.main.zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  records         = [each.value.resource_record_value]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "sut" {
  certificate_arn         = aws_acm_certificate.sut.arn
  validation_record_fqdns = [for record in aws_route53_record.sut_certificate_validation : record.fqdn]
}

# API로 만들면 기본 정책이 TLS 1.3 없는 ELBSecurityPolicy-2016-08이라 명시한다.
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.sut.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.sut.certificate_arn

  # 도메인이 아닌 Host는 서버에 닿지 않게 여기서 끝낸다.
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "site" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 100

  condition {
    host_header {
      values = [local.domain]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

# 프론트 헬스 체크 주소는 밖에 열지 않는다. ALB 헬스 체크는 리스너를 거치지 않아 영향이 없다.
resource "aws_lb_listener_rule" "hide_frontend_health" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 40

  condition {
    host_header {
      values = [local.domain]
    }
  }

  condition {
    path_pattern {
      values = ["/health"]
    }
  }

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.sut.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# --- 수집 API와 공개 API는 백엔드로 바로 보낸다 ---

resource "aws_lb_target_group" "backend" {
  name_prefix = "back-"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.main.id
  # 막 뜬 서버는 JVM이 덜 데워져 같은 양에 CPU를 두 배 넘게 쓴다(2026-11-03 측정, 98%와 46%). 처리 중 요청이 적은 서버에 보내 받을 수 있는 만큼만 준다.
  load_balancing_algorithm_type = "least_outstanding_requests"
  # 빠지는 서버가 하던 요청을 끝낼 시간. 기본 300초면 축소와 롤링 배포 때 서버마다 5분을 기다린다. 가장 느린 조회도 DB 소켓 제한 30초 안에 끝난다.
  deregistration_delay = 30

  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 10
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 2
  }

  tags = {
    Name = "${local.prefix}-backend"
  }

  # 이름이 고정이면 교체할 때 옛 그룹을 먼저 지우려다 규칙이 쓰는 중이라 실패한다(ResourceInUse). 새 그룹을 먼저 만든다.
  lifecycle {
    create_before_destroy = true
  }
}

# 도메인 규칙(100)보다 먼저 본다. 프론트의 백엔드 호출은 ALB를 거치지 않고 서버 안에서 백엔드로 간다.
resource "aws_lb_listener_rule" "ingest_to_backend" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 50

  condition {
    host_header {
      values = [local.domain]
    }
  }

  condition {
    path_pattern {
      values = ["/v1/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}
