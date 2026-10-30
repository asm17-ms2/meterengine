package com.meterengine.payment.service;

import com.meterengine.payment.dto.PaymentJobResult;

// 작업 하나가 후보마다 낸 결과를 센다.
final class PaymentJobTally {

  private int processedCount;
  private int skippedCount;
  private int failedCount;

  void count(boolean processed) {
    if (processed) {
      processedCount++;
    } else {
      skippedCount++;
    }
  }

  void countFailed() {
    failedCount++;
  }

  PaymentJobResult result() {
    return new PaymentJobResult(processedCount, skippedCount, failedCount);
  }
}
