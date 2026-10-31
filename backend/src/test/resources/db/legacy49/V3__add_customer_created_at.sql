ALTER TABLE customer ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE customer ALTER COLUMN created_at SET DEFAULT clock_timestamp();
