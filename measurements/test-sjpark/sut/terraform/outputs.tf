output "domain" {
  description = "SUT 도메인"
  value       = local.domain
}

output "param_path" {
  description = "SUT가 읽는 Parameter Store 경로"
  value       = local.param_path
}

output "sut_instance_id" {
  description = "SUT EC2 인스턴스 ID"
  value       = aws_instance.sut.id
}

output "sut_public_ip" {
  description = "SUT 탄력적 IP. 도메인 A 레코드가 가리키는 값"
  value       = aws_eip.sut.public_ip
}

output "db_identifier" {
  description = "SUT RDS 인스턴스 식별자"
  value       = aws_db_instance.sut.identifier
}

output "db_address" {
  description = "SUT RDS 엔드포인트 호스트. deploy.sh 로그의 DB 줄과 대조하는 값"
  value       = aws_db_instance.sut.address
}
