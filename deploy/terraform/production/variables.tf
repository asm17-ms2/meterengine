variable "instance_type" {
  type    = string
  default = "m7g.large"
  validation {
    condition     = contains(["m7g.large", "t4g.medium"], var.instance_type)
    error_message = "Use the requested size or observed size for import-only diagnostics."
  }
}
variable "db_instance_class" {
  type    = string
  default = "db.m7g.large"
  validation {
    condition     = contains(["db.m7g.large", "db.t4g.small"], var.db_instance_class)
    error_message = "Use the requested size or observed size for import-only diagnostics."
  }
}
