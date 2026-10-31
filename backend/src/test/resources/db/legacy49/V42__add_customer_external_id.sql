ALTER TABLE customer ADD COLUMN external_id VARCHAR(255);
ALTER TABLE customer ADD CONSTRAINT customer_external_id_check CHECK (external_id IS NULL OR length(external_id) BETWEEN 1 AND 255);
CREATE UNIQUE INDEX customer_organization_external_id_unique ON customer (organization_id, external_id) WHERE external_id IS NOT NULL;
