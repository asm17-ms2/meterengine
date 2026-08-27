package com.meterengine.customer.exception;

import java.util.UUID;

public class CustomerHasInvoicesException extends RuntimeException {

  public CustomerHasInvoicesException(UUID customerId) {
    super(
        "customer %s has finalized invoices; deleting it would leave an issued invoice without a customer"
            .formatted(customerId));
  }
}
