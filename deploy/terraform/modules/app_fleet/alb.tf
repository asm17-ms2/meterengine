resource "aws_lb" "app" {
  name               = "${local.name}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids
  # 결제 승인 조회(읽기 60초)와 프론트의 백엔드 대기(최대 120초)보다 길게 둔다.
  idle_timeout = 130
  # 백엔드는 맨 오른쪽 X-Forwarded-For를 사용자 IP로 읽는다. ALB가 받은 값 뒤에 붙여야 위조한 값이 맨 오른쪽에 오지 않는다.
  xff_header_processing_mode = "append"
  enable_xff_client_port     = false
  enable_deletion_protection = var.alb_deletion_protection
  # 접속 로그는 켜지 않는다. 토큰이 실린 URL이 로그에 그대로 남는다.
  tags = merge(local.tags, { Name = "${local.name}-alb" })
}

locals {
  certificate_domains = toset(concat([var.site_domain], var.site_aliases))
  validation_options  = { for option in aws_acm_certificate.site.domain_validation_options : option.domain_name => option }
}

resource "aws_acm_certificate" "site" {
  domain_name               = var.site_domain
  subject_alternative_names = var.site_aliases
  validation_method         = "DNS"
  tags                      = local.tags
  lifecycle {
    create_before_destroy = true
  }
}

# 키를 설정 값으로 고정한다. domain_validation_options로 돌면 plan에서 키를 몰라 for_each가 멈춘다.
resource "aws_route53_record" "certificate_validation" {
  for_each        = local.certificate_domains
  zone_id         = var.route53_zone_id
  name            = local.validation_options[each.key].resource_record_name
  type            = local.validation_options[each.key].resource_record_type
  records         = [local.validation_options[each.key].resource_record_value]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "site" {
  certificate_arn         = aws_acm_certificate.site.arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}

# API 기본 정책은 TLS 1.3이 없어 명시한다. 도메인이 아닌 Host는 서버에 닿지 않게 404로 끝낸다.
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.site.certificate_arn
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
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

# port, protocol, name_prefix, vpc_id, target_type을 바꾸면 그룹이 교체된다. 서버가 있으면 새 그룹이 healthy가 될 때까지 503이다.
resource "aws_lb_target_group" "frontend" {
  name_prefix = "front-"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  # 기본 300초면 축소와 롤링 때 서버마다 5분을 기다린다. 가장 느린 조회도 DB 소켓 제한 30초 안에 끝난다.
  deregistration_delay = 30
  health_check {
    path    = "/health"
    matcher = "200"
    # 약 75초 연속 실패해야 뺀다. 컨테이너가 스스로 다시 뜨는 시간보다 길게 둔다.
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 5
  }
  tags = merge(local.tags, { Name = "${local.name}-frontend" })
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_target_group" "backend" {
  name_prefix = "back-"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  # 막 뜬 서버는 JVM이 덜 데워져 같은 양에 CPU를 더 쓴다. 처리 중 요청이 적은 서버로 보낸다.
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
  tags = merge(local.tags, { Name = "${local.name}-backend" })
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_listener_rule" "alias_to_site" {
  count        = length(var.site_aliases) > 0 ? 1 : 0
  listener_arn = aws_lb_listener.https.arn
  priority     = 10
  condition {
    host_header {
      values = var.site_aliases
    }
  }
  action {
    type = "redirect"
    redirect {
      host        = var.site_domain
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# 프론트 헬스 주소는 밖에 열지 않는다. ALB 헬스 체크는 리스너를 거치지 않아 영향이 없다.
resource "aws_lb_listener_rule" "hide_frontend_health" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 40
  condition {
    host_header {
      values = [var.site_domain]
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

resource "aws_lb_listener_rule" "api_to_backend" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 50
  condition {
    host_header {
      values = [var.site_domain]
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

resource "aws_lb_listener_rule" "site_to_frontend" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 100
  condition {
    host_header {
      values = [var.site_domain]
    }
  }
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}
