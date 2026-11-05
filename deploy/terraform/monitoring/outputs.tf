output "monitoring_server" {
  value = {
    instance_id         = aws_instance.monitoring.id
    security_group_id   = aws_security_group.monitoring.id
    security_group_name = aws_security_group.monitoring.name
  }
}
