variable "instance_type" {
  description = "부하 발생기 인스턴스 유형. m7g.large는 초당 4,000건에서 CPU가 64%였다. 그보다 많이 보내는 날에만 -var로 키운다"
  type        = string
  default     = "m7g.large"
}
