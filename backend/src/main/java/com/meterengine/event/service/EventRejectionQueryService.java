package com.meterengine.event.service;

import com.meterengine.event.dto.EventRejectionsResponse;
import com.meterengine.event.repository.EventRejectionRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventRejectionQueryService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private final EventRejectionRepository eventRejectionRepository;
  private final Clock clock;

  public EventRejectionQueryService(
      EventRejectionRepository eventRejectionRepository, Clock clock) {
    this.eventRejectionRepository = eventRejectionRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public EventRejectionsResponse search(
      UUID organizationId,
      YearMonth requestedMonth,
      UUID customerId,
      String errorCode,
      String query,
      int page,
      int size) {
    YearMonth month = requestedMonth == null ? YearMonth.now(clock.withZone(ZONE)) : requestedMonth;
    return eventRejectionRepository.search(
        organizationId,
        month,
        month.atDay(1).atStartOfDay(ZONE).toOffsetDateTime(),
        month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toOffsetDateTime(),
        customerId,
        errorCode,
        query,
        page,
        size);
  }
}
