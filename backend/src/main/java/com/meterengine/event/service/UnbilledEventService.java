package com.meterengine.event.service;

import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.repository.UnbilledEventRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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
    OffsetDateTime now =
        OffsetDateTime.now(clock).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    return aggregate(organizationId, month == null ? YearMonth.from(now) : month, now);
  }

  @Transactional(readOnly = true)
  public UnbilledEventsResponse aggregate(
      UUID organizationId, YearMonth month, OffsetDateTime now) {
    OffsetDateTime startAt =
        month.atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    OffsetDateTime endAt =
        month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    OffsetDateTime cutoffAt = (now.isBefore(endAt) ? now : endAt);
    return new UnbilledEventsResponse(
        month.toString(),
        cutoffAt,
        unbilledEventRepository.findUnmatchedTypes(organizationId, startAt, cutoffAt),
        unbilledEventRepository.findNonNumericValues(organizationId, startAt, cutoffAt),
        unbilledEventRepository.findFutureTimestamps(organizationId, startAt, endAt, cutoffAt),
        unbilledEventRepository.findNegativeQuantities(organizationId, startAt, cutoffAt));
  }
}
