# 고치기 전에 운영 app_fleet, service와 비교하고 measurements/test-sjpark/README.md 차이 표를 맞춘다.
variable "environment" {
  type = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*$", var.environment))
    error_message = "Environment names go into AWS resource names and must be lowercase ASCII."
  }
}

variable "instance_type" {
  type = string
}

variable "db_instance_class" {
  type = string
}

# 바꾸면 DB가 그 스냅샷에서 다시 만들어진다.
variable "snapshot_identifier" {
  type = string
}

variable "ami_id" {
  type    = string
  default = "ami-02b68e7eb6b798d63"
}

variable "asg_min_size" {
  type    = number
  default = 0
  validation {
    condition     = var.asg_min_size >= 0 && var.asg_min_size <= 3 && floor(var.asg_min_size) == var.asg_min_size
    error_message = "Minimum size must be an integer between 0 and the maximum of 3."
  }
}

variable "asg_max_size" {
  type    = number
  default = 0
  validation {
    condition     = var.asg_max_size >= var.asg_min_size && var.asg_max_size <= 3 && floor(var.asg_max_size) == var.asg_max_size
    error_message = "Maximum size must be an integer between asg_min_size and 3."
  }
}

variable "auto_stop_state" {
  type    = string
  default = "ENABLED"
  validation {
    condition     = contains(["ENABLED", "DISABLED"], var.auto_stop_state)
    error_message = "Use ENABLED or DISABLED."
  }
}
