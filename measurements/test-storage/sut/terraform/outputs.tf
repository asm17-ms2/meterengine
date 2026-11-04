output "domain" {
  description = "SUT 도메인"
  value       = local.domain
}

output "param_path" {
  description = "SUT가 읽는 Parameter Store 경로"
  value       = local.param_path
}

output "alb_dns_name" {
  description = "도메인 A 레코드 별칭이 가리키는 ALB 이름"
  value       = aws_lb.sut.dns_name
}

output "alb_arn_suffix" {
  description = "CloudWatch AWS/ApplicationELB 지표의 LoadBalancer 차원 값"
  value       = aws_lb.sut.arn_suffix
}

output "frontend_target_group_arn_suffix" {
  description = "화면 요청 타깃 그룹의 CloudWatch TargetGroup 차원 값"
  value       = aws_lb_target_group.frontend.arn_suffix
}

output "backend_target_group_arn_suffix" {
  description = "수집 API 타깃 그룹의 CloudWatch TargetGroup 차원 값"
  value       = aws_lb_target_group.backend.arn_suffix
}

output "db_identifier" {
  description = "SUT RDS 인스턴스 식별자"
  value       = aws_db_instance.sut.identifier
}

output "db_address" {
  description = "SUT RDS 엔드포인트 호스트. deploy.sh 로그의 DB 줄과 대조하는 값"
  value       = aws_db_instance.sut.address
}

output "asg_name" {
  description = "앱 서버 오토스케일링 그룹 이름"
  value       = aws_autoscaling_group.sut.name
}

output "app_version_parameter_name" {
  description = "sut-deploy.sh가 커밋 SHA를 쓰고 서버가 부팅 때 읽는 버전 파라미터 이름"
  value       = aws_ssm_parameter.app_version.name
}
