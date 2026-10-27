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

variable "sut_instance_type" {
  description = "SUT EC2 인스턴스 유형"
  type        = string
  default     = "m7g.large"
}

variable "db_instance_class" {
  description = "SUT RDS 인스턴스 클래스"
  type        = string
  default     = "db.m7g.large"
}

variable "ami_id" {
  description = "SUT EC2 AMI. 개발 서버와 같은 Amazon Linux 2023 arm64"
  type        = string
  default     = "ami-0f4109eb491b0476f"
}

variable "db_max_allocated_storage" {
  description = "SUT RDS 스토리지 자동 확장 상한(GB)"
  type        = number
  default     = 100
}

variable "db_multi_az" {
  description = "SUT RDS Multi-AZ 여부. 장애 주입 실험 날에만 켠다"
  type        = bool
  default     = false
}

variable "auto_stop_state" {
  description = "매일 04:00(Asia/Seoul) EC2 자동 중지 스케줄 상태. 밤새 측정할 때 DISABLED"
  type        = string
  default     = "ENABLED"

  validation {
    condition     = contains(["ENABLED", "DISABLED"], var.auto_stop_state)
    error_message = "auto_stop_state는 ENABLED나 DISABLED만 쓸 수 있다."
  }
}
