variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
  validation {
    condition     = contains(["db.t4g.small"], var.db_instance_class)
    error_message = "Production runs the observed db.t4g.small database."
  }
}
