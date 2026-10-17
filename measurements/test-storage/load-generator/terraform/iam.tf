data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "load_generator" {
  name               = "${local.prefix}-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "load_generator_ssm_core" {
  role       = aws_iam_role.load_generator.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "load_generator" {
  statement {
    sid    = "DenyParametersOutsideSut"
    effect = "Deny"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:GetParameterHistory",
    ]
    not_resources = [local.sut_params_arn]
  }
}

resource "aws_iam_role_policy" "load_generator" {
  name   = "${local.prefix}-policy"
  role   = aws_iam_role.load_generator.id
  policy = data.aws_iam_policy_document.load_generator.json
}

resource "aws_iam_instance_profile" "load_generator" {
  name = "${local.prefix}-profile"
  role = aws_iam_role.load_generator.name
}
