variable "name_prefix" {
  type = string

  validation {
    # IAM 역할 이름 한도 64자에서 가장 긴 접미 "-rds-auto-stop-workflow"(23자)를 뺀 값이다.
    condition     = can(regex("^[a-z0-9-]{1,41}$", var.name_prefix))
    error_message = "name_prefix는 41자 이내의 소문자, 숫자, 하이픈이어야 한다."
  }
}

variable "account_id" {
  type = string
}

variable "region" {
  type = string
}

variable "db_identifier" {
  type = string
}

variable "db_arn" {
  type = string
}

variable "enabled" {
  type    = bool
  default = true
}

variable "alarm_actions" {
  description = "경보를 받을 기존 SNS 등의 ARN. 비워 두면 CloudWatch 경보 상태만 제공한다."
  type        = list(string)
  default     = []
}
