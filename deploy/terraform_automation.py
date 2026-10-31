"""Keep Terraform approval artifacts private and bind them to one main dispatch."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import zipfile

import terraform_plan

TARGET_PATH = Path(__file__).parent / 'terraform' / 'automation-targets.v1.json'
REPO_ROOT = Path(__file__).resolve().parents[1]
BUCKET = 'meterengine-tfstate-928618307647'
ACCOUNT = '928618307647'
REGION = 'ap-northeast-2'
SECRET_KEYS = {'db-shared-password', 'db-test-password', 'db-live-password', 'crypto-encryption-key', 'operator-password'}
METADATA_FIELDS = {
    'schema_version', 'repository_id', 'repository_owner_id', 'aws_account_id', 'aws_region',
    'source_sha', 'target', 'state_key', 'state_exists', 'state_version_id', 'run_id', 'run_attempt',
    'terraform_version', 'lockfile_sha256', 'plan_key', 'plan_version_id', 'plan_sha256',
    'ownership_audit_key', 'ownership_audit_version_id', 'ownership_audit_sha256',
}
CONTEXT_FIELDS = {'context_key', 'context_version_id', 'context_sha256'}
STAGING_ADDRESSES = {
    'data.aws_caller_identity.current', 'data.aws_region.current',
    'module.service.aws_instance.app', 'module.service.aws_db_instance.app',
    'aws_security_group.app', 'aws_security_group.database', 'aws_iam_role.runtime',
    'aws_iam_instance_profile.runtime', 'aws_iam_role_policy.runtime',
    'aws_eip.app', 'aws_eip_association.app', 'aws_route53_record.app',
    'aws_vpc_security_group_ingress_rule.web', 'aws_vpc_security_group_ingress_rule.database',
    'aws_vpc_security_group_egress_rule.https', 'aws_vpc_security_group_egress_rule.database',
    'aws_iam_role.github_deploy', 'aws_iam_role_policy.github_deploy',
    'aws_iam_role.github_owner', 'aws_iam_role_policy.github_owner',
    'aws_ssm_parameter.migration_username', 'aws_ssm_parameter.migration_password',
}


class AutomationError(ValueError):
    pass


def require(condition, code='AUTOMATION_REJECTED'):
    if not condition:
        raise AutomationError(code)


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def _hash(value):
    return isinstance(value, str) and re.fullmatch(r'[a-f0-9]{64}', value) is not None


def _version(value):
    return isinstance(value, str) and bool(value) and value != 'null' and not any(ord(c) < 32 for c in value)


def load_target(name: str, path: Path = TARGET_PATH) -> dict:
    require(name in {'staging', 'production'}, 'TARGET_REJECTED')
    config = json.loads(path.read_text())
    require(config.get('schema_version') == 1, 'TARGET_SCHEMA_REJECTED')
    target = config['targets'][name]
    expected = {
        'name': name, 'root': f'deploy/terraform/{name}', 'state_key': f'deploy/{name}/terraform.tfstate',
        'aws_account_id': ACCOUNT, 'aws_region': REGION, 'bucket': BUCKET,
        'repository': 'asm17-ms2/meterengine', 'repository_id': '1313421667', 'repository_owner_id': '293532363',
        'terraform_version': '1.17.1', 'environment': f'{name}-infrastructure',
        'plan_role_arn': f'arn:aws:iam::{ACCOUNT}:role/meterengine-terraform-{name}-plan',
        'apply_role_arn': f'arn:aws:iam::{ACCOUNT}:role/meterengine-terraform-{name}-apply',
    }
    require(all(target.get(k) == v for k, v in expected.items()), 'TARGET_REJECTED')
    return target


def prefix(context):
    require(context.get('target') in {'staging', 'production'}, 'CONTEXT_REJECTED')
    for field in ['run_id', 'run_attempt']:
        require(isinstance(context.get(field), str) and re.fullmatch(r'[1-9][0-9]*', context[field]), 'CONTEXT_REJECTED')
    require(isinstance(context.get('source_sha'), str) and re.fullmatch(r'[a-f0-9]{40}', context['source_sha']), 'CONTEXT_REJECTED')
    return f"deploy/plans/{context['target']}/{context['run_id']}/{context['run_attempt']}/{context['source_sha']}"


def validate_context(context: dict, target: dict) -> None:
    require(set(context) == METADATA_FIELDS, 'CONTEXT_SCHEMA_REJECTED')
    expected = {'schema_version': 1, 'repository_id': target['repository_id'], 'repository_owner_id': target['repository_owner_id'],
                'aws_account_id': ACCOUNT, 'aws_region': REGION, 'target': target['name'], 'state_key': target['state_key'],
                'terraform_version': '1.17.1'}
    require(all(context.get(k) == v for k, v in expected.items()), 'CONTEXT_REJECTED')
    require(context['plan_key'] == prefix(context) + '/plan.tfplan', 'PLAN_KEY_REJECTED')
    require(_hash(context['plan_sha256']) and _hash(context['lockfile_sha256']) and _version(context['plan_version_id']), 'ARTIFACT_REJECTED')
    require(type(context['state_exists']) is bool, 'STATE_REJECTED')
    require(_version(context['state_version_id']) if context['state_exists'] else context['state_version_id'] is None, 'STATE_REJECTED')
    audit = [context[k] for k in ['ownership_audit_key', 'ownership_audit_version_id', 'ownership_audit_sha256']]
    if target['name'] == 'production' and not context['state_exists']:
        require(isinstance(audit[0], str) and re.fullmatch(r'deploy/ownership/production/[a-zA-Z0-9_./-]+\.json', audit[0]) and '..' not in audit[0], 'AUDIT_REQUIRED')
        require(_version(audit[1]) and _hash(audit[2]), 'AUDIT_REQUIRED')
    else:
        require(audit == [None, None, None], 'AUDIT_REJECTED')


def validate_github(env, context):
    target = load_target(context['target'])
    expected = {'GITHUB_REPOSITORY': target['repository'], 'GITHUB_REPOSITORY_ID': target['repository_id'],
                'GITHUB_REPOSITORY_OWNER_ID': target['repository_owner_id'], 'GITHUB_REF': 'refs/heads/main',
                'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_SHA': context['source_sha'],
                'GITHUB_RUN_ID': context['run_id'], 'GITHUB_RUN_ATTEMPT': context['run_attempt']}
    require(all(env.get(k) == v for k, v in expected.items()), 'GITHUB_CONTEXT_REJECTED')
    prefix(context)


def validate_approval(environment: dict, branch_policies: dict) -> None:
    rules = [rule for rule in environment.get('protection_rules', []) if rule.get('type') == 'required_reviewers']
    require(len(rules) == 1 and isinstance(rules[0].get('reviewers'), list) and bool(rules[0]['reviewers']), 'REVIEWER_REQUIRED')
    require(all(r.get('type') in {'User', 'Team'} and isinstance(r.get('reviewer', {}).get('id'), int) and r['reviewer']['id'] > 0 for r in rules[0]['reviewers']), 'REVIEWER_REQUIRED')
    require(environment.get('deployment_branch_policy') == {'protected_branches': False, 'custom_branch_policies': True}, 'BRANCH_POLICY_REJECTED')
    policies = branch_policies.get('branch_policies')
    require(branch_policies.get('total_count') == 1 and isinstance(policies, list) and len(policies) == 1 and
            policies[0].get('name') == 'main' and policies[0].get('type') == 'branch', 'BRANCH_POLICY_REJECTED')


def command_json(args):
    env = dict(os.environ, AWS_PAGER='', GH_PAGER='', TF_LOG='OFF')
    env.pop('TF_LOG_PATH', None)
    try:
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, check=False)
        if (result.returncode != 0 and args[:7] == ['aws', '--region', REGION, '--output', 'json', 's3api', 'get-bucket-policy-status']
                and re.match(rb'(?:aws: \[ERROR\]: )?An error occurred \(NoSuchBucketPolicy\) when calling the GetBucketPolicyStatus operation:', result.stderr.strip())):
            raise AutomationError('BUCKET_POLICY_ABSENT')
        require(result.returncode == 0, 'REMOTE_API_REJECTED')
        return json.loads(result.stdout)
    except AutomationError:
        raise
    except (OSError, ValueError, TypeError) as error:
        raise AutomationError('REMOTE_API_REJECTED') from None


def gh_api(path):
    return command_json(['gh', 'api', '-H', 'Accept: application/vnd.github+json', '-H', 'X-GitHub-Api-Version: 2022-11-28', path])


def aws_api(args):
    return command_json(['aws', '--region', REGION, '--output', 'json', *args])


def github_preflight(target, env, context, *, gh=gh_api):
    validate_github(env, context)
    base = f"repos/{target['repository']}/environments/{target['environment']}"
    environment = gh(base)
    require(environment.get('name') == target['environment'], 'ENVIRONMENT_REJECTED')
    policies, total, page = [], None, 1
    while total is None or len(policies) < total:
        require(page <= 100, 'BRANCH_PAGINATION_REJECTED')
        response = gh(f'{base}/deployment-branch-policies?per_page=100&page={page}')
        batch = response.get('branch_policies')
        require(isinstance(batch, list) and isinstance(response.get('total_count'), int), 'BRANCH_PAGINATION_REJECTED')
        if total is None:
            total = response['total_count']
        require(total == response['total_count'] and (batch or len(policies) == total), 'BRANCH_PAGINATION_REJECTED')
        policies.extend(batch)
        require(len(policies) <= total, 'BRANCH_PAGINATION_REJECTED')
        page += 1
    validate_approval(environment, {'total_count': total, 'branch_policies': policies})
    return {'github_preflight': 'passed', 'target': target['name']}


def private_directory(parent=None):
    path = Path(tempfile.mkdtemp(prefix='terraform-private-', dir=parent))
    path.chmod(0o700)
    return path


def private_write(path, data):
    path = Path(path)
    require(not path.is_symlink(), 'PRIVATE_PATH_REJECTED')
    require(stat.S_IMODE(path.parent.stat().st_mode) == 0o700, 'PRIVATE_DIRECTORY_REQUIRED')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        os.fchmod(stream.fileno(), 0o600)
        stream.write(data)


def _private_file(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), 'PRIVATE_PATH_REJECTED')
    require(stat.S_IMODE(path.parent.stat().st_mode) == 0o700, 'PRIVATE_DIRECTORY_REQUIRED')
    path.chmod(0o600)


def run_private(args, directory, *, env=None):
    directory = Path(directory)
    require(stat.S_IMODE(directory.stat().st_mode) == 0o700, 'PRIVATE_DIRECTORY_REQUIRED')
    clean = dict(os.environ if env is None else env, TF_LOG='OFF', TF_INPUT='0')
    clean.pop('TF_LOG_PATH', None)
    for key in list(clean):
        if key.startswith('TF_CLI_ARGS') or key in {'SHELLOPTS', 'BASHOPTS', 'BASH_ENV', 'ENV'}:
            clean.pop(key)
    stdout, stderr = directory / 'stdout.log', directory / 'stderr.log'
    private_write(stdout, b'')
    private_write(stderr, b'')
    with stdout.open('wb') as out, stderr.open('wb') as err:
        result = subprocess.run(args, env=clean, stdout=out, stderr=err, check=False)
    return result.returncode, stdout, stderr


def show_private_plan(path, target):
    _private_file(path)
    with tempfile.TemporaryDirectory(prefix='terraform-private-') as tmp:
        directory = Path(tmp)
        directory.chmod(0o700)
        status, out, err = run_private(['terraform', '-chdir=' + str(REPO_ROOT / target['root']), 'show', '-json', str(Path(path).resolve())], directory)
        require(status == 0, 'PRIVATE_PLAN_SHOW_FAILED')
        return _json(out)


def _address_allowed(address, target):
    if target['name'] == 'production':
        return address in set(terraform_plan.TARGETS) | {'data.aws_caller_identity.current', 'data.aws_region.current'}
    if address in STAGING_ADDRESSES:
        return True
    return address in {f'aws_ssm_parameter.runtime["{key}"]' for key in SECRET_KEYS} | {
        f'aws_ssm_parameter.runtime["{key}"]' for key in ['db-host', 'db-port', 'db-name', 'db-shared-username', 'db-test-username', 'db-live-username', 'operator-email', 'meterengine-cutover-mode']}


def summarize_plan(plan: dict, target: dict) -> dict:
    require(plan.get('format_version') == '1.2' and plan.get('terraform_version') == '1.17.1' and
            plan.get('complete') is True and not plan.get('errored') and not plan.get('deferred_changes'), 'PLAN_REJECTED')
    result = {'target': target['name'], 'counts': dict.fromkeys(['create', 'update', 'delete', 'replace'], 0), 'resources': []}
    seen = set()
    for resource in plan.get('resource_changes', []):
        address, change = resource.get('address'), resource.get('change', {})
        require(_address_allowed(address, target) and address not in seen, 'ADDRESS_REJECTED')
        seen.add(address)
        actions = change.get('actions')
        require(actions in [['create'], ['read'], ['update'], ['delete'], ['no-op'], ['delete', 'create'], ['create', 'delete']], 'ACTION_REJECTED')
        item = {'address': address, 'actions': actions}
        importing = change.get('importing')
        if importing is not None:
            require(target['name'] == 'production' and address in terraform_plan.TARGETS and importing.get('id') == terraform_plan.TARGETS[address][2], 'IMPORT_REJECTED')
            item['import_id'] = terraform_plan.TARGETS[address][2]
        if len(actions) == 2:
            result['counts']['replace'] += 1
        for action in actions:
            if action in result['counts']:
                result['counts'][action] += 1
        result['resources'].append(item)
    return result


def verify_artifact(metadata: dict, expected: dict, plan_path: Path) -> None:
    validate_context(metadata, load_target(expected['target']))
    require(METADATA_FIELDS <= set(expected) and all(metadata[field] == expected[field] for field in METADATA_FIELDS), 'ARTIFACT_CONTEXT_MISMATCH')
    require(digest(plan_path) == expected['plan_sha256'], 'PLAN_HASH_MISMATCH')


def verify_state(context, current):
    require(type(current.get('exists')) is bool and
            (_version(current.get('version_id')) if current['exists'] else current.get('version_id') is None), 'STATE_REJECTED')
    require(context['state_exists'] == current['exists'] and context['state_version_id'] == current['version_id'], 'STALE_STATE')


def state_versions(*, aws=aws_api):
    response = aws(['s3api', 'list-object-versions', '--bucket', BUCKET, '--expected-bucket-owner', ACCOUNT])
    require(not response.get('IsTruncated'), 'STATE_PAGINATION_REJECTED')
    result = {}
    for version in response.get('Versions', []):
        if version.get('IsLatest') and version.get('Key', '').endswith('.tfstate'):
            require(_version(version.get('VersionId')), 'STATE_VERSION_REQUIRED')
            result[version['Key']] = version['VersionId']
    return result


def target_state(target, *, aws=aws_api):
    response = aws(['s3api', 'list-object-versions', '--bucket', BUCKET, '--prefix', target['state_key'], '--expected-bucket-owner', ACCOUNT])
    require(not response.get('IsTruncated'), 'STATE_PAGINATION_REJECTED')
    versions = [v for v in response.get('Versions', []) if v.get('IsLatest') and v.get('Key') == target['state_key']]
    require(len(versions) <= 1, 'STATE_REJECTED')
    if not versions:
        return {'exists': False, 'version_id': None}
    require(_version(versions[0].get('VersionId')), 'STATE_VERSION_REQUIRED')
    return {'exists': True, 'version_id': versions[0]['VersionId']}


def bucket_preflight(target, *, aws=aws_api):
    require(aws(['sts', 'get-caller-identity']).get('Account') == ACCOUNT, 'AWS_ACCOUNT_REJECTED')
    require(aws(['s3api', 'get-bucket-versioning', '--bucket', BUCKET, '--expected-bucket-owner', ACCOUNT]).get('Status') == 'Enabled', 'BUCKET_VERSIONING_REQUIRED')
    access = aws(['s3api', 'get-public-access-block', '--bucket', BUCKET, '--expected-bucket-owner', ACCOUNT]).get('PublicAccessBlockConfiguration', {})
    require(all(access.get(key) is True for key in ['BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets']), 'PRIVATE_BUCKET_REQUIRED')
    try:
        policy = aws(['s3api', 'get-bucket-policy-status', '--bucket', BUCKET, '--expected-bucket-owner', ACCOUNT])
    except AutomationError as error:
        require(error.args == ('BUCKET_POLICY_ABSENT',), 'REMOTE_API_REJECTED')
    else:
        require(policy.get('PolicyStatus', {}).get('IsPublic') is False, 'PRIVATE_BUCKET_REQUIRED')
    return target_state(target, aws=aws)


def verify_audit_versions(audit, versions):
    expected = audit.get('state_versions', {})
    require(bool(expected) and set(expected) == set(versions) and all(expected[key].get('version_id') == value and _version(value) for key, value in versions.items()), 'STALE_OWNERSHIP_AUDIT')


def upload_object(key, path, *, aws=aws_api):
    _private_file(path)
    try:
        response = aws(['s3api', 'put-object', '--bucket', BUCKET, '--key', key, '--body', str(path),
                        '--server-side-encryption', 'AES256', '--expected-bucket-owner', ACCOUNT])
        require(_version(response.get('VersionId')), 'ARTIFACT_VERSION_REQUIRED')
        return {'key': key, 'version_id': response['VersionId'], 'sha256': digest(path)}
    except Exception:
        raise AutomationError('PRIVATE_UPLOAD_FAILED') from None


def download_object(key, version, sha256, path, *, aws=aws_api):
    require(_version(version) and _hash(sha256), 'ARTIFACT_VERSION_REQUIRED')
    private_write(path, b'')
    try:
        response = aws(['s3api', 'get-object', '--bucket', BUCKET, '--key', key, '--version-id', version,
                        '--expected-bucket-owner', ACCOUNT, str(path)])
        require(response.get('VersionId') == version and digest(path) == sha256, 'ARTIFACT_HASH_MISMATCH')
        _private_file(path)
    except Exception:
        Path(path).unlink(missing_ok=True)
        raise AutomationError('PRIVATE_DOWNLOAD_FAILED') from None


def store_diagnostics(context, paths, *, aws=aws_api):
    base = prefix(context)
    return [upload_object(f'{base}/diagnostics/{index}.log', path, aws=aws) for index, path in enumerate(paths)]


def apply_ephemeral_env(target: dict, supplied: dict) -> dict[str, str]:
    require(not any(key.startswith('TF_VAR_') for key in supplied), 'UNREVIEWED_APPLY_INPUT')
    if target['name'] == 'production':
        require(not any(key in supplied for key in ['TF_STAGING_DB_PASSWORD', 'TF_STAGING_RUNTIME_SECRETS_JSON']), 'PRODUCTION_PASSWORD_REJECTED')
        return {}
    password = supplied.get('TF_STAGING_DB_PASSWORD')
    def valid(value):
        return isinstance(value, str) and bool(value.strip()) and 'TF_PLAN_ONLY' not in value
    require(valid(password), 'APPLY_SECRET_REQUIRED')
    try:
        secrets = json.loads(supplied.get('TF_STAGING_RUNTIME_SECRETS_JSON', ''))
    except (ValueError, TypeError):
        raise AutomationError('APPLY_SECRET_REQUIRED') from None
    require(isinstance(secrets, dict) and set(secrets) == SECRET_KEYS and all(valid(v) for v in secrets.values()), 'APPLY_SECRET_REQUIRED')
    return {'TF_VAR_db_password_wo': password, 'TF_VAR_runtime_secrets': json.dumps(secrets)}


def plan_ephemeral_env(target):
    if target['name'] == 'production':
        return {}
    return {'TF_VAR_db_password_wo': 'TF_PLAN_ONLY_NeverDeploy123!',
            'TF_VAR_runtime_secrets': json.dumps(dict.fromkeys(sorted(SECRET_KEYS), 'TF_PLAN_ONLY_NeverDeploy123!'))}


def plan_variable_env(target, supplied):
    if target['name'] == 'production':
        return {}
    try:
        version = json.loads(supplied['TF_STAGING_DB_PASSWORD_VERSION'])
        versions = json.loads(supplied['TF_STAGING_RUNTIME_SECRET_VERSIONS_JSON'])
        email = supplied['TF_STAGING_OPERATOR_EMAIL']
        positive = lambda value: type(value) is int and value > 0
        require(positive(version) and isinstance(versions, dict) and set(versions) == SECRET_KEYS and all(positive(v) for v in versions.values()), 'PLAN_VERSION_REQUIRED')
        require(isinstance(email, str) and re.fullmatch(r'[^\s@]+@[^\s@]+', email), 'PLAN_OPERATOR_REQUIRED')
        return {'TF_VAR_db_password_version': str(version), 'TF_VAR_runtime_secret_versions': json.dumps(versions), 'TF_VAR_operator_email': email}
    except (ValueError, KeyError, TypeError):
        raise AutomationError('PLAN_INPUT_REJECTED') from None


def _json(path):
    _private_file(path)
    return json.loads(Path(path).read_text())


def _live_context(target):
    return {'target': target['name'], 'source_sha': os.environ.get('GITHUB_SHA'),
            'run_id': os.environ.get('GITHUB_RUN_ID'), 'run_attempt': os.environ.get('GITHUB_RUN_ATTEMPT')}


def _bind_local(context, target):
    validate_github(os.environ, context)
    require(digest(REPO_ROOT / target['root'] / '.terraform.lock.hcl') == context['lockfile_sha256'], 'LOCKFILE_MISMATCH')
    require(command_json(['terraform', 'version', '-json']).get('terraform_version') == '1.17.1', 'TERRAFORM_VERSION_REJECTED')


def _production_validation(plan, context, directory, observed, *, aws=aws_api):
    require(observed is not None, 'OBSERVATION_REQUIRED')
    if not context['state_exists']:
        audit_path = directory / 'audit.json'
        download_object(context['ownership_audit_key'], context['ownership_audit_version_id'], context['ownership_audit_sha256'], audit_path, aws=aws)
        audit = _json(audit_path)
        verify_audit_versions(audit, state_versions(aws=aws))
        return terraform_plan.validate_production_plan(plan, audit, observed=observed)
    state_path = directory / 'owned-state.json'
    private_write(state_path, b'')
    response = aws(['s3api', 'get-object', '--bucket', BUCKET, '--key', context['state_key'], '--version-id', context['state_version_id'], '--expected-bucket-owner', ACCOUNT, str(state_path)])
    require(response.get('VersionId') == context['state_version_id'], 'STATE_VERSION_MISMATCH')
    state = _json(state_path)
    owned = {}
    for resource in state.get('resources', []):
        if resource.get('mode') != 'managed':
            continue
        address = '.'.join(filter(None, [resource.get('module'), resource.get('type'), resource.get('name')]))
        instances = resource.get('instances', [])
        require(len(instances) == 1 and 'index_key' not in instances[0] and address not in owned, 'OWNED_STATE_REJECTED')
        owned[address] = instances[0].get('attributes', {}).get('id')
    return terraform_plan.validate_owned_production_plan(plan, owned, observed=observed)


def assert_dummy_not_persisted(path, plan):
    require('TF_PLAN_ONLY' not in json.dumps(plan), 'DUMMY_PERSISTED')
    try:
        with zipfile.ZipFile(path) as archive:
            for member in archive.namelist():
                require(b'TF_PLAN_ONLY' not in archive.read(member), 'DUMMY_PERSISTED')
    except (OSError, ValueError, zipfile.BadZipFile):
        raise AutomationError('PLAN_ARCHIVE_REJECTED') from None


def store_plan(context, plan_path, observed, *, aws=aws_api):
    target = load_target(context['target'])
    context = dict(context, plan_key=prefix(context) + '/plan.tfplan', plan_version_id='pending', plan_sha256=digest(plan_path))
    validate_context(context, target)
    _bind_local(context, target)
    verify_state(context, target_state(target, aws=aws))
    plan_json = show_private_plan(plan_path, target)
    assert_dummy_not_persisted(plan_path, plan_json)
    summarize_plan(plan_json, target)
    with tempfile.TemporaryDirectory(prefix='terraform-private-') as tmp:
        directory = Path(tmp)
        directory.chmod(0o700)
        if target['name'] == 'production':
            _production_validation(plan_json, context, directory, observed, aws=aws)
        plan = upload_object(context['plan_key'], plan_path, aws=aws)
        context['plan_version_id'] = plan['version_id']
        verify_state(context, target_state(target, aws=aws))
        metadata = directory / 'context.json'
        private_write(metadata, json.dumps(context, sort_keys=True).encode())
        artifact = upload_object(prefix(context) + '/context.json', metadata, aws=aws)
    return dict(context, context_key=artifact['key'], context_version_id=artifact['version_id'], context_sha256=artifact['sha256'])


def fetch_plan(expected, out, observed=None, *, aws=aws_api):
    require(set(expected) == METADATA_FIELDS | CONTEXT_FIELDS, 'EXPECTED_CONTEXT_REQUIRED')
    context = {key: expected[key] for key in METADATA_FIELDS}
    target = load_target(expected['target'])
    validate_context(context, target)
    _bind_local(context, target)
    require(expected['context_key'] == prefix(context) + '/context.json', 'CONTEXT_KEY_REJECTED')
    with tempfile.TemporaryDirectory(prefix='terraform-private-') as tmp:
        path = Path(tmp) / 'context.json'
        download_object(expected['context_key'], expected['context_version_id'], expected['context_sha256'], path, aws=aws)
        metadata = _json(path)
        require(metadata == context, 'ARTIFACT_CONTEXT_MISMATCH')
        verify_state(context, target_state(target, aws=aws))
        download_object(expected['plan_key'], expected['plan_version_id'], expected['plan_sha256'], out, aws=aws)
        verify_artifact(metadata, expected, out)
        plan = show_private_plan(out, target)
        assert_dummy_not_persisted(out, plan)
        summarize_plan(plan, target)
        if target['name'] == 'production':
            _production_validation(plan, context, Path(tmp), observed, aws=aws)
        verify_state(context, target_state(target, aws=aws))
    return {'target': target['name'], 'source_sha': context['source_sha'], 'plan_sha256': expected['plan_sha256']}


class _Parser(argparse.ArgumentParser):
    def error(self, message):
        raise AutomationError('ARGUMENT_REJECTED')


def main(argv=None):
    parser = _Parser(description=__doc__)
    parser.add_argument('command', choices=['preflight', 'summarize', 'store-plan', 'fetch-plan', 'store-diagnostics', 'check-apply-inputs'])
    parser.add_argument('--target', required=True, choices=['staging', 'production'])
    parser.add_argument('--scope', choices=['github', 'bucket'])
    for name in ['plan', 'context', 'expected', 'out', 'observed']:
        parser.add_argument('--' + name, type=Path)
    parser.add_argument('--files', nargs='+', type=Path)
    try:
        args = parser.parse_args(argv)
        os.umask(0o077)
        target = load_target(args.target)
        if args.command == 'preflight':
            context = _live_context(target)
            validate_github(os.environ, context)
            require(args.scope is not None, 'PREFLIGHT_SCOPE_REQUIRED')
            result = github_preflight(target, os.environ, context) if args.scope == 'github' else bucket_preflight(target)
        elif args.command == 'summarize':
            expected = _json(args.context)
            context = {key: expected[key] for key in METADATA_FIELDS}
            require(context['target'] == args.target, 'TARGET_MISMATCH')
            validate_context(context, target)
            _bind_local(context, target)
            verify_artifact(context, expected, args.plan)
            plan = show_private_plan(args.plan, target)
            result = summarize_plan(plan, target)
            result.update(source_sha=context['source_sha'], plan_sha256=context['plan_sha256'],
                          plan_key=context['plan_key'], plan_version_id=context['plan_version_id'])
            if CONTEXT_FIELDS <= set(expected):
                require(expected['context_key'] == prefix(context) + '/context.json' and
                        _version(expected['context_version_id']) and _hash(expected['context_sha256']), 'CONTEXT_REJECTED')
                result.update({key: expected[key] for key in CONTEXT_FIELDS})
            if target['name'] == 'production':
                with tempfile.TemporaryDirectory(prefix='terraform-private-') as tmp:
                    result['policy'] = _production_validation(plan, context, Path(tmp), _json(args.observed))
        elif args.command == 'store-plan':
            context = _json(args.context)
            require(context.get('target') == args.target, 'TARGET_MISMATCH')
            result = store_plan(context, args.plan, _json(args.observed) if args.observed else None)
        elif args.command == 'fetch-plan':
            expected = _json(args.expected)
            require(expected.get('target') == args.target, 'TARGET_MISMATCH')
            require(args.out is not None and stat.S_IMODE(args.out.parent.stat().st_mode) == 0o700, 'PRIVATE_DIRECTORY_REQUIRED')
            result = fetch_plan(expected, args.out, _json(args.observed) if args.observed else None)
        elif args.command == 'store-diagnostics':
            context = _live_context(target)
            validate_github(os.environ, context)
            require(bool(args.files), 'DIAGNOSTICS_REQUIRED')
            result = store_diagnostics(context, args.files)
        else:
            supplied = {key: value for key, value in os.environ.items() if key.startswith('TF_VAR_') or key in target['secrets'].values()}
            require(args.out is not None and stat.S_IMODE(args.out.parent.stat().st_mode) == 0o700, 'PRIVATE_DIRECTORY_REQUIRED')
            private_write(args.out, json.dumps(apply_ephemeral_env(target, supplied)).encode())
            result = {'apply_inputs': 'verified', 'target': args.target}
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError, TypeError, KeyError, AttributeError):
        print('AUTOMATION_REJECTED: inspect private artifacts.', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
