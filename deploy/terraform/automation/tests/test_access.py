import fnmatch
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
BUCKET = 'arn:aws:s3:::meterengine-tfstate-928618307647'
EC2 = 'arn:aws:ec2:ap-northeast-2:928618307647:'
IAM = 'arn:aws:iam::928618307647:'
REGION = {'aws:RequestedRegion': 'ap-northeast-2'}


def matches_conditions(conditions, context):
    for operator, entries in conditions.items():
        for key, expected in entries.items():
            expected = expected if isinstance(expected, list) else [expected]
            actual = context.get(key)
            actual = actual if isinstance(actual, list) else ([] if actual is None else [actual])
            if operator == 'Null':
                if (not actual) != (expected == ['true']):
                    return False
            elif operator == 'ForAllValues:StringEquals':
                if not all(v in expected for v in actual):
                    return False
            elif operator in ('StringEquals', 'ArnEquals', 'Bool'):
                if not actual or not any(str(v).lower() == str(e).lower() if operator == 'Bool' else v == e for v in actual for e in expected):
                    return False
            elif operator in ('StringLike', 'ArnLike'):
                if not actual or not any(fnmatch.fnmatchcase(v, e) for v in actual for e in expected):
                    return False
            else:
                raise AssertionError('Unsupported condition operator: ' + operator)
    return True


def permitted(documents, action, resource, context=None):
    allowed = False
    for document in documents:
        for statement in document['Statement']:
            if not any(fnmatch.fnmatchcase(action.lower(), a.lower()) for a in statement['Action']):
                continue
            if not any(fnmatch.fnmatchcase(resource, r) for r in statement['Resource']):
                continue
            if not matches_conditions(statement.get('Condition', {}), context or {}):
                continue
            if statement['Effect'] == 'Deny':
                return False
            allowed = True
    return allowed


class AccessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run(['terraform', '-chdir=' + str(ROOT), 'test', '-json', '-verbose', '-filter=tests/access.tftest.hcl'], capture_output=True, text=True, env={**os.environ, 'AWS_EC2_METADATA_DISABLED': 'true'})
        if result.returncode:
            raise AssertionError('Mock Terraform plan failed:\n' + result.stdout + result.stderr)
        plans = [e['test_plan'] for line in result.stdout.splitlines() if (e := json.loads(line)).get('type') == 'test_plan' and e.get('@testrun') == 'apply_roles_are_target_scoped']
        outputs = plans[0]['output_changes']
        cls.documents = {k: json.loads(v) for k, v in outputs['policy_documents']['after'].items()}
        cls.trust = {k: json.loads(v) for k, v in outputs['trust_documents']['after'].items()}

    def check(self, role, action, resource, allowed, context=None):
        docs = [v for k, v in self.documents.items() if k.startswith(role + '-')]
        self.assertEqual(permitted(docs, action, resource, context), allowed, (role, action, resource, context))

    def test_backend_and_artifact_boundaries(self):
        for target in ['staging', 'production']:
            for phase in ['plan', 'apply']:
                role = target + '-' + phase
                state = BUCKET + '/deploy/' + target + '/terraform.tfstate'
                other = 'production' if target == 'staging' else 'staging'
                cases = [
                    ('s3:GetObject', state, True, {}),
                    ('s3:GetObjectVersion', state, True, {}),
                    ('s3:PutObject', state, phase == 'apply', {}),
                    ('s3:DeleteObject', state, False, {}),
                    ('s3:DeleteObjectVersion', state, False, {}),
                    ('s3:PutObject', state + '.tflock', True, {}),
                    ('s3:DeleteObject', state + '.tflock', True, {}),
                    ('s3:GetObject', BUCKET + '/deploy/' + other + '/terraform.tfstate', False, {}),
                    ('s3:GetObjectVersion', BUCKET + '/deploy/automation/terraform.tfstate', False, {}),
                    ('s3:GetObject', BUCKET + '/deploy/plans/' + target + '/run/plan.tfplan', False, {}),
                    ('s3:GetObjectVersion', BUCKET + '/deploy/plans/' + target + '/run/plan.tfplan', True, {}),
                    ('s3:PutObject', BUCKET + '/deploy/plans/' + target + '/run/plan.tfplan', phase == 'plan', {'s3:x-amz-server-side-encryption': 'AES256'}),
                    ('s3:PutObject', BUCKET + '/deploy/plans/' + target + '/run/plan.tfplan', False, {}),
                    ('s3:ListBucketVersions', BUCKET, True, {'s3:prefix': 'deploy/' + target + '/terraform.tfstate'}),
                    ('s3:ListBucketVersions', BUCKET, target == 'production', {}),
                    ('s3:ListBucket', BUCKET, False, {'s3:prefix': 'deploy/' + other + '/'}),
                    ('s3:GetObjectVersion', BUCKET + '/deploy/ownership/production/approved.json', target == 'production', {}),
                    ('s3:PutObject', BUCKET + '/deploy/ownership/production/approved.json', False, {}),
                ]
                for action, resource, allowed, context in cases:
                    with self.subTest(role=role, action=action, resource=resource, context=context):
                        self.check(role, action, resource, allowed, context)

    def test_apply_can_write_only_its_encrypted_diagnostics(self):
        encrypted = {'s3:x-amz-server-side-encryption': 'AES256'}
        for target in ['staging', 'production']:
            role = target + '-apply'
            other = 'production' if target == 'staging' else 'staging'
            base = BUCKET + '/deploy/plans/' + target + '/100/1/' + 'a' * 40 + '/'
            for filename in ['0.log', '1.log', 'failure.log']:
                resource = base + 'diagnostics/' + filename
                with self.subTest(role=role, resource=resource):
                    self.check(role, 's3:PutObject', resource, True, encrypted)
                    self.check(role, 's3:PutObject', resource, False, {})
                    self.check(role, 's3:PutObject', resource, False, {'s3:x-amz-server-side-encryption': 'aws:kms'})
                    self.check(role, 's3:DeleteObject', resource, False, encrypted)
                    self.check(role, 's3:DeleteObjectVersion', resource, False, encrypted)
            for key in ['plan.tfplan', 'context.json', 'unreviewed.log']:
                self.check(role, 's3:PutObject', base + key, False, encrypted)
            for key in ['deploy/plans/' + other + '/100/1/source/diagnostics/0.log',
                        'deploy/ownership/production/approved.json', 'deploy/automation/terraform.tfstate',
                        'deploy/' + other + '/terraform.tfstate']:
                self.check(role, 's3:PutObject', BUCKET + '/' + key, False, encrypted)

    def test_runner_apply_diagnostics_match_generated_policy_before_success(self):
        sys.path.insert(0, str(ROOT.parents[1]))
        import terraform_automation as a
        import terraform_workflow as w
        target = a.load_target('staging')
        context = dict(schema_version=1, repository_id=target['repository_id'], repository_owner_id=target['repository_owner_id'],
                       aws_account_id=a.ACCOUNT, aws_region=a.REGION, source_sha='a' * 40, target='staging',
                       state_key=target['state_key'], state_exists=False, state_version_id=None, run_id='100', run_attempt='1',
                       terraform_version='1.17.1', lockfile_sha256=a.digest(w.ROOT / target['root'] / '.terraform.lock.hcl'),
                       plan_key='deploy/plans/staging/100/1/' + 'a' * 40 + '/plan.tfplan', plan_version_id='plan-v1',
                       plan_sha256='b' * 64, ownership_audit_key=None, ownership_audit_version_id=None, ownership_audit_sha256=None)
        expected = dict(context, context_key=a.prefix(context) + '/context.json', context_version_id='context-v1', context_sha256='c' * 64)
        env = {'GITHUB_REPOSITORY': target['repository'], 'GITHUB_REPOSITORY_ID': target['repository_id'],
               'GITHUB_REPOSITORY_OWNER_ID': target['repository_owner_id'], 'GITHUB_REF': 'refs/heads/main',
               'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_SHA': 'a' * 40, 'GITHUB_RUN_ID': '100', 'GITHUB_RUN_ATTEMPT': '1',
               'EXPECTED_PLAN': json.dumps(expected), 'TF_STAGING_DB_PASSWORD': 'REAL_LOCAL_FIXTURE',
               'TF_STAGING_RUNTIME_SECRETS_JSON': json.dumps(dict.fromkeys(a.SECRET_KEYS, 'REAL_LOCAL_FIXTURE'))}
        docs = [v for k, v in self.documents.items() if k.startswith('staging-apply-')]
        for denied in [False, True]:
            with self.subTest(upload_denied=denied), tempfile.TemporaryDirectory(dir=os.environ.get('TF_WORKFLOW_TEST_SCRATCH')) as tmp:
                directory = Path(tmp)
                directory.chmod(0o700)
                events = []
                def terraform(root, args, directory, env, logs):
                    events.append(args[0])
                    log_dir = a.private_directory(directory)
                    out, err = log_dir / 'stdout.log', log_dir / 'stderr.log'
                    payload = json.dumps({'format_version': '1.2', 'terraform_version': '1.17.1', 'complete': True,
                                          'resource_changes': []}) if args[0] == 'show' else 'PRIVATE_FIXTURE_LOG'
                    a.private_write(out, payload.encode())
                    a.private_write(err, b'')
                    logs.extend([out, err])
                    return out
                def remote(command):
                    self.assertEqual(command[:7], ['aws', '--region', a.REGION, '--output', 'json', 's3api', 'put-object'])
                    key = command[command.index('--key') + 1]
                    encryption = command[command.index('--server-side-encryption') + 1]
                    allowed = permitted(docs, 's3:PutObject', BUCKET + '/' + key,
                                        {} if denied else {'s3:x-amz-server-side-encryption': encryption})
                    events.append('diagnostic-allowed' if allowed else 'diagnostic-denied')
                    a.require(allowed, 'IAM_PUT_DENIED')
                    return {'VersionId': 'diagnostic-v1'}
                error = None
                with patch.object(w, 'prepare'), patch.object(a, 'bucket_preflight', return_value={'exists': False, 'version_id': None}), \
                     patch.object(a, 'target_state', return_value={'exists': False, 'version_id': None}), patch.object(a, 'fetch_plan'), \
                     patch.object(w, 'terraform_command', side_effect=terraform), patch.object(a, 'command_json', side_effect=remote), \
                     patch.object(w, 'publish', side_effect=lambda result, **kwargs: events.append('success-output')):
                    try:
                        w.execute(target, 'apply', directory, env)
                    except a.AutomationError as failure:
                        error = failure.args[0]
                self.assertIn('apply', events)
                if denied:
                    self.assertEqual(error, 'PRIVATE_UPLOAD_FAILED')
                    self.assertNotIn('success-output', events)
                else:
                    self.assertIsNone(error, 'Generated apply policy must permit real runner diagnostic keys')
                    self.assertLess(events.index('apply'), events.index('diagnostic-allowed'))
                    self.assertLess(events.index('diagnostic-allowed'), events.index('success-output'))

    def test_plan_and_production_cannot_write_staging_resources(self):
        for role in ['staging-plan', 'production-plan', 'production-apply']:
            for action, resource in [('iam:PutRolePolicy', IAM + 'role/meterengine-staging-ec2'), ('ssm:PutParameter', 'arn:aws:ssm:ap-northeast-2:928618307647:parameter/meterengine/staging/operator-password'), ('ec2:RunInstances', EC2 + 'instance/i-new')]:
                self.check(role, action, resource, False, REGION)
        for action in ['ec2:StopInstances', 'ec2:StartInstances', 'ec2:ModifyInstanceAttribute']:
            self.check('production-apply', action, EC2 + 'instance/i-0f47bb1f028cd29a9', True, REGION)
            self.check('production-apply', action, EC2 + 'instance/i-other', False, REGION)
            self.check('production-apply', action, EC2 + 'instance/i-0f47bb1f028cd29a9', False, {'aws:RequestedRegion': 'us-east-1'})

    def test_staging_identity_tags_and_create_context(self):
        role = 'staging-apply'
        self.check(role, 'ec2:AllocateAddress', EC2 + 'elastic-ip/eipalloc-new', True, {**REGION, 'aws:RequestTag/Environment': 'staging'})
        self.check(role, 'ec2:AllocateAddress', EC2 + 'elastic-ip/eipalloc-new', False, REGION)
        self.check(role, 'ec2:AssociateAddress', EC2 + 'elastic-ip/eipalloc-existing', False, {**REGION, 'aws:ResourceTag/Environment': 'production'})
        self.check(role, 'ec2:CreateTags', EC2 + 'security-group/sg-existing', False, {**REGION, 'aws:RequestTag/Environment': 'staging', 'aws:TagKeys': ['Environment']})
        self.check(role, 'ec2:CreateTags', EC2 + 'security-group/sg-new', True, {**REGION, 'ec2:CreateAction': 'CreateSecurityGroup', 'aws:RequestTag/Environment': 'staging', 'aws:TagKeys': ['Environment']})
        self.check(role, 'ec2:CreateTags', EC2 + 'security-group/sg-new', False, {**REGION, 'ec2:CreateAction': 'CreateSecurityGroup', 'aws:RequestTag/Environment': 'staging', 'aws:TagKeys': ['Environment', 'Unreviewed']})
        self.check(role, 'ec2:RunInstances', EC2 + 'volume/vol-new', True, {**REGION, 'ec2:AvailabilityZone': 'ap-northeast-2a'})
        self.check(role, 'ec2:RunInstances', EC2 + 'network-interface/eni-new', True, {**REGION, 'ec2:Subnet': EC2 + 'subnet/subnet-0348a18c1d399c193', 'ec2:Vpc': EC2 + 'vpc/vpc-07498f0672b7f3c9e'})
        self.check(role, 'ec2:RunInstances', EC2 + 'network-interface/eni-new', False, {**REGION, 'ec2:Subnet': EC2 + 'subnet/subnet-other', 'ec2:Vpc': EC2 + 'vpc/vpc-07498f0672b7f3c9e'})

    def test_global_services_and_parameter_scope(self):
        self.check('staging-apply', 'iam:PassRole', IAM + 'role/meterengine-staging-ec2', True, {'iam:PassedToService': 'ec2.amazonaws.com'})
        self.check('staging-apply', 'iam:PassRole', IAM + 'role/meterengine-staging-owner', False, {'iam:PassedToService': 'ec2.amazonaws.com'})
        self.check('staging-apply', 'iam:PassRole', IAM + 'role/meterengine-staging-ec2', False, {'iam:PassedToService': 'lambda.amazonaws.com'})
        dns = {'route53:ChangeResourceRecordSetsNormalizedRecordNames': ['staging.meterengine.com'], 'route53:ChangeResourceRecordSetsRecordTypes': ['A'], 'route53:ChangeResourceRecordSetsActions': ['UPSERT']}
        zone = 'arn:aws:route53:::hostedzone/Z08728621CQ5R1K37A7N1'
        self.check('staging-apply', 'route53:ChangeResourceRecordSets', zone, True, dns)
        for key, value in [('route53:ChangeResourceRecordSetsNormalizedRecordNames', ['meterengine.com']), ('route53:ChangeResourceRecordSetsActions', ['DELETE'])]:
            self.check('staging-apply', 'route53:ChangeResourceRecordSets', zone, False, {**dns, key: value})
        for target in ['staging', 'production', 'prod', 'staging-unreviewed']:
            self.check('staging-apply', 'ssm:PutParameter', 'arn:aws:ssm:ap-northeast-2:928618307647:parameter/meterengine/' + target + '/operator-password', target == 'staging', REGION)
        self.check('staging-plan', 'rds:DescribeDBInstances', '*', True, REGION)
        self.check('staging-plan', 'rds:DescribeDBInstances', '*', False, {'aws:RequestedRegion': 'us-east-1'})

    def test_staging_create_and_refresh_paths_require_supported_context(self):
        role = 'staging-apply'
        instance_context = {**REGION, 'aws:RequestTag/Name': 'meterengine-staging', 'ec2:InstanceType': 'm7g.large', 'ec2:InstanceProfile': IAM + 'instance-profile/meterengine-staging-ec2', 'ec2:MetadataHttpTokens': 'required'}
        self.check(role, 'ec2:RunInstances', EC2 + 'instance/i-new', True, instance_context)
        for key in ['aws:RequestTag/Name', 'ec2:InstanceType', 'ec2:InstanceProfile', 'ec2:MetadataHttpTokens']:
            self.check(role, 'ec2:RunInstances', EC2 + 'instance/i-new', False, {k: v for k, v in instance_context.items() if k != key})
        self.check(role, 'ec2:RunInstances', 'arn:aws:ec2:ap-northeast-2::image/ami-0f4109eb491b0476f', True, REGION)
        self.check(role, 'ec2:RunInstances', 'arn:aws:ec2:ap-northeast-2::image/ami-other', False, REGION)
        db = 'arn:aws:rds:ap-northeast-2:928618307647:db:meterengine-staging-db'
        database_context = {**REGION, 'rds:DatabaseClass': 'db.m7g.large', 'rds:DatabaseEngine': 'postgres', 'rds:StorageEncrypted': 'true', 'rds:PubliclyAccessible': 'false', 'rds:Vpc': 'true'}
        self.check(role, 'rds:CreateDBInstance', db, True, database_context)
        self.check(role, 'rds:CreateDBInstance', db, False, {**database_context, 'rds:PubliclyAccessible': 'true'})
        self.check(role, 'rds:ModifyDBInstance', 'arn:aws:rds:ap-northeast-2:928618307647:db:meterengine-db', False, REGION)
        for prefix in ['staging', 'staging-migration', 'production', 'prod']:
            name = 'db-migration-password' if prefix.endswith('-migration') else 'operator-password'
            parameter = 'arn:aws:ssm:ap-northeast-2:928618307647:parameter/meterengine/' + prefix + '/' + name
            self.check('staging-plan', 'ssm:GetParameter', parameter, prefix in ['staging', 'staging-migration'], REGION)
        key = 'arn:aws:kms:ap-northeast-2:928618307647:key/08d25846-2de5-4acd-a69a-5168d7982aa8'
        kms_context = {**REGION, 'kms:ViaService': 'ssm.ap-northeast-2.amazonaws.com', 'kms:CallerAccount': '928618307647', 'kms:EncryptionContext:PARAMETER_ARN': 'arn:aws:ssm:ap-northeast-2:928618307647:parameter/meterengine/staging/operator-password'}
        self.check('staging-plan', 'kms:Decrypt', key, True, kms_context)
        self.check('staging-plan', 'kms:Decrypt', key, False, {**kms_context, 'kms:ViaService': 'ec2.ap-northeast-2.amazonaws.com'})
        self.check('staging-plan', 'kms:Decrypt', key, False, {**kms_context, 'kms:EncryptionContext:PARAMETER_ARN': 'arn:aws:ssm:ap-northeast-2:928618307647:parameter/meterengine/production/operator-password'})
        self.check('staging-plan', 'kms:Encrypt', key, False, kms_context)
        self.check(role, 'kms:Encrypt', key, True, kms_context)

    def test_managed_policy_size_attachment_and_action_quotas(self):
        for name, document in self.documents.items():
            self.assertLessEqual(len(json.dumps(document, separators=(',', ':')).encode()), 6144, name)
            for statement in document['Statement']:
                self.assertFalse(any('*' in action for action in statement['Action']))
                if statement['Effect'] == 'Allow':
                    self.assertNotIn('arn:aws:iam::aws:policy/AdministratorAccess', statement['Resource'])
        for role in self.trust:
            self.assertLessEqual(len([k for k in self.documents if k.startswith(role + '-')]), 20)

    def test_automation_roles_and_policies_remain_protected_with_extra_allow(self):
        extra = {'Statement': [{'Effect': 'Allow', 'Action': ['iam:PutRolePolicy', 'iam:CreatePolicyVersion', 'iam:PassRole', 'iam:AttachRolePolicy'], 'Resource': ['*']}]}
        for role in ['staging-plan', 'staging-apply', 'production-plan', 'production-apply']:
            docs = [v for k, v in self.documents.items() if k.startswith(role + '-')] + [extra]
            for target in ['staging', 'production']:
                for phase in ['plan', 'apply']:
                    arn = IAM + 'role/meterengine-terraform-' + target + '-' + phase
                    for action in ['iam:PutRolePolicy', 'iam:PassRole', 'iam:AttachRolePolicy']:
                        self.assertFalse(permitted(docs, action, arn))
            self.assertFalse(permitted(docs, 'iam:CreatePolicyVersion', IAM + 'policy/meterengine-terraform-staging-apply-ec2'))

    def test_oidc_subject_and_audience_fail_closed(self):
        for role, document in self.trust.items():
            statement = document['Statement'][0]
            self.assertEqual(statement['Principal'], {'Federated': IAM + 'oidc-provider/token.actions.githubusercontent.com'})
            self.assertEqual(statement['Action'], 'sts:AssumeRoleWithWebIdentity')
            condition = statement['Condition']
            subject = 'repo:asm17-ms2@293532363/meterengine@1313421667:' + ('ref:refs/heads/main' if role.endswith('-plan') else 'environment:' + role.split('-')[0] + '-infrastructure')
            claims = {'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com', 'token.actions.githubusercontent.com:sub': subject}
            self.assertTrue(matches_conditions(condition, claims))
            for bad in ['repo:asm17-ms2/meterengine:ref:refs/heads/main', subject.replace('1313421667', '999'), 'repo:asm17-ms2@293532363/meterengine@1313421667:pull_request', 'repo:asm17-ms2@293532363/meterengine@1313421667:ref:refs/heads/develop']:
                self.assertFalse(matches_conditions(condition, {**claims, 'token.actions.githubusercontent.com:sub': bad}))
            self.assertFalse(matches_conditions(condition, {**claims, 'token.actions.githubusercontent.com:aud': 'other'}))


if __name__ == '__main__':
    unittest.main(verbosity=2)
