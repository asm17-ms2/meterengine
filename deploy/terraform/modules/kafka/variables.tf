variable "name" {
  description = "자원 이름의 앞머리. 환경마다 다르게 준다"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "broker_subnet_ids" {
  description = "브로커를 한 대씩 둘 서브넷. 가용 영역이 서로 달라야 한다"
  type        = list(string)
  validation {
    condition     = length(var.broker_subnet_ids) == 3
    error_message = "Three brokers need three subnets, one per availability zone."
  }
}

variable "client_security_group_id" {
  description = "브로커에 붙는 서버(수집, 관리 API, 컨슈머)의 보안 그룹"
  type        = string
}

variable "monitoring_security_group_id" {
  description = "브로커 지표를 긁는 공용 모니터링 서버의 보안 그룹"
  type        = string
}

# kafka.t3.small은 KRaft를 고를 수 없어 ZooKeeper 방식이고, 그 마지막 버전이 3.9다.
variable "kafka_version" {
  type    = string
  default = "3.9.x"
}

variable "broker_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "broker_volume_size" {
  description = "브로커 한 대의 저장 용량(GB)"
  type        = number
  default     = 100
}

variable "tags" {
  type    = map(string)
  default = {}
}
