package com.meterengine.invoice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.LongStream;

public final class InvoiceAmount {

  private InvoiceAmount() {}

  public static long ofLine(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(-1, RoundingMode.DOWN).longValueExact();
  }

  public static long sum(LongStream amounts) {
    return amounts.reduce(0L, Math::addExact);
  }
}
