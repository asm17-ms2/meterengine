ALTER TABLE invoice ADD COLUMN customer_name VARCHAR COLLATE korean;

ALTER TABLE invoice DISABLE TRIGGER invoice_block_update;

UPDATE invoice
SET customer_name = customer.name
FROM customer
WHERE customer.organization_id = invoice.organization_id
  AND customer.id = invoice.customer_id;

ALTER TABLE invoice ENABLE TRIGGER invoice_block_update;

ALTER TABLE invoice ALTER COLUMN customer_name SET NOT NULL;
