package com.meterengine.event.service;

import com.meterengine.event.dto.Event;
import com.meterengine.global.csv.CsvExport;
import com.meterengine.global.time.BillingMonth;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class EventCsvService {
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");
  private final EventQueryService eventQueryService;

  EventCsvService(EventQueryService eventQueryService) {
    this.eventQueryService = eventQueryService;
  }

  public ResponseEntity<byte[]> export(
      UUID organizationId,
      UUID customerId,
      EventSearch search,
      String type,
      boolean includeProperties) {
    List<Event> events =
        eventQueryService.export(organizationId, customerId, search, type, includeProperties);
    List<String> columns =
        new ArrayList<>(
            List.of(
                "transaction_id",
                "customer_id",
                "customer_name",
                "type",
                "occurred_at",
                "received_at",
                "request_id",
                "actor_type",
                "actor_id"));
    if (includeProperties) columns.add("properties");
    List<List<String>> rows = new ArrayList<>();
    rows.add(columns.stream().map(CsvExport::quote).toList());
    for (Event event : events) {
      List<String> values =
          new ArrayList<>(
              List.of(
                  cell(event.transactionId()),
                  cell(event.customerId()),
                  cell(event.customerName()),
                  cell(event.type()),
                  cell(timestamp(event.occurredAt())),
                  cell(timestamp(event.receivedAt())),
                  cell(event.requestId()),
                  cell(event.actorType()),
                  cell(event.actorId())));
      if (includeProperties) values.add(cell(event.propertiesJson()));
      rows.add(values);
    }
    return CsvExport.response("events", rows);
  }

  private static String timestamp(OffsetDateTime value) {
    return value == null ? null : value.atZoneSameInstant(BillingMonth.ZONE).format(TIMESTAMP);
  }

  private static String cell(Object value) {
    return CsvExport.quote(value == null ? "" : CsvExport.formulaSafe(value.toString()));
  }
}
