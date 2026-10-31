import json
import os
from pathlib import Path
import shutil
import socket
import ssl
import subprocess
import tempfile
import threading
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
HOST = 'original.ap-northeast-2.rds.amazonaws.com'


@unittest.skipUnless(shutil.which('openssl') and shutil.which('docker'), 'OpenSSL and Compose required')
class RdsTlsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        def openssl(*args):
            subprocess.run(['openssl', *args], cwd=cls.root, check=True, capture_output=True)
        (cls.root / 'ca.conf').write_text('[req]\ndistinguished_name=dn\n[dn]\n[v3_ca]\nbasicConstraints=critical,CA:true\nkeyUsage=critical,keyCertSign,cRLSign\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid:always\n')
        for name in ('ca', 'wrong'):
            openssl('req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', name+'.key', '-out', name+'.pem', '-days', '2', '-subj', '/CN='+name, '-config', str(cls.root / 'ca.conf'), '-extensions', 'v3_ca')
        openssl('req', '-newkey', 'rsa:2048', '-nodes', '-keyout', 'server.key', '-out', 'server.csr', '-subj', '/CN='+HOST)
        (cls.root / 'extensions').write_text('subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth\nbasicConstraints=critical,CA:false\nkeyUsage=critical,digitalSignature,keyEncipherment\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid,issuer\n')
        openssl('x509', '-req', '-in', 'server.csr', '-CA', 'ca.pem', '-CAkey', 'ca.key', '-CAcreateserial', '-out', 'server.pem', '-days', '2', '-extfile', 'extensions')

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def compose(self, **overrides):
        env = dict(os.environ)
        env.pop("DB_USERNAME", None)
        env.pop("DB_PASSWORD", None)
        env.update({name: 'fixture' for name in ('CRYPTO_ENCRYPTION_KEY','OPERATOR_EMAIL','OPERATOR_PASSWORD','ECR_REGISTRY','BACKEND_BLUE_TAG','BACKEND_GREEN_TAG','IMAGE_TAG','BACKEND_MAX_RAM_PERCENTAGE','SITE_ALIASES','DB_SHARED_PASSWORD','DB_TEST_PASSWORD','DB_LIVE_PASSWORD')})
        env.update(DB_HOST=HOST, DB_PORT='5432', DB_NAME='meterengine_app', DB_SHARED_USERNAME='meterengine_shared', DB_TEST_USERNAME='meterengine_test', DB_LIVE_USERNAME='meterengine_live', BACKEND_MEM_LIMIT='768m', SITE_DOMAIN='staging.meterengine.com', DB_SSL_ROOT_CERT='/etc/meterengine/rds-ca.pem', METERENGINE_CUTOVER_MODE='read_only')
        env['METERENGINE_DEPLOYMENT_ENVIRONMENT'] = 'staging'
        for name, value in overrides.items():
            if value is None: env.pop(name, None)
            else: env[name] = value
        return subprocess.run(['docker','compose','-p','meterengine-staging','-f',str(DEPLOY / 'compose.prod.yml'),'-f',str(DEPLOY / 'compose.runtime.yml'),'config','--format','json'], env=env, text=True, capture_output=True)

    def test_runtime_never_contains_migration_or_master_credentials(self):
        result = self.compose(DB_USERNAME='FORBIDDEN_MASTER_FIXTURE', DB_PASSWORD='FORBIDDEN_MASTER_FIXTURE')
        self.assertEqual(result.returncode, 0, result.stderr)
        config = json.loads(result.stdout)
        for name in ('backend-blue','backend-green'):
            runtime = config['services'][name]['environment']
            self.assertNotIn('SPRING_DATASOURCE_USERNAME', runtime)
            self.assertNotIn('SPRING_DATASOURCE_PASSWORD', runtime)
            self.assertNotIn('SPRING_DATASOURCE_URL', runtime)
            self.assertNotIn('FORBIDDEN_MASTER_FIXTURE', json.dumps(runtime))
            self.assertFalse(any('MIGRATION' in key or key in ('DB_USERNAME','DB_PASSWORD') for key in runtime))
            self.assertEqual(runtime['DB_SHARED_USERNAME'], 'meterengine_shared')
        self.assertEqual(config['volumes']['caddy-data']['name'], 'meterengine-staging_caddy-data')

    def test_runtime_compose_succeeds_without_either_legacy_credential(self):
        result = self.compose(DB_USERNAME=None, DB_PASSWORD=None)
        self.assertEqual(result.returncode, 0, result.stderr)
        config = json.loads(result.stdout)
        for name in ('backend-blue', 'backend-green'):
            runtime = config['services'][name]['environment']
            self.assertEqual(runtime['DB_SHARED_USERNAME'], 'meterengine_shared')
            self.assertNotIn('SPRING_DATASOURCE_USERNAME', runtime)
            self.assertNotIn('SPRING_DATASOURCE_PASSWORD', runtime)

    def test_verify_full_and_ca_are_required(self):
        result = self.compose()
        self.assertEqual(result.returncode, 0, result.stderr)
        config = json.loads(result.stdout)
        for name in ('backend-blue','backend-green'):
            runtime = config['services'][name]
            self.assertEqual(runtime['environment']['DB_SSL_ROOT_CERT'], '/etc/meterengine/rds-ca.pem')
            self.assertTrue(any(mount['target'] == '/etc/meterengine/rds-ca.pem' and mount['read_only'] for mount in runtime['volumes']))
        self.assertIn('sslmode=verify-full', config['services']['postgres-exporter']['environment']['DATA_SOURCE_URI'])
        for key in ('DB_SSL_ROOT_CERT','DB_SHARED_PASSWORD','DB_TEST_PASSWORD','DB_LIVE_PASSWORD','METERENGINE_CUTOVER_MODE','METERENGINE_DEPLOYMENT_ENVIRONMENT'):
            with self.subTest(missing=key):
                self.assertNotEqual(self.compose(**{key: None}).returncode, 0)

    def test_wrong_ca_and_hostname_fail(self):
        listener = socket.socket()
        listener.bind(('127.0.0.1', 0)); listener.listen()
        port = listener.getsockname()[1]
        server = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server.load_cert_chain(str(self.root / 'server.pem'), str(self.root / 'server.key'))
        def serve():
            for _ in range(3):
                conn, _ = listener.accept()
                try:
                    conn.recv(8)
                    conn.sendall(b'S')
                    with server.wrap_socket(conn, server_side=True) as tls: tls.recv(1)
                except ssl.SSLError: conn.close()
            listener.close()
        worker = threading.Thread(target=serve, daemon=True); worker.start()
        def connect(ca, hostname):
            return subprocess.run(['bash',str(DEPLOY / 'rds-ca.sh'),'verify',hostname,str(port),str(self.root / ca)], capture_output=True, text=True, timeout=10)
        self.assertEqual(connect('ca.pem', 'localhost').returncode, 0)
        self.assertNotEqual(connect('wrong.pem', 'localhost').returncode, 0)
        self.assertNotEqual(connect('ca.pem', '127.0.0.1').returncode, 0)
        worker.join(5); self.assertFalse(worker.is_alive())

    def test_ca_install_is_atomic_validated_and_world_readable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); binary = root / 'bin'; binary.mkdir()
            curl = binary / 'curl'
            curl.write_text('''#!/usr/bin/env python3
import os, shutil, sys
args=sys.argv[1:]
assert args[-1]=='https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem'
assert '--proto' in args and args[args.index('--proto')+1]=='=https'
shutil.copyfile(os.environ['CA_FIXTURE'], args[args.index('--output')+1])
'''); curl.chmod(0o755)
            destination = root / 'rds-ca.pem'; destination.write_text('preserve')
            env = dict(os.environ, PATH=str(binary)+':'+os.environ['PATH'])
            invalid = root / 'invalid.pem'; invalid.write_text('not a certificate')
            for source, success in ((invalid, False), (self.root / 'ca.pem', True)):
                result = subprocess.run(['bash',str(DEPLOY / 'rds-ca.sh'),'install',str(destination)], env=dict(env, CA_FIXTURE=str(source)), text=True, capture_output=True)
                self.assertEqual(result.returncode == 0, success, result.stderr)
                self.assertEqual(destination.read_bytes(), source.read_bytes() if success else b'preserve')
                self.assertEqual([path.name for path in root.glob('.rds-ca.*')], [])
            self.assertEqual(destination.stat().st_mode & 0o777, 0o644)


if __name__ == '__main__':
    unittest.main()
