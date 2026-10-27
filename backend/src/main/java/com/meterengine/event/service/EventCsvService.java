package com.meterengine.event.service;

import com.meterengine.event.dto.Event;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class EventCsvService {
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
    List<Event> events = eventQueryService.export(organizationId, customerId, search, type);
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
    StringBuilder csv = new StringBuilder("\ufeff");
    append(csv, columns);
    for (Event event : events) {
      List<String> values =
          new ArrayList<>(
              List.of(
                  cell(event.transactionId()),
                  cell(event.customerId()),
                  cell(event.customerName()),
                  cell(event.type()),
                  cell(event.occurredAt()),
                  cell(event.receivedAt()),
                  cell(event.requestId()),
                  cell(event.actorType()),
                  cell(event.actorId())));
      if (includeProperties) values.add(cell(event.propertiesJson()));
      append(csv, values);
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"events.csv\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String cell(Object value) {
    if (value == null) return "";
    String text = value.toString();
    String trimmed = text.stripLeading();
    if (!text.isEmpty()
        && ("=+-@\t\r\n".indexOf(text.charAt(0)) >= 0
            || !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)) {
      return "'" + text;
    }
    return text;
  }

  private static void append(StringBuilder csv, List<String> values) {
    csv.append(
            values.stream()
                .map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")))
        .append("\r\n");
  }
}
