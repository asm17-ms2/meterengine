package com.meterengine.event.service;

import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.repository.UnbilledEventRepository;
import com.meterengine.global.time.BillingMonth;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnbilledEventService {
  private final UnbilledEventRepository unbilledEventRepository;
  private final Clock clock;

  UnbilledEventService(UnbilledEventRepository unbilledEventRepository, Clock clock) {
    this.unbilledEventRepository = unbilledEventRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public UnbilledEventsResponse get(UUID organizationId) {
    return get(organizationId, null);
  }

  @Transactional(readOnly = true)
  public UnbilledEventsResponse get(UUID organizationId, YearMonth month) {
    OffsetDateTime now = BillingMonth.now(clock);
    return aggregate(organizationId, month == null ? YearMonth.from(now) : month, now);
  }

  @Transactional(readOnly = true)
  public UnbilledEventsResponse aggregate(
      UUID organizationId, YearMonth month, OffsetDateTime now) {
    OffsetDateTime startAt = BillingMonth.start(month);
    OffsetDateTime endAt = BillingMonth.end(month);
    OffsetDateTime cutoffAt = BillingMonth.cutoff(month, now);
    return new UnbilledEventsResponse(
        month.toString(),
        cutoffAt,
        unbilledEventRepository.findUnmatchedTypes(organizationId, startAt, cutoffAt),
        unbilledEventRepository.findNonNumericValues(organizationId, startAt, cutoffAt),
        unbilledEventRepository.findFutureTimestamps(organizationId, startAt, endAt, cutoffAt),
        unbilledEventRepository.findNegativeQuantities(organizationId, startAt, cutoffAt));
  }
}
