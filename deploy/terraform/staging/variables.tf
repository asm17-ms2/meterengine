variable "db_password_wo" {
  type      = string
  sensitive = true
  ephemeral = true
  nullable  = false
}
variable "db_password_version" {
  type = number
  validation {
    condition     = var.db_password_version >= 1 && floor(var.db_password_version) == var.db_password_version
    error_message = "Owner password version must be a positive integer."
  }
}
variable "runtime_secrets" {
  type      = map(string)
  sensitive = true
  ephemeral = true
  nullable  = false
  validation {
    condition     = toset(keys(var.runtime_secrets)) == toset(["crypto-encryption-key", "operator-password"])
    error_message = "Supply exactly the approved runtime secrets."
  }
}
variable "runtime_secret_versions" {
  type = map(number)
  validation {
    condition = toset(keys(var.runtime_secret_versions)) == toset(["crypto-encryption-key", "operator-password"]) && alltrue([
      for version in values(var.runtime_secret_versions) : version >= 1 && floor(version) == version
    ])
    error_message = "Each runtime secret needs an explicit positive integer version."
  }
}
variable "operator_email" {
  type = string
  validation {
    condition     = can(regex("^[^\\s@]+@[^\\s@]+$", var.operator_email))
    error_message = "Supply the operator email explicitly."
  }
}

variable "kafka_enabled" {
  description = "MSK 클러스터를 만들고 서버 그룹을 수집, 관리 API, 컨슈머로 나눈다. 끄기 전에 토픽에 밀린 이벤트가 없는지 본다"
  type        = bool
  default     = false
}
