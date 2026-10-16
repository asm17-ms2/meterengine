# --- 출력 ---

output "instance_id" {
  description = "부하 발생기 EC2 인스턴스 ID"
  value       = aws_instance.loadgen.id
}
