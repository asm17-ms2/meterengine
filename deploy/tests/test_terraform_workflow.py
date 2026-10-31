import contextlib
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
ROOT = Path(__file__).resolve().parents[2]
SCRATCH = os.environ.get('TF_WORKFLOW_TEST_SCRATCH')


def workflow(name):
    result = subprocess.run(['ruby', '-ryaml', '-rjson', '-e',
                             'd = YAML.load_file(ARGV[0]); d["on"] = d.delete(true) if d.key?(true); puts d.to_json',
                             str(ROOT / '.github/workflows' / name)], capture_output=True, text=True)
    if result.returncode:
        raise AssertionError('Workflow YAML must parse: ' + name)
    return json.loads(result.stdout)


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('terraform_workflow'), 'workflow runner is missing')
        import terraform_workflow
        self.w = terraform_workflow
        from test_terraform_automation import AutomationTests
        fixture = AutomationTests()
        fixture.setUp()
        self.a = fixture.a
        self.target = fixture.target
        self.env = fixture.github
        self.expected = dict(fixture.context, lockfile_sha256=self.a.digest(ROOT / self.target['root'] / '.terraform.lock.hcl'),
                             context_key=self.a.prefix(fixture.context) + '/context.json',
                             context_version_id='context-v1', context_sha256='c' * 64)
        self.environment, self.branches = fixture.environment, fixture.branches
        self.env['EXPECTED_PLAN'] = json.dumps(self.expected)
        self.env.update(TF_STAGING_DB_PASSWORD='REAL_FIXTURE_PASSWORD',
                        TF_STAGING_RUNTIME_SECRETS_JSON=json.dumps(dict.fromkeys(self.a.SECRET_KEYS, 'REAL_FIXTURE_SECRET')),
                        TF_STAGING_DB_PASSWORD_VERSION='1', TF_STAGING_OPERATOR_EMAIL='fixture@example.com',
                        TF_STAGING_RUNTIME_SECRET_VERSIONS_JSON=json.dumps(dict.fromkeys(self.a.SECRET_KEYS, 1)))
        self.temp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.addCleanup(self.temp.cleanup)
        self.private = Path(self.temp.name)
        self.private.chmod(0o700)

    def gh(self, path):
        return self.branches if 'deployment-branch-policies' in path else self.environment

    def prepare(self, phase='apply'):
        with patch.object(self.a, 'gh_api', side_effect=self.gh), patch.object(self.a, 'command_json', return_value={'terraform_version': '1.17.1'}):
            return self.w.prepare(self.target, phase, self.env)

    def test_missing_reviewers_and_wrong_main_stop_before_credentials(self):
        for mutation in ['reviewer', 'branch', 'fork', 'pull_request']:
            with self.subTest(mutation=mutation):
                env, environment = dict(self.env), copy.deepcopy(self.environment)
                if mutation == 'reviewer':
                    environment['protection_rules'] = []
                elif mutation == 'branch':
                    env['GITHUB_REF'] = 'refs/heads/develop'
                elif mutation == 'fork':
                    env['GITHUB_REPOSITORY_ID'] = '999'
                else:
                    env['GITHUB_EVENT_NAME'] = 'pull_request'
                credentials = self.private / 'credential-acquired'
                def gh(path):
                    return self.branches if 'deployment-branch-policies' in path else environment
                with patch.object(self.a, 'gh_api', side_effect=gh), patch.object(self.a, 'command_json', return_value={'terraform_version': '1.17.1'}):
                    with self.assertRaises(self.a.AutomationError):
                        self.w.prepare(self.target, 'apply', env)
                        credentials.touch()
                self.assertFalse(credentials.exists())

    def test_other_run_attempt_sha_lock_and_versions_stop_before_credentials(self):
        for field, value in [('run_id', '101'), ('run_attempt', '2'), ('source_sha', 'b' * 40),
                             ('target', 'production'), ('lockfile_sha256', 'd' * 64),
                             ('terraform_version', '1.16.0'), ('context_version_id', None),
                             ('context_key', 'wrong/context.json'), ('context_sha256', 'wrong')]:
            with self.subTest(field=field):
                self.env['EXPECTED_PLAN'] = json.dumps(dict(self.expected, **{field: value}))
                with self.assertRaises(self.a.AutomationError):
                    self.prepare()

    def test_prepare_exports_only_reviewed_target_outputs(self):
        result = self.prepare()
        self.assertEqual(result, {'target': 'staging', 'environment': 'staging-infrastructure',
                                 'role_arn': 'arn:aws:iam::928618307647:role/meterengine-terraform-staging-apply'})

    def test_missing_null_and_dummy_apply_inputs_stop_before_credentials(self):
        for value in [None, '', 'null', 'TF_PLAN_ONLY_NeverDeploy123!']:
            with self.subTest(value=value):
                self.env['TF_STAGING_RUNTIME_SECRETS_JSON'] = value
                with self.assertRaises(self.a.AutomationError):
                    self.prepare()

    def test_terraform_environment_removes_ambient_inputs_and_production_secrets(self):
        env = dict(self.env, TF_VAR_operator_email='ambient', TF_VAR_db_password_wo='TF_PLAN_ONLY',
                   TF_CLI_ARGS_apply='-lock=false', TF_WORKSPACE='other', TF_DATA_DIR='/unsafe', TF_LOG_PATH='/unsafe')
        actual = self.a.apply_ephemeral_env(self.target, {k: env[k] for k in self.target['secrets'].values()})
        clean = self.w.terraform_env(env, self.private, actual)
        self.assertEqual({k: v for k, v in clean.items() if k.startswith('TF_VAR_')}, actual)
        self.assertEqual(clean['TF_WORKSPACE'], 'default')
        self.assertEqual(clean['TF_DATA_DIR'], str(self.private / 'data'))
        self.assertNotIn('TF_STAGING_RUNTIME_SECRETS_JSON', clean)
        self.assertNotIn('TF_CLI_ARGS_apply', clean)
        self.assertNotIn('TF_LOG_PATH', clean)
        with self.assertRaises(self.a.AutomationError):
            self.w.prepare(self.a.load_target('production'), 'apply', self.env)

    def test_private_subprocess_keeps_distinct_success_and_failure_logs(self):
        logs = []
        env = self.w.terraform_env(self.env, self.private, {})
        self.w.private_command([sys.executable, '-c', 'print("PRIVATE_FIRST")'], self.private, env, logs)
        with self.assertRaises(self.a.AutomationError):
            self.w.private_command([sys.executable, '-c', 'import sys; print("PRIVATE_SECOND"); sys.exit(8)'], self.private, env, logs)
        self.assertEqual(len(logs), 4)
        self.assertEqual(len(set(logs)), 4)
        self.assertIn('PRIVATE_FIRST', logs[0].read_text())
        self.assertIn('PRIVATE_SECOND', logs[2].read_text())
        self.assertTrue(all(p.stat().st_mode & 0o777 == 0o600 for p in logs))

    def test_cli_failure_contains_no_exception_or_private_output(self):
        out, err = io.StringIO(), io.StringIO()
        with patch.object(self.w, 'prepare', side_effect=RuntimeError('PRIVATE_EXCEPTION')), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            self.assertEqual(self.w.main(['prepare-plan', '--target', 'staging']), 2)
        self.assertEqual(out.getvalue(), '')
        self.assertEqual(err.getvalue(), 'TERRAFORM_WORKFLOW_REJECTED: inspect private diagnostics.\n')

    def test_workflow_precredential_script_executes_and_blocks_bad_fixtures(self):
        script = next(s['run'] for s in workflow('terraform.yml')['jobs']['apply-staging']['steps'] if s.get('id') == 'preflight')
        bin_dir = self.private / 'bin'
        bin_dir.mkdir(mode=0o700)
        gh = bin_dir / 'gh'
        gh.write_text('#!' + sys.executable + '\nimport json, os, sys\n'
                      'print(os.environ["FIXTURE_BRANCHES"] if "deployment-branch-policies" in sys.argv[-1] else os.environ["FIXTURE_ENVIRONMENT"])\n')
        tf = bin_dir / 'terraform'
        tf.write_text('#!' + sys.executable + '\nprint(\'{"terraform_version":"1.17.1"}\')\n')
        for path in [gh, tf]:
            path.chmod(0o700)
        for mutation in ['reviewer', 'attempt', 'branch', 'good']:
            with self.subTest(mutation=mutation):
                env = dict(os.environ, **self.env, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'],
                           GITHUB_OUTPUT=str(self.private / 'output'), FIXTURE_BRANCHES=json.dumps(self.branches))
                environment = copy.deepcopy(self.environment)
                if mutation == 'reviewer':
                    environment['protection_rules'] = []
                elif mutation == 'attempt':
                    env['EXPECTED_PLAN'] = json.dumps(dict(self.expected, run_attempt='2'))
                elif mutation == 'branch':
                    env['GITHUB_REF'] = 'refs/heads/develop'
                env['FIXTURE_ENVIRONMENT'] = json.dumps(environment)
                marker = self.private / 'credentials'
                marker.unlink(missing_ok=True)
                result = subprocess.run(['bash', '-e', '-c', script + '\ntouch "$CREDENTIAL_MARKER"'],
                                        env=dict(env, CREDENTIAL_MARKER=str(marker)), cwd=ROOT, capture_output=True, text=True)
                self.assertEqual(result.returncode == 0, mutation == 'good')
                self.assertEqual(marker.exists(), mutation == 'good')
                self.assertNotIn('REAL_FIXTURE', result.stdout + result.stderr)

    def test_bucket_failure_has_no_terraform_or_upload_side_effects(self):
        calls = []
        with patch.object(self.w, 'prepare'), patch.object(self.a, 'bucket_preflight', side_effect=self.a.AutomationError('NO_BUCKET')), \
             patch.object(self.w, 'terraform_command', side_effect=lambda *args: calls.append('terraform')), \
             patch.object(self.a, 'store_diagnostics', side_effect=lambda *args: calls.append('upload')):
            with self.assertRaises(self.a.AutomationError):
                self.w.execute(self.target, 'plan', self.private, self.env)
        self.assertEqual(calls, [])

    def test_plan_and_apply_preserve_failure_logs_without_success_outputs(self):
        for phase in ['plan', 'apply']:
            calls, stored = [], []
            def tf(root, args, directory, env, logs):
                calls.append(args)
                self.w.private_command([sys.executable, '-c', 'import sys; print("PRIVATE_FAILURE"); sys.exit(3)'], directory, env, logs)
            with patch.object(self.w, 'prepare'), patch.object(self.a, 'bucket_preflight', return_value={'exists': False, 'version_id': None}), \
                 patch.object(self.w, 'terraform_command', side_effect=tf), \
                 patch.object(self.a, 'store_diagnostics', side_effect=lambda context, paths: stored.extend(paths)), \
                 patch.object(self.w, 'publish') as publish:
                with self.assertRaises(self.a.AutomationError):
                    self.w.execute(self.target, phase, self.private, self.env)
                publish.assert_not_called()
            self.assertTrue(stored)
            self.assertTrue(any('PRIVATE_FAILURE' in p.read_text() for p in stored))

    def test_checks_use_three_private_default_workspaces_without_backend(self):
        calls = []
        def command(root, args, directory, env, logs):
            calls.append((root, args, env))
        with patch.object(self.w, 'terraform_command', side_effect=command):
            self.w.checks(self.private, dict(self.env, AWS_ACCESS_KEY_ID='ambient', TF_WORKSPACE='other'))
        self.assertEqual({root.name for root, _, _ in calls}, {'staging', 'production', 'automation'})
        self.assertEqual(len(calls), 12)
        for root, args, env in calls:
            self.assertNotIn('AWS_ACCESS_KEY_ID', env)
            self.assertEqual(env['TF_WORKSPACE'], 'default')
            self.assertEqual(Path(env['TF_DATA_DIR']), self.private / root.name / 'data')
            if args[0] == 'init':
                self.assertIn('-backend=false', args)
                self.assertIn('-lockfile=readonly', args)

    def test_plan_stores_same_context_before_publishing_allowlist_summary(self):
        events = []
        empty_plan = {'format_version': '1.2', 'terraform_version': '1.17.1', 'complete': True, 'resource_changes': []}
        def tf(root, args, directory, env, logs):
            events.append(args[0])
            if args[0] == 'plan':
                self.assertIn('TF_PLAN_ONLY', env['TF_VAR_db_password_wo'])
                self.a.private_write(directory / 'plan.tfplan', b'private plan')
            if args[0] == 'show':
                path = directory / 'fixture-show.json'
                self.a.private_write(path, json.dumps(empty_plan).encode())
                return path
        def store(context, path, observed):
            self.assertEqual(context['state_exists'], False)
            self.assertEqual(context['source_sha'], 'a' * 40)
            self.assertEqual(path.read_bytes(), b'private plan')
            events.append('store')
            return self.expected
        def publish(result, **kwargs):
            events.append('publish')
            self.assertNotIn('TF_PLAN_ONLY', json.dumps(result))
            self.assertNotIn('REAL_FIXTURE', json.dumps(result))
        with patch.object(self.w, 'prepare'), patch.object(self.a, 'bucket_preflight', return_value={'exists': False, 'version_id': None}), \
             patch.object(self.w, 'terraform_command', side_effect=tf), patch.object(self.a, 'store_plan', side_effect=store), \
             patch.object(self.a, 'store_diagnostics', side_effect=lambda *args: events.append('diagnostics')), \
             patch.object(self.w, 'publish', side_effect=publish):
            self.w.execute(self.target, 'plan', self.private, self.env)
        self.assertEqual(events, ['init', 'plan', 'show', 'store', 'diagnostics', 'publish', 'publish'])

    @unittest.skipUnless(shutil.which('terraform'), 'Terraform required for private native runner fixture')
    def test_native_runner_resupplies_ephemeral_and_preserves_stale_failure(self):
        root = self.private / 'native'
        root.mkdir(mode=0o700)
        (root / 'main.tf').write_text('''terraform { required_version = "= 1.17.1" }
variable "db_password_wo" {
  type = string
  sensitive = true
  ephemeral = true
}
variable "runtime_secrets" {
  type = map(string)
  sensitive = true
  ephemeral = true
}
variable "revision" { type = number }
resource "terraform_data" "fixture" {
  input = var.revision
  provisioner "local-exec" {
    command = "test \\"$DB\\" = \\"$EXPECTED_NATIVE\\" && test \\"$TOKEN\\" = \\"$EXPECTED_NATIVE\\""
    environment = { DB = var.db_password_wo, TOKEN = var.runtime_secrets["operator-password"] }
  }
}
''')
        logs = []
        ambient = {k: v for k, v in os.environ.items() if not k.startswith('AWS_')}
        ambient['EXPECTED_NATIVE'] = 'REAL_NATIVE_RUNNER'
        def run(args, variables):
            env = self.w.terraform_env(ambient, self.private, variables)
            return self.w.terraform_command(root, args, self.private, env, logs)
        dummy = self.a.plan_ephemeral_env(self.target)
        supplied = {'TF_STAGING_DB_PASSWORD': 'REAL_NATIVE_RUNNER',
                    'TF_STAGING_RUNTIME_SECRETS_JSON': json.dumps(dict.fromkeys(self.a.SECRET_KEYS, 'REAL_NATIVE_RUNNER'))}
        real = self.a.apply_ephemeral_env(self.target, supplied)
        run(['init', '-backend=false', '-input=false'], {})
        initial, old, new = [self.private / name for name in ['initial.tfplan', 'old.tfplan', 'new.tfplan']]
        run(['plan', '-input=false', '-out=' + str(initial)], dict(dummy, TF_VAR_revision='1'))
        run(['apply', '-input=false', '-lock-timeout=5m', str(initial)], real)
        run(['plan', '-input=false', '-out=' + str(old)], dict(dummy, TF_VAR_revision='2'))
        run(['plan', '-input=false', '-out=' + str(new)], dict(dummy, TF_VAR_revision='3'))
        run(['apply', '-input=false', '-lock-timeout=5m', str(new)], real)
        with self.assertRaises(self.a.AutomationError):
            run(['apply', '-input=false', '-lock-timeout=5m', str(old)], real)
        self.assertIn('Saved plan is stale', logs[-1].read_text())
        self.assertNotIn('REAL_NATIVE_RUNNER', (root / 'terraform.tfstate').read_text())
        self.a.assert_dummy_not_persisted(initial, {})

    def test_apply_fetches_exact_plan_and_rejects_stale_before_apply(self):
        calls = []
        def tf(root, args, *unused):
            calls.append(args)
        for existed, prior, state in [(True, 'prior-state', {'exists': True, 'version_id': 'new-state'}),
                                      (False, None, {'exists': True, 'version_id': 'first-state'})]:
            env = dict(self.env, EXPECTED_PLAN=json.dumps(dict(self.expected, state_exists=existed, state_version_id=prior)))
            with patch.object(self.w, 'prepare'), patch.object(self.a, 'bucket_preflight', return_value=state), \
                 patch.object(self.a, 'fetch_plan') as fetch, patch.object(self.w, 'terraform_command', side_effect=tf), \
                 patch.object(self.a, 'store_diagnostics'):
                with self.assertRaises(self.a.AutomationError):
                    self.w.execute(self.target, 'apply', self.private, env)
                fetch.assert_not_called()
        self.assertFalse(any(args[0] == 'apply' for args in calls))

    def test_apply_uses_only_fetched_saved_plan_and_real_ephemeral_values(self):
        calls = []
        def tf(root, args, directory, env, logs):
            calls.append((args, env))
            if args[0] == 'show':
                shown = directory / 'fixture-show.json'
                self.a.private_write(shown, json.dumps({'format_version': '1.2', 'terraform_version': '1.17.1',
                                                       'complete': True, 'resource_changes': []}).encode())
                return shown
        def fetch(expected, path, observed):
            self.assertEqual(expected, self.expected)
            self.a.private_write(path, b'verified saved plan')
        env = dict(self.env, TF_VAR_operator_email='ambient', TF_VAR_db_password_wo='TF_PLAN_ONLY')
        with patch.object(self.w, 'prepare'), patch.object(self.a, 'bucket_preflight', return_value={'exists': False, 'version_id': None}), \
             patch.object(self.a, 'fetch_plan', side_effect=fetch), patch.object(self.w, 'terraform_command', side_effect=tf), \
             patch.object(self.a, 'target_state', return_value={'exists': False, 'version_id': None}), \
             patch.object(self.a, 'store_diagnostics'), patch.object(self.w, 'publish'):
            self.w.execute(self.target, 'apply', self.private, env)
        args, actual = calls[-1]
        self.assertEqual(args, ['apply', '-input=false', '-lock-timeout=5m', str(self.private / 'plan.tfplan')])
        self.assertEqual(actual['TF_VAR_db_password_wo'], 'REAL_FIXTURE_PASSWORD')
        self.assertNotIn('TF_VAR_operator_email', actual)
        self.assertFalse(any(command[0] == 'plan' for command, _ in calls))

    def test_production_inventory_rejects_pending_wrong_identity_and_eip(self):
        from test_terraform_plan import ProductionPlanTests
        fixture = ProductionPlanTests()
        fixture.setUp()
        ec2 = {'InstanceId': 'i-0f47bb1f028cd29a9', 'InstanceType': 't4g.medium', 'ImageId': 'ami-0f4109eb491b0476f',
               'VpcId': 'vpc-07498f0672b7f3c9e', 'SubnetId': 'subnet-0348a18c1d399c193',
               'SecurityGroups': [{'GroupId': 'sg-0c2691d037bcdd8b1'}],
               'IamInstanceProfile': {'Arn': 'arn:aws:iam::928618307647:instance-profile/meterengine-ec2-role'},
               'Placement': {'AvailabilityZone': 'ap-northeast-2a'}}
        db = {'DBInstanceIdentifier': 'meterengine-db', 'DbiResourceId': 'db-72GJNZYUHSQRXAKNQGC7ECV7UI',
              'DBInstanceClass': 'db.t4g.small', 'DBInstanceArn': 'arn:aws:rds:ap-northeast-2:928618307647:db:meterengine-db',
              'PendingModifiedValues': {}, 'DBSubnetGroup': {'DBSubnetGroupName': 'meterengine-rds-subnet-private', 'VpcId': ec2['VpcId']},
              'VpcSecurityGroups': [{'VpcSecurityGroupId': 'sg-068a54ab4cfa91730'}], 'Endpoint': {'Port': 5432}, 'PubliclyAccessible': False}
        eip = {'PublicIp': '43.203.50.31', 'InstanceId': ec2['InstanceId']}
        def aws(args):
            if args[:2] == ['sts', 'get-caller-identity']:
                return {'Account': '928618307647'}
            if args[:2] == ['ec2', 'describe-instances']:
                return {'Reservations': [{'Instances': [ec2]}]}
            if args[:2] == ['rds', 'describe-db-instances']:
                return {'DBInstances': [db]}
            if args[:2] == ['ec2', 'describe-addresses']:
                return {'Addresses': [eip]}
            raise AssertionError('Unexpected production inventory request')
        with patch.object(self.a, 'aws_api', side_effect=aws):
            observed = self.w.production_inventory()
            self.assertEqual(observed['instance_id'], ec2['InstanceId'])
            for obj, field, value in [(db, 'PendingModifiedValues', {'AllocatedStorage': 99}),
                                      (ec2, 'InstanceId', 'i-other'), (eip, 'InstanceId', 'i-other')]:
                old = obj[field]
                obj[field] = value
                with self.assertRaises(self.a.AutomationError):
                    self.w.production_inventory()
                obj[field] = old
        fixture.plan['resource_changes'][0]['change']['before'].update(self.w.inventory_before(observed)[self.w.policy.EC2])
        fixture.plan['resource_changes'][1]['change']['before'].update(self.w.inventory_before(observed)[self.w.policy.RDS])
        self.w.compare_inventory(fixture.plan, observed)
        for address, attribute in [(self.w.policy.EC2, 'instance_type'), (self.w.policy.EC2, 'ami'),
                                   (self.w.policy.EC2, 'vpc_security_group_ids'), (self.w.policy.EC2, 'iam_instance_profile'),
                                   (self.w.policy.RDS, 'instance_class'), (self.w.policy.RDS, 'db_subnet_group_name'),
                                   (self.w.policy.RDS, 'port'), (self.w.policy.RDS, 'publicly_accessible')]:
            changed = copy.deepcopy(fixture.plan)
            next(r for r in changed['resource_changes'] if r['address'] == address)['change']['before'][attribute] = 'changed'
            with self.assertRaises(self.a.AutomationError):
                self.w.compare_inventory(changed, observed)

    def test_workflows_isolate_credentials_and_gate_exact_same_run(self):
        checks = workflow('terraform-checks.yml')
        self.assertIn('pull_request', checks['on'])
        self.assertNotIn('pull_request_target', checks['on'])
        self.assertEqual(checks['permissions'], {'contents': 'read'})
        for job in checks['jobs'].values():
            self.assertNotIn('environment', job)
            self.assertNotIn('id-token', job.get('permissions', {}))
            self.assertNotIn('secrets.', json.dumps(job))
            self.assertNotIn('configure-aws-credentials', json.dumps(job))
        manual = workflow('terraform.yml')
        self.assertEqual(set(manual['on']), {'workflow_dispatch'})
        self.assertEqual(manual['on']['workflow_dispatch']['inputs']['target']['options'], ['staging', 'production'])
        self.assertEqual(manual['concurrency'], {'group': 'terraform-${{ inputs.target }}', 'cancel-in-progress': False})
        for name, job in manual['jobs'].items():
            self.assertIn("github.ref == 'refs/heads/main'", job['if'])
            steps = job['steps']
            self.assertEqual(steps[0]['with']['ref'], '${{ github.sha }}')
            setup = next(s for s in steps if 'setup-terraform@' in s.get('uses', ''))
            self.assertEqual(setup['with'], {'terraform_version': '1.17.1', 'terraform_wrapper': False})
            credentials = next(i for i, s in enumerate(steps) if 'configure-aws-credentials@' in s.get('uses', ''))
            self.assertIn('prepare-' + ('plan' if name == 'plan' else 'apply'), steps[credentials - 1]['run'])
            self.assertEqual(steps[credentials]['with']['mask-aws-account-id'], False)
            if name != 'plan':
                self.assertEqual(job['needs'], 'plan')
                target = name.removeprefix('apply-')
                self.assertEqual(job['environment'], target + '-infrastructure')
                self.assertEqual(job['timeout-minutes'], 170 if target == 'staging' else 110)
                self.assertEqual(steps[credentials]['with']['role-duration-seconds'], 10800 if target == 'staging' else 7200)
                self.assertEqual(job['env']['EXPECTED_PLAN'], '${{ needs.plan.outputs.expected }}')
                if target == 'production':
                    self.assertNotIn('TF_STAGING_', json.dumps(job))
            else:
                self.assertEqual(steps[credentials]['with']['role-duration-seconds'], 3600)
            for step in steps:
                if 'uses' in step:
                    self.assertRegex(step['uses'], r'@[a-f0-9]{40}$')
                    self.assertNotIn('upload-artifact', step['uses'])
                self.assertNotRegex(step.get('run', ''), r'\b(terraform|cat|tee|echo)\s')


if __name__ == '__main__':
    unittest.main()
