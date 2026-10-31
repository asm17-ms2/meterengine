import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest
import sys
import importlib.util
from unittest.mock import patch
import signal
import time


ROOT = Path(__file__).resolve().parents[2]
FILES = ['compose.runtime.yml', 'deploy.sh', 'environment.py', 'environments/staging.json',
         'install-host.sh', 'observe_images.py', 'rds-ca.sh', 'runtime-parameters.v1.json', 'runtime_parameters.py']


@unittest.skipUnless(sys.platform == 'linux' and os.geteuid() == 0 and shutil.which('git'), 'Linux root fixture and Git required')
class HostInstallTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        if os.environ.get('DEPLOY_FIXTURE_ARTIFACTS'):
            destination = Path(os.environ['DEPLOY_FIXTURE_ARTIFACTS']) / self.id()
            self.addCleanup(lambda: shutil.copytree(self.root, destination, symlinks=True))
        self.etc = self.root / 'etc'
        self.etc.mkdir()
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.trace = self.root / 'trace'
        self.trace.touch()
        self.src = self.root / 'source'
        (self.src / 'deploy/environments').mkdir(parents=True)
        self.target = json.loads((ROOT / 'deploy/environments/staging.json').read_text())
        self.target.update(instance_id='i-0123456789abcdef0', database_host='db.fixture.invalid')
        self.target_file = self.root / 'target.json'
        self.target_file.write_text(json.dumps(self.target))
        self.installer = self.root / 'install-host.sh'
        if (ROOT / 'deploy/install-host.sh').exists():
            self.installer.write_text((ROOT / 'deploy/install-host.sh').read_text().replace('/etc/meterengine', str(self.etc)).replace('/opt/meterengine', str(self.repo)))
        for name in FILES:
            path = self.src / 'deploy' / name
            source = ROOT / 'deploy' / name
            path.write_bytes(source.read_bytes() if source.exists() else b'not implemented\n')
            path.chmod(0o755 if name.endswith('.sh') else 0o644)
        (self.src / 'deploy/environments/staging.json').write_text(json.dumps(self.target))
        # Only network boundaries are replaced; filesystem, Git archive and locks stay real.
        loader = (ROOT / 'deploy/environment.py').read_text().split('\ndef validate_target')[0]
        (self.src / 'deploy/environment.py').write_text(loader+'\nimport os\nif __name__ == "__main__":\n load_target("staging",Path(__file__).parent/"environments")\n Path(os.environ["TRACE"]).open("a").write("preflight\\n")\n if os.environ.get("IDENTITY_FAIL"): sys.exit(2)\n')
        (self.src / 'deploy/rds-ca.sh').write_text('#!/bin/bash\nset -eu\necho "ca $1" >> "$TRACE"\nif [ "$1" = install ]; then printf "fixture CA\\n" > "$2"; fi\nif [ "${CA_HOLD:-}" = 1 ]; then touch "$TRACE.holding"; while [ ! -f "$TRACE.release" ]; do sleep .02; done; fi\n[ -z "${CA_FAIL:-}" ]\n')
        self.git('init', '-q', str(self.src))
        self.git('-C', str(self.src), 'add', '.')
        self.git('-C', str(self.src), '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture')
        self.sha = self.git('-C', str(self.src), 'rev-parse', 'HEAD').stdout.strip()
        self.tree = self.git('-C', str(self.src), 'rev-parse', 'HEAD^{tree}').stdout.strip()
        self.git('clone', '--bare', '-q', str(self.src), str(self.root / 'source.git'))
        self.bundle = self.root / 'bundle.tar'
        self.bundle.write_bytes(subprocess.check_output(['git', '-C', str(self.src), '-c', 'tar.umask=0022', 'archive', '--format=tar', self.sha, *['deploy/' + n for n in FILES]]))
        self.git('init', '-q', str(self.repo))
        self.git('-C', str(self.repo), 'remote', 'add', 'origin', 'https://github.com/asm17-ms2/meterengine.git')
        self.env = dict(os.environ, TRACE=str(self.trace), PYTHONDONTWRITEBYTECODE='1')

    def git(self, *args):
        return subprocess.run(['git', *args], text=True, capture_output=True, check=True)

    def run_install(self, **env):
        self.assertTrue(self.installer.exists(), 'trusted installer is not implemented')
        return subprocess.run(['bash', str(self.installer), 'staging', '--source-sha', self.sha,
                               '--source-tree', self.tree, '--bundle', str(self.bundle),
                               '--bundle-sha256', hashlib.sha256(self.bundle.read_bytes()).hexdigest(),
                               '--target-file', str(self.target_file)], env=dict(self.env, **env), capture_output=True, text=True, timeout=10)

    def install_with_boundary_signal(self, boundary, signum):
        runner = self.root / 'signal-boundary.py'
        trace = self.root / 'boundary.jsonl'
        previous_events = len(trace.read_text().splitlines()) if trace.exists() else 0
        runner.write_text('''import json,os,signal,sys
from pathlib import Path
sys.argv=sys.argv[1:]
original_link,original_replace=os.link,os.replace
def after_call(operation,source,destination,*args,**kwargs):
 result=operation(source,destination,*args,**kwargs)
 path=Path(destination)
 boundary={'rds-ca.pem':'ca-link','deploy.conf':'config-link','current':'current-replace'}.get(path.name)
 if boundary==os.environ['SIGNAL_BOUNDARY']:
  with open(os.environ['SIGNAL_TRACE'],'a') as trace:
   trace.write(json.dumps({'boundary':boundary,'signal':int(os.environ['BOUNDARY_SIGNUM']),'actual_destination':str(path),'syscall_completed':True})+'\\n')
  os.kill(os.getpid(),int(os.environ['BOUNDARY_SIGNUM']))
 return result
os.link=lambda source,destination,*args,**kwargs:after_call(original_link,source,destination,*args,**kwargs)
os.replace=lambda source,destination,*args,**kwargs:after_call(original_replace,source,destination,*args,**kwargs)
body=Path(sys.argv[0]).read_text().split("<<'PY'\\n",1)[1].rsplit('\\nPY',1)[0]
exec(compile(body,sys.argv[0],'exec'))
''')
        args = [sys.executable, '-B', str(runner), str(self.installer), 'staging', '--source-sha', self.sha,
                '--source-tree', self.tree, '--bundle', str(self.bundle),
                '--bundle-sha256', hashlib.sha256(self.bundle.read_bytes()).hexdigest(), '--target-file', str(self.target_file)]
        result = subprocess.run(args, env=dict(self.env, SIGNAL_BOUNDARY=boundary, BOUNDARY_SIGNUM=str(int(signum)),
                                              SIGNAL_TRACE=str(self.root / 'boundary.jsonl')), capture_output=True, text=True, timeout=10)
        (self.root / f'boundary-result-{previous_events+1}.json').write_text(json.dumps({'argv':args,'exit':result.returncode,'stdout':result.stdout,'stderr':result.stderr},indent=2)+'\n')
        self.assertTrue(trace.exists(), result.stdout + result.stderr)
        events = trace.read_text().splitlines()
        self.assertEqual(len(events),previous_events+1,result.stdout+result.stderr)
        observation = json.loads(events[previous_events])
        self.assertEqual(observation['boundary'],boundary)
        self.assertEqual(observation['signal'],int(signum))
        self.assertTrue(observation['syscall_completed'])
        return result

    def completed_install_survives_boundary_signal(self, signum):
        result = self.install_with_boundary_signal('current-replace',signum)
        current = self.etc / 'deployer/current'
        self.assertEqual(current.resolve(),self.etc/'deployer'/self.sha)
        self.assertTrue((self.etc/'rds-ca.pem').is_file(),result.stdout+result.stderr)
        self.assertEqual((self.etc/'rds-ca.pem').read_bytes(),b'fixture CA\n')
        self.assertTrue((self.etc/'deploy.conf').is_file())
        self.assertTrue((current/'installation.json').is_file())
        self.assertFalse((current/'failure.json').exists())
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        before = {str(path.relative_to(self.etc)):(path.read_bytes(),path.stat().st_mode & 0o7777,path.stat().st_mtime_ns)
                  for path in self.etc.rglob('*') if path.is_file()}
        retry = self.run_install()
        self.assertEqual(retry.returncode,0,retry.stdout+retry.stderr)
        retry_signal = self.install_with_boundary_signal('current-replace',signum)
        self.assertEqual(retry_signal.returncode,0,retry_signal.stdout+retry_signal.stderr)
        self.assertEqual(before,{str(path.relative_to(self.etc)):(path.read_bytes(),path.stat().st_mode & 0o7777,path.stat().st_mtime_ns)
                                 for path in self.etc.rglob('*') if path.is_file()})

    def uncommitted_install_cleans_boundary_link(self, boundary, signum):
        old = self.etc/'deployer/old'
        old.mkdir(parents=True)
        (old/'installation.json').write_text('old completed receipt\n')
        (self.etc/'deployer/current').symlink_to('old')
        for name in ['staging.env','staging-state']:
            (self.etc/name).write_text('preserve runtime\n')
        if boundary == 'config-link':
            (self.etc/'rds-ca.pem').write_text('existing CA\n')
            (self.etc/'rds-ca.pem').chmod(0o644)
        before = {str(path.relative_to(self.etc)):(path.read_bytes(),path.stat().st_mode & 0o7777,path.stat().st_mtime_ns)
                  for path in self.etc.rglob('*') if path.is_file()}
        result = self.install_with_boundary_signal(boundary,signum)
        self.assertNotEqual(result.returncode,0)
        self.assertEqual((self.etc/'deployer/current').resolve(),old)
        self.assertFalse((self.etc/'deploy.conf').exists())
        if boundary == 'ca-link': self.assertFalse((self.etc/'rds-ca.pem').exists())
        for relative,expected in before.items():
            path = self.etc/relative
            self.assertEqual((path.read_bytes(),path.stat().st_mode & 0o7777,path.stat().st_mtime_ns),expected)
        self.assertTrue(list((self.etc/'deployer').glob('.candidate-*/failure.json')))

    def test_sigterm_after_current_replace_preserves_completed_install_and_reinstall(self):
        self.completed_install_survives_boundary_signal(signal.SIGTERM)

    def test_sigint_after_current_replace_preserves_completed_install_and_reinstall(self):
        self.completed_install_survives_boundary_signal(signal.SIGINT)

    def test_sigterm_after_ca_link_cleans_only_uncommitted_install(self):
        self.uncommitted_install_cleans_boundary_link('ca-link',signal.SIGTERM)

    def test_sigint_after_ca_link_cleans_only_uncommitted_install(self):
        self.uncommitted_install_cleans_boundary_link('ca-link',signal.SIGINT)

    def test_sigterm_after_config_link_cleans_only_uncommitted_install(self):
        self.uncommitted_install_cleans_boundary_link('config-link',signal.SIGTERM)

    def test_sigint_after_config_link_cleans_only_uncommitted_install(self):
        self.uncommitted_install_cleans_boundary_link('config-link',signal.SIGINT)

    def test_first_install_and_same_source_reinstall_are_atomic(self):
        result = self.run_install()
        self.assertEqual(result.returncode, 0, result.stderr)
        current = self.etc / 'deployer/current'
        self.assertEqual(current.resolve(), self.etc / 'deployer' / self.sha)
        receipt = json.loads((current / 'installation.json').read_text())
        self.assertEqual(receipt['source_sha'], self.sha)
        self.assertEqual(receipt['source_tree'], self.tree)
        self.assertEqual(receipt['target'], self.target)
        for name in FILES:
            path = current / name
            self.assertEqual(path.read_bytes(), (self.src / 'deploy' / name).read_bytes())
            self.assertEqual(path.stat().st_mode & 0o7777, 0o755 if name.endswith('.sh') else 0o644)
        before = {str(p): (p.read_bytes(), p.stat().st_mtime_ns) for p in current.resolve().rglob('*') if p.is_file()}
        result = self.run_install()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(before, {str(p): (p.read_bytes(), p.stat().st_mtime_ns) for p in current.resolve().rglob('*') if p.is_file()})
        (current / 'deploy.sh').write_text('tampered')
        self.assertNotEqual(self.run_install().returncode, 0)
        self.assertEqual((current / 'deploy.sh').read_text(), 'tampered')

    def test_untrusted_bundle_is_rejected_before_target_probe(self):
        original = self.bundle.read_bytes()
        for name, kind, mode in [('../escape', tarfile.REGTYPE, 0o644), ('/absolute', tarfile.REGTYPE, 0o644),
                                 ('deploy/unknown', tarfile.REGTYPE, 0o644), ('deploy/deploy.sh', tarfile.SYMTYPE, 0o755),
                                 ('deploy/deploy.sh', tarfile.CHRTYPE, 0o755), ('deploy/deploy.sh', tarfile.REGTYPE, 0o4755),
                                 ('deploy/deploy.sh', tarfile.REGTYPE, 0o644)]:
            with self.subTest(name=name, kind=kind, mode=mode):
                with tarfile.open(self.bundle, 'w', format=tarfile.PAX_FORMAT, pax_headers={'comment': self.sha}) as archive:
                    info = tarfile.TarInfo(name); info.type = kind; info.mode = mode; info.size = 1 if kind == tarfile.REGTYPE else 0
                    archive.addfile(info, io.BytesIO(b'x') if info.size else None)
                result = self.run_install()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.trace.read_text(), '')
                self.assertEqual(list(self.etc.iterdir()), [])
        self.bundle.write_bytes(original)
        self.sha = 'f' * 40
        self.assertNotEqual(self.run_install().returncode, 0)
        self.assertEqual(self.trace.read_text(), '')

    def test_identity_mismatch_preserves_install_and_runtime_files(self):
        for name in ['prod.env', 'deploy-state', 'deploy.conf', 'rds-ca.pem']:
            (self.etc / name).write_text('preserve\n')
        result = self.run_install(IDENTITY_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual({p.name:p.read_text() for p in self.etc.iterdir()}, {n:'preserve\n' for n in ['prod.env','deploy-state','deploy.conf','rds-ca.pem']})
        self.assertNotIn('ca ', self.trace.read_text())

    def test_target_override_is_rejected_before_preflight(self):
        self.target_file.write_text(json.dumps(dict(self.target, database_host='wrong.invalid')))
        self.assertNotEqual(self.run_install().returncode, 0)
        self.assertEqual(self.trace.read_text(), '')
        self.assertEqual(list(self.etc.iterdir()), [])

    def test_wrong_source_tree_is_rejected_before_target_probe(self):
        self.tree='f'*40
        self.assertNotEqual(self.run_install().returncode,0)
        self.assertEqual(self.trace.read_text(),'')
        self.assertEqual(list(self.etc.iterdir()),[])

    def test_missing_wrong_or_symlinked_source_sidecar_cannot_probe_target(self):
        source_git=self.root/'source.git'
        saved=self.root/'saved.git'
        source_git.rename(saved)
        self.assertNotEqual(self.run_install().returncode,0)
        source_git.symlink_to(saved,target_is_directory=True)
        self.assertNotEqual(self.run_install().returncode,0)
        source_git.unlink()
        self.git('init','--bare','-q',str(source_git))
        self.assertNotEqual(self.run_install().returncode,0)
        shutil.rmtree(source_git)
        saved.rename(source_git)
        (source_git/'objects/external').symlink_to(self.root,target_is_directory=True)
        self.assertNotEqual(self.run_install().returncode,0)
        self.assertEqual(self.trace.read_text(),'')
        self.assertEqual(list(self.etc.iterdir()),[])

    def test_interrupted_install_preserves_committed_pointer(self):
        old = self.etc / 'deployer/old'
        old.mkdir(parents=True)
        (self.etc / 'deployer/current').symlink_to('old')
        result = self.run_install(CA_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.etc / 'deployer/current').resolve(), old)
        self.assertFalse((self.etc / 'deploy.conf').exists())
        self.assertFalse((self.etc / 'rds-ca.pem').exists())
        self.assertTrue(list((self.etc / 'deployer').glob('.candidate-*/failure.json')))

    def test_duplicate_hardlink_and_wrong_hash_cannot_probe_target(self):
        original = self.bundle.read_bytes()
        for kind in (tarfile.LNKTYPE, tarfile.REGTYPE):
            with tarfile.open(fileobj=io.BytesIO(original)) as source, tarfile.open(self.bundle, 'w', format=tarfile.PAX_FORMAT, pax_headers={'comment':self.sha}) as archive:
                for member in source:
                    archive.addfile(member, source.extractfile(member) if member.isreg() else None)
                info = tarfile.TarInfo('deploy/deploy.sh'); info.type = kind; info.mode = 0o755
                archive.addfile(info)
            self.assertNotEqual(self.run_install().returncode, 0)
            self.assertEqual(self.trace.read_text(), '')
        self.bundle.write_bytes(original)
        command = ['bash', str(self.installer), 'staging', '--source-sha', self.sha, '--source-tree', self.tree,
                   '--bundle', str(self.bundle), '--bundle-sha256', 'f'*64, '--target-file', str(self.target_file)]
        self.assertNotEqual(subprocess.run(command,env=self.env,capture_output=True).returncode, 0)
        self.assertEqual(self.trace.read_text(), '')

    def test_install_cannot_pass_fd8_held_by_deploy(self):
        holder = subprocess.Popen(['bash','-ec','exec 8>"$1"; flock -n 8; touch "$2"; while [ ! -f "$3" ]; do sleep .02; done',
                                   'holder',str(self.etc/'deploy-command.lock'),str(self.root/'holding'),str(self.root/'release')])
        try:
            deadline=time.monotonic()+5
            while not (self.root/'holding').exists() and time.monotonic()<deadline: time.sleep(.01)
            self.assertTrue((self.root/'holding').exists())
            result=self.run_install()
            self.assertNotEqual(result.returncode,0)
            self.assertFalse((self.etc/'deployer').exists())
            self.assertEqual(self.trace.read_text(),'preflight\n')
        finally:
            (self.root/'release').touch(); holder.wait(timeout=5)

    def test_sigterm_before_publish_preserves_current_and_all_existing_bytes(self):
        old=self.etc/'deployer/old'; old.mkdir(parents=True)
        (self.etc/'deployer/current').symlink_to('old')
        command=['bash',str(self.installer),'staging','--source-sha',self.sha,'--source-tree',self.tree,
                 '--bundle',str(self.bundle),'--bundle-sha256',hashlib.sha256(self.bundle.read_bytes()).hexdigest(),'--target-file',str(self.target_file)]
        process=subprocess.Popen(command,env=dict(self.env,CA_HOLD='1'),stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        try:
            deadline=time.monotonic()+5
            while not Path(str(self.trace)+'.holding').exists() and time.monotonic()<deadline: time.sleep(.01)
            self.assertTrue(Path(str(self.trace)+'.holding').exists())
            process.send_signal(signal.SIGTERM)
            Path(str(self.trace)+'.release').touch()
            stdout,stderr=process.communicate(timeout=5)
            self.assertNotEqual(process.returncode,0,stdout+stderr)
            self.assertEqual((self.etc/'deployer/current').resolve(),old)
            self.assertFalse((self.etc/'rds-ca.pem').exists())
            self.assertTrue(list((self.etc/'deployer').glob('.candidate-*/failure.json')))
        finally:
            Path(str(self.trace)+'.release').touch()
            if process.poll() is None: process.kill(); process.communicate()

    def test_reinstall_rejects_changed_directory_modes(self):
        self.assertEqual(self.run_install().returncode,0)
        version=self.etc/'deployer'/self.sha
        (version/'environments').chmod(0o777)
        self.assertNotEqual(self.run_install().returncode,0)
        self.assertEqual((version/'environments').stat().st_mode & 0o777,0o777)


class InstallRequestTest(unittest.TestCase):
    def setUp(self):
        path = ROOT / 'deploy/install_host_request.py'
        self.assertTrue(path.exists(), 'pinned public bootstrap renderer is not implemented')
        spec = importlib.util.spec_from_file_location('request_renderer', path)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_default_clone_cannot_supply_installer(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            repo = root / 'published'
            (repo / 'deploy').mkdir(parents=True)
            installer = b'#!/bin/bash\necho approved-installer\n'
            for name in FILES:
                path = repo / 'deploy' / name
                path.parent.mkdir(exist_ok=True)
                path.write_bytes(installer if name == 'install-host.sh' else b'fixture\n')
                path.chmod(0o755 if name.endswith('.sh') else 0o644)
            git = lambda *args: subprocess.check_output(['git','-C',str(repo),*args],text=True).strip()
            git('init','-q'); git('add','.')
            git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture')
            sha, tree = git('rev-parse','HEAD'), git('rev-parse','HEAD^{tree}')
            bundle = subprocess.check_output(['git','-C',str(repo),'-c','tar.umask=0022','archive','--format=tar',sha,*['deploy/'+name for name in FILES]])
            target = {'instance_id':'i-0123456789abcdef0'}
            with patch.object(self.module, 'REPOSITORY', str(repo)), patch.object(self.module,'PRIVATE_PARENT', str(root)):
                request = self.module.bootstrap_request(sha,tree,sha,hashlib.sha256(installer).hexdigest(),hashlib.sha256(bundle).hexdigest(),target)
            self.assertLess(len(json.dumps(request).encode()), 64*1024)
            result = subprocess.run(['bash','-ec',request['Parameters']['commands'][0]],text=True,capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('approved-installer',result.stdout)
            with patch.object(self.module, 'REPOSITORY', str(repo)), patch.object(self.module,'PRIVATE_PARENT', str(root)):
                bad = self.module.bootstrap_request(sha,tree,sha,'f'*64,hashlib.sha256(bundle).hexdigest(),target)
            result = subprocess.run(['bash','-ec',bad['Parameters']['commands'][0]],text=True,capture_output=True)
            self.assertNotEqual(result.returncode,0)
            self.assertNotIn('approved-installer',result.stdout)


if __name__ == '__main__':
    unittest.main()
