package com.meterengine.invoice.dto;

import com.meterengine.invoice.entity.Invoice;
import java.util.List;

public record InvoiceFinalizationResult(
    List<Invoice> invoices, int skippedCount, int failedCount) {}
