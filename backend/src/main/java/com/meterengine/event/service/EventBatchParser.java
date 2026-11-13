package com.meterengine.event.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse.EventResult;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.error.ErrorResponse.FieldError;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 배치의 원소를 하나씩 읽고 형식을 검사한다. 형식이 틀린 원소는 거절 결과로, 맞는 원소는 요청으로 돌려준다.
@Component
public class EventBatchParser {

  static final String REJECTED = "rejected";

  private static final String TRANSACTION_ID = jsonField("transactionId");
  private static final String TIMESTAMP = jsonField("occurredAt");

  private final Validator validator;
  private final JsonMapper jsonMapper;

  EventBatchParser(Validator validator, JsonMapper jsonMapper) {
    this.validator = validator;
    this.jsonMapper =
        jsonMapper
            .rebuild()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();
  }

  ParsedEventBatch parse(IngestEventBatchRequest batch) {
    List<EventResult> results = new ArrayList<>(Collections.nCopies(batch.events().size(), null));
    Map<Integer, IngestEventRequest> requestsByIndex = new LinkedHashMap<>();
    for (int index = 0; index < batch.events().size(); index++) {
      JsonNode node = batch.events().get(index);
      String transactionId =
          node != null && node.isObject() && node.path(TRANSACTION_ID).isString()
              ? node.path(TRANSACTION_ID).asString()
              : null;
      IngestEventRequest request;
      try {
        if (node == null || !node.isObject()) {
          throw new IllegalArgumentException();
        }
        if (node.hasNonNull(TIMESTAMP)) {
          OffsetDateTime.parse(node.path(TIMESTAMP).asString());
        }
        request = jsonMapper.treeToValue(node, IngestEventRequest.class);
      } catch (RuntimeException exception) {
        String field =
            node != null
                    && node.isObject()
                    && node.has(TIMESTAMP)
                    && !validTimestamp(node.path(TIMESTAMP))
                ? "." + TIMESTAMP
                : "";
        results.set(
            index,
            rejected(
                index,
                transactionId,
                ErrorCode.VALIDATION_ERROR,
                List.of(new FieldError("events[" + index + "]" + field, "이벤트 필드의 형식이 올바르지 않습니다"))));
        continue;
      }
      final int resultIndex = index;
      List<FieldError> errors =
          validator.validate(request).stream()
              .map(
                  violation ->
                      new FieldError(
                          "events["
                              + resultIndex
                              + "]."
                              + jsonField(violation.getPropertyPath().toString()),
                          violation.getMessage()))
              .sorted(Comparator.comparing(FieldError::field))
              .toList();
      if (!errors.isEmpty()) {
        results.set(index, rejected(index, transactionId, ErrorCode.VALIDATION_ERROR, errors));
      } else {
        requestsByIndex.put(index, request);
      }
    }
    return new ParsedEventBatch(results, requestsByIndex);
  }

  static EventResult rejected(
      int index, String transactionId, ErrorCode code, List<FieldError> errors) {
    return new EventResult(index, transactionId, REJECTED, ErrorResponse.of(code, errors));
  }

  static EventResult tooOld(int index, String transactionId) {
    return rejected(
        index,
        transactionId,
        ErrorCode.EVENT_TOO_OLD,
        List.of(new FieldError("events[" + index + "].timestamp", "수집 판정 시각보다 35일 넘게 과거인 시각입니다")));
  }

  private static String jsonField(String javaField) {
    try {
      JsonProperty property =
          IngestEventRequest.class.getDeclaredField(javaField).getAnnotation(JsonProperty.class);
      return property == null || property.value().isEmpty() ? javaField : property.value();
    } catch (NoSuchFieldException exception) {
      return javaField;
    }
  }

  private static boolean validTimestamp(JsonNode node) {
    try {
      OffsetDateTime.parse(node.asString());
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  // results는 요청과 같은 길이이고, 형식이 맞는 원소 자리는 아직 비어 있다.
  record ParsedEventBatch(
      List<EventResult> results, Map<Integer, IngestEventRequest> requestsByIndex) {}
}
