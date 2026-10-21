CREATE TABLE outbound_email (
  id              UUID NOT NULL,
  organization_id UUID NOT NULL,
  kind            VARCHAR NOT NULL
                    CONSTRAINT outbound_email_kind_check
                    CHECK (kind IN ('invoice', 'finalization_report')),
  period          VARCHAR NOT NULL
                    CONSTRAINT outbound_email_period_format
                    CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  invoice_id      UUID,
  recipient_email VARCHAR,
  subject         VARCHAR NOT NULL,
  html_body       VARCHAR NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL,

  CONSTRAINT outbound_email_pk PRIMARY KEY (id),

  CONSTRAINT outbound_email_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id),

  CONSTRAINT outbound_email_invoice_same_organization_fk
    FOREIGN KEY (organization_id, invoice_id)
    REFERENCES invoice (organization_id, id),

  CONSTRAINT outbound_email_kind_invoice_check
    CHECK ((kind = 'invoice') = (invoice_id IS NOT NULL))
);

CREATE FUNCTION outbound_email_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'outbound_email is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER outbound_email_block_update
  BEFORE UPDATE ON outbound_email
  FOR EACH ROW EXECUTE FUNCTION outbound_email_block_mutation();

CREATE TRIGGER outbound_email_block_delete
  BEFORE DELETE ON outbound_email
  FOR EACH ROW EXECUTE FUNCTION outbound_email_block_mutation();

CREATE TRIGGER outbound_email_block_truncate
  BEFORE TRUNCATE ON outbound_email
  FOR EACH STATEMENT EXECUTE FUNCTION outbound_email_block_mutation();
