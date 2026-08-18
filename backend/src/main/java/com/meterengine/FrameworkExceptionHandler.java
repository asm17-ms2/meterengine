package com.meterengine;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 프레임워크가 내는 4xx에 {@code code}를 붙인다 (MS2-150 A-1).
 *
 * <p><b>왜 상속인가.</b> {@code ResponseEntityExceptionHandler}는 프레임워크 예외 20종을 다루고, 그 20종 중 <b>본문이 나가는
 * 19종이 전부 {@link #handleExceptionInternal}을 지난다</b>(바이트코드 실측). 그래서 그 한 자리를 오버라이드하면 상태 코드 결정과 응답 헤더는
 * 프레임워크에 그대로 남기고 {@code code}만 얹을 수 있다. 실측으로 남는 것을 확인한 헤더는 405의 {@code Allow: POST, GET}과 415의
 * {@code Accept: application/json, ...}이다. 지나지 않는 하나({@code AsyncRequestNotUsableException})는 본문이
 * {@code return null}이라 실을 자리가 없다.
 *
 * <p>대안이던 전역 {@code @ExceptionHandler} 방식은 20종 중 6종만 덮었고, 커버리지를 늘리려고 슈퍼타입을 선언하면 {@code
 * ConversionNotSupportedException}(500)이 {@code TypeMismatchException}(400)에 섞여 <b>서버 설정 오류가 "당신
 * 입력이 잘못됐다"는 400으로 둔갑</b>한다. 그래서 상속을 골랐다.
 *
 * <p><b>application.properties의 "advice는 상속하지 말 것" [주의]는 이 클래스로 무효가 됐다.</b> 그 문장은 사고로 상속했을 때 자동 설정이
 * 물러난다는 경고였고, 여기서는 물러나게 하는 것이 의도다.
 * {@code @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)}는 버그가 아니라 설계된 인수인계 지점이다.
 *
 * <p><b>{@code @Order(0)}인 이유.</b> 대체하는 자동 설정 핸들러가 {@code @Order(0)}이라 그 상대 위치를 그대로 물려받는다. 도메인
 * advice({@code EventExceptionHandler})는 {@code HIGHEST_PRECEDENCE}라 여전히 먼저 걸린다. 다만 <b>이 값이 없어도 지금은
 * 동작이 같다.</b> 도메인 advice가 잡는 두 예외({@code UnknownCustomerException}, {@code
 * DataIntegrityViolationException})가 위 20종에 없어 겹치는 예외가 0개다. 숫자를 박는 것은 나중에 겹치는 도메인 예외가 생겼을 때를 위한
 * 것이고, "안 하면 지금 깨진다"는 아니다.
 *
 * <p><b>4xx만 건드린다.</b> 5xx는 본문 형식을 약속하지 않기로 했다(MS2-150 B-3). {@code
 * spring.mvc.problemdetails.enabled}가 일부 5xx도 problem+json으로 만들지만, FE는 5xx에서 상태 코드만 쓴다. 쓰지 않기로 한
 * 자리에 {@code code}를 넣으면 계약에 잡음만 늘어난다.
 *
 * <p><b>{@code detail}을 우리 문구로 덮는다.</b> 프레임워크 기본 문구가 도입사가 보낸 값을 반사하는 경우가 있다({@code
 * MethodArgumentTypeMismatchException}의 "Failed to convert 'month' with value: '2026-13'"). 값을
 * 되돌려주지 않는다는 정책(MS2-150 B-4)을 지키려면 그 자리를 우리가 채워야 한다. 무엇이 틀렸는지는 {@code errors}의 필드 이름과 사유로 충분하다.
 */
@ControllerAdvice
@Order(0)
class FrameworkExceptionHandler extends ResponseEntityExceptionHandler {

  /**
   * 파라미터 이름을 끝내 못 얻었을 때 {@code field}에 넣는 값 (MS2-150 5단계 결정).
   *
   * <p><b>지금 빌드에서는 도달하지 않는다.</b> 이 값이 나오려면 {@code MethodParameter.getParameterName()}이 {@code
   * null}이어야 하고, 그것은 {@code -parameters} 없이 컴파일했을 때다. Boot Gradle 플러그인이 그 플래그를 기본으로 넣고, 실제로 {@code
   * MethodParameters} 속성이 클래스 파일에 있는 것을 확인했다. 플래그가 빠지면 {@code @RequestParam(name=...)}을 안 준 파라미터의
   * 바인딩부터 깨지므로 이 값보다 먼저 티가 난다.
   *
   * <p><b>그래도 {@code field}를 빼지 않는 이유.</b> 도달 불가인 경우 때문에 {@code field}를 optional로 낮추면 FE가 실제로는 늘 있는
   * 값을 없을 수도 있는 값으로 다뤄야 한다. 계약을 약하게 만드는 대가가 방어값 하나보다 크다. {@link ProblemFieldError}의 {@code field}는
   * required로 둔다.
   */
  private static final String UNRESOLVED_FIELD = "unknown";

  /**
   * 프레임워크가 만든 본문에 {@code code}와 {@code errors}를 얹는다.
   *
   * <p><b>{@code super}를 먼저 부른다.</b> 넘어온 {@code body}가 {@code null}일 수 있고 그때는 부모가 본문을 만든다. 결과를 받아
   * 얹으면 어느 경로로 왔든 같은 자리에서 처리된다. {@code ProblemDetail}이 가변 객체라 가능하다.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(exception, body, headers, statusCode, request);

    if (response == null
        || !statusCode.is4xxClientError()
        || !(response.getBody() instanceof ProblemDetail problem)) {
      return response;
    }

    String code = codeFor(exception);
    if (code == null) {
      return response;
    }
    problem.setProperty(ProblemMembers.CODE, code);
    problem.setDetail(detailFor(code));

    List<Map<String, String>> errors = fieldErrors(exception);
    if (!errors.isEmpty()) {
      problem.setProperty(ProblemMembers.ERRORS, errors);
    }
    return response;
  }

  /**
   * 예외를 {@code code}로 옮긴다. 아는 것만 옮기고 모르면 {@code null}이다.
   *
   * <p><b>왜 fallback을 두지 않나.</b> 모르는 예외를 {@code validation_error}로 뭉개면 서버 쪽 문제를 "당신 입력이 잘못됐다"로 답할 수
   * 있다. code 하나가 (상태, 의미) 하나만 가리킨다는 규칙(MS2-150 A-3)도 어긴다. 그래서 모르면 {@code code}를 안 붙이고, FE는 이미 정해 둔
   * "code 부재 시 기본 문구"로 떨어진다.
   *
   * <p><b>도달 가능한 4xx는 실측으로 열거했다</b>(MS2-150 [1], 4xx 26건). 400(검증 + 본문 파싱), 404, 405, 406, 415가
   * 전부다. 413({@code MaxUploadSizeExceededException})은 업로드 엔드포인트가 없어 도달하지 않는다. 그래서 {@code null}로
   * 떨어지는 경로는 지금 없다.
   */
  private static String codeFor(Exception exception) {
    return switch (exception) {
      case HttpMessageNotReadableException ignored -> ErrorCodes.MALFORMED_REQUEST_BODY;
      case HttpMediaTypeNotSupportedException ignored -> ErrorCodes.REQUEST_TYPE_NOT_SUPPORTED;
      case HttpMediaTypeNotAcceptableException ignored -> ErrorCodes.RESPONSE_TYPE_NOT_ACCEPTABLE;
      case HttpRequestMethodNotSupportedException ignored -> ErrorCodes.METHOD_NOT_ALLOWED;
      case NoResourceFoundException ignored -> ErrorCodes.ENDPOINT_NOT_FOUND;
      case MethodArgumentNotValidException ignored -> ErrorCodes.VALIDATION_ERROR;
      case HandlerMethodValidationException ignored -> ErrorCodes.VALIDATION_ERROR;
      case MissingRequestHeaderException ignored -> ErrorCodes.VALIDATION_ERROR;
      case MethodArgumentTypeMismatchException ignored -> ErrorCodes.VALIDATION_ERROR;
      default -> null;
    };
  }

  /**
   * code마다 고정 문구를 준다. 프레임워크 기본 문구가 값을 반사하는 것을 막는다 (클래스 javadoc 참조).
   *
   * <p>{@code default}는 도달하지 않는다. {@link #codeFor}가 아는 code만 돌려주고 모르면 {@code null}을 주며, 호출부가 그때 이
   * 메서드를 부르지 않는다. 그래도 문구를 하나 둔 이유는 새 code를 {@code codeFor}에만 추가하고 여기 빠뜨렸을 때 <b>빈 문자열이나 예외가 아니라 안전한
   * 기본 문구</b>가 나가게 하려는 것이다. 그 누락은 인수기준 6의 실제 응답 단언이 잡는다.
   */
  private static String detailFor(String code) {
    return switch (code) {
      case ErrorCodes.VALIDATION_ERROR -> "the request could not be accepted as sent";
      case ErrorCodes.MALFORMED_REQUEST_BODY -> "the request body could not be read as JSON";
      case ErrorCodes.REQUEST_TYPE_NOT_SUPPORTED -> "the request Content-Type is not supported";
      case ErrorCodes.RESPONSE_TYPE_NOT_ACCEPTABLE -> "no representation matches the Accept header";
      case ErrorCodes.METHOD_NOT_ALLOWED -> "the method is not allowed on this path";
      case ErrorCodes.ENDPOINT_NOT_FOUND -> "no endpoint matches this path";
      default -> "the request could not be accepted as sent";
    };
  }

  /**
   * 필드 단위로 짚을 수 있는 실수에 {@code errors}를 붙인다.
   *
   * <p><b>프레임워크 기본 처리는 이것을 만들지 않는다.</b> {@code MethodArgumentNotValidException}의 기본 {@code detail}은
   * 필수 필드 다섯 개 중 무엇이 빠져도 "Invalid request content."로 같아서, 도입사가 400을 받고도 무엇을 고칠지 알 수 없다. 그래서 네 예외
   * 타입에서 각각 필드 오류를 다시 뽑는다. 이 코드는 {@code EventExceptionHandler}에서 옮겨 온 것이다 (MS2-131에서 만들었다).
   *
   * <p><b>{@code field}는 와이어 이름으로 통일한다</b> (MS2-150 5단계, 팀 결정 A-2). 검증은 자바 이름 위에서 도는데 도입사가 고칠 것은
   * 자기가 보낸 JSON 키와 쿼리 파라미터다. 자바 이름을 그대로 돌려주면 {@code eventType}을 받은 도입사가 {@code event_type}을 고쳐야 한다는
   * 것을 스스로 알아내야 한다. 출처마다 되찾는 방법이 달라 {@link #wireName}과 {@link #parameterWireName}으로 갈라 둔다.
   */
  private List<Map<String, String>> fieldErrors(Exception exception) {
    return switch (exception) {
      case MethodArgumentNotValidException failure ->
          failure.getBindingResult().getFieldErrors().stream()
              .map(
                  (FieldError error) ->
                      entry(
                          wireName(failure.getBindingResult().getTarget(), error.getField()),
                          messageOr(error.getDefaultMessage())))
              .toList();
      case HandlerMethodValidationException failure ->
          failure.getParameterValidationResults().stream()
              .flatMap(
                  result ->
                      result.getResolvableErrors().stream()
                          .map(
                              error ->
                                  entry(
                                      parameterWireName(result.getMethodParameter()),
                                      messageOr(error.getDefaultMessage()))))
              .toList();
      case MissingRequestHeaderException failure ->
          List.of(entry(failure.getHeaderName(), message("problem.field.required")));
      case MethodArgumentTypeMismatchException failure ->
          List.of(entry(failure.getName(), cannotBeParsed(failure.getRequiredType())));
      default -> List.of();
    };
  }

  /**
   * 우리가 만드는 {@code errors[].message}를 로케일에 맞춰 꺼낸다 (MS2-150 6단계).
   *
   * <p><b>{@code MessageSource}는 부모가 이미 들고 있다.</b> {@code ResponseEntityExceptionHandler}가 {@code
   * MessageSourceAware}를 구현해서 Spring이 넣어 주고, {@code getMessageSource()}로 꺼낸다. 따로 주입하면 같은 빈이 두 경로로
   * 들어와 설정이 갈릴 자리가 생긴다.
   *
   * <p>로케일은 {@code LocaleContextHolder}에서 온다. 요청이 {@code Accept-Language}를 보냈으면 그것이고, 안 보냈으면 {@code
   * spring.web.locale}로 못박은 ko다 (application.properties 참조).
   *
   * <p>{@code getMessageSource()}가 {@code null}인 경우를 받아 두는 이유는 이 클래스가 빈으로 만들어지지 않은 채 단위 테스트에서 직접
   * 호출될 수 있어서다. 그때는 키를 그대로 돌려주어 무엇이 빠졌는지 보이게 한다.
   */
  private String message(String code, Object... arguments) {
    MessageSource source = getMessageSource();
    if (source == null) {
      return code;
    }
    return source.getMessage(code, arguments, code, LocaleContextHolder.getLocale());
  }

  private static Map<String, String> entry(String field, String message) {
    return Map.of(ProblemMembers.FIELD, field, ProblemMembers.MESSAGE, message);
  }

  /**
   * 본문 검증의 자바 필드명을 JSON 키로 되돌린다.
   *
   * <p><b>{@code @JsonProperty}는 선언한 record 컴포넌트가 아니라 그것이 만든 private 필드에서 읽는다.</b> {@code
   * JsonProperty}의 {@code @Target}에 {@code RECORD_COMPONENT}가 없어서 {@code
   * RecordComponent.getAnnotation}은 이 애노테이션을 못 돌려준다. 컴파일러가 같은 애노테이션을 필드와 접근자와 생성자 파라미터에 복사해
   * 두므로(바이트코드 실측) 그중 필드를 읽는다.
   *
   * <p>애노테이션이 없는 필드({@code properties}, {@code timestamp})는 Jackson도 자바 이름을 그대로 키로 쓰므로 자바 이름이 곧 와이어
   * 이름이다. 그래서 못 찾았을 때 넘어온 이름을 그대로 돌려주는 것이 맞는 기본값이다.
   *
   * <p>중첩 경로({@code properties.foo})는 손대지 않는다. 지금 중첩 검증을 하는 DTO가 없고, 첫 마디만 바꾸면 나머지 마디와 표기가 섞여 오히려
   * 읽기 어려워진다. 중첩 검증이 생기면 그때 마디별로 다시 푼다.
   */
  private static String wireName(Object target, String javaField) {
    if (target == null) {
      return javaField;
    }
    try {
      Field field = target.getClass().getDeclaredField(javaField);
      JsonProperty annotation = field.getAnnotation(JsonProperty.class);
      return annotation == null || annotation.value().isEmpty() ? javaField : annotation.value();
    } catch (NoSuchFieldException | SecurityException ignored) {
      return javaField;
    }
  }

  /**
   * 파라미터 제약 위반의 자바 파라미터명을 요청에 실린 이름으로 되돌린다.
   *
   * <p>{@code name}과 {@code value}를 둘 다 본다. 둘은 {@code @AliasFor}로 묶인 같은 자리인데, {@code
   * MethodParameter.getParameterAnnotations}는 리플렉션 원본을 주고 별칭을 합성하지 않아 {@code @RequestParam("x")}로 쓴
   * 경우 {@code name()}이 빈 문자열이다. 한쪽만 보면 그 표기법이 조용히 자바 이름으로 떨어진다.
   *
   * <p>이름을 안 준 파라미터({@code page}, {@code size})는 자바 이름이 곧 요청 이름이라 그대로 쓴다.
   *
   * <p><b>{@code @RequestHeader} 가지는 지금 도달하지 않는다.</b> 제약이 붙은 헤더 파라미터가 있어야 이 예외로 오는데 아직 없다. 헤더가 틀렸을
   * 때 실제로 오는 경로는 누락({@code MissingRequestHeaderException})과 타입 불일치({@code
   * MethodArgumentTypeMismatchException})이고 둘 다 헤더 이름을 직접 들고 있어 이 메서드를 지나지 않는다(두 경우 다 테스트로 확인). 그래도
   * 남기는 이유는 {@code @RequestHeader}에 {@code @Size} 하나만 붙어도 자바 이름이 조용히 새기 때문이다.
   */
  private static String parameterWireName(MethodParameter parameter) {
    RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
    if (requestParam != null) {
      String explicit = requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
      if (!explicit.isEmpty()) {
        return explicit;
      }
    }
    RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
    if (requestHeader != null) {
      String explicit =
          requestHeader.name().isEmpty() ? requestHeader.value() : requestHeader.name();
      if (!explicit.isEmpty()) {
        return explicit;
      }
    }
    String name = parameter.getParameterName();
    return name == null ? UNRESOLVED_FIELD : name;
  }

  /**
   * Bean Validation이 준 문구를 그대로 쓴다. 없을 때만 우리 기본값으로 떨어진다.
   *
   * <p>제약 문구를 우리가 번역하지 않는 이유는 messages.properties에 적어 뒀다. 요약하면 Hibernate Validator가 ko 번들을 이미 들고
   * 있고, 덮어쓰면 제약이 늘 때마다 번역 누락이 영어로 샌다.
   */
  private String messageOr(String message) {
    return message == null ? message("problem.field.invalid") : message;
  }

  private String cannotBeParsed(Class<?> requiredType) {
    return requiredType == null
        ? message("problem.field.unparseable")
        : message("problem.field.type-mismatch", requiredType.getSimpleName());
  }
}
