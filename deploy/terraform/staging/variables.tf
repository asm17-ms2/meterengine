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
    condition     = toset(keys(var.runtime_secrets)) == toset(["crypto-encryption-key", "operator-password"])
    error_message = "Supply exactly the approved runtime secrets."
  }
}
variable "runtime_secret_versions" {
  type = map(number)
  validation {
    condition = toset(keys(var.runtime_secret_versions)) == toset(["crypto-encryption-key", "operator-password"]) && alltrue([
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
# false면 도메인이 옛 서버 탄력적 IP로 돌아간다. 서버를 켜기만 하는 첫 apply와 되돌리기에 쓴다.
variable "serve_domain_from_alb" {
  type     = bool
  default  = true
  nullable = false
}
