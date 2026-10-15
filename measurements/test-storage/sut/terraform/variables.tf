variable "sut_name" {
  description = "이름 접두, 서브도메인, 파라미터 경로를 정하는 SUT 이름"
  type        = string
  default     = "test-storage"

  # prod로 두면 파라미터 경로가 개발 서버의 /meterengine/prod와 겹친다.
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]*$", var.sut_name)) && var.sut_name != "prod"
    error_message = "sut_name은 소문자, 숫자, 하이픈만 쓰고 prod는 쓸 수 없다."
  }
}

variable "owner" {
  description = "Owner 태그 값"
  type        = string
  default     = "moonino"
}

variable "db_instance_class" {
  description = "SUT RDS 인스턴스 클래스"
  type        = string
  default     = "db.m7g.large"
}

variable "db_max_allocated_storage" {
  description = "SUT RDS 스토리지 자동 확장 상한(GB)"
  type        = number
  default     = 100
}
