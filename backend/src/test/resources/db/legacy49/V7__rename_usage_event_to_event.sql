ALTER TABLE usage_event RENAME TO event;
ALTER TABLE event RENAME COLUMN event_type TO type;
ALTER TABLE event RENAME CONSTRAINT usage_event_pkey TO event_pkey;
ALTER TABLE event RENAME CONSTRAINT usage_event_organization_id_fkey TO event_organization_id_fkey;
ALTER TABLE event RENAME CONSTRAINT usage_event_customer_same_org TO event_customer_same_org;
ALTER TABLE event RENAME CONSTRAINT usage_event_organization_id_not_null TO event_organization_id_not_null;
ALTER TABLE event RENAME CONSTRAINT usage_event_transaction_id_not_null TO event_transaction_id_not_null;
ALTER TABLE event RENAME CONSTRAINT usage_event_customer_id_not_null TO event_customer_id_not_null;
ALTER TABLE event RENAME CONSTRAINT usage_event_event_type_not_null TO event_type_not_null;
ALTER TABLE event RENAME CONSTRAINT usage_event_properties_not_null TO event_properties_not_null;
ALTER TABLE event RENAME CONSTRAINT usage_event_occurred_at_not_null TO event_occurred_at_not_null;
ALTER TABLE event RENAME CONSTRAINT usage_event_received_at_not_null TO event_received_at_not_null;
ALTER TRIGGER usage_event_set_received_at ON event RENAME TO event_set_received_at;
ALTER TRIGGER usage_event_block_update ON event RENAME TO event_block_update;
ALTER TRIGGER usage_event_block_delete ON event RENAME TO event_block_delete;
ALTER TRIGGER usage_event_block_truncate ON event RENAME TO event_block_truncate;
ALTER FUNCTION usage_event_set_received_at() RENAME TO event_set_received_at;
ALTER FUNCTION usage_event_block_mutation() RENAME TO event_block_mutation;

CREATE OR REPLACE FUNCTION event_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'event is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;
