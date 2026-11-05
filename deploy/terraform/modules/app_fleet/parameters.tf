# 배포가 커밋 SHA를 쓰고 서버가 부팅 때 읽는다. Terraform은 자리만 만들고 값을 되돌리지 않는다.
resource "aws_ssm_parameter" "app_version" {
  name  = local.app_version_parameter_name
  type  = "String"
  value = "unset"
  tags  = local.tags
  lifecycle {
    ignore_changes = [value]
  }
}
