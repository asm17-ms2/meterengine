# --- EC2 역할 ---

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "loadgen" {
  name               = "${local.prefix}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# --- 역할 권한 ---

resource "aws_iam_role_policy_attachment" "loadgen_ssm_core" {
  role       = aws_iam_role.loadgen.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# AmazonSSMManagedInstanceCore가 ssm:GetParameter(s)를 Resource *로 허용한다. 부하 발생기는 파라미터를 쓰지 않으므로 전부 막아 팀 비밀을 읽지 못하게 한다.
data "aws_iam_policy_document" "loadgen" {
  statement {
    sid    = "DenyAllParameters"
    effect = "Deny"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:GetParameterHistory",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "loadgen" {
  name   = "${local.prefix}-ec2-policy"
  role   = aws_iam_role.loadgen.id
  policy = data.aws_iam_policy_document.loadgen.json
}

# --- 인스턴스 프로파일 ---

resource "aws_iam_instance_profile" "loadgen" {
  name = "${local.prefix}-ec2-profile"
  role = aws_iam_role.loadgen.name
}
