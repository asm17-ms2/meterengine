# --- 태그 ---

variable "owner" {
  description = "Owner 태그 값"
  type        = string
  default     = "sjparkx1129"
}

# --- 부하 발생기 EC2 ---

variable "instance_type" {
  description = "부하 발생기 EC2 인스턴스 유형. k6가 못 보낸 요청(dropped_iterations)이 생기면 키운다"
  type        = string
  # SUT가 끊긴 동안 응답을 기다리는 VU가 쌓여 메모리가 먼저 모자랄 수 있어 c가 아니라 m을 쓴다.
  default = "m7g.large"

  # AMI와 k6 바이너리를 arm64용으로 받는다.
  validation {
    condition     = can(regex("^[a-z][0-9]+g[a-z]*\\.", var.instance_type))
    error_message = "instance_type은 Graviton(arm64) 유형만 쓸 수 있다."
  }
}

variable "k6_version" {
  description = "설치할 k6 버전"
  type        = string
  default     = "2.3.0"
}
