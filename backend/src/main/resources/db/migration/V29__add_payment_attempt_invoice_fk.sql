ALTER TABLE payment_attempt
  ADD CONSTRAINT payment_attempt_invoice_same_organization_fk
  FOREIGN KEY (organization_id, invoice_id)
  REFERENCES invoice (organization_id, id);
