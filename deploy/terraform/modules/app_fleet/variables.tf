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

# false면 ALB의 80, 443 인바운드 규칙을 만들지 않는다. 콘솔에서 넣은 규칙은 Terraform이 건드리지 않는다.
variable "alb_public_ingress" {
  type    = bool
  default = true
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

variable "scaling_target_average_cpu_percent" {
  type = number
  validation {
    condition     = var.scaling_target_average_cpu_percent > 0 && var.scaling_target_average_cpu_percent < 100
    error_message = "Average CPU target must be between 0 and 100 percent."
  }
}

variable "monitoring_security_group_id" {
  type = string
}

# 앱이 가입 인증 메일을 보낼 SES 자격 증명. 비우면 앱 역할에 SES 권한을 주지 않는다.
# SES는 자격 증명에 걸린 기본 구성 세트에도 발송 권한을 요구하므로 그 ARN을 같이 받는다.
variable "email_sender" {
  type = object({
    identity_arn          = string
    configuration_set_arn = string
    from_address          = string
  })
  default = null
}
