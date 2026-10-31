ALTER TABLE billable_metric
  DROP CONSTRAINT billable_metric_organization_event_type_target_property_unique,
  ADD COLUMN unit_size NUMERIC NOT NULL DEFAULT 1 CHECK (unit_size > 0),
  ADD COLUMN rounding_mode VARCHAR NOT NULL DEFAULT 'none'
    CHECK (rounding_mode IN ('none', 'ceil', 'floor', 'round')),
  ADD COLUMN rounding_precision INTEGER NOT NULL DEFAULT 0
    CHECK (rounding_precision BETWEEN 0 AND 12),
  ADD COLUMN percentile NUMERIC CHECK (percentile BETWEEN 0 AND 100),
  ADD CONSTRAINT billable_metric_supported_aggregation
    CHECK (aggregation IN ('sum', 'count', 'count_distinct', 'min', 'max', 'avg', 'first', 'last', 'median', 'percentile')),
  ADD CONSTRAINT billable_metric_percentile_configuration
    CHECK ((aggregation = 'percentile') = (percentile IS NOT NULL)),
  ADD CONSTRAINT billable_metric_target_configuration
    CHECK ((aggregation = 'count' AND target_property IS NULL)
      OR (aggregation <> 'count' AND target_property IS NOT NULL AND btrim(target_property) <> ''));
