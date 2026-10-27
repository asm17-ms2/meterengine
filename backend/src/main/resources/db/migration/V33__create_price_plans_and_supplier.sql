CREATE TABLE price_plan (
  id UUID NOT NULL DEFAULT gen_random_uuid() CONSTRAINT price_plan_pk PRIMARY KEY,
  organization_id UUID NOT NULL CONSTRAINT price_plan_organization_fk REFERENCES organization(id),
  customer_id UUID,
  effective_from VARCHAR NOT NULL DEFAULT '2000-01',
  effective_until VARCHAR,
  base_fee BIGINT NOT NULL CONSTRAINT price_plan_base_fee_check CHECK (base_fee >= 0),
  minimum_amount BIGINT NOT NULL CONSTRAINT price_plan_minimum_amount_check CHECK (minimum_amount >= 0),
  CONSTRAINT price_plan_customer_same_organization_fk FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id),
  CONSTRAINT price_plan_effective_from_check CHECK (effective_from ~ '^[0-9]{4}-(0[1-9]|1[0-2])$' AND effective_from >= '0001-01'),
  CONSTRAINT price_plan_effective_until_check CHECK (effective_until IS NULL OR
    (effective_until ~ '^[0-9]{4}-(0[1-9]|1[0-2])$' AND effective_until > effective_from))
);
CREATE UNIQUE INDEX price_plan_organization_version_unique ON price_plan (organization_id, effective_from)
  WHERE customer_id IS NULL;
CREATE UNIQUE INDEX price_plan_customer_version_unique ON price_plan (organization_id, customer_id, effective_from)
  WHERE customer_id IS NOT NULL;
ALTER TABLE organization ADD COLUMN business_registration_number VARCHAR(10);
ALTER TABLE organization ADD COLUMN representative_name VARCHAR(255) COLLATE korean;
ALTER TABLE organization ADD COLUMN business_address VARCHAR(1000);
ALTER TABLE organization ADD CONSTRAINT organization_business_registration_number_check
  CHECK (business_registration_number IS NULL OR business_registration_number ~ '^[0-9]{10}$');
