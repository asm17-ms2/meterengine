package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListInvoicePaymentsResponse(
    @JsonProperty("invoice_payments") List<InvoicePaymentResponse> invoicePayments) {}
