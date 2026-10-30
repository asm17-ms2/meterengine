package com.meterengine.invoiceview.dto;

import com.meterengine.global.validation.StorableText;

public record GetPublicInvoiceRequest(@StorableText String token) {}
