output "alb_dns_name" {
  value = aws_lb.app.dns_name
}
output "alb_zone_id" {
  value = aws_lb.app.zone_id
}
output "autoscaling_group_name" {
  value = aws_autoscaling_group.app.name
}
output "autoscaling_group_arn" {
  value = aws_autoscaling_group.app.arn
}
output "app_version_parameter_name" {
  value = aws_ssm_parameter.app_version.name
}
output "min_size" {
  value = aws_autoscaling_group.app.min_size
}
output "https_listener_arn" {
  value = aws_lb_listener.https.arn
}
output "alb_security_group_id" {
  value = aws_security_group.alb.id
}
output "alb_arn_suffix" {
  value = aws_lb.app.arn_suffix
}
output "alb_public_ingress_ports" {
  value = toset(keys(aws_vpc_security_group_ingress_rule.alb_web))
}
output "app_security_group_id" {
  value = aws_security_group.asg.id
}
# 카프카를 켰을 때만 있는 서버 그룹. 배포가 이 그룹들도 새 버전으로 바꾼다.
output "kafka_autoscaling_group_names" {
  value = concat(aws_autoscaling_group.consumer[*].name, aws_autoscaling_group.ingest[*].name)
}
output "kafka_autoscaling_group_arns" {
  value = concat(aws_autoscaling_group.consumer[*].arn, aws_autoscaling_group.ingest[*].arn)
}
# 아래는 루트의 시험이 서버 그룹 구성을 확인하는 데 쓴다.
output "app_group_tags" {
  value = aws_autoscaling_group.app.tag
}
output "app_group_refresh_triggers" {
  value = aws_autoscaling_group.app.instance_refresh[0].triggers
}
output "kafka_parameters" {
  value = { for key, parameter in aws_ssm_parameter.kafka : key => parameter.value }
}
output "kafka_parameter_names" {
  value = [for parameter in aws_ssm_parameter.kafka : parameter.name]
}
output "ingest_rule_methods" {
  value = toset(flatten(aws_lb_listener_rule.ingest_to_ingest_servers[*].condition[*].http_request_method[*].values))
}
output "ingest_rule_paths" {
  value = toset(flatten(aws_lb_listener_rule.ingest_to_ingest_servers[*].condition[*].path_pattern[*].values))
}
output "ingest_rule_priority" {
  value = one(aws_lb_listener_rule.ingest_to_ingest_servers[*].priority)
}
output "kafka_group_roles" {
  value = {
    for group in concat(aws_autoscaling_group.consumer, aws_autoscaling_group.ingest) :
    group.name => one([for tag in group.tag : tag.value if tag.key == "ServerRole"])
  }
}
