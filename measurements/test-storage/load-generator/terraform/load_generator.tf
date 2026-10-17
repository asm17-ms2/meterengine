resource "aws_instance" "load_generator" {
  ami                         = local.al2023_arm64_ami
  instance_type               = "m7g.large"
  subnet_id                   = data.aws_subnet.public_2a.id
  vpc_security_group_ids      = [aws_security_group.load_generator.id]
  iam_instance_profile        = aws_iam_instance_profile.load_generator.name
  associate_public_ip_address = true
  user_data                   = file("${path.module}/templates/load-generator-user-data.sh")

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  tags = {
    Name = local.prefix
  }

  depends_on = [
    aws_iam_role_policy_attachment.load_generator_ssm_core,
    aws_iam_role_policy.load_generator,
  ]

  lifecycle {
    ignore_changes = [associate_public_ip_address]
  }
}
