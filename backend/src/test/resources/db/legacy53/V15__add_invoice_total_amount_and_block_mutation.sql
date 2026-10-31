ALTER TABLE invoice ADD COLUMN total_amount BIGINT;

UPDATE invoice SET total_amount = supply_amount + tax_amount;

ALTER TABLE invoice ALTER COLUMN total_amount SET NOT NULL;

ALTER TABLE invoice
  ADD CONSTRAINT invoice_total_amount_check CHECK (total_amount = supply_amount + tax_amount);

CREATE FUNCTION invoice_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'invoice is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_block_update
  BEFORE UPDATE ON invoice
  FOR EACH ROW EXECUTE FUNCTION invoice_block_mutation();

CREATE TRIGGER invoice_block_delete
  BEFORE DELETE ON invoice
  FOR EACH ROW EXECUTE FUNCTION invoice_block_mutation();

CREATE TRIGGER invoice_block_truncate
  BEFORE TRUNCATE ON invoice
  FOR EACH STATEMENT EXECUTE FUNCTION invoice_block_mutation();

CREATE FUNCTION invoice_line_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'invoice_line is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_line_block_update
  BEFORE UPDATE ON invoice_line
  FOR EACH ROW EXECUTE FUNCTION invoice_line_block_mutation();

CREATE TRIGGER invoice_line_block_delete
  BEFORE DELETE ON invoice_line
  FOR EACH ROW EXECUTE FUNCTION invoice_line_block_mutation();

CREATE TRIGGER invoice_line_block_truncate
  BEFORE TRUNCATE ON invoice_line
  FOR EACH STATEMENT EXECUTE FUNCTION invoice_line_block_mutation();
