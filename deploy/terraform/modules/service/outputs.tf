output "instance_id" {
  value = one(aws_instance.app[*].id)
}
output "database_identifier" {
  value = aws_db_instance.app.identifier
}
output "database_host" {
  value = aws_db_instance.app.address
}
