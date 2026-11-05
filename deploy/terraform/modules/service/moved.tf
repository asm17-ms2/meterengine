moved {
  from = aws_instance.app
  to   = aws_instance.app[0]
}
