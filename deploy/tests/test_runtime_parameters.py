import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
HOST = "meterengine-staging-db.example.ap-northeast-2.rds.amazonaws.com"


class RuntimeParametersTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.trace = self.root / "trace"
        aws = self.root / "aws"
        aws.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
args=sys.argv[1:]
with open(os.environ['TRACE'],'a') as f: f.write(json.dumps(args)+'\\n')
if args[:2] != ['ssm','get-parameters']: sys.exit(71)
names=args[args.index('--names')+1:args.index('--with-decryption')]
values={'db-host':os.environ['HOST'],'db-port':'5432','db-name':'meterengine_app','meterengine-cutover-mode':'read_only','db-shared-username':'meterengine_shared','db-test-username':'meterengine_test','db-live-username':'meterengine_live'}
items=[{'Name':n,'Value':values.get(n.rsplit('/',1)[1],"D2 fixture '$dollar \\\\ literal")} for n in names]
invalid=[]
optional=[n for n in names if '/meterengine-auth-rate-limit-' in n]
webhook=[n for n in names if n.endswith('/meterengine-staging-webhook-allowed-urls')]
if os.environ.get('WEBHOOK_PRESENT'):
 for p in items:
  if p['Name'] in webhook: p['Value']=os.environ.get('WEBHOOK_VALUE','')
else:
 items=[p for p in items if p['Name'] not in webhook]
 invalid.extend(webhook)
if not os.environ.get('OPTIONAL_PRESENT'):
 items=[p for p in items if p['Name'] not in optional]
 invalid.extend([] if os.environ.get('OPTIONAL_UNREPORTED') else optional)
else:
 for p in items:
  if p['Name'] in optional: p['Value']=os.environ.get('OPTIONAL_VALUE','37')
if os.environ.get('INVALID_REQUIRED'): invalid.append('/meterengine/staging/db-host');items=[p for p in items if not p['Name'].endswith('/db-host')]
if os.environ.get('INVALID_EXTRA'): invalid.append('/meterengine/production/db-host')
if os.environ.get('INVALID_DUPLICATE') and invalid: invalid.append(invalid[0])
if os.environ.get('INVALID_AND_PRESENT') and optional:
 invalid=[optional[0]]
 items.append({'Name':optional[0],'Value':'37'})
if os.environ.get('DUPLICATE') and items: items.append(items[0])
if os.environ.get('OTHER_ENV'): items.append({'Name':'/meterengine/production/db-host','Value':'FORBIDDEN_ENV_FIXTURE'})
if os.environ.get('OWNER_ROLE'):
 for p in items:
  if p['Name'].endswith('/db-shared-username'): p['Value']='meterengine_owner'
if os.environ.get('EMPTY_REQUIRED'):
 for p in items:
  if p['Name'].endswith('/db-shared-password'): p['Value']=''
if os.environ.get('OAUTH_MISSING'):
 items=[p for p in items if not p['Name'].endswith('/'+os.environ['OAUTH_MISSING'])]
if os.environ.get('OAUTH_EMPTY'):
 for p in items:
  if p['Name'].endswith('/'+os.environ['OAUTH_EMPTY']): p['Value']=os.environ.get('OAUTH_VALUE','')
if os.environ.get('MISSING'): items=items[1:]
if os.environ.get('EXTRA'): items.append({'Name':'/meterengine/staging-migration/db-migration-password','Value':'FORBIDDEN_OWNER_FIXTURE'})
if os.environ.get('NUL_VALUE'):
 for p in items:
  if p['Name'] in optional: p['Value']='bad'+chr(0)+'value'
if os.environ.get('WRONG_DB'):
 for p in items:
  if p['Name'].endswith('/db-host'): p['Value']='wrong.rds.amazonaws.com'
print(json.dumps({'Parameters':items,'InvalidParameters':invalid}))
''')
        aws.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{self.root}:{os.environ['PATH']}", TRACE=str(self.trace), HOST=HOST)

    def snapshot(self, *services, **env):
        environment = env.pop('DEPLOY_ENVIRONMENT', 'staging')
        return subprocess.run(["python3", str(DEPLOY / "runtime_parameters.py"), "--environment", environment, "--expected-db-host", HOST, "--services", *services],
                              env=dict(self.env, **env), text=True, capture_output=True)

    def test_runtime_never_contains_migration_or_master_credentials(self):
        result = self.snapshot("backend-blue", "backend-green", "caddy", "frontend")
        self.assertEqual(result.returncode, 0, result.stderr)
        names = [arg for line in self.trace.read_text().splitlines() for arg in json.loads(line) if arg.startswith('/meterengine/')]
        self.assertEqual(len(names), 20)
        self.assertTrue(all(name.startswith('/meterengine/staging/') for name in names))
        self.assertFalse(any('migration' in name or 'oauth2-proxy' in name or name.endswith(('/db-password', '/db-username')) for name in names))
        self.assertIn("DB_SHARED_USERNAME=meterengine_shared", result.stdout)
        self.assertNotIn("DB_MIGRATION_", result.stdout)
        self.assertNotIn("DB_USERNAME=", result.stdout)
        self.assertNotIn("DB_PASSWORD=", result.stdout)
        self.assertNotIn("DB_EXPORTER_PASSWORD", result.stdout)
        self.assertEqual(result.stderr, "")

    def test_selected_oauth_requires_each_value_even_when_sites_are_off(self):
        for name in ('oauth2-proxy-client-id', 'oauth2-proxy-client-secret', 'oauth2-proxy-cookie-secret'):
            for mutation in ({'OAUTH_MISSING': name}, {'OAUTH_EMPTY': name}, {'OAUTH_EMPTY': name, 'OAUTH_VALUE': ' '}):
                with self.subTest(name=name, mutation=mutation):
                    result = self.snapshot('backend-blue', 'oauth2-proxy', DEPLOY_ENVIRONMENT='production', **mutation)
                    self.assertEqual(result.returncode, 2, result.stdout)
                    self.assertEqual(result.stdout, '')
        result = self.snapshot('backend-blue', 'oauth2-proxy', DEPLOY_ENVIRONMENT='production')
        self.assertEqual(result.returncode, 0, result.stderr)
        for name in ('CLIENT_ID', 'CLIENT_SECRET', 'COOKIE_SECRET'):
            self.assertIn('OAUTH2_PROXY_'+name+'=', result.stdout)

    def test_optional_absence_omits_environment_lines(self):
        result = self.snapshot('backend-blue')
        self.assertEqual(result.returncode, 0, result.stderr)
        names = [arg for line in self.trace.read_text().splitlines() for arg in json.loads(line) if arg.startswith('/meterengine/')]
        self.assertIn('/meterengine/staging/meterengine-auth-rate-limit-login-ip-limit', names)
        self.assertNotIn('METERENGINE_AUTH_RATE_LIMIT_LOGIN_IP_LIMIT=', result.stdout)
        self.assertIn('DB_NAME=meterengine_app', result.stdout)

    def test_unreported_optional_absence_also_omits_environment_lines(self):
        result = self.snapshot('backend-blue', OPTIONAL_UNREPORTED='1')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('METERENGINE_AUTH_RATE_LIMIT_LOGIN_IP_LIMIT=', result.stdout)

    def test_both_environments_resolve_defaults_and_present_overrides(self):
        defaults = {'LOGIN_IP_LIMIT': '100', 'LOGIN_ACCOUNT_LIMIT': '10', 'LOGIN_WINDOW': '5m',
                    'SIGNUP_IP_LIMIT': '20', 'SIGNUP_ACCOUNT_LIMIT': '3', 'SIGNUP_WINDOW': '30m'}
        for environment in ('staging', 'production'):
            for present in ('', '1'):
                with self.subTest(environment=environment, present=present):
                    result = self.snapshot('backend-blue', DEPLOY_ENVIRONMENT=environment, OPTIONAL_PRESENT=present, OPTIONAL_VALUE='37')
                    self.assertEqual(result.returncode, 0, result.stderr)
                    env_file = self.root / 'runtime.env'
                    env_file.write_text(result.stdout)
                    env = {key: value for key, value in os.environ.items() if not key.startswith('METERENGINE_AUTH_RATE_LIMIT_')}
                    env.update({key: 'fixture' for key in ('ECR_REGISTRY', 'BACKEND_BLUE_TAG', 'BACKEND_GREEN_TAG', 'IMAGE_TAG', 'BACKEND_MAX_RAM_PERCENTAGE', 'SITE_ALIASES')})
                    env.update(BACKEND_MEM_LIMIT='768m', SITE_DOMAIN='fixture.example.invalid', DB_SSL_ROOT_CERT='/etc/meterengine/rds-ca.pem', METERENGINE_DEPLOYMENT_ENVIRONMENT=environment)
                    resolved = subprocess.run(['docker', 'compose', '--env-file', str(env_file), '-p', 'meterengine-'+environment,
                                               '-f', str(DEPLOY/'compose.prod.yml'), '-f', str(DEPLOY/'compose.runtime.yml'),
                                               'config', '--format', 'json'], env=env, text=True, capture_output=True)
                    self.assertEqual(resolved.returncode, 0, resolved.stderr)
                    config = json.loads(resolved.stdout)
                    for backend in ('backend-blue', 'backend-green'):
                        values = config['services'][backend]['environment']
                        for name, default in defaults.items():
                            self.assertEqual(values['METERENGINE_AUTH_RATE_LIMIT_'+name], '37' if present else default)
                        self.assertEqual(values['DB_SHARED_USERNAME'], 'meterengine_shared')
                        self.assertNotIn('SPRING_DATASOURCE_USERNAME', values)
                    self.assertEqual(config['services']['frontend']['networks']['proxy']['ipv4_address'], '172.30.0.11')

    def test_present_optional_values_are_preserved(self):
        result = self.snapshot('backend-green', OPTIONAL_PRESENT='1', OPTIONAL_VALUE='47m')
        self.assertEqual(result.returncode, 0, result.stderr)
        for name in ('LOGIN_IP_LIMIT', 'LOGIN_ACCOUNT_LIMIT', 'LOGIN_WINDOW', 'SIGNUP_IP_LIMIT', 'SIGNUP_ACCOUNT_LIMIT', 'SIGNUP_WINDOW'):
            self.assertIn(f'METERENGINE_AUTH_RATE_LIMIT_{name}=47m\n', result.stdout)

    def test_staging_webhook_optional_is_exact_and_empty_value_means_empty_list(self):
        for value in ('', 'https://example.com/Exact%2f?A=%2C, https://example.com/empty?'):
            result = self.snapshot('backend-blue', WEBHOOK_PRESENT='1', WEBHOOK_VALUE=value)
            self.assertEqual(result.returncode, 0, result.stderr)
            env_file = self.root / 'webhook.env'
            env_file.write_text(result.stdout)
            spec = importlib.util.spec_from_file_location('runtime_webhook_fixture', DEPLOY / 'runtime_parameters.py')
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            self.assertEqual(module.read_env_value(env_file, 'METERENGINE_STAGING_WEBHOOK_ALLOWED_URLS'), value)
        self.assertNotIn('METERENGINE_DEPLOYMENT_ENVIRONMENT=', result.stdout)

    def test_invalid_response_and_owner_role_fail_without_partial_output(self):
        for mutation in ('INVALID_REQUIRED', 'INVALID_EXTRA', 'INVALID_DUPLICATE', 'INVALID_AND_PRESENT', 'DUPLICATE', 'OTHER_ENV', 'OWNER_ROLE', 'EMPTY_REQUIRED'):
            with self.subTest(mutation=mutation):
                result = self.snapshot('backend-blue', **{mutation: '1'})
                self.assertEqual(result.returncode, 2, result.stdout)
                self.assertEqual(result.stdout, '')
                self.assertNotIn('FORBIDDEN_', result.stderr)

    def test_empty_or_multiline_present_optional_value_fails_without_output(self):
        for value in ('', ' ', '\n', '37\nFORBIDDEN=1', '37\r', '\0'):
            with self.subTest(value=value):
                result = self.snapshot('backend-blue', OPTIONAL_PRESENT='1', **({'NUL_VALUE': '1'} if '\0' in value else {'OPTIONAL_VALUE': value}))
                self.assertEqual(result.returncode, 2, result.stdout)
                self.assertEqual(result.stdout, '')

    def test_missing_extra_or_changed_database_parameter_fails_without_partial_output(self):
        for mutation in ('MISSING', 'EXTRA', 'WRONG_DB'):
            with self.subTest(mutation=mutation):
                result = self.snapshot('backend-blue', **{mutation: '1'})
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, '')
                self.assertNotIn('FORBIDDEN_OWNER_FIXTURE', result.stderr)
                self.assertNotIn('$dollar', result.stderr)

    def test_secret_punctuation_survives_real_compose_env_parsing(self):
        result = self.snapshot('backend-blue')
        self.assertEqual(result.returncode, 0, result.stderr)
        env_file = self.root / 'runtime.env'
        env_file.write_text(result.stdout)
        compose_file = self.root / 'compose.yml'
        compose_file.write_text('services:\n  app:\n    image: fixture\n    environment:\n      SECRET: ${DB_SHARED_PASSWORD:?required}\n')
        result = subprocess.run(['docker','compose','--env-file',str(env_file),'-f',str(compose_file),'config','--format','json'], text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['services']['app']['environment']['SECRET'], "D2 fixture '$$dollar \\ literal")

    def test_secret_ending_in_backslash_and_quoted_backslash_survive_compose(self):
        spec = importlib.util.spec_from_file_location('runtime_parameters', DEPLOY / 'runtime_parameters.py')
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        compose_file = self.root / 'compose.yml'
        compose_file.write_text('services:\n  app:\n    image: fixture\n    environment:\n      SECRET: ${SECRET:?required}\n')
        for value in ("ends\\", "has\\'quote", "has\\$val", "has\\\\two", "quote'", 'a"b'):
            with self.subTest(value=value):
                env_file=self.root / 'runtime.env'; env_file.write_text('SECRET='+module.env_value(value)+'\n')
                result=subprocess.run(['docker','compose','--env-file',str(env_file),'-f',str(compose_file),'config','--format','json'],text=True,capture_output=True)
                self.assertEqual(result.returncode,0,result.stderr)
                self.assertEqual(json.loads(result.stdout)['services']['app']['environment']['SECRET'],value.replace('$','$$'))

    def test_encoded_env_can_be_read_for_grafana_without_corrupting_password(self):
        result=self.snapshot('backend-blue','grafana')
        self.assertEqual(result.returncode,0,result.stderr)
        env_file=self.root/'runtime.env';env_file.write_text(result.stdout)
        result=subprocess.run(['python3',str(DEPLOY/'runtime_parameters.py'),'read-env',str(env_file),'GRAFANA_ADMIN_PASSWORD'],text=True,capture_output=True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout,"D2 fixture '$dollar \\ literal")

    def test_enabled_observability_fetches_only_its_parameters(self):
        result = self.snapshot('backend-blue', 'grafana', 'postgres-exporter')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('GRAFANA_ADMIN_PASSWORD=', result.stdout)
        self.assertIn('SLACK_WEBHOOK_URL=', result.stdout)
        self.assertIn('DB_EXPORTER_PASSWORD=', result.stdout)


if __name__ == '__main__':
    unittest.main()
