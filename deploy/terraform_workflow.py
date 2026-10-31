"""Connect the two Terraform workflows to private, single-run saved plans."""
import json
import os
from pathlib import Path
import re
import sys

import terraform_automation as a
import terraform_plan as policy

ROOT = Path(__file__).resolve().parents[1]


def expected_plan(target, env):
    expected = json.loads(env.get('EXPECTED_PLAN', '{}'))
    a.require(set(expected) == a.METADATA_FIELDS | a.CONTEXT_FIELDS, 'EXPECTED_CONTEXT_REQUIRED')
    context = {k: expected[k] for k in a.METADATA_FIELDS}
    a.require(context['target'] == target['name'], 'TARGET_MISMATCH')
    a.validate_context(context, target)
    a.validate_github(env, context)
    a.require(expected['lockfile_sha256'] == a.digest(ROOT / target['root'] / '.terraform.lock.hcl'), 'LOCKFILE_MISMATCH')
    a.require(expected['context_key'] == a.prefix(context) + '/context.json' and
              a._version(expected['context_version_id']) and a._hash(expected['context_sha256']), 'CONTEXT_REJECTED')
    return expected


def live_context(target, env):
    return {'target': target['name'], 'source_sha': env.get('GITHUB_SHA'),
            'run_id': env.get('GITHUB_RUN_ID'), 'run_attempt': env.get('GITHUB_RUN_ATTEMPT')}


def apply_inputs(target, env):
    names = {'TF_STAGING_DB_PASSWORD', 'TF_STAGING_RUNTIME_SECRETS_JSON'}
    return a.apply_ephemeral_env(target, {k: env[k] for k in names if k in env})


def prepare(target, phase, env):
    context = expected_plan(target, env) if phase == 'apply' else live_context(target, env)
    a.github_preflight(target, env, context, gh=a.gh_api)
    a.require(a.command_json(['terraform', 'version', '-json']).get('terraform_version') == target['terraform_version'], 'TERRAFORM_VERSION_REJECTED')
    if phase == 'apply':
        apply_inputs(target, env)
    else:
        a.plan_variable_env(target, env)
    return {'target': target['name'], 'environment': target['environment'], 'role_arn': target[phase + '_role_arn']}


def terraform_env(env, directory, variables):
    clean = {k: v for k, v in env.items() if not k.startswith('TF_') and k not in {'GH_TOKEN', 'GITHUB_TOKEN', 'EXPECTED_PLAN'}}
    clean.update(TF_WORKSPACE='default', TF_DATA_DIR=str(Path(directory) / 'data'), TF_IN_AUTOMATION='1',
                 TF_INPUT='0', TF_LOG='OFF', AWS_REGION=a.REGION, AWS_DEFAULT_REGION=a.REGION, **variables)
    return clean


def private_command(command, directory, env, logs):
    log_dir = a.private_directory(directory)
    status, out, err = a.run_private(command, log_dir, env=env)
    logs.extend([out, err])
    a.require(status == 0, 'PRIVATE_COMMAND_FAILED')
    return out


def terraform_command(root, arguments, directory, env, logs):
    return private_command(['terraform', '-chdir=' + str(root), *arguments], directory, env, logs)


def checks(directory, env):
    logs = []
    clean = {k: v for k, v in env.items() if not k.startswith(('AWS_', 'ACTIONS_ID_TOKEN_'))}
    for name in ['staging', 'production', 'automation']:
        private = directory / name
        private.mkdir(mode=0o700, exist_ok=True)
        a.require(not private.is_symlink() and private.stat().st_mode & 0o777 == 0o700, 'PRIVATE_DIRECTORY_REQUIRED')
        tfenv = terraform_env(clean, private, {})
        root = ROOT / 'deploy/terraform' / name
        for args in [['fmt', '-check', '-recursive'], ['init', '-backend=false', '-input=false', '-lockfile=readonly'],
                     ['validate', '-no-color'], ['test', '-test-directory=tests', '-no-color']]:
            terraform_command(root, args, private, tfenv, logs)


def production_inventory():
    account = a.aws_api(['sts', 'get-caller-identity']).get('Account')
    response = a.aws_api(['ec2', 'describe-instances', '--instance-ids', policy.TARGETS[policy.EC2][1]])
    instances = [instance for reservation in response.get('Reservations', []) for instance in reservation.get('Instances', [])]
    databases = a.aws_api(['rds', 'describe-db-instances', '--db-instance-identifier', 'meterengine-db']).get('DBInstances', [])
    addresses = a.aws_api(['ec2', 'describe-addresses', '--public-ips', '43.203.50.31']).get('Addresses', [])
    a.require(account == a.ACCOUNT and len(instances) == len(databases) == len(addresses) == 1, 'PRODUCTION_INVENTORY_REJECTED')
    ec2, db, eip = instances[0], databases[0], addresses[0]
    expected_ec2 = {'InstanceId': policy.TARGETS[policy.EC2][1], 'ImageId': 'ami-0f4109eb491b0476f',
                    'VpcId': 'vpc-07498f0672b7f3c9e', 'SubnetId': 'subnet-0348a18c1d399c193'}
    a.require(all(ec2.get(k) == v for k, v in expected_ec2.items()) and
              re.fullmatch(a.REGION + '[a-z]', ec2.get('Placement', {}).get('AvailabilityZone', '')) and
              ec2.get('InstanceType') in {'t4g.medium', 'm7g.large'} and
              sorted(sg['GroupId'] for sg in ec2.get('SecurityGroups', [])) == ['sg-0c2691d037bcdd8b1'] and
              ec2.get('IamInstanceProfile', {}).get('Arn') == f'arn:aws:iam::{a.ACCOUNT}:instance-profile/meterengine-ec2-role', 'PRODUCTION_INVENTORY_REJECTED')
    expected_db = {'DBInstanceIdentifier': 'meterengine-db', 'DbiResourceId': policy.TARGETS[policy.RDS][1],
                   'DBInstanceArn': f'arn:aws:rds:{a.REGION}:{a.ACCOUNT}:db:meterengine-db',
                   'PendingModifiedValues': {}, 'PubliclyAccessible': False}
    a.require(all(db.get(k) == v for k, v in expected_db.items()) and
              db.get('DBInstanceClass') in {'db.t4g.small', 'db.m7g.large'} and
              db.get('DBSubnetGroup', {}).get('DBSubnetGroupName') == 'meterengine-rds-subnet-private' and
              db.get('DBSubnetGroup', {}).get('VpcId') == expected_ec2['VpcId'] and
              sorted(sg['VpcSecurityGroupId'] for sg in db.get('VpcSecurityGroups', [])) == ['sg-068a54ab4cfa91730'] and
              db.get('Endpoint', {}).get('Port') == 5432 and eip.get('PublicIp') == '43.203.50.31' and
              eip.get('InstanceId') == expected_ec2['InstanceId'], 'PRODUCTION_INVENTORY_REJECTED')
    return {'account_id': account, 'region': a.REGION, 'instance_id': ec2['InstanceId'],
            'database_identifier': db['DBInstanceIdentifier'], 'database_resource_id': db['DbiResourceId'],
            'rds_pending_modifications': [], 'eip_public_ip': eip['PublicIp'], 'eip_instance_id': eip['InstanceId'],
            'ec2': ec2, 'rds': db}


def inventory_before(observed):
    ec2, db = observed['ec2'], observed['rds']
    return {policy.EC2: {'instance_type': ec2['InstanceType'], 'ami': ec2['ImageId'], 'subnet_id': ec2['SubnetId'],
                         'vpc_security_group_ids': sorted(sg['GroupId'] for sg in ec2['SecurityGroups']),
                         'iam_instance_profile': ec2['IamInstanceProfile']['Arn'].rsplit('/', 1)[1]},
            policy.RDS: {'instance_class': db['DBInstanceClass'], 'db_subnet_group_name': db['DBSubnetGroup']['DBSubnetGroupName'],
                         'vpc_security_group_ids': sorted(sg['VpcSecurityGroupId'] for sg in db['VpcSecurityGroups']),
                         'port': db['Endpoint']['Port'], 'publicly_accessible': db['PubliclyAccessible']}}


def compare_inventory(plan, observed):
    changes = {resource.get('address'): resource.get('change', {}).get('before', {}) for resource in plan.get('resource_changes', [])}
    for address, fields in inventory_before(observed).items():
        for field, value in fields.items():
            before = changes.get(address, {}).get(field)
            if field == 'vpc_security_group_ids' and isinstance(before, list):
                before = sorted(before)
            a.require(before == value, 'PRODUCTION_CHANGED_REPLAN_REQUIRED')


def execute(target, phase, directory, env):
    logs, bucket_verified = [], False
    context = live_context(target, env)
    try:
        prepare(target, phase, env)
        current = a.bucket_preflight(target)
        bucket_verified = True
        if phase == 'apply':
            expected = expected_plan(target, env)
            a.verify_state(expected, current)
            variables = apply_inputs(target, env)
        else:
            variables = a.plan_variable_env(target, env) | a.plan_ephemeral_env(target)
        tfenv = terraform_env(env, directory, variables)
        root = ROOT / target['root']
        terraform_command(root, ['init', '-input=false', '-reconfigure', '-lockfile=readonly'], directory, tfenv, logs)
        observed = production_inventory() if target['name'] == 'production' else None
        plan_path = directory / 'plan.tfplan'
        if phase == 'plan':
            context.update(schema_version=1, repository_id=target['repository_id'], repository_owner_id=target['repository_owner_id'],
                           aws_account_id=target['aws_account_id'], aws_region=target['aws_region'], state_key=target['state_key'],
                           state_exists=current['exists'], state_version_id=current['version_id'], terraform_version=target['terraform_version'],
                           lockfile_sha256=a.digest(root / '.terraform.lock.hcl'), ownership_audit_key=None,
                           ownership_audit_version_id=None, ownership_audit_sha256=None)
            if target['name'] == 'production' and not current['exists']:
                for key in ['key', 'version_id', 'sha256']:
                    context['ownership_audit_' + key] = env.get('TF_PRODUCTION_OWNERSHIP_AUDIT_' + key.upper())
                # Validate required audit identifiers before remote planning.
                a.validate_context(dict(context, plan_key=a.prefix(context) + '/plan.tfplan',
                                        plan_version_id='pending', plan_sha256='0' * 64), target)
            terraform_command(root, ['plan', '-input=false', '-lock-timeout=5m', '-out=' + str(plan_path)], directory, tfenv, logs)
        else:
            a.fetch_plan(expected, plan_path, observed)
        shown = terraform_command(root, ['show', '-json', str(plan_path)], directory, tfenv, logs)
        plan = json.loads(shown.read_text())
        summary = a.summarize_plan(plan, target)
        if observed is not None:
            compare_inventory(plan, observed)
        if phase == 'plan':
            expected = a.store_plan(context, plan_path, observed)
            summary.update({key: expected[key] for key in ['source_sha', 'target', 'run_id', 'run_attempt', 'state_exists',
                            'state_version_id', 'plan_key', 'plan_version_id', 'plan_sha256', 'context_key', 'context_version_id', 'context_sha256']})
            if observed is not None:
                summary['policy'] = a._production_validation(plan, expected, directory, observed)
                summary['retention'] = policy.POLICY[policy.RDS]
        else:
            # A1 checks fixed S3 versions; native Terraform still checks lineage/serial.
            a.verify_state(expected, a.target_state(target))
            terraform_command(root, ['apply', '-input=false', '-lock-timeout=5m', str(plan_path)], directory, tfenv, logs)
            summary = {'target': target['name'], 'source_sha': expected['source_sha'], 'plan_sha256': expected['plan_sha256'], 'apply': 'succeeded'}
        a.store_diagnostics(context, logs)
        publish({'expected': expected} if phase == 'plan' else {}, output=env.get('GITHUB_OUTPUT'))
        publish(summary, summary=env.get('GITHUB_STEP_SUMMARY'))
    except Exception as error:
        error_path = a.private_directory(directory) / 'exception.log'
        a.private_write(error_path, str(error).encode())
        logs.append(error_path)
        if bucket_verified:
            a.store_diagnostics(context, logs)
        raise


def publish(result, *, output=None, summary=None):
    if output:
        with Path(output).open('a') as stream:
            for key, value in result.items():
                value = json.dumps(value, sort_keys=True, separators=(',', ':')) if isinstance(value, dict) else str(value)
                a.require('\n' not in value and '\r' not in value, 'OUTPUT_REJECTED')
                stream.write(f'{key}={value}\n')
    if summary:
        with Path(summary).open('a') as stream:
            stream.write('```json\n' + json.dumps(result, sort_keys=True, indent=2) + '\n```\n')


def main(argv=None):
    parser = a._Parser(description=__doc__)
    parser.add_argument('command', choices=['checks', 'prepare-plan', 'plan', 'prepare-apply', 'apply'])
    parser.add_argument('--target', choices=['staging', 'production'])
    parser.add_argument('--directory', type=Path)
    try:
        args = parser.parse_args(argv)
        os.umask(0o077)
        env = dict(os.environ)
        if args.command.startswith('prepare-'):
            result = prepare(a.load_target(args.target), args.command.removeprefix('prepare-'), env)
            publish(result, output=env.get('GITHUB_OUTPUT'))
        else:
            a.require(args.directory is not None, 'PRIVATE_DIRECTORY_REQUIRED')
            args.directory.mkdir(mode=0o700, parents=False, exist_ok=True)
            a.require(not args.directory.is_symlink() and args.directory.stat().st_mode & 0o777 == 0o700, 'PRIVATE_DIRECTORY_REQUIRED')
            if args.command == 'checks':
                checks(args.directory, env)
            else:
                target = a.load_target(args.target)
                # A1's plan decoder uses the same private provider directory.
                for key in list(os.environ):
                    if key.startswith('TF_'):
                        os.environ.pop(key)
                os.environ.update(TF_DATA_DIR=str(args.directory / 'data'), TF_WORKSPACE='default')
                execute(target, args.command, args.directory, env)
        return 0
    except Exception as error:
        error_path = a.private_directory() / 'exception.log'
        a.private_write(error_path, str(error).encode())
        print('TERRAFORM_WORKFLOW_REJECTED: inspect private diagnostics.', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
