import json
import os
from pathlib import Path
import shutil
import signal
import time
import socket
import subprocess
import tempfile
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
HOST = 'original.ap-northeast-2.rds.amazonaws.com'
IMAGE = '928618307647.dkr.ecr.ap-northeast-2.amazonaws.com/meterengine-backend@sha256:'+'a'*64


class MigrationRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.bin = self.root / 'bin'; self.bin.mkdir()
        shutil.copytree(DEPLOY, self.root / 'deploy', ignore=shutil.ignore_patterns('terraform', '__pycache__'))
        manifest = json.loads((DEPLOY / 'environments/production.json').read_text())
        manifest.update(database_host=HOST, enabled_services=['caddy','frontend','backend-blue','backend-green'])
        (self.root / 'deploy/environments/production.json').write_text(json.dumps(manifest))
        staging = dict(manifest, deployment_environment='staging', parameter_path='/meterengine/staging', database_identifier='meterengine-staging-db')
        (self.root / 'deploy/environments/staging.json').write_text(json.dumps(staging))
        self.trace = self.root / 'trace'; self.trace.touch()
        self.ca = self.root / 'ca.pem'; self.ca.write_text('isolated CA fixture')
        stub = '''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
args=sys.argv[1:]
name=Path(sys.argv[0]).name
with open(os.environ['TRACE'],'a') as f: f.write(json.dumps({'command':name,'args':args})+'\\n')
if name=='uname': print(os.environ.get('TEST_OS','Linux'))
elif name=='aws':
 if args[:2]==['sts','get-caller-identity']: print(json.dumps({'Account':os.environ.get('TEST_ACCOUNT','928618307647')}))
 elif args[:2]==['ssm','get-parameters']:
  names=args[args.index('--names')+1:args.index('--with-decryption')]
  values={'db-host':os.environ['HOST'],'db-port':'5432','db-name':'meterengine_app','db-migration-username':'meterengine_owner','db-migration-password':'D2_OWNER_TEST_ONLY'}
  for role in ('shared','test','live'):
   values['db-'+role+'-username']='meterengine_'+role
   values['db-'+role+'-password']='SF1_'+role.upper()+'_TEST_ONLY'
  values.update(json.loads(os.environ.get('VALUE_OVERRIDES','{}')))
  parameters=[{'Name':n,'Value':values[n.rsplit('/',1)[1]],'Type':'SecureString' if n.endswith('password') else 'String','Version':1} for n in reversed(names)]
  fault=os.environ.get('RESPONSE_FAULT','')
  if fault=='missing': parameters.pop()
  if fault=='duplicate': parameters.append(parameters[0])
  if fault=='outside': parameters[0]['Name']='/meterengine/production/unrequested'
  if fault=='cross-batch' and names[0].endswith('db-shared-username'): parameters[0]['Name']='/meterengine/staging/db-host'
  if fault=='bad-parameters': parameters={}
  if fault=='malformed': print('{invalid'); sys.exit(0)
  if fault=='aws-error': print('private provider error',file=sys.stderr); sys.exit(1)
  response={'Parameters':parameters,'InvalidParameters':['missing'] if fault=='invalid' else []}
  if os.environ.get('OMIT_INVALID_PARAMETERS'): response.pop('InvalidParameters')
  if 'INVALID_PARAMETERS_JSON' in os.environ: response['InvalidParameters']=json.loads(os.environ['INVALID_PARAMETERS_JSON'])
  print(json.dumps(response))
 else: sys.exit(87)
elif name=='docker':
 if args[0]=='info':
  if os.environ.get('INFO_FAIL'): sys.exit(1)
  print(os.environ.get('INFO_RAW') or json.dumps({'OperatingSystem':os.environ.get('ENGINE','OrbStack'),'OSType':os.environ.get('ENGINE_OS','linux'),'Architecture':os.environ.get('ARCH','aarch64')})); sys.exit(0)
 if args[:2]==['rm','--force']: sys.exit(0)
 assert args[0]=='run'
 envfile=Path(args[args.index('--env-file')+1])
 with open(os.environ['TRACE'],'a') as f: f.write(json.dumps({'mode':oct(envfile.stat().st_mode&0o777),'environment':envfile.read_text(),'envfile':str(envfile),'parent_mode':oct(envfile.parent.stat().st_mode&0o777)})+'\\n')
 if os.environ.get('RUN_FAIL'): sys.exit(19)
 if os.environ.get('PAUSE_RUN'):
  import time; time.sleep(30)
else: sys.exit(88)
'''
        for name in ('aws','docker','uname'):
            path=self.bin / name; path.write_text(stub); path.chmod(0o755)
        self.env=dict(os.environ,PATH=str(self.bin)+':'+os.environ['PATH'],TRACE=str(self.trace),HOST=HOST)

    def invoke(self, *tail, environment='staging', **overrides):
        self.trace.write_text('')
        return subprocess.run(['bash', str(self.root / 'deploy/migrate.sh'), environment, IMAGE, '15432', str(self.ca), *tail],
                              env=dict(self.env, **overrides), capture_output=True, text=True)

    def observations(self):
        return [json.loads(line) for line in self.trace.read_text().splitlines()]

    def assert_no_secret_read_or_run(self, result):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        for entry in self.observations():
            args = entry.get('args', [])
            self.assertNotEqual(args[:2], ['ssm', 'get-parameters'])
            self.assertFalse(entry.get('command') == 'docker' and args[0] == 'run')

    def test_omitted_invalid_parameters_allows_exact_requested_values(self):
        cases = [('staging', ()), ('staging', ('--migrate-only',)),
                 ('staging', ('--bootstrap-database',)), ('staging', ('--activate-target', '--fresh-target')),
                 ('production', ()), ('production', ('--migrate-only',))]
        for environment, tail in cases:
            with self.subTest(environment=environment, tail=tail):
                result = self.invoke(*tail, environment=environment, OMIT_INVALID_PARAMETERS='1',
                                     DB_TARGET_IDENTIFIER='meterengine-staging-db')
                self.assertEqual(result.returncode, 0, result.stderr)
                trace = self.observations()
                batches = [item['args'][item['args'].index('--names')+1:item['args'].index('--with-decryption')]
                           for item in trace if item.get('args', [])[:2] == ['ssm', 'get-parameters']]
                self.assertEqual([len(batch) for batch in batches], [5, 6] if tail == ('--bootstrap-database',) else [5])
                run = next(item['args'] for item in trace if item.get('command') == 'docker' and item['args'][0] == 'run')
                self.assertEqual(run[run.index(IMAGE)+1:], list(tail) if tail else ['--migrate-only'])
                snapshot = next(item for item in trace if 'environment' in item)
                self.assertFalse(Path(snapshot['envfile']).exists())

    def test_present_invalid_parameters_rejects_wrong_types_and_nonempty_values(self):
        for value in (None, False, '', 'invalid', {}, ['missing']):
            with self.subTest(value=value):
                result = self.invoke('--bootstrap-database', INVALID_PARAMETERS_JSON=json.dumps(value))
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertNotIn('Traceback', result.stderr)
                self.assertNotIn('TEST_ONLY', result.stdout+result.stderr)
                self.assertFalse(any(item.get('command') == 'docker' and item['args'][0] == 'run' for item in self.observations()))

    def test_staging_bootstrap_reads_exact_roles_in_separate_validated_batches(self):
        result = self.invoke('--bootstrap-database')
        self.assertEqual(result.returncode, 0, result.stderr)
        trace = self.observations()
        batches = [line['args'][line['args'].index('--names')+1:line['args'].index('--with-decryption')]
                   for line in trace if line.get('args', [])[:2] == ['ssm', 'get-parameters']]
        self.assertEqual([len(batch) for batch in batches], [5, 6])
        expected = {'/meterengine/staging/db-host', '/meterengine/staging/db-port', '/meterengine/staging/db-name',
                    '/meterengine/staging-migration/db-migration-username', '/meterengine/staging-migration/db-migration-password',
                    '/meterengine/staging/db-shared-username', '/meterengine/staging/db-shared-password',
                    '/meterengine/staging/db-test-username', '/meterengine/staging/db-test-password',
                    '/meterengine/staging/db-live-username', '/meterengine/staging/db-live-password'}
        self.assertEqual(set(sum(batches, [])), expected)
        self.assertEqual(len(sum(batches, [])), 11)
        run = next(item['args'] for item in trace if item.get('command') == 'docker' and item['args'][0] == 'run')
        self.assertEqual(run[-2:], [IMAGE, '--bootstrap-database'])
        snapshot = next(item for item in trace if 'environment' in item)
        for role in ('SHARED', 'TEST', 'LIVE'):
            self.assertIn('DB_'+role+'_USERNAME=meterengine_'+role.lower()+'\n', snapshot['environment'])
            self.assertIn('DB_'+role+'_PASSWORD=SF1_'+role+'_TEST_ONLY\n', snapshot['environment'])
            self.assertNotIn('SF1_'+role+'_TEST_ONLY', result.stdout+result.stderr+json.dumps(run))
        self.assertEqual(snapshot['mode'], '0o600')
        self.assertEqual(snapshot['parent_mode'], '0o700')
        self.assertFalse(Path(snapshot['envfile']).exists())

    def test_default_and_explicit_migration_read_only_five_parameters(self):
        for environment in ('staging', 'production'):
            for tail in ((), ('--migrate-only',)):
                with self.subTest(environment=environment, tail=tail):
                    result = self.invoke(*tail, environment=environment)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    trace = self.observations()
                    names = [arg for line in trace for arg in line.get('args', []) if arg.startswith('/meterengine/')]
                    self.assertEqual(set(names), {'/meterengine/'+environment+'/db-host', '/meterengine/'+environment+'/db-port',
                        '/meterengine/'+environment+'/db-name', '/meterengine/'+environment+'-migration/db-migration-username',
                        '/meterengine/'+environment+'-migration/db-migration-password'})
                    self.assertEqual(len(names), 5)
                    run = next(line['args'] for line in trace if line.get('command') == 'docker' and line['args'][0] == 'run')
                    self.assertEqual(run[-1], '--migrate-only')
                    snapshot = next(item for item in trace if 'environment' in item)
                    self.assertNotIn('DB_SHARED_', snapshot['environment'])
                    self.assertNotIn('DB_TARGET_IDENTIFIER', snapshot['environment'])

    def test_fresh_activation_requires_exact_explicit_target_and_passes_only_owner(self):
        result = self.invoke('--activate-target', '--fresh-target', DB_TARGET_IDENTIFIER='meterengine-staging-db',
                             DB_HOST='evil', DB_PORT='evil', DB_NAME='evil', DB_MIGRATION_USERNAME='evil', DB_MIGRATION_PASSWORD='evil',
                             DB_SSL_ROOT_CERT='evil', DB_SHARED_USERNAME='evil', DB_SHARED_PASSWORD='evil', OPERATION='evil')
        self.assertEqual(result.returncode, 0, result.stderr)
        trace = self.observations()
        run = next(item['args'] for item in trace if item.get('command') == 'docker' and item['args'][0] == 'run')
        self.assertEqual(run[-3:], [IMAGE, '--activate-target', '--fresh-target'])
        snapshot = next(item for item in trace if 'environment' in item)
        self.assertIn('DB_TARGET_IDENTIFIER=meterengine-staging-db\n', snapshot['environment'])
        self.assertNotIn('evil', snapshot['environment'])
        self.assertNotIn('DB_SHARED_', snapshot['environment'])
        names = [arg for line in trace for arg in line.get('args', []) if arg.startswith('/meterengine/')]
        self.assertEqual(len(names), 5)
        for target in ('', 'other-db', 'meterengine-staging-db\n', 'meterengine-staging-db\r'):
            with self.subTest(target=target):
                self.assert_no_secret_read_or_run(self.invoke('--activate-target', '--fresh-target', DB_TARGET_IDENTIFIER=target))

    def test_invalid_modes_fail_before_any_aws_or_docker(self):
        tails = [('--fresh-target',), ('--activate-target',), ('--fresh-target', '--activate-target'),
                 ('--migrate-only', '--bootstrap-database'), ('--migrate-only', '--migrate-only'),
                 ('--bootstrap-database', '--bootstrap-database'), ('--unknown',),
                 ('--activate-target', '--fresh-target', '--fresh-target'),
                 ('--bootstrap-database', '--activate-target', '--fresh-target')]
        for tail in tails:
            with self.subTest(tail=tail):
                self.assert_no_secret_read_or_run(self.invoke(*tail))
                self.assertEqual(self.observations(), [])
        for tail in (('--bootstrap-database',), ('--activate-target', '--fresh-target')):
            with self.subTest(production=tail):
                self.assert_no_secret_read_or_run(self.invoke(*tail, environment='production'))
                self.assertEqual(self.observations(), [])

    def test_darwin_staging_supports_only_verified_orbstack_arm64(self):
        for arch in ('arm64', 'aarch64'):
            with self.subTest(arch=arch):
                result = self.invoke(TEST_OS='Darwin', ARCH=arch)
                self.assertEqual(result.returncode, 0, result.stderr)
                trace = self.observations()
                commands = [entry['command'] for entry in trace if 'command' in entry]
                self.assertEqual(commands[:3], ['uname', 'docker', 'aws'])
                self.assertEqual(trace[1]['args'], ['info', '--format', '{{json .}}'])
        for overrides in ({'ENGINE':'Docker Desktop'}, {'ENGINE_OS':'windows'}, {'ARCH':'x86_64'}, {'INFO_FAIL':'1'}, {'INFO_RAW':'{broken'}, {'INFO_RAW':'[]'}, {'INFO_RAW':'{}'}):
            with self.subTest(overrides=overrides):
                self.assert_no_secret_read_or_run(self.invoke(TEST_OS='Darwin', **overrides))
        for tail in (('--bootstrap-database',), ('--activate-target', '--fresh-target')):
            with self.subTest(darwin_mode=tail):
                result = self.invoke(*tail, TEST_OS='Darwin', DB_TARGET_IDENTIFIER='meterengine-staging-db')
                self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_no_secret_read_or_run(self.invoke(environment='production', TEST_OS='Darwin'))
        self.assert_no_secret_read_or_run(self.invoke(TEST_OS='FreeBSD'))

    def test_parameter_failures_never_start_docker_or_print_decrypted_values(self):
        for fault in ('missing', 'duplicate', 'outside', 'cross-batch', 'bad-parameters', 'invalid', 'malformed', 'aws-error'):
            with self.subTest(fault=fault):
                result = self.invoke('--bootstrap-database', RESPONSE_FAULT=fault)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertNotIn('Traceback', result.stderr)
                self.assertNotIn('TEST_ONLY', result.stdout+result.stderr)
                self.assertFalse(any(item.get('command') == 'docker' and item['args'][0] == 'run' for item in self.observations()))
        overrides = [('db-host', 'other.rds.amazonaws.com'), ('db-port', '5433'), ('db-name', 'other'),
                     ('db-migration-username', 'other'), ('db-shared-username', 'meterengine_test'),
                     ('db-test-username', 'meterengine_live'), ('db-live-username', 'meterengine_shared')]
        overrides += [(name, bad) for name in ('db-migration-password', 'db-shared-password', 'db-test-password', 'db-live-password')
                      for bad in ('', 'bad\nvalue', 'bad\rvalue', 'bad\0value', None, 42)]
        for name, value in overrides:
            with self.subTest(name=name, value=value):
                result = self.invoke('--bootstrap-database', VALUE_OVERRIDES=json.dumps({name:value}))
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertNotIn('Traceback', result.stderr)
                self.assertNotIn('TEST_ONLY', result.stdout+result.stderr)
                self.assertFalse(any(item.get('command') == 'docker' and item['args'][0] == 'run' for item in self.observations()))
        self.assert_no_secret_read_or_run(self.invoke('--bootstrap-database', TEST_ACCOUNT='000000000000'))

    def test_container_failure_still_removes_ephemeral_credentials(self):
        result = self.invoke('--bootstrap-database', RUN_FAIL='1')
        self.assertEqual(result.returncode, 19, result.stderr)
        snapshot = next(item for item in self.observations() if 'environment' in item)
        self.assertFalse(Path(snapshot['envfile']).exists())

    def test_owner_oneoff_preserves_hostname_tls_and_ephemeral_credentials(self):
        result = subprocess.run(['bash',str(self.root / 'deploy/migrate.sh'),'production',IMAGE,'15432',str(self.ca)], env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        trace=[json.loads(line) for line in self.trace.read_text().splitlines()]
        run=next(line['args'] for line in trace if line.get('command')=='docker')
        self.assertEqual(run[run.index('--network')+1], 'host')
        self.assertEqual(run[run.index('--add-host')+1], HOST+':127.0.0.1')
        self.assertEqual(run[-2:], [IMAGE,'--migrate-only'])
        self.assertIn(str(self.ca)+':/etc/meterengine/rds-ca.pem:ro', run)
        snapshot=next(line for line in trace if 'environment' in line)
        self.assertEqual(snapshot['mode'], '0o600')
        self.assertIn('DB_HOST='+HOST+'\n',snapshot['environment'])
        self.assertIn('DB_PORT=15432\n',snapshot['environment'])
        self.assertIn('DB_SSL_ROOT_CERT=/etc/meterengine/rds-ca.pem\n',snapshot['environment'])
        self.assertIn('DB_MIGRATION_PASSWORD=D2_OWNER_TEST_ONLY\n',snapshot['environment'])
        self.assertNotIn('DB_SHARED_',snapshot['environment'])
        self.assertFalse(Path(snapshot['envfile']).exists())
        self.assertNotIn('D2_OWNER_TEST_ONLY', result.stdout+result.stderr+json.dumps(run))
        names=[arg for line in trace for arg in line.get('args',[]) if arg.startswith('/meterengine/')]
        self.assertEqual(set(names), {'/meterengine/production/db-host','/meterengine/production/db-port','/meterengine/production/db-name','/meterengine/production-migration/db-migration-username','/meterengine/production-migration/db-migration-password'})

    def test_cancellation_removes_only_its_oneoff_and_temporary_credentials(self):
        process=subprocess.Popen(['bash',str(self.root/'deploy/migrate.sh'),'production',IMAGE,'15432',str(self.ca)],env=dict(self.env,PAUSE_RUN='1'),text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        self.addCleanup(lambda: process.kill() if process.poll() is None else None)
        snapshot=None
        for _ in range(100):
            lines=[json.loads(line) for line in self.trace.read_text().splitlines()]
            snapshot=next((line for line in lines if 'environment' in line),None)
            if snapshot: break
            time.sleep(0.02)
        self.assertIsNotNone(snapshot)
        process.send_signal(signal.SIGTERM)
        stdout,stderr=process.communicate(timeout=5)
        self.assertEqual(process.returncode,143,stdout+stderr)
        self.assertFalse(Path(snapshot['envfile']).exists())
        lines=[json.loads(line) for line in self.trace.read_text().splitlines()]
        run=next(line['args'] for line in lines if line.get('command')=='docker' and line['args'][0]=='run')
        name=run[run.index('--name')+1]
        cleanup=next(line['args'] for line in lines if line.get('command')=='docker' and line['args'][0]=='rm')
        self.assertEqual(cleanup,['rm','--force',name])

    def test_missing_ca_or_non_digest_image_fails_before_secret_reads(self):
        for image,ca in ((IMAGE,self.root/'missing.pem'),('backend:latest',self.ca)):
            with self.subTest(image=image):
                result=subprocess.run(['bash',str(self.root/'deploy/migrate.sh'),'production',image,'15432',str(ca)],env=self.env,capture_output=True,text=True)
                self.assertNotEqual(result.returncode,0)
                self.assertNotIn('get-parameters',self.trace.read_text())


if __name__ == '__main__':
    unittest.main()
