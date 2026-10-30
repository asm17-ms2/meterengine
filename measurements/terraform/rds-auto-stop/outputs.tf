output "state_machine_arns" {
  value = { for environment, detector in module.rds_auto_stop : environment => detector.state_machine_arn }
}

output "event_rule_names" {
  value = { for environment, detector in module.rds_auto_stop : environment => detector.event_rule_name }
}

output "dead_letter_queue_urls" {
  value = { for environment, detector in module.rds_auto_stop : environment => detector.dead_letter_queue_url }
}
