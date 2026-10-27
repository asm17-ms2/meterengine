variable "enabled" {
  description = "시험 DB별 자동 재기동 감지 상태"
  type        = map(bool)
  default = {
    test-slo     = true
    test-storage = true
  }

  validation {
    condition     = toset(keys(var.enabled)) == toset(["test-slo", "test-storage"])
    error_message = "enabled에는 test-slo와 test-storage를 모두 지정해야 한다."
  }
}

variable "alarm_actions" {
  description = "두 환경의 실패 경보를 받을 기존 SNS 등의 ARN. 비워 두면 외부 알림은 없다."
  type        = list(string)
  default     = []
}
