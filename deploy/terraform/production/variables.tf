variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
  validation {
    condition     = contains(["db.t4g.small"], var.db_instance_class)
    error_message = "Production runs the observed db.t4g.small database."
  }
}

variable "kafka_enabled" {
  description = "MSK 클러스터를 만들고 서버 그룹을 수집, 관리 API, 컨슈머로 나눈다. 끄기 전에 토픽에 밀린 이벤트가 없는지 본다"
  type        = bool
  default     = false
}
