output "role_arns" {
  value = { for key, role in aws_iam_role.automation : key => role.arn }
}

output "policy_documents" {
  value = { for key, policy in aws_iam_policy.automation : key => policy.policy }
}

output "trust_documents" {
  value = { for key, role in aws_iam_role.automation : key => role.assume_role_policy }
}
