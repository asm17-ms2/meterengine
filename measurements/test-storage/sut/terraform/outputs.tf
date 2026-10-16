output "param_path" {
  description = "SUT가 읽는 Parameter Store 경로"
  value       = local.param_path
}

output "db_identifier" {
  description = "SUT RDS 인스턴스 식별자"
  value       = aws_db_instance.sut.identifier
}

output "db_address" {
  description = "SUT RDS 엔드포인트 호스트. deploy.sh 로그의 DB 줄과 대조하는 값"
  value       = aws_db_instance.sut.address
}
