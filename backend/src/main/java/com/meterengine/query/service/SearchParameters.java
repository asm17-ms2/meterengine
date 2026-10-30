package com.meterengine.query.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.validation.UnknownQueryParameterValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SearchParameters(
    String query,
    String status,
    String sort,
    String order,
    int page,
    int size,
    UUID customerId,
    String metricCode,
    String externalId,
    YearMonth month,
    OffsetDateTime fromAt,
    OffsetDateTime toAt,
    Long minAmount,
    Long maxAmount,
    String mode) {

  private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");

  public static SearchParameters parse(Map<String, String> parameters, String resource) {
    Set<String> specific =
        switch (resource) {
          case "customers" -> Set.of("status", "external_id", "customer_id");
          case "metrics" -> Set.of("status", "billable_metric_code");
          case "prices", "usage" ->
              Set.of("status", "month", "customer_id", "billable_metric_code");
          case "drafts" ->
              Set.of("month", "customer_id", "billable_metric_code", "min_amount", "max_amount");
          case "invoices" ->
              Set.of(
                  "month",
                  "customer_id",
                  "billable_metric_code",
                  "from",
                  "to",
                  "status",
                  "mode",
                  "min_amount",
                  "max_amount");
          default -> throw new IllegalArgumentException("Unknown query resource");
        };
    Set<String> supported = new java.util.HashSet<>(specific);
    supported.addAll(Set.of("q", "sort", "order", "page", "size"));
    UnknownQueryParameterValidator.validate(parameters, supported);
    return parse(parameters);
  }

  public static SearchParameters parse(Map<String, String> parameters) {
    parameters.forEach((key, value) -> validateText(key, value));
    int page = integer(parameters, "page", 1, 1, Integer.MAX_VALUE);
    int size = integer(parameters, "size", 25, 1, 200);
    String direction = parameters.getOrDefault("order", "asc");
    if (!Set.of("asc", "desc").contains(direction)) throw invalid("order", "asc 또는 desc를 선택하세요");
    UUID customer = null;
    if (present(parameters.get("customer_id"))) {
      try {
        String value = parameters.get("customer_id");
        customer = UUID.fromString(value);
        if (!customer.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
      } catch (IllegalArgumentException exception) {
        throw invalid("customer_id", "정규 UUID를 입력하세요");
      }
    }
    YearMonth month = null;
    if (present(parameters.get("month"))) {
      try {
        String value = parameters.get("month");
        if (!value.matches("[0-9]{4}-[0-9]{2}"))
          throw new DateTimeParseException("month", value, 0);
        month = YearMonth.parse(value);
        if (month.getYear() < 1 || month.getYear() > 9999)
          throw new DateTimeParseException("month", value, 0);
      } catch (DateTimeParseException exception) {
        throw invalid("month", "0001-01부터 9999-12까지의 yyyy-MM을 입력하세요");
      }
    }
    OffsetDateTime start = date(parameters.get("from"), false);
    OffsetDateTime end = date(parameters.get("to"), true);
    if (start != null && end != null && !start.isBefore(end))
      throw invalid("to", "종료는 시작보다 뒤여야 합니다");
    Long minimum = amount(parameters, "min_amount");
    Long maximum = amount(parameters, "max_amount");
    if (minimum != null && maximum != null && minimum > maximum)
      throw invalid("max_amount", "최대 금액은 최소 금액보다 작을 수 없습니다");
    String mode = parameters.get("mode");
    if (present(mode) && !Set.of("test", "live").contains(mode))
      throw invalid("mode", "test 또는 live를 선택하세요");
    return new SearchParameters(
        blankToNull(parameters.get("q")),
        blankToNull(parameters.get("status")),
        blankToNull(parameters.get("sort")),
        direction,
        page,
        size,
        customer,
        blankToNull(parameters.get("billable_metric_code")),
        blankToNull(parameters.get("external_id")),
        month,
        start,
        end,
        minimum,
        maximum,
        blankToNull(mode));
  }

  public String sortOr(String fallback, Set<String> supported) {
    String value = sort == null ? fallback : sort;
    if (!supported.contains(value)) throw invalid("sort", "지원하지 않는 정렬 필드입니다");
    return value;
  }

  public boolean descending() {
    return "desc".equals(order);
  }

  public long offset() {
    return Math.multiplyExact((long) page - 1, size);
  }

  public YearMonth monthOrCurrent() {
    return month == null ? YearMonth.now(BILLING_ZONE) : month;
  }

  public SearchParameters forExport() {
    return new SearchParameters(
        query,
        status,
        sort,
        order,
        1,
        10001,
        customerId,
        metricCode,
        externalId,
        month,
        fromAt,
        toAt,
        minAmount,
        maxAmount,
        mode);
  }

  public <T> List<T> page(List<T> values) {
    long offset = offset();
    if (offset >= values.size()) return List.of();
    int start = (int) offset;
    return values.subList(start, (int) Math.min(values.size(), offset + size));
  }

  public boolean matches(String... values) {
    if (query == null) return true;
    String needle = query.toLowerCase(java.util.Locale.ROOT);
    return java.util.Arrays.stream(values)
        .filter(java.util.Objects::nonNull)
        .anyMatch(value -> value.toLowerCase(java.util.Locale.ROOT).contains(needle));
  }

  public static InvalidRequestException invalid(String field, String message) {
    return new InvalidRequestException(
        ErrorCode.VALIDATION_ERROR, List.of(new FieldError(field, message)));
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static String blankToNull(String value) {
    return present(value) ? value : null;
  }

  private static void validateText(String field, String value) {
    if (value == null) return;
    if (value.length() > 2000
        || value.codePoints().anyMatch(code -> code == 0 || code >= 0xd800 && code <= 0xdfff))
      throw invalid(field, "길이 또는 문자 형식이 올바르지 않습니다");
  }

  private static int integer(
      Map<String, String> values, String key, int fallback, int min, int max) {
    String value = values.get(key);
    if (value == null) return fallback;
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < min || parsed > max) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException exception) {
      throw invalid(key, min + "부터 " + max + "까지의 정수를 입력하세요");
    }
  }

  private static Long amount(Map<String, String> values, String key) {
    String value = values.get(key);
    if (!present(value)) return null;
    try {
      long amount = new BigDecimal(value).longValueExact();
      if (amount < 0) throw new ArithmeticException();
      return amount;
    } catch (ArithmeticException | NumberFormatException exception) {
      throw invalid(key, "0 이상의 원 단위 정수를 입력하세요");
    }
  }

  private static OffsetDateTime date(String value, boolean end) {
    if (!present(value)) return null;
    try {
      if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        LocalDate day = LocalDate.parse(value);
        return (end ? day.plusDays(1) : day).atStartOfDay(BILLING_ZONE).toOffsetDateTime();
      }
      try {
        return OffsetDateTime.parse(value);
      } catch (DateTimeParseException exception) {
        return LocalDateTime.parse(value).atZone(BILLING_ZONE).toOffsetDateTime();
      }
    } catch (java.time.DateTimeException | ArithmeticException exception) {
      throw invalid(end ? "to" : "from", "날짜 또는 ISO 8601 시각을 입력하세요. 시간대 생략은 KST입니다");
    }
  }
}
