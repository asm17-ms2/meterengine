package com.meterengine.query.dto;

import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.payment.dto.InvoicePaymentResponse;

public record InvoiceSearchResponse(InvoiceResponse invoice, InvoicePaymentResponse payment) {}
