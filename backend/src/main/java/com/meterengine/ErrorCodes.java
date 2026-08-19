package com.meterengine;

/**
 * 오류 응답의 {@code code} 확장 멤버에 쓰는 값의 정본 (MS2-150).
 *
 * <p><b>왜 상수로 뽑나.</b> 같은 문자열이 여러 곳에 리터럴로 흩어져 있으면 하나를 고쳐도 나머지가 조용히 낡는다. 검사 수단도 없다. 어휘 대조를 {@code
 * grep}으로 호출 지점의 문자열 리터럴을 긁는 방식에 맡기면, 리터럴이 사라지는 순간 그 grep이 실패가 아니라 <b>0건</b>을 뱉고 대조는 "양쪽 다 비었으니
 * 같다"로 통과한다. 상수로 묶으면 값 변경이 컴파일러를 타고 전파되고, 대조는 이 파일 하나만 보면 된다.
 *
 * <p><b>왜 루트 패키지인가.</b> 참조하는 쪽이 두 패키지에 걸쳐 있다. 응답을 만드는 {@code com.meterengine.event.controller}의
 * advice와, 문서 스키마를 선언하는 루트의 {@code ProblemResponse}다. 어느 한쪽 도메인에 두면 다른 쪽이 그 도메인을 import하게 된다.
 * MS2-149 재구조화에서 도메인 패키지는 customer, event, invoice, metric 넷으로 정했는데 이 클래스는 어느 도메인 소속도 아니라, {@code
 * OpenApiConfig}와 같이 루트에 남긴다 (MS2-141은 취소됨).
 *
 * <p><b>왜 {@code public}인가.</b> {@code event.controller} 패키지의 advice가 참조해야 한다. 루트 안에서만 쓰인다면
 * package-private으로 충분했다.
 *
 * <p>값이 모두 컴파일 타임 상수다. 그래서 {@code @Schema(allowableValues = {ErrorCodes.VALIDATION_ERROR, ...})}처럼
 * 애너테이션 멤버에 그대로 넣을 수 있고, 문서의 enum이 이 파일에서 나온다.
 *
 * <p><b>code를 늘리거나 없애면 이 파일과 테스트 메서드 이름만 고친다.</b> 값을 참조하는 곳은 전부 이 상수를 거친다(운영 코드, 문서 스키마의 {@code
 * allowableValues}, 통합 테스트 단언). 예외는 테스트 <b>메서드 이름</b>에 code가 박힌 자리인데, 자바 식별자라 상수로 만들 수 없다. 그 짝을 지키는
 * 검사는 MS2-150 인수기준 5에 있다.
 *
 * <p>멤버 <b>이름</b>({@code code}, {@code errors} 등)은 {@link ProblemMembers}에 있다. 여기는 값의 어휘만 담는다.
 */
public final class ErrorCodes {

  /**
   * 형식 검증 실패. 헤더 누락, 쿼리 파라미터 타입 불일치, 본문 필드 제약 위반이 여기 해당한다.
   *
   * <p>이 값일 때만 {@code errors} 배열이 실린다.
   */
  public static final String VALIDATION_ERROR = "validation_error";

  /**
   * 요청이 실어 보낸 {@code customer_id}로 (도입사, 고객) 조합을 찾지 못했다. <b>400이다.</b>
   *
   * <p><b>2026-08-17에 {@code customer_not_found}에서 개명했다 (MS2-150 A-3).</b> 옛 이름은 404를 연상시키는데 이 오류는
   * 400이다. URL이 가리키는 자원({@code /v1/events})은 존재하고, 잘못된 것은 본문이나 쿼리에 실린 <b>값</b>이다. 그래서 "찾을 수 없다"가
   * 아니라 "가리킨 참조가 알 수 없다"가 맞다.
   *
   * <p><b>{@code customer_not_found}는 비워 둔다.</b> MS2-155의 {@code GET /v1/customers/{id}}가 404로 쓴다.
   * code 하나는 (HTTP 상태, 의미) 하나만 가리킨다는 규칙이라 재사용하지 않는다.
   *
   * <p>이 오류는 {@code X-Organization-Id}가 틀렸을 때도 난다. 조회가 두 값의 조합이라 어느 쪽이 틀렸는지 구분되지 않는다. FE 문구가 둘을 함께
   * 짚어야 하는 이유다 ({@code UnknownCustomerException} javadoc 참조).
   */
  public static final String UNKNOWN_CUSTOMER_REFERENCE = "unknown_customer_reference";

  /**
   * DB가 저장을 거부한 이벤트. 같은 본문을 다시 보내도 성공하지 않는다.
   *
   * <p>FE가 이 값에는 재시도 버튼을 붙이지 않는다.
   */
  public static final String INVALID_EVENT = "invalid_event";

  /**
   * 요청 본문을 JSON으로 읽지 못했다. 400이다.
   *
   * <p>{@link #VALIDATION_ERROR}와 갈라 두는 이유: 저쪽은 <b>구조는 읽혔고 값이 규칙에 걸린</b> 것이라 어느 필드가 왜 걸렸는지 {@code
   * errors}로 짚을 수 있다. 이쪽은 파싱 자체가 실패해서 필드를 짚을 수 없다. FE가 보여줄 문구도 "어느 칸을 고쳐라"가 아니라 "보낸 본문 형식을 확인하라"다.
   *
   * <p>깨진 JSON, 빈 본문, 오프셋 없는 timestamp가 여기 온다. 마지막 것은 Jackson이 값을 못 바꿔서 파싱 단계에서 끊기기 때문이다.
   */
  public static final String MALFORMED_REQUEST_BODY = "malformed_request_body";

  /**
   * 보낸 {@code Content-Type}을 받을 수 없다. 415다.
   *
   * <p>{@link #RESPONSE_TYPE_NOT_ACCEPTABLE}과 방향이 반대다. 이쪽은 <b>요청</b> 본문의 형식, 저쪽은 <b>응답</b>의 형식이다.
   * HTTP 상태(415/406)로도 갈리지만 이름에 방향을 넣어 FE가 헷갈리지 않게 한다. 연동 초기에 가장 흔한 실수가 {@code Content-Type} 누락인데,
   * 그때 브라우저가 붙이는 {@code application/x-www-form-urlencoded}가 여기 걸린다.
   */
  public static final String REQUEST_TYPE_NOT_SUPPORTED = "request_type_not_supported";

  /**
   * {@code Accept}로 만족시킬 응답 표현이 없다. 406이다.
   *
   * <p>방향이 반대인 짝은 {@link #REQUEST_TYPE_NOT_SUPPORTED}다.
   */
  public static final String RESPONSE_TYPE_NOT_ACCEPTABLE = "response_type_not_acceptable";

  /**
   * 그 경로에 그 HTTP 메서드가 없다. 405다.
   *
   * <p>{@link #ENDPOINT_NOT_FOUND}와 갈라 두는 이유: 405는 <b>경로는 있고 메서드가 틀린</b> 것이라 도입사가 고칠 것이 메서드다. 404는
   * 경로 자체가 없다. 둘을 한 code로 뭉개면 "URL을 확인하라"와 "메서드를 확인하라" 중 무엇을 띄울지 FE가 못 고른다.
   */
  public static final String METHOD_NOT_ALLOWED = "method_not_allowed";

  /**
   * 그 경로에 대응하는 엔드포인트가 없다. 404다.
   *
   * <p><b>{@link #UNKNOWN_CUSTOMER_REFERENCE}와 혼동하지 않는다.</b> 이쪽은 <b>주소</b>가 없는 것이고 저쪽은 주소는 맞는데
   * <b>실어 보낸 값</b>이 없는 고객을 가리킨 것이다. 그래서 이쪽이 404, 저쪽이 400이다. MS2-155가 만들 {@code GET
   * /v1/customers/{id}}의 "그 고객이 없다"는 또 다른 경우이고 {@code customer_not_found}를 쓴다.
   */
  public static final String ENDPOINT_NOT_FOUND = "endpoint_not_found";

  /**
   * 경로가 가리킨 고객이 없다. <b>404다</b> (MS2-155).
   *
   * <p>{@link #UNKNOWN_CUSTOMER_REFERENCE}가 이 이름을 비워 두고 개명한 자리다. 저쪽은 {@code /v1/events}의 본문이나 쿼리에
   * 실린 <b>값</b>이 알 수 없는 고객을 가리킨 것이라 400이고, 이쪽은 {@code /v1/customers/&#123;id&#125;}의 경로가 가리키는
   * <b>리소스</b>가 없는 것이라 404다. code 하나는 (HTTP 상태, 의미) 하나만 가리킨다.
   *
   * <p>두 경우가 이 값으로 묶인다. 없는 고객과 다른 도입사 소속. 구별해 답하면 남의 도입사에 그 고객이 있다는 사실이 새어 나간다.
   */
  public static final String CUSTOMER_NOT_FOUND = "customer_not_found";

  /**
   * 사용량 이벤트가 있어 고객을 지울 수 없다. 409다 (MS2-155).
   *
   * <p>FE가 이 값에는 재시도 버튼을 붙이지 않는다. 요청 자체는 형식과 대상 모두 올바르고, 도입사가 요청을 고쳐서 될 일이 아니다.
   */
  public static final String CUSTOMER_HAS_EVENTS = "customer_has_events";

  /**
   * {@code X-Organization-Id}가 등록된 도입사가 아니다. 400이다 (MS2-155).
   *
   * <p>고객 등록의 FK 위반에서만 난다. 이 값이 없으면 헤더 오타 하나가 500이 되고, 5xx는 서버 잘못이라는 신호라 도입사가 자기 헤더를 의심하지 않는다.
   *
   * <p>MS2-126이 Bearer 인증을 붙이면 인증 단계에서 걸러져 이 code는 도달 불가능해진다. 그때 지운다.
   */
  public static final String UNKNOWN_ORGANIZATION = "unknown_organization";

  private ErrorCodes() {}
}
