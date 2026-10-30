package com.meterengine.event.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.validation.StorableTimestampValidator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

public record EventSearch(
    String month,
    OffsetDateTime fromAt,
    OffsetDateTime toAt,
    String query,
    String sort,
    String order,
    String properties) {

  private static final Set<String> SORT_FIELDS =
      Set.of("occurred_at", "received_at", "transaction_id", "customer_name", "type");

  public static EventSearch resolve(
      YearMonth month,
      String from,
      String to,
      String query,
      String sort,
      String order,
      String properties) {
    boolean ranged = from != null || to != null;
    if (ranged && (from == null || to == null)) {
      throw invalid("from", "기간 조회에는 시작과 종료 시각이 모두 필요합니다");
    }
    if (ranged && month != null) {
      throw invalid("month", "월 조회와 기간 조회 중 하나만 선택하세요");
    }
    YearMonth target = month == null ? YearMonth.now(ZoneId.of("Asia/Seoul")) : month;
    OffsetDateTime start =
        ranged
            ? parse(from, false)
            : target.atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    OffsetDateTime end =
        ranged
            ? parse(to, true)
            : target
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();
    if (!start.isBefore(end)) throw invalid("to", "종료 시각은 시작 시각보다 뒤여야 합니다");
    String field = sort == null ? "occurred_at" : sort;
    String direction = order == null ? "desc" : order;
    if (!SORT_FIELDS.contains(field)) throw invalid("sort", "지원하지 않는 정렬 필드입니다");
    if (!Set.of("asc", "desc").contains(direction)) throw invalid("order", "asc 또는 desc를 선택하세요");
    return new EventSearch(
        ranged ? null : target.toString(), start, end, query, field, direction, properties);
  }

  private static OffsetDateTime parse(String value, boolean endDate) {
    OffsetDateTime parsed = parseTimestamp(value, endDate);
    if (!new StorableTimestampValidator().isValid(parsed, null))
      throw invalid(endDate ? "to" : "from", "저장할 수 있는 범위 밖의 시각입니다");
    return parsed;
  }

  private static OffsetDateTime parseTimestamp(String value, boolean endDate) {
    try {
      if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
        LocalDate day = LocalDate.parse(value);
        return (endDate ? day.plusDays(1) : day)
            .atStartOfDay(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime();
      }
      try {
        return OffsetDateTime.parse(value);
      } catch (DateTimeParseException ignored) {
        return LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
      }
    } catch (DateTimeParseException | ArithmeticException exception) {
      throw invalid(endDate ? "to" : "from", "날짜 또는 ISO 8601 시각을 입력하세요. 시간대가 없으면 KST입니다");
    }
  }

  static InvalidRequestException invalid(String field, String message) {
    return new InvalidRequestException(
        ErrorCode.VALIDATION_ERROR, List.of(new FieldError(field, message)));
  }
}
