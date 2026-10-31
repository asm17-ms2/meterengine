ALTER TABLE price_rate DROP CONSTRAINT price_rate_pk;
ALTER TABLE price_rate ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE price_rate ADD CONSTRAINT price_rate_pk PRIMARY KEY (id);
ALTER TABLE price_rate ADD COLUMN customer_id UUID;
ALTER TABLE price_rate ADD COLUMN effective_from VARCHAR NOT NULL DEFAULT '2000-01';
ALTER TABLE price_rate ADD COLUMN effective_until VARCHAR;
ALTER TABLE price_rate ADD COLUMN model VARCHAR NOT NULL DEFAULT 'per_unit';
ALTER TABLE price_rate ADD COLUMN tiers JSONB;
ALTER TABLE price_rate ALTER COLUMN unit_price DROP NOT NULL;
ALTER TABLE price_rate ADD CONSTRAINT price_rate_customer_same_organization_fk
  FOREIGN KEY (organization_id, customer_id) REFERENCES customer (organization_id, id);
ALTER TABLE price_rate ADD CONSTRAINT price_rate_effective_from_check
  CHECK (effective_from ~ '^[0-9]{4}-(0[1-9]|1[0-2])$' AND effective_from >= '0001-01');
ALTER TABLE price_rate ADD CONSTRAINT price_rate_effective_until_check
  CHECK (effective_until IS NULL OR (effective_until ~ '^[0-9]{4}-(0[1-9]|1[0-2])$' AND effective_until > effective_from));

CREATE FUNCTION valid_price_tiers(tiers JSONB) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE item JSONB; previous_limit NUMERIC := 0; position INTEGER := 0; limit_value NUMERIC;
BEGIN
  IF tiers IS NULL OR jsonb_typeof(tiers) <> 'array' THEN RETURN FALSE; END IF;
  IF jsonb_array_length(tiers) < 1 OR jsonb_array_length(tiers) > 100 THEN RETURN FALSE; END IF;
  FOR item IN SELECT value FROM jsonb_array_elements(tiers) LOOP
    position := position + 1;
    IF jsonb_typeof(item) <> 'object' OR NOT (item ? 'unit_price') OR item->'unit_price' = 'null'::jsonb OR jsonb_typeof(item->'unit_price') <> 'number'
      OR (item->>'unit_price')::numeric < 0 THEN RETURN FALSE; END IF;
    IF item->'up_to' IS NULL OR item->'up_to' = 'null'::jsonb THEN
      IF position <> jsonb_array_length(tiers) THEN RETURN FALSE; END IF;
    ELSE
      IF jsonb_typeof(item->'up_to') <> 'number' THEN RETURN FALSE; END IF;
      limit_value := (item->>'up_to')::numeric;
      IF limit_value <= previous_limit OR position = jsonb_array_length(tiers) THEN RETURN FALSE; END IF;
      previous_limit := limit_value;
    END IF;
  END LOOP;
  RETURN TRUE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END $$;

ALTER TABLE price_rate ADD CONSTRAINT price_rate_model_check CHECK (
  (model = 'per_unit' AND unit_price IS NOT NULL AND tiers IS NULL)
  OR (model IN ('graduated', 'volume') AND unit_price IS NULL AND valid_price_tiers(tiers)));
CREATE UNIQUE INDEX price_rate_organization_version_unique
  ON price_rate (organization_id, billable_metric_code, dimension_values, effective_from)
  WHERE customer_id IS NULL;
CREATE UNIQUE INDEX price_rate_customer_version_unique
  ON price_rate (organization_id, billable_metric_code, dimension_values, customer_id, effective_from)
  WHERE customer_id IS NOT NULL;
