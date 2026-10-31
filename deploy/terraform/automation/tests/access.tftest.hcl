mock_provider "aws" {}

variables {
  infrastructure_environments_verified = true
}

run "plan_roles_cannot_write_state_or_resources" {
  command = plan
  assert {
    condition = alltrue([for name, policy in output.policy_documents :
      alltrue([for statement in jsondecode(policy).Statement :
        statement.Effect != "Allow" || alltrue([for action in statement.Action :
          startswith(action, "s3:") || startswith(action, "sts:Get") ||
          can(regex("^[^:]+:(Get|List|Describe)", action)) || action == "kms:Decrypt"
        ])
      ]) if strcontains(name, "-plan-")
    ])
    error_message = "Plan roles must only refresh, lock and store private plans."
  }
}

run "apply_roles_are_target_scoped" {
  command = plan
  assert {
    condition = alltrue([for name, policy in output.policy_documents :
      alltrue([for statement in jsondecode(policy).Statement :
        statement.Effect != "Allow" || alltrue([for resource in statement.Resource :
          !strcontains(resource, name == "production-apply-write" ? "staging" : "deploy/automation/")
        ])
      ])
    ])
    error_message = "Production writes and all state access must stay within the target."
  }
  assert {
    condition = alltrue([for policy in values(output.policy_documents) :
      length(jsonencode(jsondecode(policy))) <= 6144 && alltrue([for statement in jsondecode(policy).Statement :
        alltrue([for action in statement.Action : !strcontains(action, "*")])
      ])
    ])
    error_message = "Policies must fit the managed-policy quota without wildcard actions."
  }
}

run "managed_policy_attachments_fit_quota" {
  command = plan
  assert {
    condition     = alltrue([for role in keys(output.trust_documents) : length([for name in keys(output.policy_documents) : name if startswith(name, "${role}-")]) <= 20])
    error_message = "Role managed-policy attachments must fit the default quota."
  }
}

run "apply_diagnostics_writes_are_encrypted_and_narrow" {
  command = plan
  assert {
    condition = alltrue([for target in ["staging", "production"] :
      anytrue([for statement in jsondecode(output.policy_documents["${target}-apply-storage"]).Statement :
        statement.Effect == "Allow" && statement.Action == ["s3:PutObject"] &&
        statement.Resource == ["arn:aws:s3:::meterengine-tfstate-928618307647/deploy/plans/${target}/*/diagnostics/*"] &&
        try(statement.Condition.StringEquals["s3:x-amz-server-side-encryption"], "") == "AES256"
      ])
    ])
    error_message = "Apply roles must write only their encrypted diagnostics, leaving plan/context read-only."
  }
}

run "apply_trust_requires_infrastructure_environment" {
  command = plan
  assert {
    condition     = jsondecode(output.trust_documents["staging-apply"]).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:asm17-ms2@293532363/meterengine@1313421667:environment:staging-infrastructure" && jsondecode(output.trust_documents["production-apply"]).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:asm17-ms2@293532363/meterengine@1313421667:environment:production-infrastructure"
    error_message = "Apply trust requires the immutable infrastructure Environment subject."
  }
  assert {
    condition = alltrue([for document in values(output.trust_documents) :
      jsondecode(document).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:aud"] == "sts.amazonaws.com"
    ])
    error_message = "OIDC audience must be STS."
  }
}

run "no_role_can_edit_automation_roles" {
  command = plan
  assert {
    condition = alltrue([for name, policy in output.policy_documents :
      anytrue([for statement in jsondecode(policy).Statement :
        statement.Effect == "Deny" && contains(statement.Action, "iam:PassRole") && contains(statement.Resource, "arn:aws:iam::928618307647:role/meterengine-terraform-staging-apply") && contains(statement.Resource, "arn:aws:iam::928618307647:role/meterengine-terraform-production-plan")
      ]) if endswith(name, "-storage")
    ])
    error_message = "Every role needs an explicit automation role protection boundary."
  }
}

run "apply_sessions_cover_provider_waiters" {
  command = plan
  assert {
    condition     = aws_iam_role.automation["staging-apply"].max_session_duration == 10800 && aws_iam_role.automation["production-apply"].max_session_duration == 7200 && aws_iam_role.automation["staging-plan"].max_session_duration == 3600 && aws_iam_role.automation["production-plan"].max_session_duration == 3600
    error_message = "Apply sessions must cover RDS provider waiters while plan sessions remain one hour."
  }
}

run "unverified_environments_block_bootstrap" {
  command = plan
  variables {
    infrastructure_environments_verified = false
  }
  expect_failures = [aws_iam_role.automation]
}
