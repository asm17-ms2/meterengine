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
