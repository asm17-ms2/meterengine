import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import shlex

from test_locking import function


ROOT = Path(__file__).resolve().parents[2]
SHA = "a" * 40
TARGET = {
    "schema_version": 1, "deployment_environment": "production",
    "aws_account_id": "928618307647", "aws_region": "ap-northeast-2",
    "instance_id": "i-0f47bb1f028cd29a9", "database_identifier": "meterengine-db",
    "database_host": "meterengine-db.cbqy2s86g8zf.ap-northeast-2.rds.amazonaws.com",
    "parameter_path": "/meterengine/production", "site_domain": "meterengine.com",
    "site_aliases": ["www.meterengine.com"], "compose_project": "meterengine-prod",
    "enabled_services": ["caddy", "frontend", "backend-blue", "backend-green"],
}


class EnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        if os.environ.get('DEPLOY_FIXTURE_ARTIFACTS'):
            destination = Path(os.environ['DEPLOY_FIXTURE_ARTIFACTS']) / self.id()
            self.addCleanup(lambda: shutil.copytree(self.root, destination, symlinks=True))
        self.deploy = self.root / "deploy"
        shutil.copytree(ROOT / "deploy", self.deploy, ignore=shutil.ignore_patterns("__pycache__", ".terraform"))
        (self.deploy / "environments").mkdir(exist_ok=True)
        (self.deploy / "environments/production.json").write_text(json.dumps(TARGET))
        (self.root / "rds-ca.pem").write_text("local config fixture, no real connection\n")
        self.env_file = self.root / "prod.env"
        self.state = self.root / "deploy-state"
        for path in (self.env_file, self.state, self.deploy / "caddy/upstreams.caddy", self.deploy / "caddy/access-log.caddy"):
            path.write_text("preserve existing data\n")
        script = (self.deploy / "deploy.sh").read_text()
        replacements = {
            "REPO_DIR=/opt/meterengine": f"REPO_DIR={self.root}",
            "ENV_FILE=/etc/meterengine/prod.env": f"ENV_FILE={self.env_file}",
            "CONFIG_FILE=/etc/meterengine/deploy.conf": f"CONFIG_FILE={self.root}/deploy.conf",
            "STATE_FILE=/etc/meterengine/deploy-state": f"STATE_FILE={self.state}",
            "RDS_CA_FILE=/etc/meterengine/rds-ca.pem": f"RDS_CA_FILE={self.root}/rds-ca.pem",
            "LOCK_FILE=/etc/meterengine/deploy-command.lock": f"LOCK_FILE={self.root}/lock",
        }
        for before, after in replacements.items():
            script = script.replace(before, after)
        (self.deploy / "deploy.sh").write_text(script)
        self.trace = self.root / "trace"
        self.trace.touch()
        self.secret_tmp = self.root / "secret-tmp"
        self.secret_tmp.mkdir()
        self.bin = self.root / "bin"
        self.bin.mkdir()
        stub = '''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
name=Path(sys.argv[0]).name
args=sys.argv[1:]
with open(os.environ['TRACE'], 'a') as f: f.write(name+' '+ ' '.join(args)+'\\n')
target=json.loads(os.environ['TARGET'])
if name in ('flock','sleep'): sys.exit(0)
if name == 'curl':
    if args[-1].startswith('http://127.0.0.1:'):
        print('{"database":"ok"}' if '/api/health' in args[-1] else 'ok'); sys.exit(0)
    print('token' if 'PUT' in args else json.dumps({'accountId':target['aws_account_id'],'region':target['aws_region'],'instanceId':os.environ.get('INSTANCE',target['instance_id'])}))
elif name == 'aws':
    if args[:2] == ['sts','get-caller-identity']: print(json.dumps({'Account':target['aws_account_id']}))
    elif args[:2] == ['rds','describe-db-instances']: print(json.dumps({'DBInstances':[{'DBInstanceIdentifier':target['database_identifier'],'Endpoint':{'Address':target['database_host']}}]}))
    elif args[:2] == ['ssm','get-parameter']: print(json.dumps({'Parameter':{'Value':os.environ.get('DB_HOST',target['database_host'])}}))
    elif args[:2] == ['ecr','describe-images']: print('sha256:'+('b' if 'meterengine-backend' in args else 'c')*64)
    elif args[:2] == ['ecr','get-login-password']: print('fixture-password')
    elif args[:2] == ['ssm','get-parameters']:
        modes=[oct(path.stat().st_mode & 0o777) for path in Path(os.environ['TMPDIR']).iterdir()]
        with open(os.environ['TRACE'],'a') as f: f.write('snapshot modes '+','.join(modes)+'\\n')
        if os.environ.get('FETCH_FAIL'): sys.exit(43)
        values={'db-host':os.environ.get('FETCH_DB_HOST',target['database_host']),'db-port':'5432','db-name':'meterengine_app','meterengine-cutover-mode':'read_only','db-shared-username':'meterengine_shared','db-test-username':'meterengine_test','db-live-username':'meterengine_live'}
        names=args[args.index('--names')+1:args.index('--with-decryption')]
        print(json.dumps({'Parameters':[{'Name':name,'Value':('' if name.rsplit('/',1)[1] == os.environ.get('EMPTY_OAUTH') else values.get(name.rsplit('/',1)[1],'fixture'))} for name in names if name.rsplit('/',1)[1] != os.environ.get('MISSING_OAUTH')],'InvalidParameters':[]}))
    elif args[:2] == ['ssm','get-parameters-by-path']:
        if os.environ.get('DUPLICATE_DEPLOYMENT'): print(target['parameter_path']+'/meterengine-deployment-environment\tproduction')
        modes=[oct(path.stat().st_mode & 0o777) for path in Path(os.environ['TMPDIR']).iterdir()]
        with open(os.environ['TRACE'],'a') as f: f.write('snapshot modes '+','.join(modes)+'\\n')
        for key in ['db-host','db-port','db-name','db-username','db-password','crypto-encryption-key','operator-email','operator-password']:
            if os.environ.get('MISSING_SUT_CREDENTIAL') == key: continue
            print(target['parameter_path']+'/'+key+'\\t'+(os.environ.get('FETCH_DB_HOST',target['database_host']) if key == 'db-host' else 'fixture'))
        if os.environ.get('SUT_OAUTH_VALUES'):
            for key in ('oauth2-proxy-client-id','oauth2-proxy-client-secret','oauth2-proxy-cookie-secret'):
                if key != os.environ.get('MISSING_OAUTH'):
                    print(target['parameter_path']+'/'+key+'\\t'+('' if key == os.environ.get('EMPTY_OAUTH') else 'fixture'))
        if os.environ.get('FETCH_FAIL'): sys.exit(43)
    else: sys.exit(87)
elif name == 'git':
    for descriptor in (8,9):
        try: os.fstat(descriptor)
        except OSError: continue
        print('git inherited deployment lock',file=sys.stderr); sys.exit(97)
    if args[0] == 'rev-parse': print('a'*40)
    elif args[0] == 'show':
        if os.environ.get('OLD_SOURCE'): sys.exit(1)
        path=args[1].split(':',1)[1]
        if path == 'deploy/deploy.sh' and os.environ.get('SOURCE_NO_MARKER'):
            print('# old script'); sys.exit(0)
        file=Path(os.environ['REPO'])/path
        if not file.exists(): sys.exit(1)
        sys.stdout.write(file.read_text())
    elif args[0] not in ['fetch','checkout','diff']: sys.exit(88)
elif name == 'docker':
    if args[0] == 'ps':
        service=args[-1].split('=')[-1]
        if service in ['caddy','frontend','backend-blue'] or (service == 'backend-green' and (Path(os.environ['REPO'])/'started-green').exists()): print(service+'-id')
    elif args[:2] == ['container','inspect']:
        service=args[-1][:-3]
        print(json.dumps([{'Id':args[-1], 'Image':'sha256:'+('e' if service=='frontend' else 'd')*64, 'State':{'Running':True}, 'Config':{'Image':'declared-ref', 'Labels':{'com.docker.compose.project':target['compose_project'],'com.docker.compose.service':service}}}]))
    elif args[:2] == ['image','inspect']:
        frontend=args[-1]=='sha256:'+'e'*64
        repository='frontend' if frontend else 'backend'
        digest='f' if frontend and os.environ.get('OBSERVATION_FAIL') else ('c' if frontend else 'b')
        print(json.dumps([{'Id':args[-1], 'Os':'linux','Architecture':'arm64','RepoDigests':['928618307647.dkr.ecr.ap-northeast-2.amazonaws.com/meterengine-'+repository+'@sha256:'+digest*64]}]))
        if frontend and os.environ.get('OBSERVATION_COLOR_CHANGE'): (Path(os.environ['REPO'])/'color-changed').touch()
    elif args[0] == 'inspect': print('928618307647.dkr.ecr.ap-northeast-2.amazonaws.com/meterengine-backend'+(':'+ 'd'*40 if args[-1] == 'backend-blue-id' else (':'+ 'a'*40 if target['deployment_environment']=='sut' else '@sha256:'+ 'b'*64)))
    elif args[0] == 'exec':
        url=args[-1]
        if 'localhost:8081' in url: print('  X-Upstream: backend-blue:8080' if os.environ.get('FAIL_SWITCH') or (Path(os.environ['REPO'])/'color-changed').exists() else '  X-Upstream: '+(Path(os.environ['REPO'])/'deploy/caddy/upstreams.caddy').read_text().strip()[3:],file=sys.stderr)
        else: print('{"status":"UP"}')
    elif args[0] == 'login': sys.stdin.read()
    elif args[0] == 'compose':
        if 'exec' in args and 'cloudwatch-exporter' in args and os.environ.get('CLOUDWATCH_HUP_FAIL'): sys.exit(44)
        if os.environ.get('DB_SHARED_PASSWORD') == 'FORBIDDEN_ENV_OVERRIDE': sys.exit(98)
        if 'up' in args and 'backend-green' in args: (Path(os.environ['REPO'])/'started-green').touch()
        if 'config' in args: print('caddy\\nfrontend\\nbackend-blue\\nbackend-green\\nprometheus\\ngrafana\\npostgres-exporter')
    elif args[:2] != ['image','prune']: sys.exit(88)
else: sys.exit(88)
'''
        for name in ("aws", "curl", "git", "docker", "flock", "sleep"):
            path = self.bin / name
            path.write_text(stub)
            path.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}", TRACE=str(self.trace),
                        TARGET=json.dumps(TARGET), REPO=str(self.root), TMPDIR=str(self.secret_tmp), DEPLOY_RUN_ID="123", BACKEND_IMAGE_DIGEST="sha256:"+"b"*64,
                        FRONTEND_IMAGE_DIGEST="sha256:"+"c"*64, PYTHONDONTWRITEBYTECODE="1")
        self.hashes = self.file_hashes()

    def file_hashes(self):
        return [hashlib.sha256(path.read_bytes()).hexdigest() for path in
                (self.env_file, self.state, self.deploy / "caddy/upstreams.caddy", self.deploy / "caddy/access-log.caddy")]

    def run_deploy(self, *args, **environment):
        return subprocess.run(["bash", str(self.deploy / "deploy.sh"), *args],
                              env=dict(self.env, **environment), text=True, capture_output=True, timeout=60)

    def assert_preserved(self):
        self.assertEqual(self.hashes, self.file_hashes())
        trace = self.trace.read_text()
        self.assertNotIn("with-decryption", trace)
        self.assertNotIn("get-parameters-by-path", trace)
        self.assertNotIn("docker ", trace)
        self.assertNotIn("git checkout", trace)

    def test_missing_environment_stops_before_side_effects(self):
        result = self.run_deploy(SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("environment", result.stderr.lower())
        self.assertEqual(self.trace.read_text(), "")
        self.assert_preserved()

    def test_identity_mismatch_preserves_files(self):
        result = self.run_deploy("production", SHA, INSTANCE="i-00000000000000000")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("instance_id", result.stderr)
        self.assert_preserved()

    def test_wrong_database_parameter_is_rejected(self):
        result = self.run_deploy("production", SHA, DB_HOST="wrong.rds.amazonaws.com")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("db-host", result.stderr)
        self.assert_preserved()

    def test_check_cli_returns_target_and_rejects_missing_observations(self):
        observed = self.root / "observed.json"
        observed.write_text(json.dumps(TARGET))
        command = ["python3", str(self.deploy / "environment.py"), "check", "--environment", "production",
                   "--observed", str(observed)]
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), TARGET)
        observed.write_text("{}")
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")

    def test_disabled_observability_requires_no_secrets(self):
        result = self.run_deploy("production", SHA)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stderr, "")
        commands = self.trace.read_text().splitlines()
        service_commands = [line for line in commands if line.startswith("docker compose")]
        for command in service_commands:
            self.assertNotRegex(command, r"prometheus|grafana|exporter")
        self.assertIn("LIVE_SHA=" + SHA, self.state.read_text())
        self.assertIn("BACKEND_GREEN_IMAGE=928618307647.dkr.ecr.ap-northeast-2.amazonaws.com/meterengine-backend@sha256:" + "b" * 64, self.env_file.read_text())
        self.assertIn("BACKEND_BLUE_IMAGE=928618307647.dkr.ecr.ap-northeast-2.amazonaws.com/meterengine-backend:" + "d" * 40, self.env_file.read_text())
        self.assertIn("--project-name meterengine-prod", "\n".join(service_commands))
        self.assertIn("snapshot modes 0o600", commands)
        snapshot_calls = [line for line in commands if line.startswith("aws ssm get-parameters --names")]
        self.assertEqual(len(snapshot_calls), 2)
        checkout = next(index for index, line in enumerate(commands) if line.startswith("git checkout"))
        selection = next(index for index, line in enumerate(commands) if line.startswith("docker "))
        self.assertLess(commands.index(snapshot_calls[0]), min(checkout, selection))
        self.assertEqual(list(self.secret_tmp.iterdir()), [])
        self.assertEqual(list(self.root.glob("prod.env.*")), [])

    def test_staging_core_services_do_not_exec_monitoring(self):
        target = json.loads((ROOT / 'deploy/environments/staging.json').read_text())
        self.assertEqual(set(target['enabled_services']), {'caddy', 'frontend', 'backend-blue', 'backend-green'})
        self.env['TARGET'] = json.dumps(target)
        result = self.run_deploy('staging', SHA)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        trace = self.trace.read_text()
        self.assertIn('--project-name meterengine-staging', trace)
        for command in trace.splitlines():
            if command.startswith('docker compose'):
                self.assertNotRegex(command, r'prometheus|grafana|exporter|cadvisor|oauth2-proxy')
        self.assertIn('LIVE_SHA=' + SHA, self.state.read_text())

    def test_production_manifest_requests_all_selected_monitoring_services(self):
        target = json.loads((ROOT / 'deploy/environments/production.json').read_text())
        (self.deploy / 'environments/production.json').write_text(json.dumps(target))
        self.env['TARGET'] = json.dumps(target)
        result = self.run_deploy('production', SHA)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        commands = [line for line in self.trace.read_text().splitlines() if line.startswith('docker compose')]
        for action in (' pull ', ' up '):
            selected = [line for line in commands if action in line and 'cadvisor' in line]
            self.assertEqual(len(selected), 1, commands)
            self.assertIn('oauth2-proxy', selected[0])
        self.assertEqual((self.deploy / 'caddy/sso.caddy').read_text(), 'import sso-sites.caddy\n')

    def test_selected_oauth_missing_values_stop_before_checkout_even_with_sites_off(self):
        target = dict(TARGET, enabled_services=TARGET['enabled_services'] + ['oauth2-proxy'])
        (self.deploy / 'environments/production.json').write_text(json.dumps(target))
        self.env['TARGET'] = json.dumps(target)
        (self.root / 'deploy.conf').write_text('CADDY_SSO_SITES=off\n')
        for name in ('oauth2-proxy-client-id', 'oauth2-proxy-client-secret', 'oauth2-proxy-cookie-secret'):
            for kind in ('MISSING_OAUTH', 'EMPTY_OAUTH'):
                self.trace.write_text('')
                result = self.run_deploy('production', SHA, **{kind: name})
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertEqual(self.hashes, self.file_hashes())
                self.assertNotIn('git checkout', self.trace.read_text())
                self.assertNotIn('docker ', self.trace.read_text())

    def test_sut_selected_oauth_requires_all_three_values_before_checkout(self):
        sut = dict(TARGET, deployment_environment='sut', parameter_path='/meterengine/test-slo',
                   compose_project='meterengine-test-slo', enabled_services=TARGET['enabled_services'] + ['oauth2-proxy'])
        (self.deploy / 'environments/test-slo.json').write_text(json.dumps(sut))
        (self.root / 'deploy.conf').write_text('DEPLOYMENT_TARGET=test-slo\nCADDY_SSO_SITES=off\n')
        for name in ('oauth2-proxy-client-id', 'oauth2-proxy-client-secret', 'oauth2-proxy-cookie-secret'):
            for kind in ('MISSING_OAUTH', 'EMPTY_OAUTH'):
                self.trace.write_text('')
                result = self.run_deploy('test-slo', SHA, TARGET=json.dumps(sut), SUT_OAUTH_VALUES='1', **{kind: name})
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(name.upper().replace('-', '_')+' is required', result.stderr)
                self.assertNotIn('git checkout', self.trace.read_text())
                self.assertNotIn('docker ', self.trace.read_text())


    def test_cloudwatch_hup_failure_does_not_abort_or_reload_disabled_prometheus(self):
        target = dict(TARGET, enabled_services=TARGET['enabled_services'] + ['cloudwatch-exporter'])
        (self.deploy / 'environments/production.json').write_text(json.dumps(target))
        self.env['TARGET'] = json.dumps(target)
        result = self.run_deploy('production', SHA, CLOUDWATCH_HUP_FAIL='yes')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        trace = self.trace.read_text().splitlines()
        reloads = [line for line in trace if line.startswith('docker compose') and ' exec ' in line and ' kill -HUP ' in line]
        self.assertEqual(len(reloads), 1)
        self.assertIn('exec -T cloudwatch-exporter kill -HUP 1', reloads[0])
        self.assertFalse(any(' exec -T prometheus ' in line for line in trace))
        self.assertIn('cloudwatch exporter가 설정을 다시 읽지 못했다', result.stdout)
        self.assertIn('LIVE_SHA=' + SHA, self.state.read_text())

    def test_old_backend_is_preserved_on_failed_switch(self):
        result = self.run_deploy("production", SHA, FAIL_SWITCH="yes")
        self.assertNotEqual(result.returncode, 0)
        trace = self.trace.read_text()
        self.assertNotIn("stop backend-blue", trace)
        self.assertNotIn("rm -f backend-blue", trace)
        self.assertEqual(self.hashes[1], self.file_hashes()[1])

    def test_sut_configuration_does_not_fall_back_to_production(self):
        result = self.run_deploy("test-slo", SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("DEPLOYMENT_TARGET", result.stderr)
        self.assertEqual(self.trace.read_text(), "")
        self.assert_preserved()
        for value in ("PARAM_PATH=/meterengine/prod\n", "SITE_DOMAIN=production.example\n"):
            (self.root / "deploy.conf").write_text(value)
            result = self.run_deploy("production", SHA)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("deploy.conf", result.stderr)
            self.assert_preserved()

    def test_unconfigured_staging_fails_before_observation(self):
        path = self.deploy / "environments/staging.json"
        target = json.loads(path.read_text())
        target["instance_id"] = ""
        path.write_text(json.dumps(target))
        result = self.run_deploy("staging", SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unconfigured target", result.stderr)
        self.assertEqual(self.trace.read_text(), "")
        self.assert_preserved()

    def test_wrong_image_digest_preserves_files(self):
        result = self.run_deploy("production", SHA, BACKEND_IMAGE_DIGEST="sha256:" + "e" * 64)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("digest", result.stderr)
        self.assert_preserved()

    def test_old_source_ref_cannot_checkout_or_mutate_runtime(self):
        result = self.run_deploy("production", SHA, OLD_SOURCE="yes")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SOURCE_REF", result.stderr)
        self.assert_preserved()

    def test_sut_callers_require_installed_contract_before_checkout(self):
        for target in ("test-slo", "test-storage"):
            source = (ROOT / f"measurements/{target}/sut/scripts/sut-deploy.sh").read_text()
            config = self.root / "sut.conf"
            config.write_text(f"PARAM_PATH=/meterengine/{target}\nSITE_DOMAIN={target}.meterengine.com\nDEPLOYMENT_TARGET={target}\n")
            generate = (f"REMOTE_DEPLOY_CONFIG={shlex.quote(str(config))}\n"
                        f"REMOTE_REPO_DIR={shlex.quote(str(self.root))}\n"
                        "FIRST_SHA_WITH_DEPLOY_CONF=" + "c" * 40 + "\n" + function(source, "deploy_command") + "\n"
                        f"deploy_command {SHA} /meterengine/{target} {target}.meterengine.com {'b' * 40}\n")
            result = subprocess.run(["bash", "-c", generate], text=True, capture_output=True, env=self.env)
            self.assertEqual(result.returncode, 0, result.stderr)
            command = result.stdout.replace("/etc/meterengine/deploy-command.lock", str(self.root / "lock"))
            script = self.deploy / "deploy.sh"
            for helper_exists, marker in ((False, False), (True, False), (True, True)):
                with self.subTest(target=target, helper_exists=helper_exists, marker=marker):
                    helper = self.deploy / "environment.py"
                    helper.write_text("# installed helper\n")
                    if not helper_exists:
                        helper.unlink()
                    script.write_text("#!/usr/bin/env bash\n" + ("DEPLOY_CONTRACT_VERSION=1\n" if marker else "")
                                      + 'echo "started $*" >> "$TRACE"\n')
                    script.chmod(0o755)
                    self.trace.write_text("")
                    executed = subprocess.run(["sh", "-c", command], text=True, capture_output=True, env=self.env)
                    if helper_exists and marker:
                        self.assertEqual(executed.returncode, 0, executed.stderr)
                        self.assertIn(f"started {target} {SHA} {'b' * 40}", self.trace.read_text())
                    else:
                        self.assertNotEqual(executed.returncode, 0)
                        self.assertNotIn("started", self.trace.read_text())
                    self.assertNotIn("git ", self.trace.read_text())

    def test_check_cli_rejects_malformed_manifest_and_observation(self):
        observed = self.root / "observed.json"
        observed.write_text("[]")
        command = ["python3", str(self.deploy / "environment.py"), "check", "--environment", "production",
                   "--observed", str(observed)]
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertEqual(result.returncode, 2)
        self.assertNotIn("Traceback", result.stderr)
        observed.write_text(json.dumps(TARGET))
        for changes in ({"schema_version": True}, {"parameter_path": "/meterengine/prod"},
                        {"enabled_services": ["caddy", "frontend", "backend-blue", "backend-green", "unknown"]},
                        {"secret": "must never be printed"}):
            with self.subTest(changes=changes):
                (self.deploy / "environments/production.json").write_text(json.dumps({**TARGET, **changes}))
                result = subprocess.run(command, text=True, capture_output=True)
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, "")

    def test_parameter_change_after_preflight_cannot_replace_environment_file(self):
        (self.root / "deploy.conf").write_text("DEPLOY_TIMING=on\n")
        result = self.run_deploy("production", SHA, FETCH_DB_HOST="wrong.rds.amazonaws.com")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("db-host", result.stderr)
        self.assertEqual(self.hashes, self.file_hashes())
        self.assertNotIn("git checkout", self.trace.read_text())
        self.assertNotIn("docker ", self.trace.read_text())
        self.assertEqual(list(self.secret_tmp.iterdir()), [])
        self.assertEqual(list(self.root.glob("prod.env.*")), [])
        self.assertNotIn("fixture-password", result.stdout + result.stderr)

    def test_mixed_source_tree_cannot_checkout_legacy_script(self):
        result = self.run_deploy("production", SHA, SOURCE_NO_MARKER="yes")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SOURCE_REF", result.stderr)
        self.assert_preserved()

    def test_valid_source_larger_than_pipe_buffer_reaches_checkout(self):
        script = self.deploy / "deploy.sh"
        script.write_text(script.read_text() + "\n#" + "x" * 1000000 + "\n")
        result = self.run_deploy("production", SHA)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stderr, "")
        self.assertIn("git checkout", self.trace.read_text())

    def test_shell_credentials_cannot_override_the_verified_runtime_snapshot(self):
        result = self.run_deploy('production', SHA, DB_SHARED_PASSWORD='FORBIDDEN_ENV_OVERRIDE')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('DB_SHARED_PASSWORD=fixture', self.env_file.read_text())
        self.assertNotIn('FORBIDDEN_ENV_OVERRIDE', self.env_file.read_text())

    def test_manifest_deployment_and_webhook_runtime_cannot_be_overridden_by_shell(self):
        result = self.run_deploy('production', SHA, METERENGINE_DEPLOYMENT_ENVIRONMENT='FORBIDDEN_ENV_OVERRIDE', METERENGINE_STAGING_WEBHOOK_ALLOWED_URLS='FORBIDDEN_WEBHOOK_OVERRIDE')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        lines = self.env_file.read_text().splitlines()
        self.assertEqual([line for line in lines if line.startswith('METERENGINE_DEPLOYMENT_ENVIRONMENT=')], ['METERENGINE_DEPLOYMENT_ENVIRONMENT=production'])
        self.assertNotIn('FORBIDDEN_ENV_OVERRIDE', self.env_file.read_text())
        self.assertNotIn('FORBIDDEN_WEBHOOK_OVERRIDE', self.env_file.read_text())

    def test_sut_manifest_value_reaches_backend_environment_file_and_ssm_duplicate_is_rejected(self):
        sut = dict(TARGET, deployment_environment='sut', parameter_path='/meterengine/test-slo', compose_project='meterengine-test-slo')
        (self.deploy / 'environments/test-slo.json').write_text(json.dumps(sut))
        config = self.root / 'deploy.conf'
        config.write_text('DEPLOYMENT_TARGET=test-slo\nPARAM_PATH=/meterengine/test-slo\nSITE_DOMAIN=' + sut['site_domain'] + '\n')
        result = self.run_deploy('test-slo', SHA, TARGET=json.dumps(sut), METERENGINE_DEPLOYMENT_ENVIRONMENT='FORBIDDEN')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('METERENGINE_DEPLOYMENT_ENVIRONMENT=sut\n', self.env_file.read_text())
        before = self.env_file.read_bytes()
        result = self.run_deploy('test-slo', SHA, TARGET=json.dumps(sut), DUPLICATE_DEPLOYMENT='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.env_file.read_bytes(), before)

    def test_sut_snapshot_still_requires_each_legacy_database_credential(self):
        body = function((self.deploy / 'deploy.sh').read_text(), 'fetch_parameter_snapshot')
        harness = ('set -euo pipefail\n'
                   'environment=test-slo\n'
                   'PARAM_PATH=/meterengine/test-slo\n'
                   'EXPECTED_DB_HOST='+shlex.quote(TARGET['database_host'])+'\n'
                   'parameter_snapshot=\n'
                   'trap \'rm -f "${parameter_snapshot:-}"\' EXIT\n'
                   'die() { printf "%s\\n" "$*" >&2; exit 1; }\n'
                   'service_enabled() { return 1; }\n'+body+'\nfetch_parameter_snapshot\n')
        for suffix, environment_name in (('db-username','DB_USERNAME'),('db-password','DB_PASSWORD')):
            with self.subTest(missing=suffix):
                result = subprocess.run(['bash','-c',harness], env=dict(self.env,MISSING_SUT_CREDENTIAL=suffix), text=True,capture_output=True)
                self.assertNotEqual(result.returncode,0)
                self.assertIn(environment_name+' is required', result.stderr)

    def test_failed_parameter_snapshot_fetch_is_cleaned_without_runtime_changes(self):
        result = self.run_deploy("production", SHA, FETCH_FAIL="yes")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.hashes, self.file_hashes())
        self.assertIn("snapshot modes 0o600", self.trace.read_text())
        self.assertNotIn("git checkout", self.trace.read_text())
        self.assertNotIn("docker ", self.trace.read_text())
        self.assertEqual(list(self.secret_tmp.iterdir()), [])
        self.assertEqual(list(self.root.glob("prod.env.*")), [])
        self.assertNotIn("fixture-password", result.stdout + result.stderr)

    def test_first_deployment_records_observed_image_ids_and_source_tree(self):
        self.state.unlink()
        result = self.run_deploy('production', SHA, DEPLOY_RUN_ID='manual.fixture:1')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        state = dict(line.split('=', 1) for line in self.state.read_text().splitlines())
        self.assertEqual(state['SOURCE_REF'], SHA)
        self.assertEqual(state['SOURCE_TREE'], SHA)
        self.assertEqual(state['DEPLOY_RUN_ID'], 'manual.fixture:1')
        self.assertEqual(state['BACKEND_IMAGE_ID'], 'sha256:'+'d'*64)
        self.assertEqual(state['FRONTEND_IMAGE_ID'], 'sha256:'+'e'*64)
        self.assertEqual(state['BACKEND_IMAGE_DIGEST'], 'sha256:'+'b'*64)
        self.assertEqual(state['FRONTEND_IMAGE_DIGEST'], 'sha256:'+'c'*64)

    def test_failed_observation_preserves_previous_state(self):
        before = self.state.read_bytes()
        result = self.run_deploy('production', SHA, OBSERVATION_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.state.read_bytes(), before)
        self.assertIn('RepoDigest', result.stderr)

    def test_color_change_during_observation_cannot_publish_success(self):
        before=self.state.read_bytes()
        result=self.run_deploy('production',SHA,OBSERVATION_COLOR_CHANGE='1')
        self.assertNotEqual(result.returncode,0)
        self.assertEqual(self.state.read_bytes(),before)
        self.assertIn('Caddy',result.stderr)

    def test_run_identifier_rejects_state_injection_before_target_probe(self):
        for value in ['', 'a\nb=2', 'x=y', '-first', '한글', 'a'*129]:
            with self.subTest(value=value):
                result = self.run_deploy('production', SHA, DEPLOY_RUN_ID=value)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.trace.read_text(), '')
                self.assert_preserved()

    def test_probe_observes_under_lock_without_runtime_mutation(self):
        result = self.run_deploy('production', SHA)
        self.assertEqual(result.returncode,0,result.stderr)
        before = self.file_hashes()
        self.trace.write_text('')
        result = self.run_deploy('--probe', 'production', SHA, SHA)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(before,self.file_hashes())
        self.assertNotIn('with-decryption',self.trace.read_text())
        self.assertNotIn('git checkout',self.trace.read_text())
        self.assertNotIn('docker compose',self.trace.read_text())
        self.assertNotEqual(self.run_deploy('--probe','production',SHA,SHA,OBSERVATION_FAIL='1').returncode,0)


if __name__ == "__main__":
    unittest.main()
