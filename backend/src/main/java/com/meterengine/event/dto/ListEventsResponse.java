package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 조회 응답 본문 (MS2-131).
 *
 * <p>화면 한 페이지를 그리는 재료다. {@code total}이 페이지 번호(이전 1 2 3 다음)를 만들고, {@code events}가 표의 행이 된다.
 *
 * <p>JSON 이름은 {@code @JsonProperty}로 못박는다. 전역 SNAKE_CASE 설정은 springdoc 생성물처럼 우리가 만들지 않은 응답까지 바꾼다
 * (수집 API의 DTO와 같은 규칙).
 *
 * @param month 조회 기준 월. yyyy-MM, KST다. 요청이 month를 생략했을 때 서버가 어느 달로 계산했는지 응답만 보고 알 수 있어야 한다 (사용량 조회
 *     API의 {@code ListBillableMetricUsagesResponse.month}와 같은 이유, 같은 형식).
 * @param total 필터를 적용한 뒤의 전체 건수. 이 페이지에 담긴 개수가 아니다. 화면이 마지막 페이지 번호를 그리려면 필요하다.
 */
public record ListEventsResponse(
    String month, int page, int size, long total, List<EventResponse> events) {

  /**
   * 이벤트 한 건. 표의 한 행이 된다.
   *
   * @param properties DB의 jsonb를 문자열 그대로 싣는다. {@link JsonRawValue}가 이 문자열을 따옴표 없이 박아 넣어, 파싱과 재직렬화를
   *     한 번도 거치지 않는다. 파싱을 끼우면 정밀도가 {@code use-big-decimal-for-floats} 설정 한 줄에 매달리게 되고, 그 설정이 꺼지거나
   *     조회용 매퍼가 따로 생기면 수집은 멀쩡한데 조회만 다른 숫자를 낸다. 조회는 어느 키가 사용량인지 판정하지도 않으므로 파싱해서 얻을 것이 없다.
   *     <p>화면은 키를 가리지 않고 전부 펼쳐 보여주므로(FE 초안) 서버가 키를 골라 내보내서도 안 된다.
   *     <p>{@code @Schema}에 {@code implementation}까지 주는 이유는, {@code type}만으로는 swagger-core가 해석한 자바
   *     타입(String)을 덮지 못해 문서에 {@code type: string}으로 나가기 때문이다(실측). 그러면 생성기가 만든 클라이언트가 이 필드를 문자열로 받아
   *     실제 응답과 어긋난다.
   */
  public record EventResponse(
      @JsonProperty("transaction_id") String transactionId,
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      @JsonProperty("type") String type,
      @JsonRawValue @Schema(implementation = Map.class) String properties,
      @JsonProperty("occurred_at") OffsetDateTime occurredAt,
      @JsonProperty("received_at") OffsetDateTime receivedAt) {}
}
