CREATE FUNCTION payment_attempt_guard_update() RETURNS trigger AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_attempt is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.customer_id, NEW.invoice_id,
         NEW.amount, NEW.order_name, NEW.requested_at)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.customer_id, OLD.invoice_id,
         OLD.amount, OLD.order_name, OLD.requested_at) THEN
    RAISE EXCEPTION 'payment_attempt can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_attempt_guard_update
  BEFORE UPDATE ON payment_attempt
  FOR EACH ROW EXECUTE FUNCTION payment_attempt_guard_update();

CREATE FUNCTION payment_attempt_block_delete() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'payment_attempt is append-only: DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_attempt_block_delete
  BEFORE DELETE ON payment_attempt
  FOR EACH ROW EXECUTE FUNCTION payment_attempt_block_delete();

CREATE TRIGGER payment_attempt_block_truncate
  BEFORE TRUNCATE ON payment_attempt
  FOR EACH STATEMENT EXECUTE FUNCTION payment_attempt_block_delete();
