output "load_generator_instance_id" {
  description = "부하 발생기 EC2 인스턴스 ID"
  value       = aws_instance.load_generator.id
}
