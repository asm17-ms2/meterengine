#!/usr/bin/env bash
set -euo pipefail
set +x
exec python3 - "$@" <<'PY'
import argparse
import fcntl
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import sys
import tarfile
import tempfile

ROOT = Path('/etc/meterengine')
REPO = Path('/opt/meterengine')
FILES = ('compose.runtime.yml', 'deploy.sh', 'environment.py', 'environments/staging.json',
         'install-host.sh', 'observe_images.py', 'rds-ca.sh', 'runtime-parameters.v1.json', 'runtime_parameters.py')
MODES = {name: 0o755 if name.endswith('.sh') else 0o644 for name in FILES}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def run(*args):
    return subprocess.check_output(args, text=True)


def interrupted(signum, frame):
    raise RuntimeError('installation interrupted')


def link_owned(source, destination, created):
    # The signal handler must not split a successful link from its ownership record.
    previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM, signal.SIGINT})
    try:
        os.link(source, destination)
        created.append(destination)
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous)


def install():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('environment', choices=['staging'])
    for name in ('source-sha', 'source-tree', 'bundle', 'bundle-sha256', 'target-file'):
        parser.add_argument('--' + name, required=True)
    flags = [arg.split('=', 1)[0] for arg in sys.argv[1:] if arg.startswith('--')]
    if len(flags) != len(set(flags)):
        raise ValueError('duplicate arguments')
    args = parser.parse_args()
    for value, size in ((args.source_sha, 40), (args.source_tree, 40), (args.bundle_sha256, 64)):
        if not re.fullmatch('[0-9a-f]{' + str(size) + '}', value):
            raise ValueError('invalid external pin')
    for path in (Path(args.bundle), Path(args.target_file)):
        if path.is_symlink() or not path.is_file():
            raise ValueError('input must be a regular file')
    data = Path(args.bundle).read_bytes()
    if digest(data) != args.bundle_sha256:
        raise ValueError('bundle hash mismatch')
    contents = {}
    seen = set()
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:') as archive:
        if archive.pax_headers.get('comment') != args.source_sha:
            raise ValueError('bundle source commit mismatch')
        for member in archive.getmembers():
            name = member.name.rstrip('/')
            if name in seen:
                raise ValueError('duplicate bundle member')
            seen.add(name)
            if member.isdir() and name in ('deploy', 'deploy/environments') and member.mode == 0o755:
                continue
            if not name.startswith('deploy/') or name[7:] not in MODES or not member.isreg():
                raise ValueError('bundle member is not allowlisted regular file')
            relative = name[7:]
            if member.mode != MODES[relative]:
                raise ValueError('bundle mode mismatch')
            contents[relative] = archive.extractfile(member).read()
    if set(contents) != set(FILES):
        raise ValueError('incomplete bundle')
    source_git = Path(args.bundle).resolve().parent / 'source.git'
    if source_git.is_symlink() or not source_git.is_dir() or any(p.is_symlink() for p in source_git.rglob('*')) or any(
            (source_git / name).exists() for name in ('objects/info/alternates', 'commondir')):
        raise ValueError('private pinned source.git is missing')
    git = ('git', '--git-dir=' + str(source_git))
    if run(*git, 'rev-parse', '--is-bare-repository').strip() != 'true':
        raise ValueError('source.git must be private bare repository')
    if run(*git, 'rev-parse', args.source_sha + '^{commit}').strip() != args.source_sha or run(*git, 'rev-parse', args.source_sha + '^{tree}').strip() != args.source_tree:
        raise ValueError('source commit or tree mismatch')
    canonical = subprocess.check_output((*git, '-c', 'tar.umask=0022', 'archive', '--format=tar', args.source_sha, *['deploy/' + name for name in FILES]))
    if canonical != data:
        raise ValueError('bundle differs from pinned Git source')
    target = json.loads(Path(args.target_file).read_bytes())
    if target != json.loads(contents['environments/staging.json']):
        raise ValueError('committed target mismatch')
    # Extraction is private and never follows archive paths or links.
    with tempfile.TemporaryDirectory(prefix='meterengine-install-') as private:
        staged = Path(private)
        (staged / 'environments').mkdir(mode=0o755)
        for name, content in contents.items():
            path = staged / name
            path.write_bytes(content)
            path.chmod(MODES[name])
        spec = importlib.util.spec_from_file_location('install_environment', staged / 'environment.py')
        helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(helper)
        helper.load_target('staging', staged / 'environments')
        preflight = (sys.executable, str(staged / 'environment.py'), 'preflight', '--environment', 'staging')
        run(*preflight)
        if run('git', '-C', str(REPO), 'remote', 'get-url', 'origin').strip() != 'https://github.com/asm17-ms2/meterengine.git':
            raise ValueError('default clone origin mismatch')
        if not ROOT.is_dir() or ROOT.is_symlink():
            raise ValueError('host configuration directory is missing or unsafe')
        lock = ROOT / 'deploy-command.lock'
        try:
            inherited = os.fstat(8)
            same = (inherited.st_dev, inherited.st_ino) == (lock.stat().st_dev, lock.stat().st_ino)
        except OSError:
            same = False
        if not same:
            fd = os.open(lock, os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW, 0o600)
            if fd != 8:
                os.dup2(fd, 8)
                os.close(fd)
        fcntl.flock(8, fcntl.LOCK_EX | fcntl.LOCK_NB)
        run(*preflight)
        publish(staged, contents, args, target)


def publish(staged, contents, args, target):
    versions = ROOT / 'deployer'
    if versions.is_symlink():
        raise ValueError('unsafe version directory')
    versions.mkdir(mode=0o755, exist_ok=True)
    if versions.stat().st_mode & 0o7777 != 0o755 or versions.stat().st_uid != 0:
        raise ValueError('unsafe version directory owner or mode')
    destination = versions / args.source_sha
    current = versions / 'current'
    ca = ROOT / 'rds-ca.pem'
    config = ROOT / 'deploy.conf'
    config_data = ('AWS_REGION=' + shlex.quote(target['aws_region']) + '\nENV_FILE=/etc/meterengine/staging.env\nSTATE_FILE=/etc/meterengine/staging-state\n').encode()
    for path in (ca, config):
        if path.is_symlink() or (path.exists() and (not path.is_file() or path.stat().st_mode & 0o7777 != 0o644 or path.stat().st_uid != 0)):
            raise ValueError('unsafe existing config or CA')
    if config.exists() and config.read_bytes() != config_data:
        raise ValueError('existing deploy.conf differs')
    candidate = Path(tempfile.mkdtemp(prefix='.candidate-', dir=versions))
    candidate.chmod(0o755)
    created = []
    committed = False
    pointer = versions / ('.current-' + candidate.name)
    try:
        for name, content in contents.items():
            path = candidate / name
            path.parent.mkdir(mode=0o755, exist_ok=True)
            path.write_bytes(content)
            path.chmod(MODES[name])
        if not ca.exists():
            run('bash', str(staged / 'rds-ca.sh'), 'install', str(candidate / '.ca.pem'))
            (candidate / '.ca.pem').chmod(0o644)
            run('bash', str(staged / 'rds-ca.sh'), 'verify', target['database_host'], '5432', str(candidate / '.ca.pem'))
            link_owned(candidate / '.ca.pem', ca, created)
            (candidate / '.ca.pem').unlink()
        else:
            run('bash', str(staged / 'rds-ca.sh'), 'verify', target['database_host'], '5432', str(ca))
        if not config.exists():
            path = candidate / '.deploy.conf'
            path.write_bytes(config_data)
            path.chmod(0o644)
            link_owned(path, config, created)
            path.unlink()
        receipt = {'source_sha':args.source_sha, 'source_tree':args.source_tree, 'bundle_sha256':args.bundle_sha256,
                   'target':target, 'files':{name:{'sha256':digest(content), 'mode':MODES[name]} for name,content in contents.items()},
                   'ca_sha256':digest(ca.read_bytes())}
        receipt_data = (json.dumps(receipt, sort_keys=True, indent=2) + '\n').encode()
        (candidate / 'installation.json').write_bytes(receipt_data)
        (candidate / 'installation.json').chmod(0o644)
        if destination.exists() or destination.is_symlink():
            expected = {**contents, 'installation.json':receipt_data}
            directories = [destination, *[p for p in destination.rglob('*') if p.is_dir()]]
            if {str(p.relative_to(destination)) for p in directories} != {'.', 'environments'} or any(
                    p.is_symlink() or p.stat().st_mode & 0o7777 != 0o755 or p.stat().st_uid != 0 for p in directories):
                raise ValueError('same source directory owner or mode differs')
            actual = {str(p.relative_to(destination)):p for p in destination.rglob('*') if not p.is_dir()}
            if destination.is_symlink() or set(actual) != set(expected):
                raise ValueError('same source installation differs')
            for name, content in expected.items():
                path = actual[name]
                if path.is_symlink() or not path.is_file() or path.read_bytes() != content or path.stat().st_mode & 0o7777 != MODES.get(name, 0o644) or path.stat().st_uid != 0:
                    raise ValueError('same source bytes or mode differs')
            import shutil
            shutil.rmtree(candidate)
        else:
            os.rename(candidate, destination)
            candidate = destination
        pointer.symlink_to(args.source_sha)
        # A complete receipt exists before this final commit point.
        previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM, signal.SIGINT})
        try:
            os.replace(pointer, current)
            committed = True
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, previous)
    except BaseException as error:
        if committed:
            return
        pointer.unlink(missing_ok=True)
        for path in reversed(created):
            path.unlink(missing_ok=True)
        if candidate.is_dir():
            (candidate / 'failure.json').write_text(json.dumps({'error':str(error)}) + '\n')
        raise


signal.signal(signal.SIGTERM, interrupted)
signal.signal(signal.SIGINT, interrupted)
try:
    if os.geteuid() != 0:
        raise ValueError('root required')
    install()
except (OSError, ValueError, KeyError, TypeError, RuntimeError, tarfile.TarError, subprocess.CalledProcessError) as error:
    print('[install] failed: ' + str(error), file=sys.stderr)
    sys.exit(2)
PY
