"""Validate the initial production import saved plan without exposing its values."""
import argparse
import json
import sys
from pathlib import Path

EC2 = 'module.service.aws_instance.app'
RDS = 'module.service.aws_db_instance.app'
TARGETS = {
    EC2: ('aws_instance', 'i-0f47bb1f028cd29a9', 'i-0f47bb1f028cd29a9'),
    RDS: ('aws_db_instance', 'db-72GJNZYUHSQRXAKNQGC7ECV7UI', 'meterengine-db'),
}
OWNERSHIP_IDS = {value for target in TARGETS.values() for value in target[1:]} | {
    'arn:aws:rds:ap-northeast-2:928618307647:db:meterengine-db',
}
POLICY = {
    EC2: {'instance_type': 'm7g.large'},
    RDS: {'instance_class': 'db.m7g.large', 'apply_immediately': True, 'skip_final_snapshot': False,
          'delete_automated_backups': False, 'final_snapshot_identifier': 'meterengine-production-final'},
}


class PlanValidationError(ValueError):
    pass


def validate_ownership(ownership):
    if not isinstance(ownership, dict) or any(ownership.get(key) != value for key, value in {
        'account_id': '928618307647', 'region': 'ap-northeast-2',
        'bucket': 'meterengine-tfstate-928618307647',
    }.items()) or not isinstance(ownership.get('states'), dict) or not ownership['states']:
        raise PlanValidationError('A current known-state ownership inventory is required.')
    if not ownership.get('audited_at') or not isinstance(ownership.get('state_versions'), dict) or set(ownership['state_versions']) != set(ownership['states']):
        raise PlanValidationError('Ownership audit needs its timestamp and state object versions.')
    for metadata in ownership['state_versions'].values():
        if not isinstance(metadata, dict) or not (metadata.get('version_id') or metadata.get('etag')):
            raise PlanValidationError('Ownership audit is missing a state object version.')
    for resources in ownership['states'].values():
        if not isinstance(resources, list):
            raise PlanValidationError('Invalid ownership inventory.')
        for resource in resources:
            if not isinstance(resource, dict):
                raise PlanValidationError('Invalid ownership resource.')
            if any(resource.get(key) in OWNERSHIP_IDS for key in ('id', 'identifier', 'resource_id', 'arn')):
                raise PlanValidationError('Production resource already has a state owner.')


def _unknown_paths(value, prefix=''):
    if value is True:
        return {prefix}
    if isinstance(value, dict):
        return set().union(*(_unknown_paths(v, f'{prefix}.{k}' if prefix else k) for k, v in value.items()), set())
    if isinstance(value, list):
        return set().union(*(_unknown_paths(v, f'{prefix}.{i}') for i, v in enumerate(value)), set())
    return set()


def validate_production_plan(plan, ownership, *, observed=None, import_only=False):
    """Return nonsecret counts/attribute names; raise PlanValidationError on any drift.

    ownership is an approved initial audit: account_id, region, bucket,
    audited_at, state_versions and states: {key: [{address, id, identifier?,
    resource_id?, arn?}]}. observed is a separate fresh target identity and
    pending-modification check. This API is only for the initial import.
    """
    validate_ownership(ownership)
    return _validate_changes(plan, observed, import_only=import_only, initial=True)


def validate_owned_production_plan(plan, owned, *, observed=None):
    if owned != {address: values[1] for address, values in TARGETS.items()}:
        raise PlanValidationError('Target state must own both expected production resources.')
    return _validate_changes(plan, observed, import_only=False, initial=False)


def _validate_changes(plan, observed, *, import_only, initial):
    expected = {
        'account_id': '928618307647', 'region': 'ap-northeast-2',
        'instance_id': TARGETS[EC2][1], 'database_identifier': TARGETS[RDS][2],
        'database_resource_id': TARGETS[RDS][1], 'rds_pending_modifications': [],
    }
    if not isinstance(observed, dict) or any(observed.get(key) != value for key, value in expected.items()):
        raise PlanValidationError('Fresh production identity and empty RDS pending modifications are required.')
    if (plan.get('format_version') != '1.2' or plan.get('terraform_version') != '1.17.1'
            or plan.get('errored') or plan.get('complete') is not True or plan.get('deferred_changes')):
        raise PlanValidationError('Expected a complete Terraform 1.17.1 saved plan.')
    seen, changes, counts = set(), [], {}
    for resource in plan.get('resource_changes', []):
        address, change = resource.get('address'), resource.get('change', {})
        if resource.get('mode') == 'data':
            if address not in {'data.aws_caller_identity.current', 'data.aws_region.current'}:
                raise PlanValidationError('Unexpected data resource address.')
            if change.get('actions') not in (['read'], ['no-op']):
                raise PlanValidationError('Unexpected data resource action.')
            continue
        if address not in TARGETS or address in seen:
            raise PlanValidationError('Unexpected or repeated managed resource address.')
        seen.add(address)
        kind, state_id, import_id = TARGETS[address]
        if resource.get('type') != kind or (initial and change.get('importing', {}).get('id') != import_id) or (not initial and change.get('importing') is not None):
            raise PlanValidationError('Expected import ID does not match.')
        actions = change.get('actions')
        if actions not in (['update'], ['no-op']) or change.get('replace_paths'):
            raise PlanValidationError('Only in-place update or no-op imports are permitted.')
        before, after = change.get('before'), change.get('after')
        if not isinstance(before, dict) or not isinstance(after, dict) or before.get('id') != state_id or after.get('id') != state_id:
            raise PlanValidationError('Imported resource identity differs.')
        unknown = _unknown_paths(change.get('after_unknown', {}))
        computed = {'public_ip', 'public_dns'} if address == EC2 and not import_only else set()
        if unknown - computed:
            raise PlanValidationError('Unreviewed unknown resource attribute.')
        if unknown & computed and (before.get('public_ip') != '43.203.50.31' or observed.get('eip_public_ip') != '43.203.50.31' or observed.get('eip_instance_id') != TARGETS[EC2][1]):
            raise PlanValidationError('Observed EIP differs before resize.')
        if address == RDS:
            for field in ('password', 'password_wo', 'password_wo_version', 'manage_master_user_password'):
                if any(value is not None and not (field == 'manage_master_user_password' and value is False) for value in (before.get(field), after.get(field))):
                    raise PlanValidationError('Production import must not write an owner password.')
        allowed = {} if import_only else POLICY[address]
        for field in sorted(before.keys() | after.keys()):
            if field in computed and field in unknown:
                continue
            default_field = 'user_data_replace_on_change' if address == EC2 else None
            if field == default_field and before.get(field) is None and after.get(field) is False:
                continue
            if before.get(field) == after.get(field):
                continue
            if field not in allowed or after.get(field) != allowed[field]:
                raise PlanValidationError('Unexpected configurable resource change.')
            changes.append({'address': address, 'attribute': field})
        if not import_only:
            for field, value in POLICY[address].items():
                if after.get(field) != value:
                    raise PlanValidationError('Approved capacity or retention policy is missing.')
        counts[actions[0]] = counts.get(actions[0], 0) + 1
    if seen != set(TARGETS):
        raise PlanValidationError('Both production resources must be imported.')
    return {'imports': len(seen) if initial else 0, 'actions': counts, 'permitted_changes': changes}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('plan', type=Path)
    parser.add_argument('--ownership', required=True, type=Path)
    parser.add_argument('--observed', required=True, type=Path)
    parser.add_argument('--import-only', action='store_true')
    args = parser.parse_args(argv)
    try:
        result = validate_production_plan(json.loads(args.plan.read_text()), json.loads(args.ownership.read_text()), observed=json.loads(args.observed.read_text()), import_only=args.import_only)
    except (OSError, ValueError, TypeError, KeyError):
        print('Production import plan rejected; inspect private artifacts.', file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
