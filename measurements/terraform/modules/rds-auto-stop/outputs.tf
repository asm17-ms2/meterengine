output "state_machine_arn" {
  value = aws_sfn_state_machine.automatic_restart.arn
}

output "event_rule_name" {
  value = aws_cloudwatch_event_rule.automatic_restart.name
}

output "claim_table_name" {
  value = aws_dynamodb_table.claims.name
}

output "dead_letter_queue_url" {
  value = aws_sqs_queue.undelivered.url
}

output "workflow_definition" {
  value = local.definition
}
