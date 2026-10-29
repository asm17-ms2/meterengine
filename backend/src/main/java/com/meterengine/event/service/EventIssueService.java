package com.meterengine.event.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.api.UsagePriceDiagnostics;
import com.meterengine.event.dto.EventIssuesResponse;
import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.repository.EventIssueRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventIssueService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private final EventIssueRepository eventIssueRepository;
  private final CustomerDirectory customerDirectory;
  private final UsagePriceDiagnostics usagePriceDiagnostics;
  private final UnbilledEventService unbilledEventService;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public EventIssueService(
      EventIssueRepository eventIssueRepository,
      CustomerDirectory customerDirectory,
      UsagePriceDiagnostics usagePriceDiagnostics,
      UnbilledEventService unbilledEventService,
      JsonMapper jsonMapper,
      Clock clock) {
    this.eventIssueRepository = eventIssueRepository;
    this.customerDirectory = customerDirectory;
    this.usagePriceDiagnostics = usagePriceDiagnostics;
    this.unbilledEventService = unbilledEventService;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public EventIssuesResponse search(
      UUID organizationId,
      YearMonth requestedMonth,
      UUID customerId,
      String reason,
      String query,
      int page,
      int size) {
    OffsetDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(ZONE).toOffsetDateTime();
    YearMonth month = requestedMonth == null ? YearMonth.from(now) : requestedMonth;
    OffsetDateTime start = month.atDay(1).atStartOfDay(ZONE).toOffsetDateTime();
    OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toOffsetDateTime();
    OffsetDateTime cutoff = now.isBefore(end) ? now : end;
    if (customerId != null && !customerDirectory.exists(organizationId, customerId))
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    var groups =
        usagePriceDiagnostics.unpriced(organizationId, month, cutoff).stream()
            .map(
                group ->
                    Map.of(
                        "customer_id",
                        group.customerId().toString(),
                        "billable_metric_code",
                        group.billableMetricCode(),
                        "dimension_values",
                        group.valueByDimension()))
            .toList();
    var result =
        eventIssueRepository.search(
            organizationId,
            month,
            start,
            cutoff,
            customerId,
            reason,
            query,
            page,
            size,
            jsonMapper.writeValueAsString(groups));
    var warnings = unbilledEventService.aggregate(organizationId, month, cutoff);
    return new EventIssuesResponse(
        month.toString(),
        cutoff,
        page,
        size,
        result.summary().total(),
        result.issues(),
        result.summary(),
        new EventIssuesResponse.Warnings(
            "organization_month",
            warnings.futureTimestamp().eventCount(),
            warnings.negativeQuantity().billableMetrics().stream()
                .mapToLong(UnbilledEventsResponse.MetricCount::eventCount)
                .sum()));
  }
}
