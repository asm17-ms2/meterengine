variable "instance_type" {
  type    = string
  default = "t4g.medium"
  validation {
    condition     = contains(["t4g.medium"], var.instance_type)
    error_message = "Production runs the observed t4g.medium server."
  }
}
variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
  validation {
    condition     = contains(["db.t4g.small"], var.db_instance_class)
    error_message = "Production runs the observed db.t4g.small database."
  }
}
