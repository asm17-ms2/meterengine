variable "environment" {
  type = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*$", var.environment))
    error_message = "Environment names go into AWS resource names and must be lowercase ASCII."
  }
}

variable "site_domain" {
  type = string
}

variable "site_aliases" {
  type    = list(string)
  default = []
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.public_subnet_ids) >= 2
    error_message = "An ALB needs subnets in at least two availability zones."
  }
}

variable "route53_zone_id" {
  type = string
}

variable "database_security_group_id" {
  type = string
}

variable "alb_deletion_protection" {
  type = bool
}

variable "app_egress_ports" {
  type    = list(number)
  default = [443]
}

variable "parameter_path" {
  type = string
  validation {
    condition     = can(regex("^/meterengine/[a-z][a-z0-9-]*$", var.parameter_path))
    error_message = "Use the environment path under /meterengine without a trailing slash."
  }
}

variable "ecr_repository_names" {
  type = list(string)
}

variable "ami_id" {
  type = string
}

variable "min_size" {
  type = number
  validation {
    condition     = var.min_size >= 0 && var.min_size <= 3 && floor(var.min_size) == var.min_size
    error_message = "Minimum size must be an integer between 0 and the maximum of 3."
  }
}

variable "scaling_target_requests_per_second" {
  type = number
}

variable "monitoring_security_group_id" {
  type = string
}
