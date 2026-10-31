ALTER TABLE webhook_delivery ADD CONSTRAINT webhook_delivery_failure_reason_check
  CHECK (failure_reason IN ('timeout', 'connection_error', 'unexpected_status', 'blocked_address', 'endpoint_disabled', 'secret_unavailable', 'lease_expired')) NOT VALID;
