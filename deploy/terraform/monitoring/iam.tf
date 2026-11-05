locals {
  ssm_arn = "arn:aws:ssm:ap-northeast-2:928618307647"
  # 이름 목록은 start.sh와 같은 파일에서 온다. 목록 밖의 DB 마스터 비밀번호와 암호화 키는 읽지 못한다.
  readable_parameters = [for name in concat(local.monitoring_parameter_names, local.environment_parameter_names) : "${local.ssm_arn}:parameter${name}"]
  parameter_reads     = ["ssm:GetParameter", "ssm:GetParameters"]
}

resource "aws_iam_role" "monitoring" {
  name = "meterengine-monitoring"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }
  ] })
}

resource "aws_iam_instance_profile" "monitoring" {
  name = "meterengine-monitoring"
  role = aws_iam_role.monitoring.name
}

resource "aws_iam_role_policy" "monitoring" {
  name = "monitoring-runtime"
  role = aws_iam_role.monitoring.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Sid = "ReadListedParameters", Effect = "Allow", Action = local.parameter_reads, Resource = local.readable_parameters },
    { Sid = "DenyOtherParameters", Effect = "Deny", Action = concat(local.parameter_reads, ["ssm:GetParametersByPath", "ssm:GetParameterHistory"]), NotResource = local.readable_parameters },
    # Prometheus가 태그로 앱 서버를 찾고 가용 영역 라벨을 채운다. 둘 다 자원으로 좁힐 수 없다.
    { Sid = "DiscoverAppServers", Effect = "Allow", Action = ["ec2:DescribeInstances", "ec2:DescribeAvailabilityZones"], Resource = "*" },
    { Sid = "ReadRdsMetrics", Effect = "Allow", Action = ["cloudwatch:GetMetricData", "cloudwatch:GetMetricStatistics", "cloudwatch:ListMetrics"], Resource = "*" },
    { Sid = "SessionManager", Effect = "Allow", Action = ["ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel", "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel", "ssm:UpdateInstanceInformation"], Resource = "*" }
  ] })
}
