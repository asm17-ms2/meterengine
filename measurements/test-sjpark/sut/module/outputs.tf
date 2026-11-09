output "domain" {
  value = local.domain
}
output "param_path" {
  value = local.parameter_path
}
output "asg_name" {
  value = aws_autoscaling_group.app.name
}
output "app_version_parameter_name" {
  value = aws_ssm_parameter.app_version.name
}
output "db_identifier" {
  value = aws_db_instance.sut.identifier
}
output "db_address" {
  value = aws_db_instance.sut.address
}
# CloudWatch AWS/ApplicationELB 지표의 LoadBalancer, TargetGroup 차원 값. 대상 그룹은 수집 API가 가는 backend다.
output "alb_arn_suffix" {
  value = aws_lb.app.arn_suffix
}
output "target_group_arn_suffix" {
  value = aws_lb_target_group.backend.arn_suffix
}
