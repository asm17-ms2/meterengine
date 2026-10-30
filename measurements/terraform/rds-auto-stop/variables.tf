variable "environments" {
  description = "감지를 둘 시험 환경. SUT를 지우기 전에 그 환경을 여기서 먼저 뺀다."
  type        = set(string)
  default     = ["test-slo", "test-storage"]

  validation {
    condition     = length(setsubtract(var.environments, ["test-slo", "test-storage"])) == 0
    error_message = "environments에는 test-slo와 test-storage만 넣을 수 있다."
  }
}

variable "enabled" {
  description = "시험 DB별 자동 재기동 감지 상태"
  type        = map(bool)
  default = {
    test-slo     = true
    test-storage = true
  }

  validation {
    condition     = alltrue([for environment in var.environments : contains(keys(var.enabled), environment)])
    error_message = "enabled에는 environments의 환경을 모두 지정해야 한다."
  }
}

variable "alarm_actions" {
  description = "두 환경의 실패 경보를 받을 기존 SNS 등의 ARN. 비워 두면 외부 알림은 없다."
  type        = list(string)
  default     = []
}
