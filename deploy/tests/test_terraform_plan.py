import copy
import importlib.util
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class ProductionPlanTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('terraform_plan'), 'production plan validator is missing')
        import terraform_plan
        self.validator = terraform_plan
        self.plan = {'format_version': '1.2', 'terraform_version': '1.17.1', 'errored': False, 'complete': True, 'resource_changes': []}
        for address, kind, resource_id, import_id, attr, before, after in [
            ('module.service.aws_instance.app', 'aws_instance', 'i-0f47bb1f028cd29a9', 'i-0f47bb1f028cd29a9', 'instance_type', 't4g.medium', 'm7g.large'),
            ('module.service.aws_db_instance.app', 'aws_db_instance', 'db-72GJNZYUHSQRXAKNQGC7ECV7UI', 'meterengine-db', 'instance_class', 'db.t4g.small', 'db.m7g.large'),
        ]:
            old = {'id': resource_id, attr: before}
            new = dict(old, **{attr: after})
            self.plan['resource_changes'].append({'address': address, 'mode': 'managed', 'type': kind, 'change': {'actions': ['update'], 'before': old, 'after': new, 'after_unknown': {}, 'importing': {'id': import_id}}})
        db = self.plan['resource_changes'][1]['change']
        db['before'].update(skip_final_snapshot=True, delete_automated_backups=True, final_snapshot_identifier=None)
        db['after']['apply_immediately'] = True
        db['after'].update(skip_final_snapshot=False, delete_automated_backups=False, final_snapshot_identifier='meterengine-production-final')
        self.observed = {'account_id': '928618307647', 'region': 'ap-northeast-2', 'instance_id': 'i-0f47bb1f028cd29a9', 'database_identifier': 'meterengine-db', 'database_resource_id': 'db-72GJNZYUHSQRXAKNQGC7ECV7UI', 'rds_pending_modifications': []}
        self.ownership = {'audited_at': '2026-10-30T04:00:00+00:00', 'state_versions': {'measurements/test-slo/sut/terraform.tfstate': {'version_id': 'audit-fixture-version'}}, 'account_id': '928618307647', 'region': 'ap-northeast-2', 'bucket': 'meterengine-tfstate-928618307647', 'states': {'measurements/test-slo/sut/terraform.tfstate': [{'address': 'aws_instance.sut', 'id': 'i-0904c8bc11fed05f9'}]}}

    def validate(self, **kwargs):
        return self.validator.validate_production_plan(self.plan, self.ownership, observed=self.observed, **kwargs)

    def reject(self):
        with self.assertRaises(self.validator.PlanValidationError):
            self.validate()

    def test_import_resize_plan_allows_only_requested_classes(self):
        summary = self.validate()
        self.assertEqual(summary['imports'], 2)
        self.assertEqual(summary['actions'], {'update': 2})
        self.assertNotIn('t4g.medium', str(summary))

    def test_import_rejects_password_rotation(self):
        for field, value in [('password', 'DO_NOT_ECHO'), ('password_wo', 'DO_NOT_ECHO'), ('password_wo_version', 1), ('manage_master_user_password', True)]:
            with self.subTest(field=field):
                plan = copy.deepcopy(self.plan)
                plan['resource_changes'][1]['change']['after'][field] = value
                with self.assertRaises(self.validator.PlanValidationError) as error:
                    self.validator.validate_production_plan(plan, self.ownership, observed=self.observed)
                self.assertNotIn('DO_NOT_ECHO', str(error.exception))

    def test_existing_resource_is_not_also_owned_by_sut_state(self):
        self.ownership['states']['measurements/test-slo/sut/terraform.tfstate'].append({'address': 'aws_db_instance.sut', 'id': 'db-72GJNZYUHSQRXAKNQGC7ECV7UI'})
        self.reject()

    def test_rejects_create_delete_replace_or_other_resource(self):
        for actions in [['create'], ['delete'], ['delete', 'create'], ['create', 'delete']]:
            plan = copy.deepcopy(self.plan)
            plan['resource_changes'][0]['change']['actions'] = actions
            with self.assertRaises(self.validator.PlanValidationError):
                self.validator.validate_production_plan(plan, self.ownership, observed=self.observed)
        self.plan['resource_changes'][0]['address'] = 'aws_instance.extra'
        self.reject()

    def test_rejects_unexpected_attribute_and_unknown_network_or_password(self):
        for field in ['user_data', 'vpc_security_group_ids', 'subnet_id', 'ami', 'password_wo_version']:
            for unknown in [False, True]:
                plan = copy.deepcopy(self.plan)
                change = plan['resource_changes'][0]['change']
                change['after_unknown' if unknown else 'after'][field] = True if unknown else 'unexpected'
                with self.assertRaises(self.validator.PlanValidationError):
                    self.validator.validate_production_plan(plan, self.ownership, observed=self.observed)

    def test_rejects_missing_wrong_or_duplicate_import_and_incomplete_plan(self):
        for mutation in ['missing', 'wrong', 'duplicate', 'incomplete']:
            plan = copy.deepcopy(self.plan)
            if mutation == 'missing':
                del plan['resource_changes'][0]['change']['importing']
            elif mutation == 'wrong':
                plan['resource_changes'][0]['change']['importing']['id'] = 'i-wrong'
            elif mutation == 'duplicate':
                plan['resource_changes'].append(copy.deepcopy(plan['resource_changes'][0]))
            else:
                plan['complete'] = False
            with self.assertRaises(self.validator.PlanValidationError):
                self.validator.validate_production_plan(plan, self.ownership, observed=self.observed)

    def test_provider_import_defaults_and_resize_computed_ip_are_narrow(self):
        ec2 = self.plan['resource_changes'][0]['change']
        db = self.plan['resource_changes'][1]['change']
        ec2['before'].update(public_ip='43.203.50.31', public_dns='ec2-43-203-50-31.ap-northeast-2.compute.amazonaws.com', user_data_replace_on_change=None)
        ec2['after'].update(user_data_replace_on_change=False)
        ec2['after_unknown'].update(public_ip=True, public_dns=True)
        self.observed.update(eip_public_ip='43.203.50.31', eip_instance_id='i-0f47bb1f028cd29a9')
        db['before']['apply_immediately'] = None
        db['after']['apply_immediately'] = True
        self.assertEqual(self.validate()['imports'], 2)
        ec2['after_unknown']['private_ip'] = True
        self.reject()

    def test_computed_public_ip_requires_fresh_eip_attachment(self):
        ec2 = self.plan['resource_changes'][0]['change']
        ec2['before']['public_ip'] = '43.203.50.31'
        ec2['after_unknown']['public_ip'] = True
        with self.assertRaises(self.validator.PlanValidationError):
            self.validate()
        self.observed.update(eip_public_ip='43.203.50.31', eip_instance_id='i-0f47bb1f028cd29a9')
        self.assertEqual(self.validate()['imports'], 2)

    def test_pending_rds_modifications_require_review_before_immediate_resize(self):
        for pending in [None, ['AllocatedStorage'], ['MasterUserPassword']]:
            observed = dict(self.observed, rds_pending_modifications=pending)
            with self.assertRaises(self.validator.PlanValidationError):
                self.validator.validate_production_plan(self.plan, self.ownership, observed=observed)

    def test_initial_import_requires_separate_target_observation_and_versioned_audit(self):
        with self.assertRaises(self.validator.PlanValidationError):
            self.validator.validate_production_plan(self.plan, self.ownership)
        for field in ['instance_id', 'database_identifier', 'database_resource_id']:
            observed = dict(self.observed, **{field: 'wrong'})
            with self.assertRaises(self.validator.PlanValidationError):
                self.validator.validate_production_plan(self.plan, self.ownership, observed=observed)
        for field in ['audited_at', 'state_versions']:
            ownership = dict(self.ownership)
            del ownership[field]
            with self.assertRaises(self.validator.PlanValidationError):
                self.validator.validate_production_plan(self.plan, ownership, observed=self.observed)

    def test_adopted_target_reuses_change_validator(self):
        owned = {address: values[1] for address, values in self.validator.TARGETS.items()}
        for resource in self.plan['resource_changes']:
            del resource['change']['importing']
        result = self.validator.validate_owned_production_plan(self.plan, owned, observed=self.observed)
        self.assertEqual(result['imports'], 0)
        self.plan['resource_changes'][0]['change']['after']['ami'] = 'unexpected'
        with self.assertRaises(self.validator.PlanValidationError):
            self.validator.validate_owned_production_plan(self.plan, owned, observed=self.observed)

    def test_adopted_target_requires_exact_owned_addresses_and_ids(self):
        for owned in [{}, {address: 'wrong' for address in self.validator.TARGETS}]:
            with self.assertRaises(self.validator.PlanValidationError):
                self.validator.validate_owned_production_plan(self.plan, owned, observed=self.observed)

    def test_import_only_diagnostic_rejects_resize(self):
        with self.assertRaises(self.validator.PlanValidationError):
            self.validate(import_only=True)
        for resource in self.plan['resource_changes']:
            change = resource['change']
            change['after'] = copy.deepcopy(change['before'])
            change['actions'] = ['no-op']
        self.assertEqual(self.validate(import_only=True)['imports'], 2)

    def test_ownership_required_and_target_must_be_unowned(self):
        for ownership in [None, {}, dict(self.ownership, account_id='000000000000')]:
            with self.assertRaises(self.validator.PlanValidationError):
                self.validator.validate_production_plan(self.plan, ownership, observed=self.observed)
        self.ownership['states']['deploy/production/terraform.tfstate'] = [{'address': 'module.service.aws_instance.app', 'id': 'i-0f47bb1f028cd29a9'}]
        self.reject()


if __name__ == '__main__':
    unittest.main()
