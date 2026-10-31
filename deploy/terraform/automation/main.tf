locals {
  account     = "928618307647"
  region      = "ap-northeast-2"
  bucket      = "arn:aws:s3:::meterengine-tfstate-928618307647"
  ec2         = "arn:aws:ec2:ap-northeast-2:928618307647:"
  iam         = "arn:aws:iam::928618307647:"
  rds         = "arn:aws:rds:ap-northeast-2:928618307647:"
  vpc         = "${local.ec2}vpc/vpc-07498f0672b7f3c9e"
  subnet      = "${local.ec2}subnet/subnet-0348a18c1d399c193"
  zone        = "arn:aws:route53:::hostedzone/Z08728621CQ5R1K37A7N1"
  ssm_key     = "arn:aws:kms:ap-northeast-2:928618307647:key/08d25846-2de5-4acd-a69a-5168d7982aa8"
  regional    = { "aws:RequestedRegion" = local.region }
  staging_tag = { "aws:ResourceTag/Environment" = "staging" }
  roles = { for pair in setproduct(["staging", "production"], ["plan", "apply"]) :
    "${pair[0]}-${pair[1]}" => { target = pair[0], phase = pair[1] }
  }
  app_roles = [for name in ["ec2", "deploy", "owner"] : "${local.iam}role/meterengine-staging-${name}"]
  profile   = "${local.iam}instance-profile/meterengine-staging-ec2"
  parameters = concat(
    [for name in ["db-host", "db-port", "db-name", "db-shared-username", "db-shared-password", "db-test-username", "db-test-password", "db-live-username", "db-live-password", "crypto-encryption-key", "meterengine-cutover-mode", "operator-email", "operator-password"] : "arn:aws:ssm:${local.region}:${local.account}:parameter/meterengine/staging/${name}"],
    [for name in ["db-migration-username", "db-migration-password"] : "arn:aws:ssm:${local.region}:${local.account}:parameter/meterengine/staging-migration/${name}"]
  )
  kms_conditions = { StringEquals = {
    "aws:RequestedRegion"                 = local.region
    "kms:ViaService"                      = "ssm.ap-northeast-2.amazonaws.com"
    "kms:CallerAccount"                   = local.account
    "kms:EncryptionContext:PARAMETER_ARN" = local.parameters
  } }
  protection = [
    {
      Sid      = "ProtectBootstrapState", Effect = "Deny"
      Action   = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:DeleteObject", "s3:DeleteObjectVersion"]
      Resource = ["${local.bucket}/deploy/automation/terraform.tfstate", "${local.bucket}/deploy/automation/terraform.tfstate.tflock"]
    },
    {
      Sid      = "ProtectAutomationRoles", Effect = "Deny"
      Action   = ["iam:CreateRole", "iam:DeleteRole", "iam:UpdateRole", "iam:UpdateRoleDescription", "iam:UpdateAssumeRolePolicy", "iam:PutRolePolicy", "iam:DeleteRolePolicy", "iam:AttachRolePolicy", "iam:DetachRolePolicy", "iam:PutRolePermissionsBoundary", "iam:DeleteRolePermissionsBoundary", "iam:TagRole", "iam:UntagRole", "iam:PassRole"]
      Resource = [for key in keys(local.roles) : "${local.iam}role/meterengine-terraform-${key}"]
    },
    {
      Sid      = "ProtectAutomationPolicies", Effect = "Deny"
      Action   = ["iam:CreatePolicy", "iam:DeletePolicy", "iam:CreatePolicyVersion", "iam:DeletePolicyVersion", "iam:SetDefaultPolicyVersion", "iam:TagPolicy", "iam:UntagPolicy"]
      Resource = ["${local.iam}policy/meterengine-terraform-*"]
    }
  ]
  storage = { for key, role in local.roles : "${key}-storage" => jsonencode({
    Version = "2012-10-17"
    Statement = concat(local.protection, [
      {
        Sid      = "OwnStateRead", Effect = "Allow", Action = ["s3:GetObject", "s3:GetObjectVersion"]
        Resource = ["${local.bucket}/deploy/${role.target}/terraform.tfstate"]
      },
      {
        Sid      = "OwnNativeLock", Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = ["${local.bucket}/deploy/${role.target}/terraform.tfstate.tflock"]
      },
      {
        Sid       = "TargetListing", Effect = "Allow", Action = ["s3:ListBucket"]
        Resource  = [local.bucket]
        Condition = { StringLike = { "s3:prefix" = ["deploy/${role.target}/terraform.tfstate", "deploy/${role.target}/terraform.tfstate.tflock", "deploy/plans/${role.target}/*"] } }
      },
      {
        Sid       = "OwnStateVersions", Effect = "Allow", Action = ["s3:ListBucketVersions"]
        Resource  = [local.bucket]
        Condition = { StringEquals = { "s3:prefix" = "deploy/${role.target}/terraform.tfstate" } }
      },
      {
        Sid      = "PrivateBucketChecks", Effect = "Allow"
        Action   = ["s3:GetBucketVersioning", "s3:GetBucketPublicAccessBlock", "s3:GetBucketPolicyStatus", "s3:GetBucketAcl", "s3:GetEncryptionConfiguration"]
        Resource = [local.bucket]
      },
      {
        Sid      = "VersionedPlanRead", Effect = "Allow", Action = ["s3:GetObjectVersion"]
        Resource = ["${local.bucket}/deploy/plans/${role.target}/*"]
      }
      ], [for grant in [{
        Sid      = "OwnStateWrite", Effect = "Allow", Action = ["s3:PutObject"]
        Resource = ["${local.bucket}/deploy/${role.target}/terraform.tfstate"]
        }] : grant if role.phase == "apply"], [for grant in [{
        Sid       = "EncryptedPlanWrite", Effect = "Allow", Action = ["s3:PutObject"]
        Resource  = ["${local.bucket}/deploy/plans/${role.target}/*"]
        Condition = { StringEquals = { "s3:x-amz-server-side-encryption" = "AES256" } }
        }] : grant if role.phase == "plan"], [for grant in [{
        Sid       = "EncryptedDiagnosticsWrite", Effect = "Allow", Action = ["s3:PutObject"]
        Resource  = ["${local.bucket}/deploy/plans/${role.target}/*/diagnostics/*"]
        Condition = { StringEquals = { "s3:x-amz-server-side-encryption" = "AES256" } }
        }] : grant if role.phase == "apply"], [for grant in [
        {
          Sid      = "OwnershipAuditRead", Effect = "Allow", Action = ["s3:GetObjectVersion"]
          Resource = ["${local.bucket}/deploy/ownership/production/*"]
        },
        {
          Sid      = "OwnershipVersionInventory", Effect = "Allow", Action = ["s3:ListBucketVersions"]
          Resource = [local.bucket]
        }
    ] : grant if role.target == "production"])
  }) }
  common_read = [
    {
      Sid = "CallerIdentity", Effect = "Allow", Action = ["sts:GetCallerIdentity"], Resource = ["*"]
    },
    {
      Sid      = "RegionalInstanceMetadata", Effect = "Allow"
      Action   = ["ec2:DescribeInstances", "ec2:DescribeInstanceTypes", "ec2:DescribeInstanceCreditSpecifications", "ec2:DescribeVolumes", "ec2:DescribeAddresses"]
      Resource = ["*"], Condition = { StringEquals = local.regional }
    },
    {
      Sid    = "RegionalDatabaseMetadata", Effect = "Allow"
      Action = ["rds:DescribeDBInstances"], Resource = ["*"], Condition = { StringEquals = local.regional }
    }
  ]
  staging_read = concat(local.common_read, [
    {
      Sid      = "StagingNetworkMetadata", Effect = "Allow"
      Action   = ["ec2:DescribeSecurityGroups", "ec2:DescribeSecurityGroupRules", "ec2:DescribeAddressesAttribute"]
      Resource = ["*"], Condition = { StringEquals = local.regional }
    },
    {
      Sid       = "StagingInstanceAttributes", Effect = "Allow", Action = ["ec2:DescribeInstanceAttribute"]
      Resource  = ["${local.ec2}instance/*"]
      Condition = { StringEquals = merge(local.regional, { "ec2:ResourceTag/Name" = "meterengine-staging" }) }
    },
    {
      Sid      = "StagingRoleRefresh", Effect = "Allow"
      Action   = ["iam:GetRole", "iam:GetRolePolicy", "iam:ListRolePolicies", "iam:ListAttachedRolePolicies"]
      Resource = local.app_roles
    },
    {
      Sid = "StagingProfileRefresh", Effect = "Allow", Action = ["iam:GetInstanceProfile"], Resource = [local.profile]
    },
    {
      Sid      = "StagingParameterRefresh", Effect = "Allow", Action = ["ssm:GetParameter", "ssm:ListTagsForResource"]
      Resource = local.parameters, Condition = { StringEquals = local.regional }
    },
    {
      Sid      = "RegionalParameterMetadata", Effect = "Allow", Action = ["ssm:DescribeParameters"]
      Resource = ["*"], Condition = { StringEquals = local.regional }
    },
    {
      Sid      = "StagingParameterDecrypt", Effect = "Allow", Action = ["kms:Decrypt"]
      Resource = [local.ssm_key], Condition = local.kms_conditions
    },
    {
      Sid = "StagingDnsRefresh", Effect = "Allow", Action = ["route53:GetHostedZone", "route53:ListResourceRecordSets"], Resource = [local.zone]
    }
  ])
  production_read = concat(local.common_read, [
    {
      Sid      = "ProductionInstanceAttributes", Effect = "Allow", Action = ["ec2:DescribeInstanceAttribute"]
      Resource = ["${local.ec2}instance/i-0f47bb1f028cd29a9"], Condition = { StringEquals = local.regional }
    }
  ])
  staging_ec2 = [
    {
      Sid       = "LaunchImageSubnet", Effect = "Allow", Action = ["ec2:RunInstances"]
      Resource  = ["arn:aws:ec2:ap-northeast-2::image/ami-0f4109eb491b0476f", local.subnet]
      Condition = { StringEquals = local.regional }
    },
    {
      Sid = "LaunchStagingInstance", Effect = "Allow", Action = ["ec2:RunInstances"], Resource = ["${local.ec2}instance/*"]
      Condition = { StringEquals = merge(local.regional, {
        "aws:RequestTag/Name" = "meterengine-staging", "ec2:InstanceType" = "m7g.large", "ec2:InstanceProfile" = local.profile, "ec2:MetadataHttpTokens" = "required"
      }) }
    },
    {
      Sid       = "LaunchStagingGroup", Effect = "Allow", Action = ["ec2:RunInstances"], Resource = ["${local.ec2}security-group/*"]
      Condition = { StringEquals = merge(local.regional, { "ec2:Vpc" = local.vpc, "ec2:ResourceTag/Environment" = "staging" }) }
    },
    {
      Sid       = "LaunchInterface", Effect = "Allow", Action = ["ec2:RunInstances"], Resource = ["${local.ec2}network-interface/*"]
      Condition = { StringEquals = merge(local.regional, { "ec2:Vpc" = local.vpc, "ec2:Subnet" = local.subnet }) }
    },
    {
      Sid       = "LaunchVolume", Effect = "Allow", Action = ["ec2:RunInstances"], Resource = ["${local.ec2}volume/*"]
      Condition = { StringEquals = merge(local.regional, { "ec2:AvailabilityZone" = "ap-northeast-2a" }) }
    },
    {
      Sid       = "CreateGroupInVpc", Effect = "Allow", Action = ["ec2:CreateSecurityGroup"], Resource = [local.vpc]
      Condition = { StringEquals = local.regional }
    },
    {
      Sid       = "CreateTaggedGroup", Effect = "Allow", Action = ["ec2:CreateSecurityGroup"], Resource = ["${local.ec2}security-group/*"]
      Condition = { StringEquals = merge(local.regional, { "aws:RequestTag/Environment" = "staging" }) }
    },
    {
      Sid       = "StagingGroupRules", Effect = "Allow"
      Action    = ["ec2:AuthorizeSecurityGroupIngress", "ec2:AuthorizeSecurityGroupEgress", "ec2:RevokeSecurityGroupEgress", "ec2:RevokeSecurityGroupIngress", "ec2:ModifySecurityGroupRules"]
      Resource  = ["${local.ec2}security-group/*"]
      Condition = { StringEquals = merge(local.regional, local.staging_tag, { "ec2:Vpc" = local.vpc }) }
    },
    {
      Sid       = "CreateTaggedRules", Effect = "Allow", Action = ["ec2:AuthorizeSecurityGroupIngress", "ec2:AuthorizeSecurityGroupEgress"]
      Resource  = ["${local.ec2}security-group-rule/*"]
      Condition = { StringEquals = merge(local.regional, { "aws:RequestTag/Environment" = "staging" }) }
    },
    {
      Sid       = "ModifyTaggedRules", Effect = "Allow", Action = ["ec2:ModifySecurityGroupRules"], Resource = ["${local.ec2}security-group-rule/*"]
      Condition = { StringEquals = merge(local.regional, local.staging_tag) }
    },
    {
      Sid       = "CreateTaggedAddress", Effect = "Allow", Action = ["ec2:AllocateAddress"], Resource = ["${local.ec2}elastic-ip/*"]
      Condition = { StringEquals = merge(local.regional, { "aws:RequestTag/Environment" = "staging" }) }
    },
    {
      Sid       = "AssociateTaggedAddress", Effect = "Allow", Action = ["ec2:AssociateAddress"], Resource = ["${local.ec2}elastic-ip/*"]
      Condition = { StringEquals = merge(local.regional, local.staging_tag) }
    },
    {
      Sid       = "AssociateStagingInstance", Effect = "Allow", Action = ["ec2:AssociateAddress"], Resource = ["${local.ec2}instance/*"]
      Condition = { StringEquals = merge(local.regional, { "ec2:ResourceTag/Name" = "meterengine-staging" }) }
    },
    {
      Sid       = "AssociateStagingInterface", Effect = "Allow", Action = ["ec2:AssociateAddress"], Resource = ["${local.ec2}network-interface/*"]
      Condition = { StringEquals = merge(local.regional, { "ec2:Subnet" = local.subnet, "ec2:Vpc" = local.vpc }) }
    },
    {
      Sid = "InstanceCreateTags", Effect = "Allow", Action = ["ec2:CreateTags"], Resource = ["${local.ec2}instance/*"]
      Condition = {
        StringEquals                = merge(local.regional, { "ec2:CreateAction" = "RunInstances", "aws:RequestTag/Name" = "meterengine-staging" })
        "ForAllValues:StringEquals" = { "aws:TagKeys" = ["Name"] }
      }
    }
  ]
  network_tag_grants = [for entry in [
    { resource = "security-group", actions = ["CreateSecurityGroup"] },
    { resource = "elastic-ip", actions = ["AllocateAddress"] },
    { resource = "security-group-rule", actions = ["AuthorizeSecurityGroupIngress", "AuthorizeSecurityGroupEgress"] }
    ] : {
    Effect = "Allow", Action = ["ec2:CreateTags"], Resource = ["${local.ec2}${entry.resource}/*"]
    Condition = {
      StringEquals                = merge(local.regional, { "ec2:CreateAction" = entry.actions, "aws:RequestTag/Environment" = "staging" })
      "ForAllValues:StringEquals" = { "aws:TagKeys" = ["Environment"] }
    }
  }]
  staging_write = [
    {
      Sid = "CreateStagingDatabase", Effect = "Allow", Action = ["rds:CreateDBInstance"], Resource = ["${local.rds}db:meterengine-staging-db"]
      Condition = {
        StringEquals = merge(local.regional, { "rds:DatabaseClass" = "db.m7g.large", "rds:DatabaseEngine" = "postgres" })
        Bool         = { "rds:StorageEncrypted" = "true", "rds:PubliclyAccessible" = "false", "rds:Vpc" = "true" }
      }
    },
    {
      Sid       = "UseExistingDatabaseGroups", Effect = "Allow", Action = ["rds:CreateDBInstance"]
      Resource  = ["${local.rds}subgrp:meterengine-rds-subnet-private", "${local.rds}pg:default.postgres18", "${local.rds}og:default:postgres-18"]
      Condition = { StringEquals = local.regional }
    },
    {
      Sid      = "UpdateStagingDatabase", Effect = "Allow", Action = ["rds:ModifyDBInstance", "rds:AddTagsToResource"]
      Resource = ["${local.rds}db:meterengine-staging-db"], Condition = { StringEquals = local.regional }
    },
    {
      Sid      = "StagingApplicationRoles", Effect = "Allow"
      Action   = ["iam:CreateRole", "iam:UpdateAssumeRolePolicy", "iam:UpdateRole", "iam:UpdateRoleDescription", "iam:PutRolePolicy"]
      Resource = local.app_roles
    },
    {
      Sid = "StagingInstanceProfile", Effect = "Allow", Action = ["iam:CreateInstanceProfile", "iam:AddRoleToInstanceProfile"], Resource = [local.profile]
    },
    {
      Sid       = "PassOnlyStagingRuntime", Effect = "Allow", Action = ["iam:PassRole"], Resource = ["${local.iam}role/meterengine-staging-ec2"]
      Condition = { StringEquals = { "iam:PassedToService" = "ec2.amazonaws.com" } }
    },
    {
      Sid      = "WriteStagingParameters", Effect = "Allow", Action = ["ssm:PutParameter"]
      Resource = local.parameters, Condition = { StringEquals = local.regional }
    },
    {
      Sid = "StagingParameterEncrypt", Effect = "Allow", Action = ["kms:Encrypt"], Resource = [local.ssm_key], Condition = local.kms_conditions
    },
    {
      Sid = "StagingDnsChange", Effect = "Allow", Action = ["route53:ChangeResourceRecordSets"], Resource = [local.zone]
      Condition = { "ForAllValues:StringEquals" = {
        "route53:ChangeResourceRecordSetsNormalizedRecordNames" = ["staging.meterengine.com"]
        "route53:ChangeResourceRecordSetsRecordTypes"           = ["A"]
        "route53:ChangeResourceRecordSetsActions"               = ["CREATE", "UPSERT"]
      } }
    },
    {
      Sid = "DnsChangeWaiter", Effect = "Allow", Action = ["route53:GetChange"], Resource = ["arn:aws:route53:::change/*"]
    }
  ]
  production_write = [
    {
      Sid      = "ResizeExistingInstance", Effect = "Allow", Action = ["ec2:StopInstances", "ec2:StartInstances", "ec2:ModifyInstanceAttribute"]
      Resource = ["${local.ec2}instance/i-0f47bb1f028cd29a9"], Condition = { StringEquals = local.regional }
    },
    {
      Sid      = "ResizeExistingDatabase", Effect = "Allow", Action = ["rds:ModifyDBInstance"]
      Resource = ["${local.rds}db:meterengine-db"], Condition = { StringEquals = local.regional }
    }
  ]
  policies = merge(local.storage,
    { for key, role in local.roles : "${key}-read" => (role.target == "staging" ? jsonencode({ Version = "2012-10-17", Statement = local.staging_read }) : jsonencode({ Version = "2012-10-17", Statement = local.production_read })) },
    {
      "staging-apply-ec2"      = jsonencode({ Version = "2012-10-17", Statement = concat(local.staging_ec2, local.network_tag_grants) })
      "staging-apply-write"    = jsonencode({ Version = "2012-10-17", Statement = local.staging_write })
      "production-apply-write" = jsonencode({ Version = "2012-10-17", Statement = local.production_write })
    }
  )
  trusts = { for key, role in local.roles : key => jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow", Action = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = "${local.iam}oidc-provider/token.actions.githubusercontent.com" }
      Condition = { StringEquals = {
        "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        "token.actions.githubusercontent.com:sub" = "repo:asm17-ms2@293532363/meterengine@1313421667:${role.phase == "plan" ? "ref:refs/heads/main" : "environment:${role.target}-infrastructure"}"
      } }
    }]
  }) }
}

resource "aws_iam_role" "automation" {
  for_each             = local.roles
  name                 = "meterengine-terraform-${each.key}"
  assume_role_policy   = local.trusts[each.key]
  max_session_duration = each.value.phase == "plan" ? 3600 : (each.value.target == "staging" ? 10800 : 7200)
  lifecycle {
    prevent_destroy = true
    precondition {
      condition     = var.infrastructure_environments_verified
      error_message = "Verify separately approved native reviewers and main-only infrastructure Environments before activating trust."
    }
  }
}

resource "aws_iam_policy" "automation" {
  for_each = local.policies
  name     = "meterengine-terraform-${each.key}"
  policy   = each.value
  lifecycle {
    prevent_destroy = true
    precondition {
      condition     = length(each.value) <= 6144
      error_message = "Each managed policy must fit the 6144 character quota."
    }
  }
}

resource "aws_iam_role_policy_attachment" "automation" {
  for_each   = local.policies
  role       = aws_iam_role.automation[join("-", slice(split("-", each.key), 0, 2))].name
  policy_arn = aws_iam_policy.automation[each.key].arn
}
