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
# false면 세 도메인이 옛 서버 탄력적 IP로 돌아간다. 서버를 켜기만 하는 첫 apply와 되돌리기에 쓴다.
variable "serve_domain_from_alb" {
  type     = bool
  default  = true
  nullable = false
}
# 옛 운영 서버 meterengine-server의 탄력적 IP다. 그 서버는 Terraform이 EIP를 갖고 있지 않다.
variable "legacy_server_ip" {
  type    = string
  default = "43.203.50.31"
  validation {
    condition     = can(cidrhost("${var.legacy_server_ip}/32", 0))
    error_message = "Legacy server address must be an IPv4 address."
  }
}
