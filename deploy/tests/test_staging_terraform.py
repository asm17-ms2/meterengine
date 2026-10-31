import json
from pathlib import Path
import shutil
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which('terraform'), 'Terraform required')
class StagingTerraformTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        command = ['terraform', '-chdir='+str(ROOT / 'deploy/terraform/staging'), 'test', '-test-directory=tests', '-json', '-verbose']
        result = subprocess.run(command, text=True, capture_output=True, timeout=120)
        if result.returncode: raise AssertionError(result.stdout + result.stderr)
        cls.rendered = result.stdout
        cls.states = {item['@testrun']: item['test_state'] for line in result.stdout.splitlines() if (item := json.loads(line)).get('type') == 'test_state'}
        cls.plans = {item['@testrun']: item['test_plan'] for line in result.stdout.splitlines() if (item := json.loads(line)).get('type') == 'test_plan'}
        cls.resources = {item['address']: item for item in cls.plans['staging_is_isolated']['resource_changes'] if item['mode'] == 'managed'}

    def resource(self, name):
        return self.resources[name]['change']['after']

    def test_root_uses_approved_capacity_private_rds_and_only_staging_network(self):
        app = self.resource('module.service.aws_instance.app')
        database = self.resource('module.service.aws_db_instance.app')
        self.assertEqual(app['instance_type'], 'm7g.large')
        self.assertEqual(app['ami'], 'ami-0f4109eb491b0476f')
        self.assertEqual(app['subnet_id'], 'subnet-0348a18c1d399c193')
        self.assertEqual(app['vpc_security_group_ids'], ['sg-11111111111111111'])
        self.assertEqual(database['instance_class'], 'db.m7g.large')
        self.assertEqual(database['vpc_security_group_ids'], ['sg-22222222222222222'])
        self.assertFalse(database['publicly_accessible'])
        self.assertTrue(database['deletion_protection'])
        self.assertFalse(database['skip_final_snapshot'])
        self.assertFalse(database['delete_automated_backups'])
        self.assertEqual(database['db_name'], 'meterengine_app')
        self.assertEqual(database['backup_retention_period'], 7)
        self.assertEqual(app['metadata_options'][0]['http_tokens'], 'required')
        types = {item['type'] for item in self.resources.values()}
        self.assertFalse(types & {'aws_scheduler_schedule','aws_cloudwatch_event_rule','aws_lambda_function'})
        for item in self.resources.values():
            after = item['change']['after']
            self.assertNotIn(after.get('security_group_id'), ['sg-068a54ab4cfa91730','sg-0c2691d037bcdd8b1'])

    def test_ephemeral_secrets_are_absent_from_rendered_plans_and_bootstrap(self):
        for marker in ('D2_TEST_ONLY_OWNER_NOT_A_REAL_SECRET','D2_TEST_ONLY_SHARED_NOT_A_REAL_SECRET','D2_TEST_ONLY_TEST_NOT_A_REAL_SECRET','D2_TEST_ONLY_LIVE_NOT_A_REAL_SECRET','D2_TEST_ONLY_CRYPTO_NOT_A_REAL_SECRET','D2_TEST_ONLY_OPERATOR_NOT_A_REAL_SECRET'):
            self.assertNotIn(marker, self.rendered)
        for address, item in self.resources.items():
            after = item['change']['after']
            if item['type'] == 'aws_ssm_parameter' and after['type'] == 'SecureString':
                self.assertIsNone(after.get('value'))
                self.assertIsNone(after.get('value_wo'))
                self.assertIsNone(after.get('insecure_value'))
                self.assertGreater(after['value_wo_version'], 0)
            if item['type'] == 'aws_db_instance':
                self.assertIsNone(after.get('password'))
                self.assertIsNone(after.get('password_wo'))
        user_data = self.resource('module.service.aws_instance.app')['user_data']
        self.assertNotIn('db-migration-password', user_data)
        self.assertNotIn('docker compose', user_data)

    def test_runtime_iam_explicitly_denies_owner_other_environments_and_history(self):
        policy = json.loads(self.resource('aws_iam_role_policy.runtime')['policy'])['Statement']
        read, outside, history = policy[:3]
        self.assertEqual(read['Action'], ['ssm:GetParameter','ssm:GetParameters'])
        expected = 'arn:aws:ssm:ap-northeast-2:928618307647:parameter/meterengine/staging/'
        self.assertTrue(all(name.startswith(expected) and '*' not in name for name in read['Resource']))
        self.assertEqual(outside['Effect'], 'Deny')
        self.assertEqual(set(outside['Action']), {'ssm:GetParameter','ssm:GetParameters','ssm:GetParametersByPath','ssm:GetParameterHistory'})
        self.assertEqual(outside['NotResource'], read['Resource'])
        self.assertNotIn(expected.replace('/staging/', '/production/')+'db-shared-password', outside['NotResource'])
        self.assertNotIn(expected.replace('/staging/', '/staging-migration/')+'db-migration-password', outside['NotResource'])
        self.assertEqual(history['Effect'], 'Deny')
        self.assertEqual(history['Resource'], '*')
        self.assertEqual(set(history['Action']), {'ssm:GetParametersByPath','ssm:GetParameterHistory'})

    def owner_policy(self):
        resources = self.states['staging_state_excludes_secrets']['root_module']['resources']
        owner = next(item for item in resources if item['address'] == 'aws_iam_role_policy.github_owner')
        return json.loads(owner['values']['policy'])['Statement']

    def test_owner_reads_only_bootstrap_roles_and_owner_connection(self):
        policy = self.owner_policy()
        reads = [statement for statement in policy if statement['Effect'] == 'Allow' and 'ssm:GetParameters' in statement['Action']]
        self.assertEqual(len(reads), 1)
        self.assertEqual(set(reads[0]['Action']), {'ssm:GetParameter', 'ssm:GetParameters'})
        prefix = 'arn:aws:ssm:ap-northeast-2:928618307647:parameter/meterengine/'
        expected = {prefix+'staging/'+name for name in ('db-host','db-port','db-name',
            'db-shared-username','db-shared-password','db-test-username','db-test-password','db-live-username','db-live-password')}
        expected |= {prefix+'staging-migration/db-migration-username', prefix+'staging-migration/db-migration-password'}
        self.assertEqual(set(reads[0]['Resource']), expected)
        self.assertEqual(len(reads[0]['Resource']), 11)
        denied = set().union(*(set(statement['Action']) for statement in policy if statement['Effect'] == 'Deny' and statement['Resource'] == '*'))
        self.assertTrue({'ssm:SendCommand','ssm:GetParametersByPath','ssm:GetParameterHistory'} <= denied)
        runtime = json.loads(self.resource('aws_iam_role_policy.runtime')['policy'])['Statement']
        runtime_reads = next(statement for statement in runtime if statement['Effect'] == 'Allow' and 'ssm:GetParameters' in statement['Action'])
        self.assertFalse(any('migration/' in resource or '/production/' in resource for resource in runtime_reads['Resource']))

    def test_owner_can_open_only_its_own_session_data_channel(self):
        owner = self.owner_policy()
        channels = [statement for statement in owner if 'ssmmessages:OpenDataChannel' in statement['Action']]
        self.assertEqual(len(channels), 1)
        self.assertEqual(channels[0]['Effect'], 'Allow')
        self.assertEqual(channels[0]['Resource'], 'arn:aws:ssm:ap-northeast-2:928618307647:session/${aws:userid}-*')
        runtime = json.loads(self.resource('aws_iam_role_policy.runtime')['policy'])['Statement']
        instance_channels = [statement for statement in runtime if 'ssmmessages:OpenDataChannel' in statement['Action']]
        self.assertEqual(instance_channels[0]['Resource'], '*')

    def test_owner_forwarding_requires_document_access_check_and_excludes_default_shell(self):
        start = [statement for statement in self.owner_policy() if 'ssm:StartSession' in statement['Action']]
        self.assertEqual(len(start), 1)
        self.assertEqual(start[0].get('Condition'), {'BoolIfExists': {'ssm:SessionDocumentAccessCheck': 'true'}})
        documents = {resource for resource in start[0]['Resource'] if ':document/' in resource}
        self.assertEqual(documents, {'arn:aws:ssm:ap-northeast-2::document/AWS-StartPortForwardingSessionToRemoteHost'})
        for document in ('SSM-SessionManagerRunShell', None):
            with self.subTest(document=document):
                resolved = document or 'SSM-SessionManagerRunShell'
                self.assertNotIn('arn:aws:ssm:ap-northeast-2:928618307647:document/'+resolved, documents)
                self.assertNotIn('arn:aws:ssm:ap-northeast-2::document/'+resolved, documents)


if __name__ == '__main__':
    unittest.main()
