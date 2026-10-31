#!/usr/bin/env python3
"""Render a pinned bootstrap request or a locked deployment command, without API calls."""

import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import shlex
import sys

REPOSITORY = 'https://github.com/asm17-ms2/meterengine.git'
PRIVATE_PARENT = '/etc/meterengine'
PATHS = ('deploy/compose.runtime.yml', 'deploy/deploy.sh', 'deploy/environment.py', 'deploy/environments/staging.json',
         'deploy/install-host.sh', 'deploy/observe_images.py', 'deploy/rds-ca.sh', 'deploy/runtime-parameters.v1.json', 'deploy/runtime_parameters.py')


def pin(value, size):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{' + str(size) + '}', value):
        raise ValueError('invalid source or content pin')
    return value


def bootstrap_request(source_sha, source_tree, public_ref, installer_sha256, bundle_sha256, target):
    pin(source_sha, 40); pin(source_tree, 40); pin(installer_sha256, 64); pin(bundle_sha256, 64)
    if not public_ref or public_ref.startswith('-') or any(c.isspace() for c in public_ref):
        raise ValueError('invalid public ref')
    if not isinstance(target, dict) or not re.fullmatch(r'i-[0-9a-f]{8,17}', target.get('instance_id') or '') or any(value is None for value in target.values()):
        raise ValueError('unconfigured target')
    data = (json.dumps(target, sort_keys=True, separators=(',', ':')) + '\n').encode()
    q = shlex.quote
    command = '\n'.join([
        'set -eu', 'umask 077',
        'export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null GIT_TERMINAL_PROMPT=0',
        'unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_ALTERNATE_OBJECT_DIRECTORIES GIT_CONFIG_COUNT GIT_CONFIG_PARAMETERS',
        'work=$(mktemp -d ' + q(PRIVATE_PARENT + '/.install-XXXXXX') + ')',
        # Keep the private source and pins available for failed-bootstrap diagnosis.
        'printf %s ' + q(base64.b64encode(data).decode()) + ' | base64 -d > "$work/target.json"',
        'printf "%s  %s\\n" ' + q(hashlib.sha256(data).hexdigest()) + ' "$work/target.json" | sha256sum -c -',
        'git init --bare -q "$work/source.git" 8>&- 9>&-',
        'git -C "$work/source.git" -c credential.helper= -c core.askPass= fetch --no-tags --depth=1 ' + q(REPOSITORY) + ' ' + q(public_ref) + ' 8>&- 9>&-',
        '[ "$(git -C "$work/source.git" rev-parse FETCH_HEAD 8>&- 9>&-)" = ' + q(source_sha) + ' ]',
        '[ "$(git -C "$work/source.git" rev-parse \'FETCH_HEAD^{tree}\' 8>&- 9>&-)" = ' + q(source_tree) + ' ]',
        'git -C "$work/source.git" show ' + q(source_sha + ':deploy/install-host.sh') + ' 8>&- 9>&- > "$work/install-host.sh"',
        'printf "%s  %s\\n" ' + q(installer_sha256) + ' "$work/install-host.sh" | sha256sum -c -',
        'git -C "$work/source.git" -c tar.umask=0022 archive --format=tar ' + q(source_sha) + ' ' + ' '.join(map(q, PATHS)) + ' 8>&- 9>&- > "$work/bundle.tar"',
        'printf "%s  %s\\n" ' + q(bundle_sha256) + ' "$work/bundle.tar" | sha256sum -c -',
        'bash "$work/install-host.sh" staging --source-sha ' + q(source_sha) + ' --source-tree ' + q(source_tree)
        + ' --bundle "$work/bundle.tar" --bundle-sha256 ' + q(bundle_sha256) + ' --target-file "$work/target.json"',
    ]) + '\n'
    request = {'DocumentName':'AWS-RunShellScript', 'InstanceIds':[target['instance_id']], 'Parameters':{'commands':[command]}}
    if len(json.dumps(request).encode()) >= 64*1024:
        raise ValueError('bootstrap request exceeds conservative 64KiB bound')
    return request


def deployment_command(image_sha, source_ref, run_id, backend_digest, frontend_digest):
    pin(image_sha,40); pin(source_ref,40)
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._:-]{0,127}',run_id):
        raise ValueError('invalid deployment run identifier')
    for value in (backend_digest, frontend_digest):
        if not re.fullmatch(r'sha256:[0-9a-f]{64}',value):
            raise ValueError('invalid image digest')
    q = shlex.quote
    return '\n'.join([
        'set -eu', 'exec 8>/etc/meterengine/deploy-command.lock', 'flock -n 8',
        'version=$(readlink -f /etc/meterengine/deployer/current)',
        '[ "$version" = /etc/meterengine/deployer/' + q(source_ref) + ' ]',
        'test -r "$version/installation.json"',
        'export DEPLOY_RUN_ID=' + q(run_id), 'export BACKEND_IMAGE_DIGEST=' + q(backend_digest), 'export FRONTEND_IMAGE_DIGEST=' + q(frontend_digest),
        '"$version/deploy.sh" staging ' + q(image_sha) + ' ' + q(source_ref),
        'curl -fsS --max-time 3 http://127.0.0.1:3000/health | grep -qx ok',
        'color=$(sed -n "s/^LIVE_COLOR=//p" /etc/meterengine/staging-state)',
        'case "$color" in blue|green) ;; *) exit 2 ;; esac',
        '"$version/deploy.sh" --probe staging ' + q(image_sha) + ' ' + q(source_ref),
    ]) + '\n'


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    sub = parser.add_subparsers(dest='command',required=True)
    bootstrap = sub.add_parser('bootstrap',allow_abbrev=False)
    for name in ('source-sha','source-tree','public-ref','installer-sha256','bundle-sha256','target-file'):
        bootstrap.add_argument('--'+name,required=True)
    deploy = sub.add_parser('deploy',allow_abbrev=False)
    for name in ('image-sha','source-ref','run-id','backend-digest','frontend-digest'):
        deploy.add_argument('--'+name,required=True)
    args = parser.parse_args()
    try:
        if args.command == 'bootstrap':
            print(json.dumps(bootstrap_request(args.source_sha,args.source_tree,args.public_ref,args.installer_sha256,args.bundle_sha256,json.loads(Path(args.target_file).read_bytes())),indent=2))
        else:
            print(deployment_command(args.image_sha,args.source_ref,args.run_id,args.backend_digest,args.frontend_digest),end='')
    except (OSError, ValueError, TypeError) as error:
        print(str(error),file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
