package com.meterengine.invoice.service;

import com.meterengine.global.config.ConditionalOnServerRole;
import com.meterengine.global.config.ServerRole;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 예약 작업을 도는 역할만 기동 때 센다. 돌지 않는 서버는 다시 세지 못해 기동 때 값이 굳고,
// 경보는 서버들 가운데 가장 큰 값을 보므로 그 서버를 바꿀 때까지 풀리지 않는다.
@Component
@ConditionalOnServerRole({ServerRole.ALL, ServerRole.API})
class InvoiceFinalizationMetricsStartup {

  private final InvoiceFinalizationMetrics invoiceFinalizationMetrics;

  InvoiceFinalizationMetricsStartup(InvoiceFinalizationMetrics invoiceFinalizationMetrics) {
    this.invoiceFinalizationMetrics = invoiceFinalizationMetrics;
  }

  @EventListener(ApplicationReadyEvent.class)
  void refresh() {
    invoiceFinalizationMetrics.refresh();
  }
}
