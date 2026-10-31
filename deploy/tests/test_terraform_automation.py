import contextlib
import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
import shutil
import subprocess
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

KEYS = ['db-shared-password', 'db-test-password', 'db-live-password', 'crypto-encryption-key', 'operator-password']
SENTINEL = 'PRIVATE_SENTINEL_DO_NOT_ECHO'


class AutomationTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('terraform_automation'), 'automation helper is missing')
        import terraform_automation
        self.a = terraform_automation
        self.target = self.a.load_target('staging', Path('deploy/terraform/automation-targets.v1.json'))
        self.context = dict(schema_version=1, repository_id='1313421667', repository_owner_id='293532363',
                            aws_account_id='928618307647', aws_region='ap-northeast-2', source_sha='a' * 40,
                            target='staging', state_key='deploy/staging/terraform.tfstate', state_exists=False,
                            state_version_id=None, run_id='100', run_attempt='1', terraform_version='1.17.1',
                            lockfile_sha256='b' * 64, plan_key='deploy/plans/staging/100/1/' + 'a' * 40 + '/plan.tfplan',
                            plan_version_id='plan-v1', plan_sha256=hashlib.sha256(b'private plan').hexdigest(),
                            ownership_audit_key=None, ownership_audit_version_id=None, ownership_audit_sha256=None)
        self.github = {'GITHUB_REPOSITORY': 'asm17-ms2/meterengine', 'GITHUB_REPOSITORY_ID': '1313421667',
                       'GITHUB_REPOSITORY_OWNER_ID': '293532363', 'GITHUB_REF': 'refs/heads/main',
                       'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_SHA': 'a' * 40,
                       'GITHUB_RUN_ID': '100', 'GITHUB_RUN_ATTEMPT': '1'}
        self.environment = {'name': 'staging-infrastructure',
                            'protection_rules': [{'type': 'required_reviewers', 'reviewers': [{'type': 'User', 'reviewer': {'id': 124319540}}]}],
                            'deployment_branch_policy': {'protected_branches': False, 'custom_branch_policies': True}}
        self.branches = {'total_count': 1, 'branch_policies': [{'id': 1, 'name': 'main', 'type': 'branch'}]}

    def reject(self, fn, *args, **kwargs):
        with self.assertRaises(self.a.AutomationError) as raised:
            fn(*args, **kwargs)
        self.assertNotIn(SENTINEL, str(raised.exception))

    def test_non_main_and_fork_rejected(self):
        self.a.validate_context(self.context, self.target)
        self.a.validate_github(self.github, self.context)
        for field, value in [('GITHUB_REF', 'refs/heads/feature'), ('GITHUB_EVENT_NAME', 'pull_request'),
                             ('GITHUB_REPOSITORY_ID', '42'), ('GITHUB_REPOSITORY_OWNER_ID', '42'),
                             ('GITHUB_REPOSITORY', 'fork/meterengine'), ('GITHUB_SHA', 'c' * 40)]:
            self.reject(self.a.validate_github, dict(self.github, **{field: value}), self.context)

    def test_missing_reviewer_or_main_rule_rejected(self):
        self.a.validate_approval(self.environment, self.branches)
        for env in [dict(self.environment, protection_rules=[]),
                    dict(self.environment, deployment_branch_policy={'protected_branches': True, 'custom_branch_policies': False})]:
            self.reject(self.a.validate_approval, env, self.branches)
        for branches in [{'total_count': 0, 'branch_policies': []},
                         {'total_count': 1, 'branch_policies': [{'name': 'main', 'type': 'tag'}]},
                         {'total_count': 2, 'branch_policies': self.branches['branch_policies']},
                         {'total_count': 2, 'branch_policies': self.branches['branch_policies'] + [{'name': '*', 'type': 'branch'}]}]:
            self.reject(self.a.validate_approval, self.environment, branches)

    def test_github_preflight_reads_all_pages_before_credentials(self):
        calls = []
        def gh(path):
            calls.append(path)
            if 'deployment-branch-policies' not in path:
                return self.environment
            if 'page=1' in path:
                return {'total_count': 2, 'branch_policies': self.branches['branch_policies']}
            return {'total_count': 2, 'branch_policies': [{'name': '*', 'type': 'branch'}]}
        self.reject(self.a.github_preflight, self.target, self.github, self.context, gh=gh)
        self.assertEqual(len(calls), 3)

    def test_wrong_target_sha_attempt_version_or_lock_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            plan = Path(tmp) / 'plan'
            plan.write_bytes(b'private plan')
            self.a.verify_artifact(self.context, self.context, plan)
            for field, value in [('target', 'production'), ('source_sha', 'c' * 40), ('run_attempt', '2'),
                                 ('plan_version_id', 'v2'), ('lockfile_sha256', 'd' * 64),
                                 ('terraform_version', '1.17.0'), ('state_exists', True), ('plan_key', SENTINEL),
                                 ('ownership_audit_key', SENTINEL)]:
                self.reject(self.a.verify_artifact, dict(self.context, **{field: value}), self.context, plan)
            plan.write_bytes(b'tampered')
            self.reject(self.a.verify_artifact, self.context, self.context, plan)

    def test_latest_without_version_rejected(self):
        for field in ['plan_version_id', 'plan_sha256']:
            self.reject(self.a.validate_context, dict(self.context, **{field: None}), self.target)
        with tempfile.TemporaryDirectory() as tmp:
            self.reject(self.a.download_object, 'deploy/plans/a', None, 'b' * 64, Path(tmp) / 'plan')

    def test_summary_omits_original_values(self):
        plan = {'format_version': '1.2', 'terraform_version': '1.17.1', 'complete': True,
                'resource_changes': [{'address': 'module.service.aws_instance.app', 'mode': 'managed',
                                      'change': {'actions': ['create'], 'before': None,
                                                 'after': {'user_data': SENTINEL, 'tags': {'secret': SENTINEL}, 'password': SENTINEL, 'value': SENTINEL}}}],
                'outputs': {'secret': SENTINEL}}
        summary = self.a.summarize_plan(plan, self.target)
        self.assertEqual(summary['counts'], {'create': 1, 'update': 0, 'delete': 0, 'replace': 0})
        self.assertNotIn(SENTINEL, json.dumps(summary))
        plan['resource_changes'][0]['address'] = 'module.' + SENTINEL
        self.reject(self.a.summarize_plan, plan, self.target)
        plan['resource_changes'][0]['address'] = 'aws_ssm_parameter.runtime["' + SENTINEL + '"]'
        self.reject(self.a.summarize_plan, plan, self.target)

    def test_missing_or_dummy_apply_secret_rejected(self):
        supplied = {'TF_STAGING_DB_PASSWORD': 'StablePassword123!', 'TF_STAGING_RUNTIME_SECRETS_JSON': json.dumps(dict.fromkeys(KEYS, 'real-value'))}
        env = self.a.apply_ephemeral_env(self.target, supplied)
        self.assertEqual(set(env), {'TF_VAR_db_password_wo', 'TF_VAR_runtime_secrets'})
        self.assertEqual(env['TF_VAR_db_password_wo'], 'StablePassword123!')
        for secret in ['', 'TF_PLAN_ONLY_NeverDeploy123!', SENTINEL + 'TF_PLAN_ONLY_x']:
            self.reject(self.a.apply_ephemeral_env, self.target, dict(supplied, TF_STAGING_DB_PASSWORD=secret))
        self.reject(self.a.apply_ephemeral_env, self.target, {})
        for data in [{}, dict.fromkeys(KEYS, ''), dict.fromkeys(KEYS, 'TF_PLAN_ONLY_x'), dict.fromkeys(KEYS + ['extra'], 'x')]:
            self.reject(self.a.apply_ephemeral_env, self.target, dict(supplied, TF_STAGING_RUNTIME_SECRETS_JSON=json.dumps(data)))

    def test_production_never_sets_password(self):
        target = self.a.load_target('production', Path('deploy/terraform/automation-targets.v1.json'))
        self.assertEqual(self.a.apply_ephemeral_env(target, {}), {})
        self.reject(self.a.apply_ephemeral_env, target, {'TF_VAR_db_password_wo': SENTINEL})
        self.assertEqual(self.a.plan_ephemeral_env(target), {})
        env = self.a.plan_ephemeral_env(self.target)
        self.assertTrue(env['TF_VAR_db_password_wo'].startswith('TF_PLAN_ONLY'))
        self.assertEqual(set(json.loads(env['TF_VAR_runtime_secrets'])), set(KEYS))

    def test_state_version_changed_rejected(self):
        self.a.verify_state(self.context, {'exists': False, 'version_id': None})
        for state in [{'exists': True, 'version_id': 'new'}, {'exists': False, 'version_id': 'version'}]:
            self.reject(self.a.verify_state, self.context, state)
        current = dict(self.context, state_exists=True, state_version_id='v1')
        self.a.verify_state(current, {'exists': True, 'version_id': 'v1'})
        self.reject(self.a.verify_state, current, {'exists': True, 'version_id': 'v2'})

    def test_initial_import_requires_approved_audit(self):
        target = self.a.load_target('production', Path('deploy/terraform/automation-targets.v1.json'))
        context = dict(self.context, target='production', state_key=target['state_key'], plan_key=self.context['plan_key'].replace('staging', 'production'))
        self.reject(self.a.validate_context, context, target)
        context.update(ownership_audit_key='deploy/ownership/production/audit.json', ownership_audit_version_id='audit-v1', ownership_audit_sha256='f' * 64)
        self.a.validate_context(context, target)
        self.reject(self.a.validate_context, dict(context, ownership_audit_key='deploy/production/terraform.tfstate'), target)

    def test_changed_known_state_versions_reject_audit(self):
        audit = {'state_versions': {'old/terraform.tfstate': {'version_id': 'v1'}}}
        self.a.verify_audit_versions(audit, {'old/terraform.tfstate': 'v1'})
        for versions in [{'old/terraform.tfstate': 'v2'}, {'old/terraform.tfstate': 'v1', 'new/terraform.tfstate': 'v1'}, {}]:
            self.reject(self.a.verify_audit_versions, audit, versions)

    def test_failed_plan_diagnostics_are_private(self):
        uploads = []
        def aws(args):
            self.assertIn('--server-side-encryption', args)
            self.assertEqual(args[args.index('--server-side-encryption') + 1], 'AES256')
            body = Path(args[args.index('--body') + 1])
            self.assertEqual(stat.S_IMODE(body.stat().st_mode), 0o600)
            uploads.append(body.read_bytes())
            return {'VersionId': 'diag-v1'}
        with tempfile.TemporaryDirectory() as tmp:
            private = self.a.private_directory(Path(tmp))
            self.assertEqual(stat.S_IMODE(private.stat().st_mode), 0o700)
            path = private / 'terraform.log'
            self.a.private_write(path, SENTINEL.encode())
            result = self.a.store_diagnostics(self.context, [path], aws=aws)
            self.assertNotIn(SENTINEL, json.dumps(result))
            self.assertEqual(uploads, [SENTINEL.encode()])
            self.assertEqual(result[0]['version_id'], 'diag-v1')
            def failure(args):
                raise RuntimeError(SENTINEL)
            self.reject(self.a.store_diagnostics, self.context, [path], aws=failure)

    def test_exact_version_download_and_hash_no_latest_fallback(self):
        calls = []
        def aws(args):
            calls.append(args)
            Path(args[-1]).write_bytes(b'private plan')
            return {'VersionId': 'plan-v1'}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'plan'
            self.a.download_object(self.context['plan_key'], 'plan-v1', self.context['plan_sha256'], path, aws=aws)
            self.assertEqual(calls[0][calls[0].index('--version-id') + 1], 'plan-v1')
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)

    def test_staging_summary_accepts_real_resource_names(self):
        addresses = ['aws_eip_association.app', 'aws_vpc_security_group_ingress_rule.web',
                     'aws_vpc_security_group_egress_rule.https', 'aws_vpc_security_group_egress_rule.database',
                     'aws_vpc_security_group_ingress_rule.database', 'aws_iam_role.github_deploy',
                     'aws_iam_role_policy.github_deploy', 'aws_iam_role.github_owner', 'aws_iam_role_policy.github_owner',
                     'aws_ssm_parameter.migration_username', 'aws_ssm_parameter.migration_password',
                     'aws_ssm_parameter.runtime["db-shared-password"]']
        plan = {'format_version': '1.2', 'terraform_version': '1.17.1', 'complete': True,
                'resource_changes': [{'address': a, 'change': {'actions': ['create']}} for a in addresses]}
        self.assertEqual(self.a.summarize_plan(plan, self.target)['counts']['create'], 12)

    def test_private_command_captures_output_and_removes_logging_overrides(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = self.a.private_directory(Path(tmp))
            code = 'import os,sys; print("' + SENTINEL + '"); print(os.environ.get("TF_LOG"), file=sys.stderr); assert "TF_CLI_ARGS" not in os.environ'
            status, stdout, stderr = self.a.run_private([sys.executable, '-c', code], directory,
                                                       env=dict(os.environ, TF_LOG='TRACE', TF_CLI_ARGS='-lock=false'))
            self.assertEqual(status, 0)
            self.assertIn(SENTINEL, stdout.read_text())
            self.assertEqual(stderr.read_text().strip(), 'OFF')
            self.assertEqual(stat.S_IMODE(stdout.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(stderr.stat().st_mode), 0o600)

    def test_plan_inputs_require_explicit_positive_versions(self):
        supplied = {'TF_STAGING_DB_PASSWORD_VERSION': '1', 'TF_STAGING_RUNTIME_SECRET_VERSIONS_JSON': json.dumps(dict.fromkeys(KEYS, 1)),
                    'TF_STAGING_OPERATOR_EMAIL': 'operator@example.com'}
        result = self.a.plan_variable_env(self.target, supplied)
        self.assertEqual(result['TF_VAR_db_password_version'], '1')
        for field, value in [('TF_STAGING_DB_PASSWORD_VERSION', '0'), ('TF_STAGING_DB_PASSWORD_VERSION', 'true'),
                             ('TF_STAGING_RUNTIME_SECRET_VERSIONS_JSON', '{}'), ('TF_STAGING_OPERATOR_EMAIL', SENTINEL)]:
            self.reject(self.a.plan_variable_env, self.target, dict(supplied, **{field: value}))

    def test_bucket_preflight_rejects_public_or_unversioned_storage(self):
        def aws(args):
            op = args[1]
            return {'get-caller-identity': {'Account': '928618307647'},
                    'get-bucket-versioning': {'Status': 'Enabled'},
                    'get-public-access-block': {'PublicAccessBlockConfiguration': dict.fromkeys(['BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets'], True)},
                    'get-bucket-policy-status': {'PolicyStatus': {'IsPublic': False}},
                    'list-object-versions': {'Versions': []}}[op]
        self.assertEqual(self.a.bucket_preflight(self.target, aws=aws), {'exists': False, 'version_id': None})
        for op, bad in [('get-caller-identity', {'Account': '42'}), ('get-bucket-versioning', {}),
                        ('get-public-access-block', {}), ('get-bucket-policy-status', {'PolicyStatus': {'IsPublic': True}})]:
            self.reject(self.a.bucket_preflight, self.target, aws=lambda args: bad if args[1] == op else aws(args))

    def test_bucket_preflight_accepts_explicit_absent_policy_for_both_targets(self):
        for name in ['staging', 'production']:
            with self.subTest(target=name):
                target = self.a.load_target(name)
                calls = []
                def run(args, **kwargs):
                    calls.append(args)
                    operation = args[6]
                    if operation == 'get-bucket-policy-status':
                        return subprocess.CompletedProcess(args, 254, b'',
                            (b'aws: [ERROR]: ' if name == 'production' else b'') + b'An error occurred (NoSuchBucketPolicy) when calling the GetBucketPolicyStatus operation: ' + SENTINEL.encode())
                    responses = {'get-caller-identity': {'Account': '928618307647'},
                                 'get-bucket-versioning': {'Status': 'Enabled'},
                                 'get-public-access-block': {'PublicAccessBlockConfiguration': dict.fromkeys(['BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets'], True)},
                                 'list-object-versions': {'Versions': []}}
                    return subprocess.CompletedProcess(args, 0, json.dumps(responses[operation]).encode(), b'')
                out, err = io.StringIO(), io.StringIO()
                with patch.object(self.a.subprocess, 'run', side_effect=run), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                    self.assertEqual(self.a.bucket_preflight(target), {'exists': False, 'version_id': None})
                self.assertEqual(len(calls), 5)
                self.assertEqual(calls[-1][calls[-1].index('--prefix') + 1], 'deploy/' + name + '/terraform.tfstate')
                self.assertEqual(out.getvalue() + err.getvalue(), '')

    def test_bucket_policy_absence_does_not_allow_other_api_errors(self):
        messages = [b'An error occurred (AccessDenied) when calling the GetBucketPolicyStatus operation: NoSuchBucketPolicy',
                    b'An error occurred (InternalError) when calling the GetBucketPolicyStatus operation: retry',
                    b'An error occurred (NoSuchBucketPolicy) when calling the GetBucketVersioning operation: wrong operation',
                    b'Could not connect to the endpoint URL: ' + SENTINEL.encode()]
        for message in messages:
            with self.subTest(message=message):
                calls = []
                def run(args, **kwargs):
                    calls.append(args)
                    operation = args[6]
                    if operation == 'get-bucket-policy-status':
                        return subprocess.CompletedProcess(args, 254, b'', message)
                    responses = {'get-caller-identity': {'Account': '928618307647'},
                                 'get-bucket-versioning': {'Status': 'Enabled'},
                                 'get-public-access-block': {'PublicAccessBlockConfiguration': dict.fromkeys(['BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets'], True)}}
                    return subprocess.CompletedProcess(args, 0, json.dumps(responses[operation]).encode(), b'')
                with patch.object(self.a.subprocess, 'run', side_effect=run):
                    self.reject(self.a.bucket_preflight, self.target)
                self.assertEqual(len(calls), 4)

    def test_plan_json_is_derived_from_exact_private_saved_plan(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = self.a.private_directory(Path(tmp))
            plan = directory / 'plan.tfplan'
            self.a.private_write(plan, b'private plan')
            def run(args, private, **kwargs):
                self.assertEqual(args, ['terraform', '-chdir=' + str(Path.cwd() / 'deploy/terraform/staging'), 'show', '-json', str(plan.resolve())])
                out, err = private / 'stdout.log', private / 'stderr.log'
                self.a.private_write(out, json.dumps({'resource_changes': [], 'secret': SENTINEL}).encode())
                self.a.private_write(err, b'')
                return 0, out, err
            with patch.object(self.a, 'run_private', side_effect=run):
                result = self.a.show_private_plan(plan, self.target)
            self.assertEqual(result['secret'], SENTINEL)
            self.assertFalse((directory / 'stdout.log').exists())

    def test_store_fetch_roundtrip_rejects_new_latest_and_changed_state(self):
        objects, latest = {}, {}
        current = []
        def aws(args):
            op = args[1]
            if op == 'list-object-versions':
                return {'Versions': current}
            key = args[args.index('--key') + 1]
            if op == 'put-object':
                version = 'version-' + str(len(objects) + 1)
                objects[(key, version)] = Path(args[args.index('--body') + 1]).read_bytes()
                latest[key] = version
                return {'VersionId': version}
            version = args[args.index('--version-id') + 1]
            Path(args[-1]).write_bytes(objects[(key, version)])
            return {'VersionId': version}
        plan_json = {'format_version': '1.2', 'terraform_version': '1.17.1', 'complete': True, 'resource_changes': []}
        with tempfile.TemporaryDirectory() as tmp:
            private = self.a.private_directory(Path(tmp))
            plan, fetched = private / 'source', private / 'fetched'
            with zipfile.ZipFile(plan, 'w') as archive:
                archive.writestr('tfplan', b'private plan')
            original = plan.read_bytes()
            with patch.object(self.a, '_bind_local'), patch.object(self.a, 'show_private_plan', return_value=plan_json):
                expected = self.a.store_plan(self.context, plan, None, aws=aws)
                objects[(expected['plan_key'], 'attacker-new-latest')] = SENTINEL.encode()
                latest[expected['plan_key']] = 'attacker-new-latest'
                self.a.fetch_plan(expected, fetched, aws=aws)
                self.assertEqual(fetched.read_bytes(), original)
                changed = dict(expected, run_attempt='2')
                self.reject(self.a.fetch_plan, changed, fetched, aws=aws)
                current.append({'Key': 'deploy/staging/terraform.tfstate', 'VersionId': 'new-state', 'IsLatest': True})
                self.reject(self.a.fetch_plan, expected, fetched, aws=aws)

    def test_local_binding_checks_actual_github_lock_and_terraform_version(self):
        context = dict(self.context, lockfile_sha256=self.a.digest(Path('deploy/terraform/staging/.terraform.lock.hcl')))
        with patch.dict(os.environ, self.github), patch.object(self.a, 'command_json', return_value={'terraform_version': '1.17.1'}):
            self.a._bind_local(context, self.target)
            self.reject(self.a._bind_local, dict(context, lockfile_sha256='0' * 64), self.target)
            with patch.dict(os.environ, {'GITHUB_RUN_ATTEMPT': '2'}):
                self.reject(self.a._bind_local, context, self.target)
        with patch.dict(os.environ, self.github), patch.object(self.a, 'command_json', return_value={'terraform_version': '1.16.0'}):
            self.reject(self.a._bind_local, context, self.target)

    def test_persisted_plan_dummy_rejected_before_upload(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'plan'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('tfplan', b'clean')
                archive.writestr('tfconfig/main.tf', b'clean')
            self.a.assert_dummy_not_persisted(path, {'value': 'clean'})
            self.reject(self.a.assert_dummy_not_persisted, path, {'secret': 'TF_PLAN_ONLY_' + SENTINEL})
            with zipfile.ZipFile(path, 'a') as archive:
                archive.writestr('tfvars', b'TF_PLAN_ONLY_' + SENTINEL.encode())
            self.reject(self.a.assert_dummy_not_persisted, path, {})

    def test_private_source_directory_is_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp) / 'public'
            directory.mkdir(mode=0o755)
            directory.chmod(0o755)
            path = directory / 'raw.json'
            path.write_text(SENTINEL)
            self.reject(self.a._private_file, path)
            self.reject(self.a.download_object, 'deploy/plans/x', 'v1', 'f' * 64, path)

    def test_production_policy_reads_only_approved_audit_or_exact_target_state(self):
        from test_terraform_plan import ProductionPlanTests
        fixture = ProductionPlanTests()
        fixture.setUp()
        target = self.a.load_target('production')
        context = dict(self.context, target='production', state_key=target['state_key'],
                       plan_key=self.context['plan_key'].replace('staging', 'production'))
        audit_bytes = json.dumps(fixture.ownership).encode()
        context.update(ownership_audit_key='deploy/ownership/production/approved.json',
                       ownership_audit_version_id='audit-v1', ownership_audit_sha256=hashlib.sha256(audit_bytes).hexdigest())
        state = {'resources': [
            {'module': 'module.service', 'type': 'aws_instance', 'name': 'app', 'mode': 'managed',
             'instances': [{'attributes': {'id': 'i-0f47bb1f028cd29a9'}}]},
            {'module': 'module.service', 'type': 'aws_db_instance', 'name': 'app', 'mode': 'managed',
             'instances': [{'attributes': {'id': 'db-72GJNZYUHSQRXAKNQGC7ECV7UI'}}]}]}
        def aws(args):
            if args[1] == 'list-object-versions':
                return {'Versions': [{'Key': 'measurements/test-slo/sut/terraform.tfstate', 'VersionId': 'audit-fixture-version', 'IsLatest': True}]}
            key = args[args.index('--key') + 1]
            version = args[args.index('--version-id') + 1]
            if key == 'deploy/ownership/production/approved.json' and version == 'audit-v1':
                data = audit_bytes
            elif key == 'deploy/production/terraform.tfstate' and version == 'state-v1':
                data = json.dumps(state).encode()
            else:
                self.fail('Attempt to read an unrelated state body or non-exact version')
            Path(args[-1]).write_bytes(data)
            return {'VersionId': version}
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            result = self.a._production_validation(fixture.plan, context, directory, fixture.observed, aws=aws)
            self.assertEqual(result['imports'], 2)
            pending = dict(fixture.observed, rds_pending_modifications=['AllocatedStorage'])
            with self.assertRaises(self.a.terraform_plan.PlanValidationError):
                self.a._production_validation(fixture.plan, context, directory, pending, aws=aws)
            context.update(state_exists=True, state_version_id='state-v1', ownership_audit_key=None,
                           ownership_audit_version_id=None, ownership_audit_sha256=None)
            for resource in fixture.plan['resource_changes']:
                del resource['change']['importing']
            result = self.a._production_validation(fixture.plan, context, directory, fixture.observed, aws=aws)
            self.assertEqual(result['imports'], 0)
            state['resources'][0]['instances'][0]['attributes']['id'] = 'wrong'
            with self.assertRaises(self.a.terraform_plan.PlanValidationError):
                self.a._production_validation(fixture.plan, context, directory, fixture.observed, aws=aws)

    def test_cli_summary_binds_same_plan_and_source_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            plan, context = directory / 'plan', directory / 'context.json'
            self.a.private_write(plan, b'private plan')
            self.a.private_write(context, json.dumps(self.context).encode())
            plan_json = {'format_version': '1.2', 'terraform_version': '1.17.1', 'complete': True, 'resource_changes': []}
            out = io.StringIO()
            with patch.object(self.a, '_bind_local'), patch.object(self.a, 'show_private_plan', return_value=plan_json), contextlib.redirect_stdout(out):
                result = self.a.main(['summarize', '--target', 'staging', '--plan', str(plan), '--context', str(context)])
            self.assertEqual(result, 0)
            summary = json.loads(out.getvalue())
            self.assertEqual(summary['source_sha'], 'a' * 40)
            self.assertEqual(summary['plan_sha256'], hashlib.sha256(b'private plan').hexdigest())
            self.assertNotIn(SENTINEL, out.getvalue())

    def test_state_inventory_includes_other_tfstate_names(self):
        def aws(args):
            return {'Versions': [{'Key': 'new/alternate.tfstate', 'VersionId': 'new-v1', 'IsLatest': True},
                                 {'Key': 'old/terraform.tfstate', 'VersionId': 'old-v1', 'IsLatest': True},
                                 {'Key': 'deploy/plans/context.json', 'VersionId': 'context-v1', 'IsLatest': True}]}
        self.assertEqual(self.a.state_versions(aws=aws), {'new/alternate.tfstate': 'new-v1', 'old/terraform.tfstate': 'old-v1'})

    def test_cli_argument_errors_do_not_echo_original_values(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = self.a.main(['preflight', '--target', SENTINEL])
        self.assertEqual(code, 2)
        self.assertNotIn(SENTINEL, out.getvalue() + err.getvalue())

    def test_cli_errors_do_not_echo_original_values(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'bad.json'
            path.write_text(SENTINEL)
            out, err = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = self.a.main(['summarize', '--target', 'staging', '--plan', str(path), '--context', str(path)])
            self.assertEqual(code, 2)
            self.assertNotIn(SENTINEL, out.getvalue() + err.getvalue())


class NativeTerraformTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('terraform'), 'Terraform executable is required for native fixtures')
    def test_native_saved_plan_ephemeral_resupply_and_staleness(self):
        import terraform_automation as a
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            root.chmod(0o700)
            config = root / 'main.tf'
            config.write_text(r'''terraform { required_version = "= 1.17.1" }
variable "token" {
  type = string
  ephemeral = true
  sensitive = true
  default = null
}
variable "revision" { type = number }
resource "terraform_data" "fixture" {
  input = var.revision
  provisioner "local-exec" {
    command = "test -n \"$TOKEN\""
    environment = { TOKEN = var.token }
  }
}
''')
            def run(*args, token=None, revision='1'):
                env = {key: value for key, value in os.environ.items() if not key.startswith(('AWS_', 'TF_'))}
                env.update(TF_IN_AUTOMATION='1', TF_INPUT='0', TF_LOG='OFF', TF_VAR_revision=revision)
                if token is not None:
                    env['TF_VAR_token'] = token
                return subprocess.run(['terraform', '-chdir=' + str(root), *args], capture_output=True, env=env)
            initialized = run('init', '-backend=false', '-input=false')
            self.assertEqual(initialized.returncode, 0, 'Native fixture init failed')
            dummy = 'TF_PLAN_ONLY_NATIVE_SENTINEL'
            plan = run('plan', '-out=initial.tfplan', token=dummy)
            self.assertEqual(plan.returncode, 0, 'Native fixture plan failed')
            with zipfile.ZipFile(root / 'initial.tfplan') as archive:
                for name in archive.namelist():
                    self.assertNotIn(dummy.encode(), archive.read(name))
            shown = run('show', '-json', 'initial.tfplan')
            self.assertEqual(shown.returncode, 0)
            self.assertNotIn(dummy.encode(), shown.stdout)
            missing = run('apply', '-input=false', 'initial.tfplan')
            self.assertNotEqual(missing.returncode, 0)
            applied = run('apply', '-input=false', 'initial.tfplan', token='REAL_NATIVE_FIXTURE')
            self.assertEqual(applied.returncode, 0, 'Native fixture ephemeral resupply failed')
            self.assertEqual(run('plan', '-out=old.tfplan', token=dummy, revision='2').returncode, 0)
            self.assertEqual(run('plan', '-out=new.tfplan', token=dummy, revision='3').returncode, 0)
            self.assertEqual(run('apply', '-input=false', 'new.tfplan', token='REAL_NATIVE_FIXTURE', revision='3').returncode, 0)
            stale = run('apply', '-input=false', 'old.tfplan', token='REAL_NATIVE_FIXTURE', revision='2')
            self.assertNotEqual(stale.returncode, 0)
            self.assertIn(b'Saved plan is stale', stale.stderr)
            null_plan = run('plan', '-out=null.tfplan', revision='4')
            self.assertEqual(null_plan.returncode, 0, 'Native null ephemeral plan failed')
            changed_null = run('apply', '-input=false', 'null.tfplan', token='REAL_NATIVE_FIXTURE', revision='4')
            self.assertNotEqual(changed_null.returncode, 0)
            self.assertIn(b'Ephemeral variable was not set during planning', changed_null.stderr)


if __name__ == '__main__':

    unittest.main()
