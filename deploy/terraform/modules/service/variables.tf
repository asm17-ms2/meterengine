variable "db_identifier" {
  type = string
}

variable "db_instance_class" {
  type = string
}

variable "db_subnet_group_name" {
  type = string
}

variable "rds_security_group_ids" {
  type = list(string)
}

variable "rds_initial_database_name" {
  type = string
}

variable "database_username" {
  type = string
}

variable "database_port" {
  type = number
}

variable "backup_retention_period" {
  type = number
}

variable "deletion_protection" {
  type = bool
}

variable "skip_final_snapshot" {
  type = bool
}

variable "final_snapshot_identifier" {
  type = string
}

variable "db_password_wo" {
  type      = string
  sensitive = true
  ephemeral = true
  default   = null
}

variable "db_password_version" {
  type    = number
  default = null
  validation {
    condition     = var.db_password_version == null ? true : var.db_password_version >= 1 && floor(var.db_password_version) == var.db_password_version
    error_message = "Password version must be a positive integer or null for an existing import."
  }
}

variable "database_kms_key_id" {
  type    = string
  default = null
}
variable "database_iops" {
  type    = number
  default = null
}
variable "database_throughput" {
  type    = number
  default = null
}

variable "database_apply_immediately" {
  type    = bool
  default = false
}
