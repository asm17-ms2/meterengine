ALTER TABLE billable_metric
  ADD CONSTRAINT billable_metric_organization_event_type_target_property_unique
  UNIQUE (organization_id, event_type, target_property);
