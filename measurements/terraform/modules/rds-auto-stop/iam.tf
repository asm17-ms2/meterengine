locals {
  workflow_policy = {
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "StopOnlyOwnDatabase"
        Effect   = "Allow"
        Action   = ["rds:StopDBInstance"]
        Resource = var.db_arn
      },
      {
        Sid      = "ReadDatabaseStatusAndNativeEvents"
        Effect   = "Allow"
        Action   = ["rds:DescribeDBInstances", "rds:DescribeEvents"]
        Resource = "*"
        Condition = {
          StringEquals = { "aws:RequestedRegion" = var.region }
        }
      },
      {
        Sid      = "ClaimAutomaticRestartOnce"
        Effect   = "Allow"
        Action   = ["dynamodb:PutItem"]
        Resource = aws_dynamodb_table.claims.arn
      },
      {
        Sid    = "DeliverExecutionLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogDelivery", "logs:GetLogDelivery", "logs:UpdateLogDelivery",
          "logs:DeleteLogDelivery", "logs:ListLogDeliveries", "logs:PutResourcePolicy",
          "logs:DescribeResourcePolicies", "logs:DescribeLogGroups",
        ]
        Resource = "*"
      },
    ]
  }
}

resource "aws_iam_role" "workflow" {
  name = "${local.name}-workflow"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "states.amazonaws.com" }
      Condition = {
        ArnEquals    = { "aws:SourceArn" = local.state_machine_arn }
        StringEquals = { "aws:SourceAccount" = var.account_id }
      }
    }]
  })
}

resource "aws_iam_role_policy" "workflow" {
  name   = local.name
  role   = aws_iam_role.workflow.id
  policy = jsonencode(local.workflow_policy)
}

resource "aws_iam_role" "eventbridge" {
  name = "${local.name}-events"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "events.amazonaws.com" }
      Condition = {
        ArnEquals    = { "aws:SourceArn" = aws_cloudwatch_event_rule.automatic_restart.arn }
        StringEquals = { "aws:SourceAccount" = var.account_id }
      }
    }]
  })
}

resource "aws_iam_role_policy" "eventbridge" {
  name = local.name
  role = aws_iam_role.eventbridge.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = local.state_machine_arn
    }]
  })
}
