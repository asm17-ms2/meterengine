variable "db_password_wo" {
  type      = string
  sensitive = true
  ephemeral = true
  nullable  = false
}
variable "db_password_version" {
  type = number
  validation {
    condition     = var.db_password_version >= 1 && floor(var.db_password_version) == var.db_password_version
    error_message = "Owner password version must be a positive integer."
  }
}
variable "runtime_secrets" {
  type      = map(string)
  sensitive = true
  ephemeral = true
  nullable  = false
  validation {
    condition     = toset(keys(var.runtime_secrets)) == toset(["db-shared-password", "db-test-password", "db-live-password", "crypto-encryption-key", "operator-password"])
    error_message = "Supply exactly the approved runtime secrets."
  }
}
variable "runtime_secret_versions" {
  type = map(number)
  validation {
    condition = toset(keys(var.runtime_secret_versions)) == toset(["db-shared-password", "db-test-password", "db-live-password", "crypto-encryption-key", "operator-password"]) && alltrue([
      for version in values(var.runtime_secret_versions) : version >= 1 && floor(version) == version
    ])
    error_message = "Each runtime secret needs an explicit positive integer version."
  }
}
variable "operator_email" {
  type = string
  validation {
    condition     = can(regex("^[^\\s@]+@[^\\s@]+$", var.operator_email))
    error_message = "Supply the operator email explicitly."
  }
}
