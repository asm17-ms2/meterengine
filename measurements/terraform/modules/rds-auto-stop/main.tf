locals {
  name              = "${var.name_prefix}-rds-auto-stop"
  state_machine_arn = "arn:aws:states:${var.region}:${var.account_id}:stateMachine:${local.name}"
  workflow          = jsondecode(file("${path.module}/workflow.asl.json"))
  definition = jsonencode(merge(local.workflow, {
    States = merge(local.workflow.States, {
      Configure = merge(local.workflow.States.Configure, {
        Assign = {
          event = "{% $states.input %}"
          config = {
            account_id    = var.account_id
            region        = var.region
            db_identifier = var.db_identifier
            db_arn        = var.db_arn
            claim_table   = aws_dynamodb_table.claims.name
          }
        }
      })
    })
  }))
}

resource "aws_cloudwatch_event_rule" "automatic_restart" {
  name        = local.name
  description = "Stop only RDS automatic restarts after the seven-day stopped limit"
  state       = var.enabled ? "ENABLED" : "DISABLED"
  event_pattern = jsonencode({
    account       = [var.account_id]
    region        = [var.region]
    source        = ["aws.rds"]
    "detail-type" = ["RDS DB Instance Event"]
    resources     = [var.db_arn]
    detail = {
      EventID          = ["RDS-EVENT-0154"]
      SourceType       = ["DB_INSTANCE"]
      SourceIdentifier = [var.db_identifier]
      SourceArn        = [var.db_arn]
    }
  })
}

resource "aws_dynamodb_table" "claims" {
  name                        = "${local.name}-claims"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "event_key"
  deletion_protection_enabled = true

  attribute {
    name = "event_key"
    type = "S"
  }

  ttl {
    attribute_name = "expires_at"
    enabled        = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_cloudwatch_log_group" "execution" {
  name              = "/aws/vendedlogs/states/${local.name}"
  retention_in_days = 30
}

resource "aws_sfn_state_machine" "automatic_restart" {
  name       = local.name
  role_arn   = aws_iam_role.workflow.arn
  type       = "STANDARD"
  definition = local.definition

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.execution.arn}:*"
    include_execution_data = true
    level                  = "ALL"
  }

  depends_on = [aws_iam_role_policy.workflow]
}

resource "aws_sqs_queue" "undelivered" {
  name                      = "${local.name}-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue_policy" "undelivered" {
  queue_url = aws_sqs_queue.undelivered.url
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.undelivered.arn
      Condition = {
        ArnEquals    = { "aws:SourceArn" = aws_cloudwatch_event_rule.automatic_restart.arn }
        StringEquals = { "aws:SourceAccount" = var.account_id }
      }
    }]
  })
}

resource "aws_cloudwatch_event_target" "workflow" {
  rule      = aws_cloudwatch_event_rule.automatic_restart.name
  target_id = "automatic-rds-restart"
  arn       = aws_sfn_state_machine.automatic_restart.arn
  role_arn  = aws_iam_role.eventbridge.arn

  dead_letter_config {
    arn = aws_sqs_queue.undelivered.arn
  }

  retry_policy {
    maximum_event_age_in_seconds = 900
    maximum_retry_attempts       = 3
  }

  depends_on = [aws_sqs_queue_policy.undelivered, aws_iam_role_policy.eventbridge]
}
