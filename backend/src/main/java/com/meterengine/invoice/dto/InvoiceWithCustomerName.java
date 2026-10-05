package com.meterengine.invoice.dto;

import com.meterengine.invoice.entity.Invoice;

public record InvoiceWithCustomerName(Invoice invoice, String customerName) {}
