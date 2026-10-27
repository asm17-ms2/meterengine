variable "name_prefix" {
  type = string

  validation {
    condition     = can(regex("^[a-z0-9-]{1,45}$", var.name_prefix))
    error_message = "name_prefix는 45자 이내의 소문자, 숫자, 하이픈이어야 한다."
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
