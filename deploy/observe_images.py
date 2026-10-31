#!/usr/bin/env python3
"""Observe running image IDs and registry digests without changing Docker state."""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True)


def observe(target, backend_service, backend_digest, frontend_digest):
    if backend_service not in ('backend-blue', 'backend-green'):
        raise ValueError('invalid backend service')
    registry = f"{target['aws_account_id']}.dkr.ecr.{target['aws_region']}.amazonaws.com"
    result = {}
    for service, repository, expected, prefix in ((backend_service, 'meterengine-backend', backend_digest, 'BACKEND'),
                                                   ('frontend', 'meterengine-frontend', frontend_digest, 'FRONTEND')):
        if not re.fullmatch(r'sha256:[0-9a-f]{64}', expected):
            raise ValueError('invalid expected digest')
        ids = docker('ps', '-q', '--filter', 'label=com.docker.compose.project=' + target['compose_project'],
                     '--filter', 'label=com.docker.compose.service=' + service).split()
        if len(ids) != 1:
            raise ValueError('expected exactly one running ' + service)
        containers = json.loads(docker('container', 'inspect', ids[0]))
        if len(containers) != 1:
            raise ValueError('invalid container inspection')
        container = containers[0]
        labels = container['Config']['Labels']
        if not container['State']['Running'] or labels.get('com.docker.compose.project') != target['compose_project'] or labels.get('com.docker.compose.service') != service:
            raise ValueError('container identity changed')
        image_id = container['Image']
        if not re.fullmatch(r'sha256:[0-9a-f]{64}', image_id):
            raise ValueError('invalid container image ID')
        images = json.loads(docker('image', 'inspect', image_id))
        if len(images) != 1:
            raise ValueError('invalid image inspection')
        image = images[0]
        if image['Id'] != image_id or image['Os'] != 'linux' or image['Architecture'] != 'arm64':
            raise ValueError('image ID or platform mismatch')
        if registry + '/' + repository + '@' + expected not in (image.get('RepoDigests') or []):
            raise ValueError('running RepoDigest mismatch: ' + service)
        result[prefix + '_IMAGE_ID'] = image_id
        result[prefix + '_IMAGE_DIGEST'] = expected
    return result


def main():
    from environment import load_target
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('environment', choices=['staging', 'production'])
    parser.add_argument('--backend-service', required=True)
    parser.add_argument('--backend-digest', required=True)
    parser.add_argument('--frontend-digest', required=True)
    args = parser.parse_args()
    try:
        target = load_target(args.environment, Path(__file__).resolve().parent / 'environments')
        for key, value in observe(target, args.backend_service, args.backend_digest, args.frontend_digest).items():
            print(key + '=' + shlex.quote(value))
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print('[deploy] image observation failed: ' + str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
