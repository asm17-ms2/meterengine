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
  description = "앱 서버 인스턴스 유형"
  type        = string
  default     = "m7g.large"
}

variable "asg_min_size" {
  description = "ASG 최소 대수. 04:00 예약이 0으로 내리므로 평소 0이고, 측정할 때만 -var로 올린다"
  type        = number
  default     = 0

  validation {
    condition     = var.asg_min_size >= 0 && var.asg_min_size <= var.asg_max_size
    error_message = "asg_min_size는 0에서 asg_max_size 사이여야 한다."
  }
}

variable "asg_max_size" {
  description = "ASG 최대 대수. 한 대가 받는 양을 넘겨 재는 날에만 -var로 올린다(초당 10,000건은 8대쯤)"
  type        = number
  default     = 3

  validation {
    condition     = var.asg_max_size >= 1 && var.asg_max_size <= 20
    error_message = "asg_max_size는 1에서 20 사이여야 한다."
  }
}

variable "db_instance_class" {
  description = "SUT RDS 인스턴스 클래스"
  type        = string
  default     = "db.m7g.large"
}

variable "ami_id" {
  description = "앱 서버 이미지. staging, production과 같은 미리 구운 이미지"
  type        = string
  default     = "ami-02b68e7eb6b798d63"
}

variable "db_allocated_storage" {
  description = "SUT RDS 디스크(GB). 초당 10,000건이면 시간당 약 17GB가 쌓이고, 자동 확장은 한 번 늘리면 6시간 동안 다시 못 늘려 실험 중에는 믿지 못한다. 많이 넣는 날에는 켜기 전에 -var로 올린다. 줄일 수는 없어서 되돌리려면 DB를 새로 만든다(-replace)"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "SUT RDS 스토리지 자동 확장 상한(GB). 디스크보다 커야 자동 확장이 켜진다"
  type        = number
  default     = 100
}

variable "db_multi_az" {
  description = "SUT RDS Multi-AZ 여부. 장애 주입 실험 날에만 켠다"
  type        = bool
  default     = false
}

variable "auto_stop_state" {
  description = "매일 04:00(Asia/Seoul) RDS 자동 중지와 ASG 0대 예약 상태. 밤새 측정할 때 DISABLED"
  type        = string
  default     = "ENABLED"

  validation {
    condition     = contains(["ENABLED", "DISABLED"], var.auto_stop_state)
    error_message = "auto_stop_state는 ENABLED나 DISABLED만 쓸 수 있다."
  }
}

variable "kafka_enabled" {
  description = "MSK 클러스터를 만들고 수집 경로를 카프카로 돌린다. MSK는 멈출 수 없고 떠 있는 동안 과금되므로 실험하는 날만 켠다. 끄면 토픽과 메시지가 지워진다"
  type        = bool
  default     = false
}

# staging, production이 쓸 kafka.t3.small은 KRaft를 고를 수 없어 ZooKeeper 방식이고, 그 마지막 버전이 3.9다.
# 브로커가 죽었을 때 새 리더를 뽑는 시간이 방식에 따라 달라, 운영과 같은 방식으로 잰다.
variable "kafka_version" {
  description = "MSK의 카프카 버전. 끝에 .kraft가 붙으면 KRaft, 없으면 ZooKeeper 방식이다. 4.2.x는 Express 브로커 전용이라 쓸 수 없다"
  type        = string
  default     = "3.9.x"
}

variable "kafka_broker_instance_type" {
  description = "MSK 브로커 인스턴스 유형"
  type        = string
  default     = "kafka.m7g.large"
}

variable "kafka_broker_volume_size" {
  description = "브로커 한 대의 저장 용량(GB)"
  type        = number
  default     = 100
}

variable "consumer_count" {
  description = "카프카를 읽어 DB에 넣는 컨슈머 서버 대수. 04:00 예약이 0으로 내리므로 평소 0이고, 측정할 때만 -var로 올린다"
  type        = number
  default     = 0

  validation {
    condition     = var.consumer_count >= 0 && var.consumer_count <= 6
    error_message = "consumer_count는 0에서 6 사이여야 한다. event.ingest 토픽의 파티션이 6개라 그보다 많으면 남는 서버가 논다."
  }
}
