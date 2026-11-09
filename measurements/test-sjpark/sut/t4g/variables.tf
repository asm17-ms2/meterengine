variable "asg_min_size" {
  type    = number
  default = 0
}

variable "asg_max_size" {
  type    = number
  default = 0
}

variable "auto_stop_state" {
  type    = string
  default = "ENABLED"
}
